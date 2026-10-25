package org.sitenetsoft.quarkus.activitypub.test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.sitenetsoft.activitystreams.ASJson;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.httpsignatures.CavageSignature;
import org.sitenetsoft.httpsignatures.Headers;
import org.sitenetsoft.httpsignatures.HttpMessageView;
import org.sitenetsoft.httpsignatures.HttpSignatureException;
import org.sitenetsoft.httpsignatures.HttpVerifier;
import org.sitenetsoft.httpsignatures.Keys;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.httpsignatures.StructuredFields;
import org.sitenetsoft.httpsignatures.VerificationKey;
import org.sitenetsoft.httpsignatures.VerificationPolicy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A remote "Mastodon" for tests: a plain Vert.x HTTP server on loopback and a random port, serving WebFinger, actor
 * documents and objects, with inboxes that verify HTTP signatures the way a Fediverse server does: the key id's
 * document is fetched (plain {@link WebClient} GET, {@code Accept: application/activity+json}) and its
 * {@code publicKey.publicKeyPem} checked with {@link HttpVerifier}. Every request (signed GETs included) is verified the
 * same way and recorded.
 * <p>
 * Routes: {@code GET /.well-known/webfinger}, {@code GET /users/{u}} (or a redirect, see
 * {@link #redirect(String, URI)}), {@code GET /objects/{id}},
 * {@code POST /users/{u}/inbox}, {@code POST /inbox}. Inboxes answer {@code 401} to an unsigned or unverifiable request,
 * otherwise {@link #inboxStatus(int)} (default {@code 202}).
 */
public final class FakeFediverseServer {

    static final String ACTIVITY_JSON = "application/activity+json";
    static final ObjectMapper MAPPER = ASJson.mapper();
    private static final String VERIFIED = FakeFediverseServer.class.getName() + ".verified";

    private final String host;
    private final Map<String, FakeActor> actors = new ConcurrentHashMap<>();
    private final Map<String, ASObject> objects = new ConcurrentHashMap<>();
    private final Map<String, URI> redirects = new ConcurrentHashMap<>();
    private final List<ReceivedRequest> received = new CopyOnWriteArrayList<>();
    private volatile boolean rejectCavage;
    private volatile int inboxStatus = 202;
    private volatile Duration inboxDelay = Duration.ZERO;
    private Vertx vertx;
    private HttpServer server;
    private WebClient client;

    /** A fake whose URLs use {@code 127.0.0.1}. */
    public FakeFediverseServer() {
        this("127.0.0.1");
    }

    /**
     * A fake whose URLs use {@code host} (for example {@code localhost}); it still listens on {@code 127.0.0.1} only,
     * so {@code host} must resolve there.
     */
    public FakeFediverseServer(String host) {
        this.host = host;
    }

    /** Starts on a random free port. */
    public FakeFediverseServer start() {
        return start(0);
    }

    /** Starts on {@code port} ({@code 0} for a random one), for tests that must put the port into config first. */
    public synchronized FakeFediverseServer start(int port) {
        if (server != null) {
            throw new IllegalStateException("Already started on port " + port());
        }
        vertx = Vertx.vertx();
        client = WebClient.create(vertx, new WebClientOptions().setFollowRedirects(false).setConnectTimeout(5_000));
        server = vertx.createHttpServer().requestHandler(router());
        server.listen(port, "127.0.0.1").toCompletionStage().toCompletableFuture().join();
        return this;
    }

    public synchronized void stop() {
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
            vertx = null;
            server = null;
            client = null;
        }
    }

    public int port() {
        if (server == null) {
            throw new IllegalStateException("FakeFediverseServer is not started");
        }
        return server.actualPort();
    }

    /** {@code http://{host}:{port}}. */
    public URI baseUri() {
        return URI.create("http://" + host + ":" + port());
    }

    public URI sharedInbox() {
        return URI.create(baseUri() + "/inbox");
    }

    /** The actor {@code username}, created (with a fresh RSA key) on first use. */
    public FakeActor actor(String username) {
        return actors.computeIfAbsent(username, u -> new FakeActor(this, u));
    }

    /** Every request received so far, oldest first. */
    public List<ReceivedRequest> received() {
        return List.copyOf(received);
    }

    /** How many received requests have a path starting with {@code pathPrefix}. */
    public int requestCount(String pathPrefix) {
        return (int) received.stream().filter(r -> r.path().startsWith(pathPrefix)).count();
    }

    /** Forgets the received requests (actors, objects and knobs stay). */
    public void clear() {
        received.clear();
    }

    /** Answer {@code 401} to every Cavage-signed request, like a server that only knows RFC 9421. */
    public FakeFediverseServer rejectCavage(boolean value) {
        rejectCavage = value;
        return this;
    }

    /** The status inboxes answer to a verified request. */
    public FakeFediverseServer inboxStatus(int status) {
        inboxStatus = status;
        return this;
    }

    /** Delays inbox answers. */
    public FakeFediverseServer delayInbox(Duration delay) {
        inboxDelay = delay;
        return this;
    }

    /** Answers {@code GET /users/{username}} with {@code 302 Location: location} (before any actor of that name). */
    public FakeFediverseServer redirect(String username, URI location) {
        redirects.put(username, location);
        return this;
    }

    void addObject(ASObject object) {
        String path = object.id().getRawPath();
        if (!object.id().getHost().equalsIgnoreCase(host) || object.id().getPort() != port() || path == null
                || !path.startsWith("/objects/")) {
            throw new IllegalArgumentException("Object id must be under " + baseUri() + "/objects/: " + object.id());
        }
        objects.put(path, object);
    }

    private Router router() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route().handler(this::verifyAndRecord);
        router.get("/.well-known/webfinger").handler(this::webfinger);
        router.get("/users/:username").handler(ctx -> {
            URI location = redirects.get(ctx.pathParam("username"));
            if (location != null) {
                ctx.response().setStatusCode(302).putHeader("Location", location.toString()).end();
                return;
            }
            FakeActor actor = actors.get(ctx.pathParam("username"));
            if (actor == null) {
                ctx.response().setStatusCode(404).end();
            } else {
                json(ctx, 200, actor.document());
            }
        });
        router.get("/objects/:id").handler(ctx -> {
            ASObject object = objects.get(ctx.request().path());
            if (object == null) {
                ctx.response().setStatusCode(404).end();
            } else {
                json(ctx, 200, MAPPER.valueToTree(object));
            }
        });
        router.post("/users/:username/inbox").handler(this::inbox);
        router.post("/inbox").handler(this::inbox);
        return router;
    }

    /**
     * Verifies the signature, if any, against the key fetched from its key id, records the request and continues;
     * Cavage signatures get {@code 401} while {@link #rejectCavage(boolean)} is on.
     */
    private void verifyAndRecord(RoutingContext ctx) {
        HttpServerRequest req = ctx.request();
        Buffer buffer = ctx.body().buffer();
        byte[] body = buffer == null || buffer.length() == 0 ? null : buffer.getBytes();
        Map<String, List<String>> headers = headers(req);
        HttpMessageView msg = HttpMessageView.of(req.method().name(), URI.create(req.absoluteURI()), Headers.of(headers),
                body);
        Optional<Scheme> scheme = scheme(msg);
        Optional<String> keyId = scheme.flatMap(s -> keyId(msg, s));
        if (keyId.isEmpty()) {
            record(req, headers, body, false, null);
            ctx.put(VERIFIED, false);
            if (scheme.isPresent() && rejectCavage && scheme.get() == Scheme.CAVAGE) {
                ctx.response().setStatusCode(401).end();
                return;
            }
            ctx.next();
            return;
        }
        if (rejectCavage && scheme.get() == Scheme.CAVAGE) {
            record(req, headers, body, false, keyId.get());
            ctx.response().setStatusCode(401).end("Cavage signatures are not accepted");
            return;
        }
        fetchKey(keyId.get()).onComplete(ar -> {
            boolean verified = ar.succeeded() && ar.result().isPresent() && verifies(msg, ar.result().get());
            record(req, headers, body, verified, keyId.get());
            ctx.put(VERIFIED, verified);
            ctx.next();
        });
    }

    private static boolean verifies(HttpMessageView msg, VerificationKey key) {
        try {
            new HttpVerifier().verify(msg,
                    VerificationPolicy.defaults(id -> id.equals(key.keyId()) ? Optional.of(key) : Optional.empty()));
            return true;
        } catch (HttpSignatureException e) {
            return false;
        }
    }

    private void record(HttpServerRequest req, Map<String, List<String>> headers, byte[] body, boolean verified,
            String keyId) {
        received.add(new ReceivedRequest(req.method().name(), req.path(), headers, body, verified, keyId));
    }

    /** The key {@code keyId} names: its document fetched without the fragment, {@code publicKey} object or array. */
    private Future<Optional<VerificationKey>> fetchKey(String keyId) {
        URI uri;
        try {
            uri = URI.create(keyId);
        } catch (IllegalArgumentException e) {
            return Future.succeededFuture(Optional.empty());
        }
        String document = uri.getFragment() == null ? keyId : keyId.substring(0, keyId.indexOf('#'));
        return client.getAbs(document).putHeader("Accept", ACTIVITY_JSON).send()
                .map(response -> response.statusCode() == 200 ? keyFrom(response.bodyAsString(), keyId)
                        : Optional.<VerificationKey> empty())
                .otherwise(Optional.empty());
    }

    private static Optional<VerificationKey> keyFrom(String document, String keyId) {
        try {
            return publicKeyPem(MAPPER.readTree(document), keyId).flatMap(pem -> {
                Key key = Keys.parsePem(pem);
                return key instanceof PublicKey pub ? Optional.of(new VerificationKey(keyId, Optional.empty(), pub))
                        : Optional.<VerificationKey> empty();
            });
        } catch (java.io.IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> publicKeyPem(JsonNode document, String keyId) {
        List<JsonNode> candidates = new ArrayList<>();
        JsonNode publicKey = document.get("publicKey");
        if (publicKey != null && publicKey.isArray()) {
            publicKey.forEach(candidates::add);
        } else if (publicKey != null && publicKey.isObject()) {
            candidates.add(publicKey);
        } else {
            candidates.add(document); // the key id names a key document
        }
        for (JsonNode candidate : candidates) {
            if (keyId.equals(candidate.path("id").asText()) && candidate.path("publicKeyPem").isTextual()) {
                return Optional.of(candidate.get("publicKeyPem").asText());
            }
        }
        return Optional.empty();
    }

    private static Optional<Scheme> scheme(HttpMessageView msg) {
        try {
            return Optional.of(HttpVerifier.detect(msg));
        } catch (HttpSignatureException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> keyId(HttpMessageView msg, Scheme scheme) {
        try {
            if (scheme == Scheme.CAVAGE) {
                return Optional.of(CavageSignature.parse(msg.headers().combined("Signature")).keyId());
            }
            for (Object value : StructuredFields.parseDictionary(msg.headers().combined("Signature-Input")).values()) {
                if (value instanceof StructuredFields.InnerList list && list.params().get("keyid") instanceof String id) {
                    return Optional.of(id);
                }
            }
        } catch (RuntimeException e) {
            // unreadable signature: treated as unsigned
        }
        return Optional.empty();
    }

    private void webfinger(RoutingContext ctx) {
        String resource = ctx.request().getParam("resource");
        FakeActor actor = null;
        if (resource != null && resource.startsWith("acct:") && resource.lastIndexOf('@') > 5) {
            String user = URLDecoder.decode(resource.substring(5, resource.lastIndexOf('@')), StandardCharsets.UTF_8);
            actor = actors.get(user);
        } else if (resource != null) {
            actor = actors.values().stream().filter(a -> a.id().toString().equals(resource)).findFirst().orElse(null);
        }
        if (actor == null) {
            ctx.response().setStatusCode(404).end();
            return;
        }
        ObjectNode jrd = MAPPER.createObjectNode();
        jrd.put("subject", "acct:" + actor.username() + "@" + baseUri().getAuthority());
        jrd.putArray("aliases").add(actor.id().toString());
        ObjectNode self = jrd.putArray("links").addObject();
        self.put("rel", "self").put("type", ACTIVITY_JSON).put("href", actor.id().toString());
        ctx.response().setStatusCode(200).putHeader("Content-Type", "application/jrd+json").end(jrd.toString());
    }

    private void inbox(RoutingContext ctx) {
        if (!Boolean.TRUE.equals(ctx.get(VERIFIED))) {
            ctx.response().setStatusCode(401).end();
            return;
        }
        int status = inboxStatus;
        long delay = inboxDelay.toMillis();
        if (delay > 0) {
            vertx.setTimer(delay, id -> ctx.response().setStatusCode(status).end());
        } else {
            ctx.response().setStatusCode(status).end();
        }
    }

    private static void json(RoutingContext ctx, int status, JsonNode body) {
        ctx.response().setStatusCode(status).putHeader("Content-Type", ACTIVITY_JSON).end(body.toString());
    }

    private static Map<String, List<String>> headers(HttpServerRequest req) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        req.headers().forEach(e -> map.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return map;
    }
}

package org.sitenetsoft.quarkus.activitypub.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Endpoints;
import org.sitenetsoft.activitystreams.Person;
import org.sitenetsoft.httpsignatures.Algorithm;
import org.sitenetsoft.httpsignatures.Headers;
import org.sitenetsoft.httpsignatures.HttpMessageView;
import org.sitenetsoft.httpsignatures.HttpSigner;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.httpsignatures.SignatureSpec;
import org.sitenetsoft.httpsignatures.SigningKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A remote actor living on a {@link FakeFediverseServer}: an RSA 2048 {@code Person} served at
 * {@code {base}/users/{username}} with its key {@code {actor}#main-key}. Tests use it to play the remote side (sign
 * and post activities) and to shape its actor document (knobs below).
 */
public final class FakeActor {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Base64.Encoder PEM = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));

    /** Headers to send with a request this actor signed: {@code Host}, {@code Date}, {@code Content-Type} (with a body),
     * the digest the scheme needs and the signature headers. */
    public record SignedRequest(Map<String, String> headers) {
        public SignedRequest {
            headers = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        }
    }

    private final FakeFediverseServer server;
    private final String username;
    private final URI id;
    private final KeyPair keys;
    private final String publicKeyPem;
    private volatile URI documentId;
    private volatile URI publicKeyOwner;
    private volatile boolean publicKeyAsArray;

    FakeActor(FakeFediverseServer server, String username) {
        this.server = server;
        this.username = username;
        this.id = URI.create(server.baseUri() + "/users/" + username);
        this.keys = generateRsa2048();
        this.publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + PEM.encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    public String username() {
        return username;
    }

    /** The URL the actor is served at (and its {@code id}, unless {@link #withId(URI)} changed the document). */
    public URI id() {
        return id;
    }

    public URI inbox() {
        return URI.create(id + "/inbox");
    }

    public URI outbox() {
        return URI.create(id + "/outbox");
    }

    public String keyId() {
        return id + "#main-key";
    }

    public PublicKey publicKey() {
        return keys.getPublic();
    }

    /** Writes {@code publicKey} as a one-element array instead of an object. */
    public FakeActor publicKeyAsArray(boolean value) {
        publicKeyAsArray = value;
        return this;
    }

    /** Claims another {@code publicKey.owner} than the actor itself. */
    public FakeActor publicKeyOwner(URI owner) {
        publicKeyOwner = owner;
        return this;
    }

    /** Serves the document with another {@code id} (and key owner), as a server lying about an actor would. */
    public FakeActor withId(URI foreignId) {
        documentId = foreignId;
        return this;
    }

    /**
     * Serves {@code object} at {@code {base}/objects/{n}}, giving it that id when it has none. An object with an id
     * must live under {@code {base}/objects/}.
     */
    public <T extends ASObject> T addObject(T object) {
        if (object.id() == null) {
            object.id(URI.create(server.baseUri() + "/objects/" + UUID.randomUUID()));
        }
        server.addObject(object);
        return object;
    }

    /** The actor document as the fake serves it. */
    JsonNode document() {
        URI docId = documentId != null ? documentId : id;
        Person person = new Person();
        person.id(docId);
        person.preferredUsername(username)
                .inbox(inbox())
                .outbox(outbox())
                .endpoints(new Endpoints().sharedInbox(server.sharedInbox()));
        org.sitenetsoft.activitystreams.PublicKey key = new org.sitenetsoft.activitystreams.PublicKey()
                .owner(publicKeyOwner != null ? publicKeyOwner : docId).publicKeyPem(publicKeyPem);
        key.id(URI.create(docId + "#main-key"));
        person.publicKey(key);
        ObjectNode json = FakeFediverseServer.MAPPER.valueToTree(person);
        if (publicKeyAsArray && json.get("publicKey").isObject()) {
            json.putArray("publicKey").add(FakeFediverseServer.MAPPER.valueToTree(key));
        }
        return json;
    }

    /** Signs a request from this actor with Cavage {@code rsa-sha256} or RFC 9421 {@code rsa-v1_5-sha256}. */
    public SignedRequest sign(String method, URI target, byte[] body, Scheme scheme) {
        Objects.requireNonNull(scheme, "scheme");
        boolean hasBody = body != null && body.length > 0;
        Instant now = Instant.now();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", target.getPort() == -1 ? target.getHost() : target.getHost() + ":" + target.getPort());
        headers.put("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(now.atOffset(ZoneOffset.UTC)));
        if (hasBody) {
            headers.put("Content-Type", FakeFediverseServer.ACTIVITY_JSON);
        }
        SignatureSpec spec = scheme == Scheme.RFC9421
                ? SignatureSpec.rfc9421(keyId(), Algorithm.RSA_V1_5_SHA256, now, hasBody)
                : SignatureSpec.cavage(keyId(), Algorithm.RSA_SHA256, now, hasBody);
        Map<String, List<String>> view = new LinkedHashMap<>();
        headers.forEach((k, v) -> view.put(k, List.of(v)));
        HttpMessageView msg = HttpMessageView.of(method, target, Headers.of(view), body);
        new HttpSigner().sign(msg, spec, new SigningKey(keyId(), spec.alg(), keys.getPrivate())).headers()
                .forEach(headers::put);
        return new SignedRequest(headers);
    }

    /** POSTs {@code activity} to {@code inbox}, signed by this actor. */
    public HttpResponse<byte[]> post(URI inbox, ASObject activity, Scheme scheme) {
        byte[] body;
        try {
            body = FakeFediverseServer.MAPPER.writeValueAsBytes(activity);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialise " + activity.id(), e);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(inbox).timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        // java.net.http sets Host itself, to the same host:port that was signed
        sign("POST", inbox, body, scheme).headers().forEach((name, value) -> {
            if (!name.equalsIgnoreCase("Host")) {
                request.header(name, value);
            }
        });
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("POST to " + inbox + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while posting to " + inbox, e);
        }
    }

    @Override
    public String toString() {
        return "FakeActor[" + id + "]";
    }

    private static KeyPair generateRsa2048() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key generation unavailable", e);
        }
    }
}

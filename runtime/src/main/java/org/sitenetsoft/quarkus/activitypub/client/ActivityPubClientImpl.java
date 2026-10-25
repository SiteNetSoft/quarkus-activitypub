package org.sitenetsoft.quarkus.activitypub.client;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.httpsignatures.Headers;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubException;
import org.sitenetsoft.quarkus.activitypub.ActivityPubException.Reason;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.ActivityPubMediaTypes;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.ActorDocuments;
import org.sitenetsoft.quarkus.activitypub.actor.InstanceActor;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.inbox.DomainPolicy;
import org.sitenetsoft.quarkus.activitypub.keys.KeyIds;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.httpsignatures.client.RequestSigner;
import org.sitenetsoft.quarkus.safefetch.FetchOptions;
import org.sitenetsoft.quarkus.safefetch.FetchResponse;
import org.sitenetsoft.quarkus.safefetch.ReactiveSafeFetchClient;
import org.sitenetsoft.quarkus.safefetch.SafeFetchException;
import org.sitenetsoft.quarkus.webfinger.AcctUri;
import org.sitenetsoft.quarkus.webfinger.Link;
import org.sitenetsoft.quarkus.webfinger.client.ReactiveWebFingerClient;
import org.sitenetsoft.quarkus.webfinger.client.WebFingerException;
import org.sitenetsoft.quarkus.webfinger.client.WebFingerNotFoundException;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * The {@link ReactiveActivityPubClient}; {@link BlockingActivityPubClient} awaits it.
 * <p>
 * Remote GETs carry {@code Accept} (ActivityPub media types), {@code User-Agent} and, when the instance actor is
 * enabled, its signature ({@code Date}, {@code Host} and {@code (request-target)} signed with the scheme the host
 * accepted before, else Cavage, which Mastodon's secure mode verifies), always with the instance actor's own key: a
 * request that would be signed with any other key fails with {@code FETCH_FAILED}. Signing reads the actor store, so
 * it runs on a worker thread. Hosts blocked by {@link DomainPolicy} are never fetched ({@code BLOCKED}).
 */
@ApplicationScoped
public class ActivityPubClientImpl implements ReactiveActivityPubClient {

    private static final Set<String> SELF = Set.of("self");

    @Inject
    ActivityPubConfig config;

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Inject
    ActorDocuments documents;

    @Inject
    ActivityPubJson json;

    @Inject
    InstanceActor instanceActor;

    @Inject
    RemoteCache cache;

    @Inject
    ReactiveSafeFetchClient safeFetch;

    @Inject
    ReactiveWebFingerClient webFinger;

    @Inject
    RequestSigner signer;

    @Inject
    DomainPolicy domains;

    @Override
    public Uni<ASObject> fetch(URI id) {
        return fetch(id, ASObject.class);
    }

    @Override
    public <T extends ASObject> Uni<T> fetch(URI id, Class<T> type) {
        if (id == null || !id.isAbsolute() || id.getHost() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Not an absolute URL: " + id));
        }
        Uni<ASObject> found = urls.isLocal(id) ? local(id) : remote(id);
        return found.map(object -> as(id, object, type));
    }

    @Override
    public Uni<Actor> fetchActor(URI id) {
        return fetch(id, Actor.class);
    }

    @Override
    public Uni<Actor> resolveActor(String acctOrUri) {
        String value = acctOrUri == null ? "" : acctOrUri.strip();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            URI uri;
            try {
                uri = URI.create(value);
            } catch (IllegalArgumentException e) {
                return Uni.createFrom().failure(e);
            }
            return fetchActor(uri);
        }
        AcctUri acct;
        try {
            acct = AcctUri.parse(value);
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().failure(e);
        }
        if (acct.host().equalsIgnoreCase(urls.host())) {
            return fetchActor(urls.actor(acct.user()));
        }
        return webFinger.lookup(acct, SELF)
                .onFailure(WebFingerNotFoundException.class).transform(e -> new ActivityPubException(Reason.NOT_FOUND,
                        "No WebFinger account " + acct, e))
                .onFailure(WebFingerException.class).transform(e -> new ActivityPubException(Reason.FETCH_FAILED,
                        "WebFinger lookup of " + acct + " failed: " + e.getMessage(), e))
                .chain(jrd -> jrd.links().stream()
                        .filter(l -> "self".equals(l.rel()) && l.href() != null
                                && ActivityPubMediaTypes.isActivityPub(l.type()))
                        .map(Link::href).findFirst()
                        .map(this::fetchActor)
                        .orElseGet(() -> Uni.createFrom().failure(new ActivityPubException(Reason.NOT_FOUND,
                                "WebFinger account " + acct + " has no ActivityPub self link"))));
    }

    private static <T extends ASObject> T as(URI id, ASObject object, Class<T> type) {
        if (!type.isInstance(object)) {
            throw new ActivityPubException(Reason.INVALID_DOCUMENT,
                    id + " is a " + object.primaryType() + ", not a " + type.getSimpleName());
        }
        return type.cast(object);
    }

    /** Local actors from {@code ActorStore} (as their documents), other local ids from {@code ObjectStore}. */
    private Uni<ASObject> local(URI id) {
        Optional<String> username = urls.usernameOf(id);
        if (username.isPresent()) {
            return stores.reactiveActors().findByUsername(username.get()).chain(found -> {
                if (found.isEmpty()) {
                    return Uni.createFrom().failure(notFound(id));
                }
                LocalActor actor = found.get();
                return stores.reactiveActors().keyPair(actor.id())
                        .map(keys -> (ASObject) documents.toDocument(actor, keys.orElse(null)));
            });
        }
        return stores.reactiveObjects().find(id).map(found -> {
            if (found.isEmpty() || found.get() instanceof Tombstone) {
                throw notFound(id);
            }
            return found.get();
        });
    }

    private Uni<ASObject> remote(URI id) {
        if (domains.allow(id.getHost()) == DomainPolicy.Decision.BLOCK) {
            return Uni.createFrom().failure(new ActivityPubException(Reason.BLOCKED,
                    "Refusing to fetch " + id + ": " + id.getHost() + " is a blocked domain"));
        }
        Optional<ASObject> cached = cache.get(id);
        if (cached.isPresent()) {
            return Uni.createFrom().item(cached.get());
        }
        return Uni.createFrom().item(() -> options(id)).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .chain(options -> safeFetch.get(id, options))
                .onFailure(SafeFetchException.class).transform(e -> new ActivityPubException(Reason.FETCH_FAILED,
                        "Fetch of " + id + " failed: " + e.getMessage(), e))
                .map(response -> parse(id, response))
                .invoke(object -> cache.put(id, object));
    }

    /**
     * The document, provided it came from the requested host: a redirect to another host is refused (that host could
     * claim any id on the requested one), and the document's {@code id} must be on the host it was finally served from.
     */
    private ASObject parse(URI id, FetchResponse response) {
        URI served = response.uri() != null ? response.uri() : id;
        if (served.getHost() == null || !served.getHost().equalsIgnoreCase(id.getHost())) {
            throw new ActivityPubException(Reason.FETCH_FAILED,
                    "Fetch of " + id + " redirected to another host: " + served.getHost());
        }
        int status = response.status();
        if (status == 404 || status == 410) {
            throw notFound(id);
        }
        if (!response.isSuccess()) {
            throw new ActivityPubException(Reason.FETCH_FAILED, "Fetch of " + id + " answered " + status);
        }
        ASObject object = json.read(response.body());
        URI documentId = object.id();
        if (documentId == null || documentId.getHost() == null
                || !documentId.getHost().equalsIgnoreCase(served.getHost())) {
            throw new ActivityPubException(Reason.ID_MISMATCH,
                    "Document fetched from " + served + " claims the id " + documentId);
        }
        return object;
    }

    private FetchOptions options(URI id) {
        FetchOptions options = FetchOptions.defaults()
                .withTimeout(config.fetch().timeout())
                .withMaxResponseSize(config.fetch().maxSize().asLongValue())
                .withHeader("Accept", ActivityPubMediaTypes.ACCEPT)
                .withHeader("User-Agent", userAgent());
        Optional<LocalActor> instance = instanceActor.ifEnabled();
        if (instance.isEmpty()) {
            return options;
        }
        String keyId = urls.keyId(instance.get().username()).toString();
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now().atOffset(ZoneOffset.UTC));
        Headers headers = Headers.of(Map.of("Accept", List.of(ActivityPubMediaTypes.ACCEPT), "Date", List.of(date)));
        Scheme scheme = signer.memory().preferredFor(id.getHost()).orElse(Scheme.CAVAGE);
        options = options.withHeader("Date", date);
        Map<String, String> signed;
        try {
            signed = signer.sign("GET", id, headers, null, Optional.of(KeyIds.clientName(keyId)), scheme).headers();
        } catch (RuntimeException e) {
            throw new ActivityPubException(Reason.FETCH_FAILED, "Cannot sign the fetch of " + id + " as the instance "
                    + "actor: " + e.getMessage(), e);
        }
        Optional<String> used = KeyIds.signedKeyId(signed);
        if (!used.filter(keyId::equals).isPresent()) {
            throw new ActivityPubException(Reason.FETCH_FAILED, "Fetch of " + id + " would be signed with key "
                    + used.orElse("<none>") + " instead of the instance actor's " + keyId);
        }
        for (Map.Entry<String, String> h : signed.entrySet()) {
            options = options.withHeader(h.getKey(), h.getValue());
        }
        return options;
    }

    private String userAgent() {
        return config.userAgent().orElseGet(() -> {
            String version = ActivityPubClientImpl.class.getPackage().getImplementationVersion();
            return "quarkus-activitypub/" + (version == null ? "dev" : version) + " (+" + urls.baseUrl() + ")";
        });
    }

    private static ActivityPubException notFound(URI id) {
        return new ActivityPubException(Reason.NOT_FOUND, id + " not found");
    }
}

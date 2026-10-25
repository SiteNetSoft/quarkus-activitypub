package org.sitenetsoft.quarkus.activitypub.keys;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Key;
import java.time.Duration;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.activitystreams.PublicKey;
import org.sitenetsoft.httpsignatures.Keys;
import org.sitenetsoft.httpsignatures.VerificationKey;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeys;
import org.sitenetsoft.quarkus.activitypub.client.ReactiveActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.client.RemoteCache;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.httpsignatures.HttpSignaturesConfig;
import org.sitenetsoft.quarkus.httpsignatures.ReactiveKeyResolver;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.smallrye.mutiny.Uni;

/**
 * Resolves HTTP signature key ids to actor keys. A key id on our own host is answered from {@code ActorStore} and never
 * fetched. Any other key id is fetched as an actor document (the key id without its fragment, bypassing the remote
 * cache so the key-rotation refetch of {@code CachingKeyResolver} sees the new key), and accepted only when the
 * document lists a {@code publicKey} with exactly that id whose {@code owner} is the document's {@code id}, on the key
 * id's host. Every failure yields {@code empty} (logged at DEBUG). {@code CachingKeyResolver} caches the hits.
 */
@Priority(50)
@ApplicationScoped
public class ActorKeyResolver implements ReactiveKeyResolver {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Inject
    ReactiveActivityPubClient client;

    @Inject
    RemoteCache remoteCache;

    @Inject
    ActivityPubConfig config;

    @Inject
    HttpSignaturesConfig signaturesConfig;

    private Cache<String, URI> owners;

    @PostConstruct
    void init() {
        Duration actorTtl = config.fetch().actorCacheTtl();
        Duration keyTtl = signaturesConfig.keyCache().ttl();
        // as long as CachingKeyResolver may keep the key, so a cached key always has its owner
        owners = Caffeine.newBuilder().maximumSize(10_000)
                .expireAfterWrite(actorTtl.compareTo(keyTtl) > 0 ? actorTtl : keyTtl).build();
    }

    @Override
    public Uni<Optional<VerificationKey>> resolve(String keyId) {
        URI keyUri;
        try {
            keyUri = new URI(keyId);
        } catch (URISyntaxException e) {
            LOG.debugf("Key id %s is not a URI", keyId);
            return Uni.createFrom().item(Optional.empty());
        }
        if (!keyUri.isAbsolute() || keyUri.getHost() == null) {
            LOG.debugf("Key id %s is not an absolute URL", keyId);
            return Uni.createFrom().item(Optional.empty());
        }
        Uni<Optional<VerificationKey>> found = urls.isLocal(keyUri) ? local(keyId, keyUri) : remote(keyId, keyUri);
        return found.onFailure().recoverWithItem(failure -> {
            LOG.debugf("Key %s not resolved: %s", keyId, failure.getMessage());
            return Optional.empty();
        });
    }

    /**
     * The actor owning {@code keyId}, for a key this resolver accepted (local keys always): the {@code publicKey.owner}
     * of the fetched document. Empty for key ids it has not resolved.
     */
    public Optional<URI> ownerOf(String keyId) {
        if (keyId == null) {
            return Optional.empty();
        }
        URI cached = owners.getIfPresent(keyId);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            URI keyUri = new URI(keyId);
            return urls.isLocal(keyUri) ? localUsername(keyId, keyUri).map(urls::actor) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private Uni<Optional<VerificationKey>> local(String keyId, URI keyUri) {
        Optional<String> username = localUsername(keyId, keyUri);
        if (username.isEmpty()) {
            LOG.debugf("Key id %s is not a local actor key", keyId);
            return Uni.createFrom().item(Optional.empty());
        }
        URI actor = urls.actor(username.get());
        return stores.reactiveActors().keyPair(actor).map(keys -> keys.map(k -> {
            owners.put(keyId, actor);
            return new VerificationKey(keyId, Optional.empty(), ActorKeys.publicKey(k));
        }));
    }

    private Optional<String> localUsername(String keyId, URI keyUri) {
        return urls.usernameOf(ActorSigningKeyProvider.withoutFragment(keyUri))
                .filter(u -> urls.keyId(u).toString().equals(keyId));
    }

    private Uni<Optional<VerificationKey>> remote(String keyId, URI keyUri) {
        URI document = ActorSigningKeyProvider.withoutFragment(keyUri);
        remoteCache.invalidate(document);
        return client.fetchActor(document).map(actor -> {
            Optional<PublicKey> key = actor.publicKey().stream().filter(k -> keyUri.equals(k.id())).findFirst();
            if (key.isEmpty()) {
                LOG.debugf("Actor %s does not list key %s", actor.id(), keyId);
                return Optional.empty();
            }
            if (!accepted(keyId, keyUri, actor, key.get())) {
                return Optional.empty();
            }
            Key parsed = Keys.parsePem(key.get().publicKeyPem());
            if (!(parsed instanceof java.security.PublicKey publicKey)) {
                LOG.debugf("Key %s is not a public key", keyId);
                return Optional.empty();
            }
            owners.put(keyId, actor.id());
            return Optional.of(new VerificationKey(keyId, Optional.empty(), publicKey));
        });
    }

    private static boolean accepted(String keyId, URI keyUri, Actor actor, PublicKey key) {
        if (key.owner() == null || !key.owner().equals(actor.id())) {
            LOG.debugf("Key %s has owner %s, not the document id %s", keyId, key.owner(), actor.id());
            return false;
        }
        if (key.owner().getHost() == null || !key.owner().getHost().equalsIgnoreCase(keyUri.getHost())) {
            LOG.debugf("Key %s is owned by %s on another host", keyId, key.owner());
            return false;
        }
        if (key.publicKeyPem() == null) {
            LOG.debugf("Key %s has no publicKeyPem", keyId);
            return false;
        }
        return true;
    }
}

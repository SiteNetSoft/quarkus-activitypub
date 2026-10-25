package org.sitenetsoft.quarkus.activitypub.keys;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.httpsignatures.Algorithm;
import org.sitenetsoft.httpsignatures.SigningKey;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeys;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.httpsignatures.SigningKeyProvider;

/**
 * Signs as a local actor: for a client name {@code "activitypub:" + keyId} (see {@link KeyIds}) whose key id is a local
 * actor's {@code {actor}#main-key}, that actor's private key with {@code rsa-sha256} ({@code RequestSigner} adapts it
 * to {@code rsa-v1_5-sha256} for RFC 9421). Any other client name is left to the other providers, so config-based keys
 * keep working. Reads {@code ActorStore}: call {@code RequestSigner} off the event loop.
 */
@Priority(50)
@ApplicationScoped
public class ActorSigningKeyProvider implements SigningKeyProvider {

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Override
    public Optional<SigningKey> select(URI target, Optional<String> clientName) {
        return KeyIds.keyIdOf(clientName).flatMap(keyId -> localActor(keyId).flatMap(actor -> stores.actors()
                .keyPair(actor).map(keys -> new SigningKey(keyId, Algorithm.RSA_SHA256, ActorKeys.privateKey(keys)))));
    }

    /** The local actor whose main key is {@code keyId}. */
    private Optional<URI> localActor(String keyId) {
        URI uri;
        try {
            uri = new URI(keyId);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        return urls.usernameOf(withoutFragment(uri)).filter(u -> urls.keyId(u).toString().equals(keyId))
                .map(urls::actor);
    }

    static URI withoutFragment(URI uri) {
        if (uri.getRawFragment() == null) {
            return uri;
        }
        String s = uri.toString();
        return URI.create(s.substring(0, s.indexOf('#')));
    }
}

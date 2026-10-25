package org.sitenetsoft.quarkus.activitypub.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.keys.ActorKeyResolver;
import org.sitenetsoft.quarkus.httpsignatures.vertx.RequestVerifier;

import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

/**
 * Who signed a request: the actor owning the verified key.
 */
@ApplicationScoped
public class SignerIdentity {

    @Inject
    RequestVerifier verifier;

    @Inject
    ActorKeyResolver keys;

    /** The signer's actor when the request carries a signature that verifies; empty otherwise (never a failure). */
    public Uni<Optional<URI>> verifiedActor(RoutingContext ctx) {
        if (!RequestVerifier.hasSignature(ctx)) {
            return Uni.createFrom().item(Optional.empty());
        }
        return verifier.verify(ctx).map(result -> actorOf(result.keyId())).onFailure().recoverWithItem(failure -> {
            ActivityPubRouting.LOG.debugf("Signature on %s not accepted: %s", ctx.normalizedPath(), failure.getMessage());
            return Optional.empty();
        });
    }

    /**
     * The owner of {@code keyId}: the {@code publicKey.owner} {@link ActorKeyResolver} checked when it resolved the key;
     * for a key another resolver supplied, the key id without its fragment
     * ({@code https://a.example/users/x#main-key} → {@code https://a.example/users/x}), the Mastodon convention. Empty
     * when the key id is not an absolute URI.
     */
    public Optional<URI> actorOf(String keyId) {
        if (keyId == null) {
            return Optional.empty();
        }
        Optional<URI> owner = keys.ownerOf(keyId);
        if (owner.isPresent()) {
            return owner;
        }
        int hash = keyId.indexOf('#');
        try {
            URI conventional = new URI(hash < 0 ? keyId : keyId.substring(0, hash));
            return conventional.isAbsolute() && conventional.getHost() != null ? Optional.of(conventional)
                    : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}

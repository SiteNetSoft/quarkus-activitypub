package org.sitenetsoft.quarkus.activitypub.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
     * The owner of {@code keyId}: the key id without its fragment ({@code https://a.example/users/x#main-key} →
     * {@code https://a.example/users/x}), the Mastodon convention. Empty when the key id is not an absolute URI.
     * The key resolver only accepts keys whose {@code publicKey.owner} is the document holding them, so for a verified
     * signature this is the owning actor.
     */
    public Optional<URI> actorOf(String keyId) {
        if (keyId == null) {
            return Optional.empty();
        }
        int hash = keyId.indexOf('#');
        try {
            URI owner = new URI(hash < 0 ? keyId : keyId.substring(0, hash));
            return owner.isAbsolute() && owner.getHost() != null ? Optional.of(owner) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}

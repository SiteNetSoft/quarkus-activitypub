package org.sitenetsoft.quarkus.activitypub.server;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;

import io.vertx.ext.web.RoutingContext;

/**
 * Applies {@code quarkus.activitypub.authorized-fetch} to the ActivityPub GET routes.
 */
@ApplicationScoped
public class FetchAuthorization {

    @Inject
    ActivityPubConfig config;

    @Inject
    SignerIdentity signers;

    /**
     * Verifies the request's signature when it has one, then calls {@code next} with the signer. When
     * {@code authorized-fetch} is on, {@code exempt} is false and no signature verified, answers {@code 401} instead.
     */
    void authorize(RoutingContext ctx, boolean exempt, Consumer<Optional<URI>> next) {
        ActivityPubRouting.respond(ctx, signers.verifiedActor(ctx), signer -> {
            if (signer.isEmpty() && config.authorizedFetch() && !exempt) {
                ActivityPubRouting.status(ctx, 401);
                return;
            }
            next.accept(signer);
        });
    }
}

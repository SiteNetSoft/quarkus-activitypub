package org.sitenetsoft.quarkus.activitypub.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.actor.ActorDocuments;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * {@code GET paths.actor}: the actor document for ActivityPub {@code Accept} values, otherwise a {@code 302} to the
 * actor's {@code url} (or {@code 406} without one). Usernames match exactly.
 */
@ApplicationScoped
public class ActorHandler implements Handler<RoutingContext> {

    @Inject
    ActivityPubConfig config;

    @Inject
    Stores stores;

    @Inject
    ActorDocuments documents;

    @Inject
    ActivityPubJson json;

    @Inject
    FetchAuthorization authorization;

    @Override
    public void handle(RoutingContext ctx) {
        if (!ActivityPubRouting.readOnly(ctx)) {
            return;
        }
        String username = ctx.pathParam("username");
        if (!ActivityPubRouting.wantsActivityJson(ctx)) {
            ActivityPubRouting.respond(ctx, stores.reactiveActors().findByUsername(username), actor -> redirect(ctx, actor));
            return;
        }
        // remote servers fetch the instance actor to check our signed fetches, so it must stay readable
        boolean exempt = config.instanceActor().enabled() && config.instanceActor().username().equals(username);
        authorization.authorize(ctx, exempt, signer -> ActivityPubRouting.respond(ctx, document(username),
                body -> body.ifPresentOrElse(b -> write(ctx, b), () -> ActivityPubRouting.status(ctx, 404))));
    }

    private Uni<Optional<byte[]>> document(String username) {
        return stores.reactiveActors().findByUsername(username).flatMap(found -> {
            if (found.isEmpty()) {
                return Uni.createFrom().item(Optional.<byte[]> empty());
            }
            LocalActor actor = found.get();
            return stores.reactiveActors().keyPair(actor.id())
                    .map(keys -> Optional.of(json.write(documents.toDocument(actor, keys.orElse(null)))));
        });
    }

    private static void redirect(RoutingContext ctx, Optional<LocalActor> actor) {
        if (actor.isEmpty()) {
            ActivityPubRouting.status(ctx, 404);
        } else if (actor.get().url().isPresent()) {
            ctx.response().putHeader("Location", actor.get().url().get().toString());
            ActivityPubRouting.status(ctx, 302);
        } else {
            ActivityPubRouting.status(ctx, 406);
        }
    }

    private void write(RoutingContext ctx, byte[] body) {
        String etag = etag(body);
        ctx.response().putHeader("ETag", etag);
        if (!ctx.response().headers().contains("Cache-Control")) {
            // authorized fetch already made it private
            ctx.response().putHeader("Cache-Control", config.cacheControl().actor());
        }
        if (matches(ctx.request().getHeader("If-None-Match"), etag)) {
            ActivityPubRouting.status(ctx, 304);
            return;
        }
        ActivityPubRouting.json(ctx, 200, body);
    }

    /** {@code "} + hex SHA-256 of the body + {@code "}. */
    static String etag(byte[] body) {
        try {
            return '"' + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)) + '"';
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** True when {@code If-None-Match} lists {@code etag} (weak or strong) or is {@code *}. */
    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String c = candidate.strip();
            if (c.startsWith("W/")) {
                c = c.substring(2);
            }
            if (c.equals("*") || c.equals(etag)) {
                return true;
            }
        }
        return false;
    }
}

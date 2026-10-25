package org.sitenetsoft.quarkus.activitypub.server;

import java.util.Locale;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.activitypub.ActivityPubMediaTypes;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

/**
 * Content negotiation and response helpers shared by the ActivityPub routes.
 */
public final class ActivityPubRouting {

    static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    /** {@code Content-Type} of every ActivityPub response. */
    public static final String CONTENT_TYPE = ActivityPubMediaTypes.ACTIVITY_JSON + "; charset=utf-8";

    private ActivityPubRouting() {
    }

    /**
     * True when an {@code Accept} media range is {@code application/activity+json} or {@code application/ld+json}
     * (with or without the Activity Streams {@code profile}). Wildcards such as {@code *}{@code /*} do not count:
     * browsers send them.
     */
    public static boolean wantsActivityJson(RoutingContext ctx) {
        for (String accept : ctx.request().headers().getAll("Accept")) {
            for (String range : accept.split(",")) {
                int semi = range.indexOf(';');
                String type = (semi < 0 ? range : range.substring(0, semi)).strip().toLowerCase(Locale.ROOT);
                if (type.equals(ActivityPubMediaTypes.ACTIVITY_JSON) || type.equals("application/ld+json")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Ends the response with an ActivityPub JSON body, {@code Content-Type} and {@code Vary: Accept} (or the wider
     * {@code Vary} of a {@linkplain #privateResponse private} response).
     */
    public static void json(RoutingContext ctx, int status, byte[] body) {
        vary(ctx);
        ctx.response().setStatusCode(status)
                .putHeader("Content-Type", CONTENT_TYPE)
                .end(Buffer.buffer(body));
    }

    /**
     * Marks the response as depending on the request's signature: {@code Cache-Control: private} and
     * {@code Vary: Accept, Signature}, so a shared cache never replays it to another caller.
     */
    static void privateResponse(RoutingContext ctx) {
        ctx.response().putHeader("Cache-Control", "private").putHeader("Vary", "Accept, Signature");
    }

    /** {@code Vary: Accept} unless {@link #privateResponse} already set a wider one. */
    private static void vary(RoutingContext ctx) {
        if (!ctx.response().headers().contains("Vary")) {
            ctx.response().putHeader("Vary", "Accept");
        }
    }

    /** Ends the response with {@code status}, {@code Vary} and no body. */
    static void status(RoutingContext ctx, int status) {
        vary(ctx);
        ctx.response().setStatusCode(status).end();
    }

    /** Answers {@code 405} for anything but GET and HEAD; returns whether the request may continue. */
    static boolean readOnly(RoutingContext ctx) {
        HttpMethod m = ctx.request().method();
        if (m == HttpMethod.GET || m == HttpMethod.HEAD) {
            return true;
        }
        ctx.response().setStatusCode(405).putHeader("Allow", "GET, HEAD").end();
        return false;
    }

    /**
     * Subscribes to {@code uni} and hands its item to {@code onItem} on the request's Vert.x context (store views may
     * complete on a worker thread). A failure, or an exception thrown by {@code onItem}, answers {@code 500}.
     */
    public static <T> void respond(RoutingContext ctx, Uni<T> uni, Consumer<T> onItem) {
        Context context = Vertx.currentContext();
        Uni<T> onContext = context == null ? uni : uni.emitOn(command -> context.runOnContext(v -> command.run()));
        onContext.subscribe().with(item -> {
            try {
                onItem.accept(item);
            } catch (RuntimeException e) {
                fail(ctx, e);
            }
        }, failure -> fail(ctx, failure));
    }

    /** Logs and answers {@code 500} unless the response already went out. */
    static void fail(RoutingContext ctx, Throwable failure) {
        LOG.errorf(failure, "ActivityPub route %s failed", ctx.normalizedPath());
        if (!ctx.response().ended() && !ctx.response().headWritten()) {
            ctx.response().setStatusCode(500).end();
        }
    }
}

package org.sitenetsoft.quarkus.activitypub.server;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

/**
 * Puts ActivityPub in front of an application's own pages, for apps whose object ids are their page URLs:
 * <pre>{@code
 * void routes(@Observes Router router, ActivityPubContentNegotiation negotiation) {
 *     router.get("/posts/*").handler(negotiation);
 *     // ... the app's HTML routes for /posts/* follow
 * }
 * }</pre>
 * For a GET with an ActivityPub {@code Accept} value it serves the stored object whose id is the request URL, with the
 * rules of the object route (public, {@code 410} for a tombstone, audience-signed for private objects). Any other
 * request, or one with no visible stored object, goes to the next handler.
 * <p>
 * The request URL is {@code quarkus.activitypub.base-url} resolved against the request path
 * ({@code base-url.resolve(normalizedPath)}), not the {@code Host} header, so a server behind a proxy or on another
 * port still finds ids minted under {@code base-url}. The query string is not part of the id.
 */
@ApplicationScoped
public class ActivityPubContentNegotiation implements Handler<RoutingContext> {

    @Inject
    ActivityPubUrls urls;

    @Inject
    ObjectHandler objects;

    @Override
    public void handle(RoutingContext ctx) {
        HttpMethod method = ctx.request().method();
        if ((method != HttpMethod.GET && method != HttpMethod.HEAD) || !ActivityPubRouting.wantsActivityJson(ctx)) {
            ctx.next();
            return;
        }
        URI id = urls.baseUrl().resolve(ctx.normalizedPath());
        objects.serve(ctx, id, ctx::next);
    }
}

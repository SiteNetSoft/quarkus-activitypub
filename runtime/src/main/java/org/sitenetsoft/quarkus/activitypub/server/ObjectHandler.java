package org.sitenetsoft.quarkus.activitypub.server;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * {@code GET paths.objects} and {@code paths.activities} from {@code ObjectStore}: public objects are served, a
 * {@code Tombstone} answers {@code 410} with its body, and a non-public object is served only to a request signed by an
 * actor in its audience (with {@code Cache-Control: private} and {@code Vary: Accept, Signature}); otherwise
 * {@code 404}, so a private object's existence never leaks.
 */
@ApplicationScoped
public class ObjectHandler implements BeanHandler.VariantHandler {

    /** Route variant for {@code paths.objects}. */
    public static final String OBJECTS = "objects";
    /** Route variant for {@code paths.activities}. */
    public static final String ACTIVITIES = "activities";

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Inject
    ActivityPubJson json;

    @Inject
    FetchAuthorization authorization;

    @Override
    public Handler<RoutingContext> variant(String variant) {
        boolean activities = ACTIVITIES.equals(variant);
        return ctx -> {
            if (!ActivityPubRouting.readOnly(ctx)) {
                return;
            }
            String id = ctx.pathParam("id");
            serve(ctx, activities ? urls.activity(id) : urls.object(id), () -> ActivityPubRouting.status(ctx, 404));
        };
    }

    /**
     * Serves the stored object {@code id} under the rules above; runs {@code notVisible} when it is missing or the
     * request may not see it.
     */
    void serve(RoutingContext ctx, URI id, Runnable notVisible) {
        authorization.authorize(ctx, false, signer -> ActivityPubRouting.respond(ctx, stores.reactiveObjects().find(id),
                found -> {
                    if (found.isEmpty()) {
                        notVisible.run();
                        return;
                    }
                    ASObject object = found.get();
                    if (object instanceof Tombstone) {
                        ActivityPubRouting.json(ctx, 410, json.write(object));
                    } else if (object.isPublic()) {
                        ActivityPubRouting.json(ctx, 200, json.write(object));
                    } else if (signer.filter(object.audienceUris()::contains).isPresent()) {
                        ActivityPubRouting.privateResponse(ctx);
                        ActivityPubRouting.json(ctx, 200, json.write(object));
                    } else {
                        notVisible.run();
                    }
                }));
    }
}

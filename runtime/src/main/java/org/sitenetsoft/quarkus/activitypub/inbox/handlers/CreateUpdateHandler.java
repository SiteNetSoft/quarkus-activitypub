package org.sitenetsoft.quarkus.activitypub.inbox.handlers;

import java.net.URI;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Update;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityHandler;
import org.sitenetsoft.quarkus.activitypub.inbox.RemoteObjectPolicy;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * {@code Create} / {@code Update}: the object (embedded; or, when only referenced, fetched, and only for an activity
 * received in a local actor's own inbox) is kept with {@code ObjectStore.putRemote} when {@link RemoteObjectPolicy}
 * says {@code KEEP}. Objects without an id, or whose id or first {@code attributedTo} is on another host than the
 * activity's actor, are ignored.
 */
public final class CreateUpdateHandler {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    private CreateUpdateHandler() {
    }

    @Priority(0)
    @ApplicationScoped
    public static class OnCreate implements InboundActivityHandler<Create> {

        @Inject
        Keeper keeper;

        @Override
        public Class<Create> type() {
            return Create.class;
        }

        @Override
        public void handle(Create create, InboundActivityContext ctx) {
            keeper.keep(create, ctx);
        }
    }

    @Priority(0)
    @ApplicationScoped
    public static class OnUpdate implements InboundActivityHandler<Update> {

        @Inject
        Keeper keeper;

        @Override
        public Class<Update> type() {
            return Update.class;
        }

        @Override
        public void handle(Update update, InboundActivityContext ctx) {
            keeper.keep(update, ctx);
        }
    }

    /** The shared logic of both handlers. */
    @ApplicationScoped
    public static class Keeper {

        @Inject
        Stores stores;

        @Inject
        ActivityPubClient client;

        @Inject
        RemoteObjectPolicy policy;

        void keep(Activity activity, InboundActivityContext ctx) {
            URI actor = ctx.signerActor();
            Optional<ASObject> object = activity.embeddedObject();
            if (object.isEmpty() && activity.objectUri().isPresent() && ctx.recipient().isPresent()) {
                object = Optional.of(client.fetch(activity.objectUri().get()));
            }
            if (object.isEmpty() || object.get().id() == null) {
                return;
            }
            ASObject o = object.get();
            Optional<URI> author = o.attributedTo().stream().findFirst().flatMap(ObjectOrLink::uri);
            if (!sameHost(o.id(), actor) || author.filter(a -> sameHost(a, actor)).isEmpty()) {
                LOG.debugf("%s %s by %s: object %s attributed to %s, ignored", activity.primaryType(), activity.id(),
                        actor, o.id(), author.orElse(null));
                return;
            }
            if (policy.decide(o, ctx) == RemoteObjectPolicy.Keep.KEEP) {
                stores.objects().putRemote(o, actor);
            }
        }
    }

    static boolean sameHost(URI a, URI b) {
        return a.getHost() != null && b.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost());
    }
}

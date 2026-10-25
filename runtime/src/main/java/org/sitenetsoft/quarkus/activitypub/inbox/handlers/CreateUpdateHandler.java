package org.sitenetsoft.quarkus.activitypub.inbox.handlers;

import java.net.URI;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Update;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityHandler;
import org.sitenetsoft.quarkus.activitypub.inbox.RemoteObjectPolicy;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * {@code Create} / {@code Update}: the object (embedded; or, when only referenced, fetched, and only for an activity
 * received in a local actor's own inbox, and only from the actor's own host) is kept with {@code ObjectStore.putRemote}
 * when {@link RemoteObjectPolicy} says {@code KEEP}. Ignored: objects without an id, with an id on another host than
 * the actor, whose {@code attributedTo} is missing or names anyone but the actor, or that we hold as another actor's.
 * A referenced object is fetched in {@code prepare}, outside the store transaction.
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
        public void prepare(Create create, InboundActivityContext ctx) {
            keeper.prepare(create, ctx);
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
        public void prepare(Update update, InboundActivityContext ctx) {
            keeper.prepare(update, ctx);
        }

        @Override
        public void handle(Update update, InboundActivityContext ctx) {
            keeper.keep(update, ctx);
        }
    }

    /** The object {@link Keeper#prepare} found for the inbound activity being processed. */
    @RequestScoped
    public static class PreparedObject {

        private Activity activity;
        private Optional<ASObject> object = Optional.empty();

        void found(Activity activity, Optional<ASObject> object) {
            this.activity = activity;
            this.object = object;
        }

        /** What {@code prepare} found for {@code activity}; its embedded object when it was not prepared. */
        Optional<ASObject> objectOf(Activity activity) {
            return activity == this.activity ? object : activity.embeddedObject();
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

        @Inject
        PreparedObject prepared;

        /** The embedded object, or the referenced one fetched (network: runs outside the transaction). */
        void prepare(Activity activity, InboundActivityContext ctx) {
            URI actor = ctx.signerActor();
            Optional<ASObject> object = activity.embeddedObject();
            Optional<URI> reference = activity.objectUri();
            if (object.isEmpty() && reference.isPresent() && ctx.recipient().isPresent()) {
                // only fetch from the actor's own host: anything else would be discarded, and fetching it would let
                // any signer make us send signed requests to a host of its choosing
                if (!sameHost(reference.get(), actor)) {
                    LOG.debugf("%s %s by %s references %s on another host, not fetched", activity.primaryType(),
                            activity.id(), actor, reference.get());
                    prepared.found(activity, Optional.empty());
                    return;
                }
                object = Optional.of(client.fetch(reference.get()));
            }
            prepared.found(activity, object);
        }

        /** Store only: keeps what {@link #prepare} found. */
        void keep(Activity activity, InboundActivityContext ctx) {
            URI actor = ctx.signerActor();
            Optional<ASObject> object = prepared.objectOf(activity);
            if (object.isEmpty() || object.get().id() == null) {
                return;
            }
            ASObject o = object.get();
            if (!sameHost(o.id(), actor) || !attributedOnlyTo(o, actor)) {
                LOG.debugf("%s %s by %s: object %s attributed to %s, ignored", activity.primaryType(), activity.id(),
                        actor, o.id(), o.attributedTo());
                return;
            }
            Optional<ASObject> held = stores.objects().find(o.id());
            if (held.isPresent() && !attributedTo(held.get(), actor)) {
                LOG.debugf("%s %s by %s of %s, held as another actor's, ignored", activity.primaryType(),
                        activity.id(), actor, o.id());
                return;
            }
            if (policy.decide(o, ctx) == RemoteObjectPolicy.Keep.KEEP) {
                stores.objects().putRemote(o, actor);
            }
        }
    }

    /** True when {@code object} has an {@code attributedTo} and every entry is {@code actor}. */
    static boolean attributedOnlyTo(ASObject object, URI actor) {
        return !object.attributedTo().isEmpty()
                && object.attributedTo().stream().allMatch(a -> a.uri().filter(actor::equals).isPresent());
    }

    /** True when an {@code attributedTo} entry of {@code object} is {@code actor}. */
    static boolean attributedTo(ASObject object, URI actor) {
        return object.attributedTo().stream().anyMatch(a -> a.uri().filter(actor::equals).isPresent());
    }

    static boolean sameHost(URI a, URI b) {
        return a.getHost() != null && b.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost());
    }
}

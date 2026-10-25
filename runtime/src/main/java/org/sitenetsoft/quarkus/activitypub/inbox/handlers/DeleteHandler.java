package org.sitenetsoft.quarkus.activitypub.inbox.handlers;

import java.net.URI;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Delete;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.quarkus.activitypub.client.RemoteCache;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityHandler;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * {@code Delete}, never fetching anything: an actor deleting itself loses its follow relationships, its kept objects
 * and its cached document; an object we hold on the actor's own host and attributed to the actor becomes a
 * {@code Tombstone}; anything else is ignored.
 */
@Priority(0)
@ApplicationScoped
public class DeleteHandler implements InboundActivityHandler<Delete> {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    Stores stores;

    @Inject
    RemoteCache remoteCache;

    @Override
    public Class<Delete> type() {
        return Delete.class;
    }

    @Override
    public void handle(Delete delete, InboundActivityContext ctx) {
        URI actor = ctx.signerActor();
        Optional<URI> object = delete.objectUri();
        if (object.isEmpty()) {
            return;
        }
        URI id = object.get();
        if (id.equals(actor)) {
            stores.followers().removeAll(actor);
            stores.objects().deleteByActor(actor);
            remoteCache.invalidate(actor);
            return;
        }
        if (!CreateUpdateHandler.sameHost(id, actor)) {
            LOG.debugf("Delete %s by %s of %s on another host ignored", delete.id(), actor, id);
            return;
        }
        Optional<ASObject> held = stores.objects().find(id);
        if (held.isPresent() && !(held.get() instanceof Tombstone)) {
            if (!CreateUpdateHandler.attributedTo(held.get(), actor)) {
                LOG.debugf("Delete %s by %s of %s, held as another actor's, ignored", delete.id(), actor, id);
                return;
            }
            stores.objects().tombstone(id);
            remoteCache.invalidate(id);
        }
    }
}

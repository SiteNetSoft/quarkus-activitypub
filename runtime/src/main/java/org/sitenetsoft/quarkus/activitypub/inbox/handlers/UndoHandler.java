package org.sitenetsoft.quarkus.activitypub.inbox.handlers;

import java.net.URI;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Undo;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityHandler;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * {@code Undo} of a {@code Follow} by the same actor of a local actor: the follower is removed. The {@code Follow} is
 * the embedded object, or a reference to one {@code ObjectStore} holds (never fetched). Other undos ({@code Like},
 * {@code Announce}, …) have no built-in side effect.
 */
@Priority(0)
@ApplicationScoped
public class UndoHandler implements InboundActivityHandler<Undo> {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Override
    public Class<Undo> type() {
        return Undo.class;
    }

    @Override
    public void handle(Undo undo, InboundActivityContext ctx) {
        Optional<ASObject> object = undo.embeddedObject()
                .or(() -> undo.objectUri().flatMap(id -> stores.objects().find(id)));
        if (object.isEmpty() || !(object.get() instanceof Follow follow)) {
            return;
        }
        URI actor = ctx.signerActor();
        if (!follow.actorUri().filter(actor::equals).isPresent()) {
            LOG.debugf("Undo %s by %s of a Follow by %s ignored", undo.id(), actor, follow.actorUri().orElse(null));
            return;
        }
        follow.objectUri().flatMap(urls::usernameOf).map(urls::actor)
                .ifPresent(local -> stores.followers().remove(local, actor));
    }
}

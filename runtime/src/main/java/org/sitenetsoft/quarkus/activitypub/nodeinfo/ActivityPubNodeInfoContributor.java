package org.sitenetsoft.quarkus.activitypub.nodeinfo;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.actor.InstanceActor;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfo;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoContributor;

/**
 * Adds the {@code activitypub} protocol and {@code usage.users.total} (local actors, not counting the instance actor).
 * {@code @Priority(100)}: contributors run in ascending priority and the last one's usage numbers win.
 */
@ApplicationScoped
@Priority(100)
public class ActivityPubNodeInfoContributor implements NodeInfoContributor {

    @Inject
    Stores stores;

    @Inject
    InstanceActor instanceActor;

    @Override
    public NodeInfo contribute(NodeInfo current) {
        long total = stores.actors().countLocalActors() - (instanceActor.ifEnabled().isPresent() ? 1 : 0);
        return current.toBuilder().withProtocol("activitypub").withUsersTotal(Math.max(0, total)).build();
    }
}

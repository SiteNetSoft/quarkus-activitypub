package org.sitenetsoft.quarkus.activitypub.inbox;

import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

import io.quarkus.arc.DefaultBean;

/** Default {@link FollowPolicy}: {@code PENDING} when the actor manually approves followers, else {@code ACCEPT}. */
@DefaultBean
@ApplicationScoped
public class DefaultFollowPolicy implements FollowPolicy {

    @Override
    public FollowDecision decide(LocalActor local, Follow follow, InboundActivityContext ctx) {
        return local.manuallyApprovesFollowers() ? FollowDecision.PENDING : FollowDecision.ACCEPT;
    }
}

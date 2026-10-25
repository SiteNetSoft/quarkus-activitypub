package org.sitenetsoft.quarkus.activitypub.inbox;

import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

/**
 * Decides how a {@code Follow} of a local actor is answered: {@code ACCEPT} stores the follower and sends
 * {@code Accept}, {@code REJECT} sends {@code Reject}, {@code PENDING} stores the request for
 * {@code ActivityPub.acceptFollow}/{@code rejectFollow} later. Runs on a worker thread.
 */
public interface FollowPolicy {

    enum FollowDecision {
        ACCEPT,
        REJECT,
        PENDING
    }

    FollowDecision decide(LocalActor local, Follow follow, InboundActivityContext ctx);
}

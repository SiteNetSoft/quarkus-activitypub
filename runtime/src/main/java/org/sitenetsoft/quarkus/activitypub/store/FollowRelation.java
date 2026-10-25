package org.sitenetsoft.quarkus.activitypub.store;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * One follow relationship as stored, in any state.
 *
 * @param remote           the remote actor
 * @param inbox            where to deliver to the remote follower; empty for the "following" direction
 * @param followActivityId the {@code Follow} that created it, when it had an id
 */
public record FollowRelation(URI remote, Optional<URI> inbox, FollowState state, Optional<URI> followActivityId) {

    public FollowRelation {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(inbox, "inbox");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(followActivityId, "followActivityId");
    }
}

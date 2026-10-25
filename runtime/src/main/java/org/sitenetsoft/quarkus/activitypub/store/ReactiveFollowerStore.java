package org.sitenetsoft.quarkus.activitypub.store;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import io.smallrye.mutiny.Uni;

/**
 * Reactive twin of {@link FollowerStore}.
 */
public interface ReactiveFollowerStore {

    Uni<Void> add(URI local, URI remote, URI remoteInbox, FollowState state, URI followActivityId);

    Uni<Void> remove(URI local, URI remote);

    Uni<Void> removeAll(URI remote);

    Uni<Page<URI>> followers(URI local, int page, int size);

    Uni<Long> followerCount(URI local);

    Uni<List<URI>> followerInboxes(URI local);

    Uni<Void> addFollowing(URI local, URI remote, FollowState state, URI followActivityId);

    Uni<Void> removeFollowing(URI local, URI remote);

    Uni<Page<URI>> following(URI local, int page, int size);

    Uni<Long> followingCount(URI local);

    Uni<Optional<FollowState>> followingState(URI local, URI remote);

    Uni<Optional<FollowRelation>> follower(URI local, URI remote);

    Uni<Optional<FollowRelation>> followingRelation(URI local, URI remote);
}

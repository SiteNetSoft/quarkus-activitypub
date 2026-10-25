package org.sitenetsoft.quarkus.activitypub.store;

import static org.sitenetsoft.quarkus.activitypub.store.Bridge.offload;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import io.smallrye.mutiny.Uni;

/** {@link ReactiveFollowerStore} over a {@link FollowerStore}, run on worker threads. */
final class ReactiveFollowerStoreAdapter implements ReactiveFollowerStore {

    private final FollowerStore d;

    ReactiveFollowerStoreAdapter(FollowerStore delegate) {
        this.d = delegate;
    }

    @Override
    public Uni<Void> add(URI local, URI remote, URI remoteInbox, FollowState state, URI followActivityId) {
        return offload(() -> d.add(local, remote, remoteInbox, state, followActivityId));
    }

    @Override
    public Uni<Void> remove(URI local, URI remote) {
        return offload(() -> d.remove(local, remote));
    }

    @Override
    public Uni<Void> removeAll(URI remote) {
        return offload(() -> d.removeAll(remote));
    }

    @Override
    public Uni<Page<URI>> followers(URI local, int page, int size) {
        return offload(() -> d.followers(local, page, size));
    }

    @Override
    public Uni<Long> followerCount(URI local) {
        return offload(() -> d.followerCount(local));
    }

    @Override
    public Uni<List<URI>> followerInboxes(URI local) {
        return offload(() -> d.followerInboxes(local));
    }

    @Override
    public Uni<Void> addFollowing(URI local, URI remote, FollowState state, URI followActivityId) {
        return offload(() -> d.addFollowing(local, remote, state, followActivityId));
    }

    @Override
    public Uni<Void> removeFollowing(URI local, URI remote) {
        return offload(() -> d.removeFollowing(local, remote));
    }

    @Override
    public Uni<Page<URI>> following(URI local, int page, int size) {
        return offload(() -> d.following(local, page, size));
    }

    @Override
    public Uni<Long> followingCount(URI local) {
        return offload(() -> d.followingCount(local));
    }

    @Override
    public Uni<Optional<FollowState>> followingState(URI local, URI remote) {
        return offload(() -> d.followingState(local, remote));
    }
}

package org.sitenetsoft.quarkus.activitypub.store;

import static org.sitenetsoft.quarkus.activitypub.store.Bridge.await;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** {@link FollowerStore} over a {@link ReactiveFollowerStore}. */
final class BlockingFollowerStoreAdapter implements FollowerStore {

    private final ReactiveFollowerStore d;

    BlockingFollowerStoreAdapter(ReactiveFollowerStore delegate) {
        this.d = delegate;
    }

    @Override
    public void add(URI local, URI remote, URI remoteInbox, FollowState state, URI followActivityId) {
        await(d.add(local, remote, remoteInbox, state, followActivityId));
    }

    @Override
    public void remove(URI local, URI remote) {
        await(d.remove(local, remote));
    }

    @Override
    public void removeAll(URI remote) {
        await(d.removeAll(remote));
    }

    @Override
    public Page<URI> followers(URI local, int page, int size) {
        return await(d.followers(local, page, size));
    }

    @Override
    public long followerCount(URI local) {
        return await(d.followerCount(local));
    }

    @Override
    public List<URI> followerInboxes(URI local) {
        return await(d.followerInboxes(local));
    }

    @Override
    public void addFollowing(URI local, URI remote, FollowState state, URI followActivityId) {
        await(d.addFollowing(local, remote, state, followActivityId));
    }

    @Override
    public void removeFollowing(URI local, URI remote) {
        await(d.removeFollowing(local, remote));
    }

    @Override
    public Page<URI> following(URI local, int page, int size) {
        return await(d.following(local, page, size));
    }

    @Override
    public long followingCount(URI local) {
        return await(d.followingCount(local));
    }

    @Override
    public Optional<FollowState> followingState(URI local, URI remote) {
        return await(d.followingState(local, remote));
    }
}

package org.sitenetsoft.quarkus.activitypub.store.memory;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.quarkus.activitypub.store.FollowRelation;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.FollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.Page;

import io.quarkus.arc.DefaultBean;

/** Default {@link FollowerStore}: process memory, lost on restart. */
@DefaultBean
@ApplicationScoped
public class InMemoryFollowerStore implements FollowerStore {

    private record Key(URI local, URI remote) {
    }

    /** {@code inbox} is null for the "following" direction. */
    private record Relation(URI remote, URI inbox, FollowState state, URI followActivityId, long seq) {

        FollowRelation view() {
            return new FollowRelation(remote, Optional.ofNullable(inbox), state, Optional.ofNullable(followActivityId));
        }
    }

    private final Map<Key, Relation> followers = new ConcurrentHashMap<>();
    private final Map<Key, Relation> following = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @PostConstruct
    void warnIfProduction() {
        ProductionWarning.warnIfProduction("FollowerStore");
    }

    @Override
    public void add(URI local, URI remote, URI remoteInbox, FollowState state, URI followActivityId) {
        followers.put(new Key(local, remote), new Relation(remote, remoteInbox, state, followActivityId, sequence.incrementAndGet()));
    }

    @Override
    public void remove(URI local, URI remote) {
        followers.remove(new Key(local, remote));
    }

    @Override
    public void removeAll(URI remote) {
        followers.keySet().removeIf(k -> k.remote().equals(remote));
        following.keySet().removeIf(k -> k.remote().equals(remote));
    }

    @Override
    public Page<URI> followers(URI local, int page, int size) {
        return Page.of(accepted(followers, local).map(Relation::remote).toList(), page, size);
    }

    @Override
    public long followerCount(URI local) {
        return accepted(followers, local).count();
    }

    @Override
    public List<URI> followerInboxes(URI local) {
        return accepted(followers, local).map(Relation::inbox).distinct().toList();
    }

    @Override
    public void addFollowing(URI local, URI remote, FollowState state, URI followActivityId) {
        following.put(new Key(local, remote), new Relation(remote, null, state, followActivityId, sequence.incrementAndGet()));
    }

    @Override
    public void removeFollowing(URI local, URI remote) {
        following.remove(new Key(local, remote));
    }

    @Override
    public Page<URI> following(URI local, int page, int size) {
        return Page.of(accepted(following, local).map(Relation::remote).toList(), page, size);
    }

    @Override
    public long followingCount(URI local) {
        return accepted(following, local).count();
    }

    @Override
    public Optional<FollowState> followingState(URI local, URI remote) {
        return Optional.ofNullable(following.get(new Key(local, remote))).map(Relation::state);
    }

    @Override
    public Optional<FollowRelation> follower(URI local, URI remote) {
        return Optional.ofNullable(followers.get(new Key(local, remote))).map(Relation::view);
    }

    @Override
    public Optional<FollowRelation> followingRelation(URI local, URI remote) {
        return Optional.ofNullable(following.get(new Key(local, remote))).map(Relation::view);
    }

    /** Accepted relations of {@code local}, newest first. */
    private static Stream<Relation> accepted(Map<Key, Relation> relations, URI local) {
        return relations.entrySet().stream()
                .filter(e -> e.getKey().local().equals(local) && e.getValue().state() == FollowState.ACCEPTED)
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparingLong(Relation::seq).reversed());
    }

    @Override
    public boolean knowsRemote(URI remote) {
        return followers.keySet().stream().anyMatch(k -> k.remote().equals(remote))
                || following.keySet().stream().anyMatch(k -> k.remote().equals(remote));
    }
}

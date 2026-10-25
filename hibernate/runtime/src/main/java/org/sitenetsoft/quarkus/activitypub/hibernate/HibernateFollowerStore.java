package org.sitenetsoft.quarkus.activitypub.hibernate;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApFollower;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApFollowing;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.FollowKey;
import org.sitenetsoft.quarkus.activitypub.store.FollowRelation;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.FollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.Page;

import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * {@link FollowerStore} on {@code ap_follower} and {@code ap_following}; every method joins the caller's transaction or
 * opens one. Collections list accepted relationships, the most recently written first.
 */
@ApplicationScoped
public class HibernateFollowerStore implements FollowerStore {

    @Inject
    ActivityPubEntityManager entityManager;

    @Inject
    Clock clock;

    private EntityManager em() {
        return entityManager.get();
    }

    @Override
    public void add(URI local, URI remote, URI remoteInbox, FollowState state, URI followActivityId) {
        QuarkusTransaction.joiningExisting().run(() -> {
            ApFollower row = em().find(ApFollower.class, new FollowKey(local.toString(), remote.toString()));
            boolean created = row == null;
            if (created) {
                row = new ApFollower(local.toString(), remote.toString());
            }
            row.setRemoteInbox(remoteInbox.toString());
            row.setState(state);
            row.setFollowActivityId(followActivityId == null ? null : followActivityId.toString());
            row.setSince(clock.instant());
            if (created) {
                em().persist(row);
            }
        });
    }

    @Override
    public void remove(URI local, URI remote) {
        QuarkusTransaction.joiningExisting().run(() -> em()
                .createQuery("delete from ApFollower f where f.local = :local and f.remote = :remote")
                .setParameter("local", local.toString()).setParameter("remote", remote.toString()).executeUpdate());
    }

    @Override
    public void removeAll(URI remote) {
        QuarkusTransaction.joiningExisting().run(() -> {
            String id = remote.toString();
            em().createQuery("delete from ApFollower f where f.remote = :remote").setParameter("remote", id)
                    .executeUpdate();
            em().createQuery("delete from ApFollowing f where f.remote = :remote").setParameter("remote", id)
                    .executeUpdate();
        });
    }

    @Override
    public Page<URI> followers(URI local, int page, int size) {
        return page("ApFollower", local, page, size);
    }

    @Override
    public long followerCount(URI local) {
        return QuarkusTransaction.joiningExisting().call(() -> count("ApFollower", local));
    }

    @Override
    public List<URI> followerInboxes(URI local) {
        return QuarkusTransaction.joiningExisting().call(() -> em()
                .createQuery("select distinct f.remoteInbox from ApFollower f"
                        + " where f.local = :local and f.state = :state", String.class)
                .setParameter("local", local.toString()).setParameter("state", FollowState.ACCEPTED)
                .getResultStream().map(URI::create).toList());
    }

    @Override
    public void addFollowing(URI local, URI remote, FollowState state, URI followActivityId) {
        QuarkusTransaction.joiningExisting().run(() -> {
            ApFollowing row = em().find(ApFollowing.class, new FollowKey(local.toString(), remote.toString()));
            boolean created = row == null;
            if (created) {
                row = new ApFollowing(local.toString(), remote.toString());
            }
            row.setState(state);
            row.setFollowActivityId(followActivityId == null ? null : followActivityId.toString());
            row.setSince(clock.instant());
            if (created) {
                em().persist(row);
            }
        });
    }

    @Override
    public void removeFollowing(URI local, URI remote) {
        QuarkusTransaction.joiningExisting().run(() -> em()
                .createQuery("delete from ApFollowing f where f.local = :local and f.remote = :remote")
                .setParameter("local", local.toString()).setParameter("remote", remote.toString()).executeUpdate());
    }

    @Override
    public Page<URI> following(URI local, int page, int size) {
        return page("ApFollowing", local, page, size);
    }

    @Override
    public long followingCount(URI local) {
        return QuarkusTransaction.joiningExisting().call(() -> count("ApFollowing", local));
    }

    @Override
    public Optional<FollowState> followingState(URI local, URI remote) {
        return followingRelation(local, remote).map(FollowRelation::state);
    }

    @Override
    public Optional<FollowRelation> follower(URI local, URI remote) {
        return QuarkusTransaction.joiningExisting().call(() -> Optional
                .ofNullable(em().find(ApFollower.class, new FollowKey(local.toString(), remote.toString())))
                .map(f -> new FollowRelation(remote, Optional.of(URI.create(f.getRemoteInbox())), f.getState(),
                        uri(f.getFollowActivityId()))));
    }

    @Override
    public Optional<FollowRelation> followingRelation(URI local, URI remote) {
        return QuarkusTransaction.joiningExisting().call(() -> Optional
                .ofNullable(em().find(ApFollowing.class, new FollowKey(local.toString(), remote.toString())))
                .map(f -> new FollowRelation(remote, Optional.empty(), f.getState(), uri(f.getFollowActivityId()))));
    }

    private static Optional<URI> uri(String value) {
        return Optional.ofNullable(value).map(URI::create);
    }

    /** {@code entity} is one of the two entity names, never user input. */
    private Page<URI> page(String entity, URI local, int page, int size) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("page and size must be >= 1");
        }
        return QuarkusTransaction.joiningExisting().call(() -> {
            List<URI> items = em()
                    .createQuery("select f.remote from " + entity + " f where f.local = :local and f.state = :state"
                            + " order by f.since desc, f.remote desc", String.class)
                    .setParameter("local", local.toString()).setParameter("state", FollowState.ACCEPTED)
                    .setFirstResult((int) Math.min(Integer.MAX_VALUE, (long) (page - 1) * size)).setMaxResults(size)
                    .getResultStream().map(URI::create).toList();
            return new Page<>(items, page, size, count(entity, local));
        });
    }

    private long count(String entity, URI local) {
        return em().createQuery("select count(f) from " + entity + " f where f.local = :local and f.state = :state",
                Long.class).setParameter("local", local.toString()).setParameter("state", FollowState.ACCEPTED)
                .getSingleResult();
    }
}

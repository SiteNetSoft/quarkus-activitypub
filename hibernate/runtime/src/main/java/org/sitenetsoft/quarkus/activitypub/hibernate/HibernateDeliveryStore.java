package org.sitenetsoft.quarkus.activitypub.hibernate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApDelivery;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApHostHealth;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.HostHealth;

import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * {@link DeliveryStore} on {@code ap_delivery} and {@code ap_host_health}; every method joins the caller's transaction
 * or opens one.
 * <p>
 * {@link #claimDue} selects the due rows {@code FOR UPDATE SKIP LOCKED} where the database supports it (PostgreSQL,
 * MySQL 8, Oracle; H2 takes plain row locks) and stamps {@code claimed_at}; a claim is honoured for 60 seconds or
 * until {@link #update}. Delivery is meant to run on one node: a send that outlasts the lease could be claimed again.
 */
@ApplicationScoped
public class HibernateDeliveryStore implements DeliveryStore {

    /** A claim older than this is considered abandoned (the claiming poll died). */
    static final Duration CLAIM_LEASE = Duration.ofSeconds(60);

    /** {@code jakarta.persistence.lock.timeout} value meaning "skip locked rows". */
    private static final int SKIP_LOCKED = -2;

    @Inject
    ActivityPubEntityManager entityManager;

    private EntityManager em() {
        return entityManager.get();
    }

    @Override
    public void save(List<Delivery> deliveries) {
        QuarkusTransaction.joiningExisting().run(() -> deliveries.forEach(d -> write(d)));
    }

    @Override
    public List<Delivery> claimDue(Instant now, int limit) {
        if (limit < 1) {
            return List.of();
        }
        return QuarkusTransaction.joiningExisting().call(() -> {
            List<ApDelivery> rows = em().createQuery("from ApDelivery d where d.state = :pending"
                    + " and d.nextAttempt <= :now and (d.claimedAt is null or d.claimedAt <= :leaseCutoff)"
                    + " order by d.nextAttempt, d.id", ApDelivery.class)
                    .setParameter("pending", DeliveryState.PENDING).setParameter("now", now)
                    .setParameter("leaseCutoff", now.minus(CLAIM_LEASE))
                    .setMaxResults(limit)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint("jakarta.persistence.lock.timeout", SKIP_LOCKED)
                    .getResultList();
            rows.forEach(r -> r.setClaimedAt(now));
            em().flush();
            return rows.stream().map(HibernateDeliveryStore::toDelivery).toList();
        });
    }

    @Override
    public void update(Delivery delivery) {
        QuarkusTransaction.joiningExisting().run(() -> write(delivery));
    }

    /** Inserts or replaces the row, releasing any claim. */
    private void write(Delivery d) {
        ApDelivery row = em().find(ApDelivery.class, d.id());
        boolean created = row == null;
        if (created) {
            row = new ApDelivery(d.id());
        }
        row.setInbox(d.inbox().toString());
        row.setActivityId(d.activityId().toString());
        row.setBody(d.body());
        row.setKeyId(d.keyId());
        row.setAttempts(d.attempts());
        row.setNextAttempt(d.nextAttempt());
        row.setLastError(d.lastError().orElse(null));
        row.setState(d.state());
        row.setCreated(d.created());
        row.setClaimedAt(null);
        if (created) {
            em().persist(row);
        }
    }

    private static Delivery toDelivery(ApDelivery r) {
        return new Delivery(r.getId(), URI.create(r.getInbox()), URI.create(r.getActivityId()), r.getBody(),
                r.getKeyId(), r.getAttempts(), r.getNextAttempt(), Optional.ofNullable(r.getLastError()), r.getState(),
                r.getCreated());
    }

    @Override
    public HostHealth host(String host) {
        return QuarkusTransaction.joiningExisting().call(() -> Optional.ofNullable(em().find(ApHostHealth.class, host))
                .map(h -> new HostHealth(host, h.getConsecutiveFailures(), Optional.ofNullable(h.getLastFailure()),
                        Optional.ofNullable(h.getLastSuccess()), Optional.ofNullable(h.getPausedUntil())))
                .orElseGet(() -> HostHealth.unknown(host)));
    }

    @Override
    public void recordHostResult(String host, boolean success, Instant at) {
        changeHost(host, h -> {
            if (success) {
                h.setConsecutiveFailures(0);
                h.setLastSuccess(at);
                h.setPausedUntil(null);
            } else {
                h.setConsecutiveFailures(h.getConsecutiveFailures() + 1);
                h.setLastFailure(at);
            }
        });
    }

    @Override
    public void pauseHost(String host, Instant until) {
        changeHost(host, h -> h.setPausedUntil(until));
    }

    /** Applies {@code change} to the host's row, locked; the row is created first when missing. */
    private void changeHost(String host, Consumer<ApHostHealth> change) {
        QuarkusTransaction.joiningExisting().run(() -> {
            ApHostHealth row = em().find(ApHostHealth.class, host, LockModeType.PESSIMISTIC_WRITE);
            if (row == null) {
                insertHost(host);
                row = em().find(ApHostHealth.class, host, LockModeType.PESSIMISTIC_WRITE);
            }
            change.accept(row);
        });
    }

    /**
     * Inserts an empty row for {@code host} in its own transaction, so concurrent sends to a new host do not both
     * insert in theirs (one would fail on the primary key). Losing that race is fine: the row exists.
     */
    private void insertHost(String host) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                if (em().find(ApHostHealth.class, host) == null) {
                    em().persist(new ApHostHealth(host));
                }
            });
        } catch (RuntimeException e) {
            boolean exists = QuarkusTransaction.requiringNew().call(() -> em().find(ApHostHealth.class, host) != null);
            if (!exists) {
                throw e;
            }
        }
    }

    @Override
    public long pendingCount() {
        return QuarkusTransaction.joiningExisting().call(() -> em()
                .createQuery("select count(d) from ApDelivery d where d.state = :pending", Long.class)
                .setParameter("pending", DeliveryState.PENDING).getSingleResult());
    }

    @Override
    public Optional<Instant> oldestPending() {
        return QuarkusTransaction.joiningExisting().call(() -> Optional.ofNullable(em()
                .createQuery("select min(d.created) from ApDelivery d where d.state = :pending", Instant.class)
                .setParameter("pending", DeliveryState.PENDING).getSingleResult()));
    }
}

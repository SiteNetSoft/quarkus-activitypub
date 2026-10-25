package org.sitenetsoft.quarkus.activitypub.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The delivery queue and per-host health. Blocking twin of {@link ReactiveDeliveryStore}; provide either.
 */
public interface DeliveryStore {

    void save(List<Delivery> deliveries);

    /**
     * Up to {@code limit} {@link DeliveryState#PENDING} deliveries with {@code nextAttempt <= now}, earliest first, and
     * marks them claimed so a concurrent poll skips them (for 60 seconds, or until {@link #update}).
     */
    List<Delivery> claimDue(Instant now, int limit);

    /** Replaces the delivery and releases its claim. */
    void update(Delivery delivery);

    /** Health of {@code host}; zero failures and no timestamps when unknown. */
    HostHealth host(String host);

    /** Success resets failures and the pause; failure increments the consecutive failures. */
    void recordHostResult(String host, boolean success, Instant at);

    /** Holds deliveries to {@code host} until {@code until}. */
    void pauseHost(String host, Instant until);

    /**
     * Up to {@code limit} deliveries in {@code state}, most recently created first (ties by id), for inspection; does
     * not claim them.
     */
    List<Delivery> list(DeliveryState state, int limit);

    long pendingCount();

    /** Creation time of the oldest pending delivery. */
    Optional<Instant> oldestPending();
}

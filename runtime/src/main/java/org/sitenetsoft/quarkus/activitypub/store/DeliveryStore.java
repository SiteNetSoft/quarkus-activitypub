package org.sitenetsoft.quarkus.activitypub.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /**
     * The earliest {@code nextAttempt} of the {@link DeliveryState#PENDING} deliveries that are due
     * ({@code nextAttempt <= now}), claimed or not: how far behind the delivery job is. Deliveries waiting for their
     * backoff are not due and do not count.
     */
    Optional<Instant> oldestDue(Instant now);

    /** Deletes the deliveries in one of {@code states} created before {@code before}; returns how many. */
    int purge(Set<DeliveryState> states, Instant before);
}

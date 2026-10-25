package org.sitenetsoft.quarkus.activitypub.store.memory;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.HostHealth;

import io.quarkus.arc.DefaultBean;

/** Default {@link DeliveryStore}: process memory, lost on restart. */
@DefaultBean
@ApplicationScoped
public class InMemoryDeliveryStore implements DeliveryStore {

    /** A claim older than this is considered abandoned (the claiming poll died). */
    static final Duration CLAIM_TIMEOUT = Duration.ofSeconds(60);

    private record Row(Delivery delivery, Instant claimedAt) {
    }

    private final Map<String, Row> rows = new ConcurrentHashMap<>();
    private final Map<String, HostHealth> hosts = new ConcurrentHashMap<>();

    @PostConstruct
    void warnIfProduction() {
        ProductionWarning.warnIfProduction("DeliveryStore");
    }

    @Override
    public synchronized void save(List<Delivery> deliveries) {
        for (Delivery d : deliveries) {
            rows.put(d.id(), new Row(d, null));
        }
    }

    @Override
    public synchronized List<Delivery> claimDue(Instant now, int limit) {
        Instant claimCutoff = now.minus(CLAIM_TIMEOUT);
        List<Delivery> due = rows.values().stream()
                .filter(r -> r.delivery().state() == DeliveryState.PENDING && !r.delivery().nextAttempt().isAfter(now))
                .filter(r -> r.claimedAt() == null || !r.claimedAt().isAfter(claimCutoff))
                .map(Row::delivery)
                .sorted(Comparator.comparing(Delivery::nextAttempt).thenComparing(Delivery::id))
                .limit(limit)
                .toList();
        for (Delivery d : due) {
            rows.put(d.id(), new Row(d, now));
        }
        return due;
    }

    @Override
    public synchronized void update(Delivery delivery) {
        rows.put(delivery.id(), new Row(delivery, null));
    }

    @Override
    public HostHealth host(String host) {
        return hosts.getOrDefault(host, HostHealth.unknown(host));
    }

    @Override
    public void recordHostResult(String host, boolean success, Instant at) {
        hosts.compute(host, (key, h) -> {
            HostHealth current = h == null ? HostHealth.unknown(host) : h;
            return success
                    ? new HostHealth(host, 0, current.lastFailure(), Optional.of(at), Optional.empty())
                    : new HostHealth(host, current.consecutiveFailures() + 1, Optional.of(at), current.lastSuccess(),
                            current.pausedUntil());
        });
    }

    @Override
    public void pauseHost(String host, Instant until) {
        hosts.compute(host, (key, h) -> {
            HostHealth current = h == null ? HostHealth.unknown(host) : h;
            return new HostHealth(host, current.consecutiveFailures(), current.lastFailure(), current.lastSuccess(),
                    Optional.of(until));
        });
    }

    @Override
    public List<Delivery> list(DeliveryState state, int limit) {
        return rows.values().stream().map(Row::delivery).filter(d -> d.state() == state)
                .sorted(Comparator.comparing(Delivery::created).thenComparing(Delivery::id).reversed())
                .limit(Math.max(limit, 0)).toList();
    }

    @Override
    public long pendingCount() {
        return rows.values().stream().filter(r -> r.delivery().state() == DeliveryState.PENDING).count();
    }

    @Override
    public Optional<Instant> oldestPending() {
        return rows.values().stream()
                .filter(r -> r.delivery().state() == DeliveryState.PENDING)
                .map(r -> r.delivery().created())
                .min(Comparator.naturalOrder());
    }

    @Override
    public Optional<Instant> oldestDue(Instant now) {
        return rows.values().stream()
                .map(Row::delivery)
                .filter(d -> d.state() == DeliveryState.PENDING && !d.nextAttempt().isAfter(now))
                .map(Delivery::nextAttempt)
                .min(Comparator.naturalOrder());
    }

    @Override
    public synchronized int purge(Set<DeliveryState> states, Instant before) {
        int[] removed = { 0 };
        rows.values().removeIf(r -> {
            boolean old = states.contains(r.delivery().state()) && r.delivery().created().isBefore(before);
            if (old) {
                removed[0]++;
            }
            return old;
        });
        return removed[0];
    }
}

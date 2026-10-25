package org.sitenetsoft.quarkus.activitypub.store;

import static org.sitenetsoft.quarkus.activitypub.store.Bridge.offload;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.smallrye.mutiny.Uni;

/** {@link ReactiveDeliveryStore} over a {@link DeliveryStore}, run on worker threads. */
final class ReactiveDeliveryStoreAdapter implements ReactiveDeliveryStore {

    private final DeliveryStore d;

    ReactiveDeliveryStoreAdapter(DeliveryStore delegate) {
        this.d = delegate;
    }

    @Override
    public Uni<Void> save(List<Delivery> deliveries) {
        return offload(() -> d.save(deliveries));
    }

    @Override
    public Uni<List<Delivery>> claimDue(Instant now, int limit) {
        return offload(() -> d.claimDue(now, limit));
    }

    @Override
    public Uni<Void> update(Delivery delivery) {
        return offload(() -> d.update(delivery));
    }

    @Override
    public Uni<HostHealth> host(String host) {
        return offload(() -> d.host(host));
    }

    @Override
    public Uni<Void> recordHostResult(String host, boolean success, Instant at) {
        return offload(() -> d.recordHostResult(host, success, at));
    }

    @Override
    public Uni<Void> pauseHost(String host, Instant until) {
        return offload(() -> d.pauseHost(host, until));
    }

    @Override
    public Uni<Long> pendingCount() {
        return offload(d::pendingCount);
    }

    @Override
    public Uni<Optional<Instant>> oldestPending() {
        return offload(d::oldestPending);
    }
}

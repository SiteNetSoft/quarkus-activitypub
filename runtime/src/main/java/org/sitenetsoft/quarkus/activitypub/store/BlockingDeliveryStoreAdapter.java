package org.sitenetsoft.quarkus.activitypub.store;

import static org.sitenetsoft.quarkus.activitypub.store.Bridge.await;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** {@link DeliveryStore} over a {@link ReactiveDeliveryStore}. */
final class BlockingDeliveryStoreAdapter implements DeliveryStore {

    private final ReactiveDeliveryStore d;

    BlockingDeliveryStoreAdapter(ReactiveDeliveryStore delegate) {
        this.d = delegate;
    }

    @Override
    public void save(List<Delivery> deliveries) {
        await(d.save(deliveries));
    }

    @Override
    public List<Delivery> claimDue(Instant now, int limit) {
        return await(d.claimDue(now, limit));
    }

    @Override
    public void update(Delivery delivery) {
        await(d.update(delivery));
    }

    @Override
    public HostHealth host(String host) {
        return await(d.host(host));
    }

    @Override
    public void recordHostResult(String host, boolean success, Instant at) {
        await(d.recordHostResult(host, success, at));
    }

    @Override
    public void pauseHost(String host, Instant until) {
        await(d.pauseHost(host, until));
    }

    @Override
    public List<Delivery> list(DeliveryState state, int limit) {
        return await(d.list(state, limit));
    }

    @Override
    public long pendingCount() {
        return await(d.pendingCount());
    }

    @Override
    public Optional<Instant> oldestPending() {
        return await(d.oldestPending());
    }

    @Override
    public Optional<Instant> oldestDue(Instant now) {
        return await(d.oldestDue(now));
    }

    @Override
    public int purge(Set<DeliveryState> states, Instant before) {
        return await(d.purge(states, before));
    }
}

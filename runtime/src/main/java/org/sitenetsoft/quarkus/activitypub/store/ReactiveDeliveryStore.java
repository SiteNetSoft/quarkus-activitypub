package org.sitenetsoft.quarkus.activitypub.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.smallrye.mutiny.Uni;

/**
 * Reactive twin of {@link DeliveryStore}.
 */
public interface ReactiveDeliveryStore {

    Uni<Void> save(List<Delivery> deliveries);

    Uni<List<Delivery>> claimDue(Instant now, int limit);

    Uni<Void> update(Delivery delivery);

    Uni<HostHealth> host(String host);

    Uni<Void> recordHostResult(String host, boolean success, Instant at);

    Uni<Void> pauseHost(String host, Instant until);

    Uni<List<Delivery>> list(DeliveryState state, int limit);

    Uni<Long> pendingCount();

    Uni<Optional<Instant>> oldestPending();

    Uni<Optional<Instant>> oldestDue(Instant now);

    Uni<Integer> purge(Set<DeliveryState> states, Instant before);
}

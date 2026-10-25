package org.sitenetsoft.quarkus.activitypub.outbound;

import java.util.List;

import org.sitenetsoft.quarkus.activitypub.store.Delivery;

/**
 * Takes the deliveries of a published activity. Called inside the publishing transaction. The default,
 * {@link OutboxDeliveryExecutor}, queues them in the {@code DeliveryStore} for {@link DeliveryScheduler}; an
 * application bean replaces it (to hand deliveries to a message broker, for example).
 */
public interface DeliveryExecutor {

    void submit(List<Delivery> deliveries);
}

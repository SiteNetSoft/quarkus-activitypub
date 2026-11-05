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

    /**
     * Like {@link #submit} and returns the deliveries as saved (a delivery to a dead host comes back
     * {@code GIVEN_UP}), which is what {@code DeliveriesSubmitted} reports. {@link ActivityPubImpl} calls this
     * method; the default delegates to {@code submit} and returns {@code deliveries} unchanged, so an executor that
     * implements only {@code submit} keeps working. An executor that changes deliveries overrides this instead.
     */
    default List<Delivery> accept(List<Delivery> deliveries) {
        submit(deliveries);
        return deliveries;
    }
}

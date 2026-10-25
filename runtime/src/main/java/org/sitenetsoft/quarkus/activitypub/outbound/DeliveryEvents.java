package org.sitenetsoft.quarkus.activitypub.outbound;

import org.sitenetsoft.quarkus.activitypub.store.Delivery;

/**
 * CDI events fired (synchronously, on the delivery thread) after each delivery attempt.
 */
public final class DeliveryEvents {

    private DeliveryEvents() {
    }

    /** The inbox accepted the activity; {@code delivery} is in state {@code DELIVERED}. */
    public record DeliveryCompleted(Delivery delivery) {
    }

    /**
     * An attempt failed. {@code willRetry}: the delivery stays {@code PENDING} for {@code delivery.nextAttempt()};
     * otherwise it is {@code FAILED} or {@code GIVEN_UP}.
     */
    public record DeliveryFailed(Delivery delivery, String error, boolean willRetry) {
    }
}

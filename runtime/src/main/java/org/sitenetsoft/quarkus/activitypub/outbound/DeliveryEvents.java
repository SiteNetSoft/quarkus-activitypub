package org.sitenetsoft.quarkus.activitypub.outbound;

import java.net.URI;
import java.util.List;

import org.sitenetsoft.quarkus.activitypub.store.Delivery;

/**
 * CDI events about deliveries, fired synchronously: {@link DeliveriesSubmitted} on the publishing thread inside the
 * publish transaction, the others after each delivery attempt (on the delivery thread) or when a delivery is
 * refused at submit time.
 */
public final class DeliveryEvents {

    private DeliveryEvents() {
    }

    /**
     * Fired once per published activity (any of {@code publish}, {@code create}, {@code update}, {@code delete},
     * {@code follow} and so on), right after the {@link DeliveryExecutor} took the deliveries. {@code deliveries}
     * lists every one in the state the executor saved it in, including those already {@code FAILED} (recipient not
     * reachable, blocked domain) or {@code GIVEN_UP} (dead host); empty when the activity has no remote recipient.
     * Each {@code FAILED}/{@code GIVEN_UP} entry also fires a {@link DeliveryFailed} with {@code willRetry=false}.
     */
    public record DeliveriesSubmitted(URI activityId, List<Delivery> deliveries) {
    }

    /** The inbox accepted the activity; {@code delivery} is in state {@code DELIVERED}. */
    public record DeliveryCompleted(Delivery delivery) {
    }

    /**
     * An attempt failed. {@code willRetry}: the delivery stays {@code PENDING} for {@code delivery.nextAttempt()};
     * otherwise it is {@code FAILED} or {@code GIVEN_UP}. Also fired, with {@code willRetry=false} and the delivery's
     * {@code lastError}, for a delivery that is {@code FAILED} or {@code GIVEN_UP} when submitted, before any attempt.
     */
    public record DeliveryFailed(Delivery delivery, String error, boolean willRetry) {
    }
}

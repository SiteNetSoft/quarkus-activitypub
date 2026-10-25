package org.sitenetsoft.quarkus.activitypub.store;

/**
 * State of an outbound delivery.
 */
public enum DeliveryState {
    /** Waiting for its next attempt. */
    PENDING,
    /** The inbox answered 2xx. */
    DELIVERED,
    /** Permanently refused by the inbox (4xx); not retried. */
    FAILED,
    /** Retries exhausted or the host is dead. */
    GIVEN_UP
}

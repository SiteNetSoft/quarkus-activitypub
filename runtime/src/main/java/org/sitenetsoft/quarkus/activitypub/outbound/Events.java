package org.sitenetsoft.quarkus.activitypub.outbound;

import jakarta.enterprise.event.Event;

import org.jboss.logging.Logger;

/** Event firing shared by the outbound classes. */
final class Events {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    private Events() {
    }

    /** Fires {@code payload}; an observer that throws is logged and does not disturb the caller. */
    static <T> void fireQuietly(Event<T> event, T payload) {
        try {
            event.fire(payload);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Observer of %s failed", payload.getClass().getSimpleName());
        }
    }
}

package org.sitenetsoft.quarkus.activitypub.store;

import java.time.Instant;
import java.util.Optional;

/**
 * Delivery health of one remote host.
 */
public record HostHealth(String host, int consecutiveFailures, Optional<Instant> lastFailure, Optional<Instant> lastSuccess,
        Optional<Instant> pausedUntil) {

    /** A host never delivered to. */
    public static HostHealth unknown(String host) {
        return new HostHealth(host, 0, Optional.empty(), Optional.empty(), Optional.empty());
    }
}

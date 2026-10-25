package org.sitenetsoft.quarkus.activitypub.outbound;

import java.time.Duration;
import java.util.List;

/**
 * Delivery retry delays: 1m, 5m, 30m, 2h, 8h, 24h, 48h, then 48h for every later attempt. Also the pause of a failing
 * host (see {@link DeliveryScheduler}).
 */
public final class Backoff {

    private static final List<Duration> DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(8), Duration.ofHours(24), Duration.ofHours(48));

    private Backoff() {
    }

    /** The wait after the {@code attempt}-th failure (1-based; values below 1 count as 1). */
    public static Duration forAttempt(int attempt) {
        return DELAYS.get(Math.min(Math.max(attempt, 1), DELAYS.size()) - 1);
    }
}

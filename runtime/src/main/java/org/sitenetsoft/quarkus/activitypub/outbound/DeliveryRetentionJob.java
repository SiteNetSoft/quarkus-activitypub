package org.sitenetsoft.quarkus.activitypub.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.scheduler.Scheduled;

/**
 * Daily at 03:30: deletes the finished deliveries ({@code DELIVERED}, {@code FAILED}, {@code GIVEN_UP}) created more
 * than {@code quarkus.activitypub.delivery.retention} ago, whatever the {@code DeliveryStore}. Pending deliveries are
 * never deleted. A retention of {@code 0} disables it.
 */
@ApplicationScoped
public class DeliveryRetentionJob {

    static final Set<DeliveryState> FINISHED = EnumSet.of(DeliveryState.DELIVERED, DeliveryState.FAILED,
            DeliveryState.GIVEN_UP);

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    Stores stores;

    @Inject
    ActivityPubConfig config;

    @Inject
    Clock clock;

    @Scheduled(identity = "activitypub-delivery-retention", cron = "0 30 3 * * ?")
    void scheduled() {
        run();
    }

    /** One run (also callable directly); returns how many deliveries were deleted. */
    public int run() {
        Duration retention = config.delivery().retention();
        if (retention.isZero() || retention.isNegative()) {
            return 0;
        }
        int deleted = stores.deliveries().purge(FINISHED, clock.instant().minus(retention));
        LOG.debugf("Retention: %d finished deliveries older than %s deleted", deleted, retention);
        return deleted;
    }
}

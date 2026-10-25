package org.sitenetsoft.quarkus.activitypub.hibernate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;

/**
 * Daily at 03:00: deletes the remote objects kept longer than {@code quarkus.activitypub.remote-objects.retention}
 * and the inbound activity ids seen longer ago than {@code quarkus.activitypub.dedup.retention}. A retention of
 * {@code 0} disables that part. Finished deliveries are purged by the core {@code DeliveryRetentionJob}
 * ({@code quarkus.activitypub.delivery.retention}) through {@code DeliveryStore.purge}, not here.
 */
@ApplicationScoped
public class RetentionJob {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    ActivityPubEntityManager entityManager;

    @Inject
    ActivityPubConfig config;

    @Inject
    Clock clock;

    /** One run (also callable directly); each delete in its own transaction. */
    @Scheduled(identity = "activitypub-retention", cron = "0 0 3 * * ?")
    public void run() {
        Instant now = clock.instant();
        Duration objects = config.remoteObjects().retention();
        if (!objects.isZero()) {
            int deleted = QuarkusTransaction.requiringNew().call(() -> entityManager.get()
                    .createQuery("delete from ApObject o where o.local = false and o.created < :cutoff")
                    .setParameter("cutoff", now.minus(objects)).executeUpdate());
            LOG.debugf("Retention: %d remote objects older than %s deleted", deleted, objects);
        }
        Duration seen = config.dedup().retention();
        if (!seen.isZero()) {
            int deleted = QuarkusTransaction.requiringNew().call(() -> entityManager.get()
                    .createQuery("delete from ApSeenActivity s where s.seenAt < :cutoff")
                    .setParameter("cutoff", now.minus(seen)).executeUpdate());
            LOG.debugf("Retention: %d seen activity ids older than %s deleted", deleted, seen);
        }
    }
}

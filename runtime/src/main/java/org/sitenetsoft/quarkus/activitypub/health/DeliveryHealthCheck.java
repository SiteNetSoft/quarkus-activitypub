package org.sitenetsoft.quarkus.activitypub.health;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * Readiness check {@code activitypub-delivery}: the number of pending deliveries ({@code pending}), the creation time
 * of the oldest ({@code oldest}, absent when none is pending) and the earliest next attempt among the due ones
 * ({@code oldestDue}, absent when nothing is due). DOWN when due work has waited longer than
 * {@code quarkus.activitypub.health.max-backlog-age}, i.e. the delivery job is not keeping up. Deliveries waiting for
 * their backoff (an unreachable remote) are not due and never turn readiness DOWN. Registered only when SmallRye Health
 * is present.
 */
@Readiness
@ApplicationScoped
public class DeliveryHealthCheck implements HealthCheck {

    static final String NAME = "activitypub-delivery";

    @Inject
    Stores stores;

    @Inject
    ActivityPubConfig config;

    @Inject
    Clock clock;

    @Override
    public HealthCheckResponse call() {
        DeliveryStore store = stores.deliveries();
        Instant now = clock.instant();
        long pending = store.pendingCount();
        Optional<Instant> oldest = store.oldestPending();
        Optional<Instant> oldestDue = store.oldestDue(now);
        Instant limit = now.minus(config.health().maxBacklogAge());
        HealthCheckResponseBuilder response = HealthCheckResponse.named(NAME)
                .status(oldestDue.filter(o -> o.isBefore(limit)).isEmpty())
                .withData("pending", pending);
        oldest.ifPresent(o -> response.withData("oldest", o.toString()));
        oldestDue.ifPresent(o -> response.withData("oldestDue", o.toString()));
        return response.build();
    }
}

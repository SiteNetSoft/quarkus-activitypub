package org.sitenetsoft.quarkus.activitypub.outbound;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.HostHealth;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.arc.DefaultBean;

/**
 * The default {@link DeliveryExecutor}: a transactional outbox. Deliveries are saved in the {@code DeliveryStore}
 * (joining the publishing transaction) and sent later by {@link DeliveryScheduler}.
 * <p>
 * A new pending delivery to a <em>dead</em> host is saved as {@code GIVEN_UP} right away, unless the host's pause has
 * run out, in which case it goes out as the probe of that period. A host is dead when it is paused-worthy
 * ({@code consecutiveFailures >= 5}), its last failure lies within its current pause period
 * ({@code Backoff.forAttempt(consecutiveFailures - 4)}), and its last success is older than {@code delivery.dead-after}.
 * A host that never succeeded is not dead: its deliveries are bounded by {@code max-attempts} and the host pause.
 */
@DefaultBean
@ApplicationScoped
public class OutboxDeliveryExecutor implements DeliveryExecutor {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    Stores stores;

    @Inject
    ActivityPubConfig config;

    @Inject
    Clock clock;

    @Override
    public void submit(List<Delivery> deliveries) {
        Instant now = clock.instant();
        Map<String, HostHealth> hosts = new HashMap<>();
        List<Delivery> out = new ArrayList<>(deliveries.size());
        for (Delivery d : deliveries) {
            if (d.state() != DeliveryState.PENDING) {
                out.add(d);
                continue;
            }
            HostHealth host = hosts.computeIfAbsent(d.host(), h -> stores.deliveries().host(h));
            if (isDead(host, now) && host.pausedUntil().filter(now::isBefore).isPresent()) {
                LOG.debugf("Delivery of %s to %s given up: host %s is dead", d.activityId(), d.inbox(), d.host());
                out.add(d.withState(DeliveryState.GIVEN_UP).withLastError(Optional.of("Host " + d.host()
                        + " is dead (last success " + host.lastSuccess().orElseThrow() + "); not attempted")));
            } else {
                out.add(d);
            }
        }
        stores.deliveries().save(out);
    }

    private boolean isDead(HostHealth host, Instant now) {
        int failures = host.consecutiveFailures();
        return failures >= DeliveryScheduler.PAUSE_AFTER_FAILURES
                && host.lastSuccess().filter(s -> s.isBefore(now.minus(config.delivery().deadAfter()))).isPresent()
                && host.lastFailure().filter(f -> f.isAfter(now.minus(DeliveryScheduler.pauseFor(failures)))).isPresent();
    }
}

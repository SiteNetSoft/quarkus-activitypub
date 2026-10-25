package org.sitenetsoft.quarkus.activitypub.outbound;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupEvent;

/**
 * Counts delivery attempts as {@code activitypub.delivery.attempts{result}} ({@code delivered}, {@code retry},
 * {@code failed}, {@code given_up}) and exposes the number of pending deliveries as the gauge
 * {@code activitypub.delivery.queue}, when Micrometer is on the class path and a {@code MeterRegistry} bean exists;
 * otherwise does nothing. Micrometer is an optional dependency, so its types are only touched inside
 * {@link Micrometer}, which is loaded only when they are present.
 */
@ApplicationScoped
public class DeliveryMetrics {

    static final String ATTEMPTS = "activitypub.delivery.attempts";
    static final String QUEUE = "activitypub.delivery.queue";

    private static final boolean MICROMETER = present("io.micrometer.core.instrument.MeterRegistry");

    @Inject
    Stores stores;

    void registerQueueGauge(@Observes StartupEvent event) {
        if (MICROMETER) {
            Micrometer.gauge(stores.deliveries());
        }
    }

    void attempt(String result) {
        if (MICROMETER) {
            Micrometer.count(result);
        }
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, DeliveryMetrics.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static final class Micrometer {

        private static volatile Instance<MeterRegistry> registry;

        static void count(String result) {
            Instance<MeterRegistry> r = registry();
            if (r.isResolvable()) {
                r.get().counter(ATTEMPTS, "result", result).increment();
            }
        }

        static void gauge(DeliveryStore store) {
            Instance<MeterRegistry> r = registry();
            if (r.isResolvable()) {
                Gauge.builder(QUEUE, store, DeliveryStore::pendingCount).strongReference(true)
                        .description("Pending ActivityPub deliveries").register(r.get());
            }
        }

        private static Instance<MeterRegistry> registry() {
            Instance<MeterRegistry> r = registry;
            if (r == null) {
                r = Arc.container().select(MeterRegistry.class);
                registry = r;
            }
            return r;
        }
    }
}

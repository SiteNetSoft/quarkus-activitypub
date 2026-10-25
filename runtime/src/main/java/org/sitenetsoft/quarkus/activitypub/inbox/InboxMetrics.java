package org.sitenetsoft.quarkus.activitypub.inbox;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.Arc;

/**
 * Counts inbox outcomes as {@code activitypub.inbox.received{type,result}} when Micrometer is on the class path and a
 * {@code MeterRegistry} bean exists; otherwise does nothing. Micrometer is an optional dependency, so its types are
 * only touched inside {@link Micrometer}, which is loaded only when they are present.
 */
@ApplicationScoped
public class InboxMetrics {

    static final String COUNTER = "activitypub.inbox.received";

    private static final boolean MICROMETER = present("io.micrometer.core.instrument.MeterRegistry");

    void count(String type, String result) {
        if (MICROMETER) {
            Micrometer.count(type, result);
        }
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, InboxMetrics.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static final class Micrometer {

        private static volatile Instance<MeterRegistry> registry;

        static void count(String type, String result) {
            Instance<MeterRegistry> r = registry;
            if (r == null) {
                r = Arc.container().select(MeterRegistry.class);
                registry = r;
            }
            if (r.isResolvable()) {
                r.get().counter(COUNTER, "type", type, "result", result).increment();
            }
        }
    }
}

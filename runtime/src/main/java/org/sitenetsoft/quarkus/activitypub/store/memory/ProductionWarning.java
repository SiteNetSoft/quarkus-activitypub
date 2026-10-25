package org.sitenetsoft.quarkus.activitypub.store.memory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkus.runtime.LaunchMode;

/**
 * The WARN each in-memory store logs when it is used in production. Called from the stores' {@code @PostConstruct}
 * (which runs on the real instance only, not on the client proxy); logged at most once per store name.
 */
final class ProductionWarning {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ProductionWarning() {
    }

    static void warnIfProduction(String store) {
        warnIfProduction(store, LaunchMode.current());
    }

    /** True when this call logged the WARN. */
    static boolean warnIfProduction(String store, LaunchMode mode) {
        if (mode != LaunchMode.NORMAL || !WARNED.add(store)) {
            return false;
        }
        LOG.warnf("In-memory %s in production: data is lost on restart and the store is not transactional. "
                + "Add quarkus-activitypub-hibernate.", store);
        return true;
    }
}

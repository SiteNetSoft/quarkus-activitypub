package org.sitenetsoft.quarkus.activitypub.store.memory;

import org.jboss.logging.Logger;

import io.quarkus.runtime.LaunchMode;

/** The WARN each in-memory store logs when it is used in production. */
final class ProductionWarning {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    private ProductionWarning() {
    }

    static void warnIfProduction(String store) {
        if (LaunchMode.current() == LaunchMode.NORMAL) {
            LOG.warnf("In-memory %s in production: data is lost on restart and the store is not transactional. "
                    + "Add quarkus-activitypub-hibernate.", store);
        }
    }
}

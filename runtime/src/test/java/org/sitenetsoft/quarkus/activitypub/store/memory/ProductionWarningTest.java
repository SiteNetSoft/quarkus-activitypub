package org.sitenetsoft.quarkus.activitypub.store.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.LaunchMode;

class ProductionWarningTest {

    @Test
    void warnsOncePerStoreInProduction() {
        assertTrue(ProductionWarning.warnIfProduction("TestStoreA", LaunchMode.NORMAL));
        assertFalse(ProductionWarning.warnIfProduction("TestStoreA", LaunchMode.NORMAL), "second call stays silent");
        assertTrue(ProductionWarning.warnIfProduction("TestStoreB", LaunchMode.NORMAL), "each store warns once");
    }

    @Test
    void silentOutsideProduction() {
        assertFalse(ProductionWarning.warnIfProduction("TestStoreC", LaunchMode.TEST));
        assertFalse(ProductionWarning.warnIfProduction("TestStoreC", LaunchMode.DEVELOPMENT));
        assertTrue(ProductionWarning.warnIfProduction("TestStoreC", LaunchMode.NORMAL), "dev/test calls do not use up the warning");
    }

    @Test
    void warningRunsFromPostConstructNotConstructor() throws Exception {
        for (Class<?> store : new Class<?>[] { InMemoryActorStore.class, InMemoryObjectStore.class, InMemoryFollowerStore.class,
                InMemoryDeliveryStore.class }) {
            assertTrue(store.getDeclaredMethod("warnIfProduction").isAnnotationPresent(jakarta.annotation.PostConstruct.class),
                    store.getSimpleName());
        }
    }
}

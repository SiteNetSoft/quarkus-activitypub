package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryRetentionJob;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;

/** {@code delivery.retention=0} keeps finished deliveries forever. */
public class DeliveryRetentionDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(MutableClock.class)
            .overrideConfigKey("quarkus.activitypub.delivery.retention", "0");

    @Inject
    Stores stores;

    @Inject
    DeliveryRetentionJob job;

    @Inject
    MutableClock clock;

    @Test
    void nothingIsDeleted() {
        Delivery old = Delivery.create(URI.create("https://remote.example/inbox/1"),
                URI.create(LoopbackTests.BASE + "/activities/1"), "{}".getBytes(StandardCharsets.UTF_8),
                LoopbackTests.BASE + "/users/alice#main-key", clock.instant().minus(Duration.ofDays(3650)))
                .withState(DeliveryState.DELIVERED);
        stores.deliveries().save(List.of(old));
        assertEquals(0, job.run());
        assertTrue(stores.deliveries().list(DeliveryState.DELIVERED, 10_000).stream()
                .anyMatch(d -> d.id().equals(old.id())));
    }
}

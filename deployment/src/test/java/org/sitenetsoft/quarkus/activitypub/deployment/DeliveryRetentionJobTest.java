package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryRetentionJob;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;

/** {@link DeliveryRetentionJob} on the in-memory store, with the default retention of 7 days. */
public class DeliveryRetentionJobTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(MutableClock.class);

    @Inject
    Stores stores;

    @Inject
    DeliveryRetentionJob job;

    @Inject
    MutableClock clock;

    static Delivery delivery(Instant created, DeliveryState state) {
        return Delivery.create(URI.create("https://remote.example/inbox/" + UUID.randomUUID()),
                URI.create(LoopbackTests.BASE + "/activities/" + UUID.randomUUID()),
                "{}".getBytes(StandardCharsets.UTF_8), LoopbackTests.BASE + "/users/alice#main-key", created)
                .withState(state);
    }

    private List<String> stored() {
        return Stream.of(DeliveryState.values()).flatMap(s -> stores.deliveries().list(s, 10_000).stream())
                .map(Delivery::id).toList();
    }

    @Test
    void finishedDeliveriesOlderThanRetentionAreDeleted() {
        Instant now = clock.instant();
        Instant old = now.minus(Duration.ofDays(8));
        Delivery delivered = delivery(old, DeliveryState.DELIVERED);
        Delivery failed = delivery(old, DeliveryState.FAILED);
        Delivery givenUp = delivery(old, DeliveryState.GIVEN_UP);
        Delivery pending = delivery(old, DeliveryState.PENDING);
        Delivery recent = delivery(now.minus(Duration.ofDays(6)), DeliveryState.DELIVERED);
        stores.deliveries().save(List.of(delivered, failed, givenUp, pending, recent));

        assertEquals(3, job.run());

        List<String> left = stored();
        assertFalse(left.contains(delivered.id()));
        assertFalse(left.contains(failed.id()));
        assertFalse(left.contains(givenUp.id()));
        assertTrue(left.contains(pending.id()), "pending deliveries are never purged");
        assertTrue(left.contains(recent.id()), "younger than 7 days");
    }
}

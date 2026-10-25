package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.outbound.Backoff;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryEvents.DeliveryFailed;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.QuarkusUnitTest;

public class DeliveryRetryTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, RecordingDeliveryStore.class,
            DeliveryEventCollector.class, MutableClock.class, OutboundTestSupport.class, SimpleRegistry.class)
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1s");

    static final FakeFediverseServer fake = new FakeFediverseServer();

    /** A backend for the Micrometer composite registry, which has none in tests. */
    @Singleton
    public static class SimpleRegistry {
        @Produces
        @Singleton
        SimpleMeterRegistry registry() {
            return new SimpleMeterRegistry();
        }
    }

    @Inject
    MeterRegistry registry;

    @Inject
    ActivityPub activityPub;

    @Inject
    DeliveryScheduler scheduler;

    @Inject
    RecordingDeliveryStore deliveries;

    @Inject
    DeliveryEventCollector events;

    @Inject
    MutableClock clock;

    @Inject
    Stores stores;

    @Inject
    AliceActor alice;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    @BeforeEach
    void cleanSlate() {
        OutboundTestSupport.onlyFollowerDave(stores, fake);
        OutboundTestSupport.flush(clock, scheduler, stores, fake);
    }

    private Activity publish() {
        return activityPub.create(alice.get(), followersNote());
    }

    @Test
    void serverErrorIsRetriedAfterBackoff() {
        fake.inboxStatus(500);
        Activity create = publish();
        Instant now = clock.instant();
        scheduler.run();

        Delivery first = deliveries.only(create.id());
        assertEquals(1, first.attempts());
        assertEquals(now.plus(Duration.ofMinutes(1)), first.nextAttempt());
        assertEquals(DeliveryState.PENDING, first.state());
        assertTrue(first.lastError().isPresent());
        List<DeliveryFailed> failed = events.failed(create.id());
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).willRetry());

        scheduler.run(); // not due yet
        assertEquals(1, fake.requestCount("/users/dave/inbox"));

        fake.inboxStatus(200); // before the clock moves: the scheduled poll may send it first
        clock.advance(Duration.ofMinutes(1));
        scheduler.run();

        Delivery delivered = deliveries.only(create.id());
        assertEquals(DeliveryState.DELIVERED, delivered.state());
        assertEquals(2, delivered.attempts());
        assertEquals(1, events.completed(create.id()).size());
        assertEquals(2, fake.requestCount("/users/dave/inbox"));
    }

    @Test
    void status410IsFailedWithoutRetry() {
        fake.inboxStatus(410);
        Activity create = publish();
        scheduler.run();

        Delivery delivery = deliveries.only(create.id());
        assertEquals(DeliveryState.FAILED, delivery.state());
        assertEquals(1, delivery.attempts());
        assertTrue(delivery.lastError().orElseThrow().contains("410"), delivery.lastError().orElseThrow());
        assertFalse(events.failed(create.id()).get(0).willRetry());

        clock.advance(Duration.ofDays(3));
        scheduler.run();
        assertEquals(1, fake.requestCount("/users/dave/inbox"));
    }

    @Test
    void status429Retries() {
        fake.inboxStatus(429);
        Activity create = publish();
        Instant now = clock.instant();
        scheduler.run();

        Delivery delivery = deliveries.only(create.id());
        assertEquals(DeliveryState.PENDING, delivery.state());
        assertEquals(now.plus(Duration.ofMinutes(1)), delivery.nextAttempt());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        fake.inboxStatus(503);
        Activity create = publish();
        for (int attempt = 1; attempt <= 8; attempt++) {
            scheduler.run();
            Delivery d = deliveries.only(create.id());
            assertEquals(attempt, d.attempts());
            if (attempt < 8) {
                assertEquals(DeliveryState.PENDING, d.state());
                clock.advance(Backoff.forAttempt(attempt));
            }
        }
        Delivery last = deliveries.only(create.id());
        assertEquals(DeliveryState.GIVEN_UP, last.state());
        assertEquals(8, fake.requestCount("/users/dave/inbox"));
        List<DeliveryFailed> failed = events.failed(create.id());
        assertEquals(8, failed.size());
        assertFalse(failed.get(7).willRetry());

        clock.advance(Duration.ofDays(3));
        scheduler.run();
        assertEquals(8, fake.requestCount("/users/dave/inbox"));
    }

    @Test
    void attemptsAreCountedAndTheQueueMeasured() {
        double failedBefore = count("failed");
        double retriedBefore = count("retry");
        fake.inboxStatus(410);
        publish();
        scheduler.run();
        fake.inboxStatus(500);
        publish();
        scheduler.run();

        assertEquals(failedBefore + 1, count("failed"));
        assertEquals(retriedBefore + 1, count("retry"));
        assertEquals(stores.deliveries().pendingCount(), registry.get("activitypub.delivery.queue").gauge().value());
        assertTrue(registry.get("activitypub.delivery.queue").gauge().value() >= 1);
    }

    private double count(String result) {
        Counter counter = registry.find("activitypub.delivery.attempts").tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void backoffSchedule() {
        assertEquals(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2),
                Duration.ofHours(8), Duration.ofHours(24), Duration.ofHours(48), Duration.ofHours(48)),
                java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(Backoff::forAttempt).toList());
    }
}

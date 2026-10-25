package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.HostHealth;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

public class DeliveryHostHealthTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, RecordingDeliveryStore.class,
            MutableClock.class, OutboundTestSupport.class)
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1s");

    static final FakeFediverseServer fake = new FakeFediverseServer();
    static final String INBOX = "/users/dave/inbox";

    @Inject
    ActivityPub activityPub;

    @Inject
    DeliveryScheduler scheduler;

    @Inject
    RecordingDeliveryStore deliveries;

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

    private String host() {
        return fake.baseUri().getHost();
    }

    private Activity publish() {
        return activityPub.create(alice.get(), followersNote());
    }

    /** Five failing deliveries in one poll: the host is paused for a minute. */
    private void failFiveTimes() {
        fake.inboxStatus(500);
        for (int i = 0; i < 5; i++) {
            publish();
        }
        scheduler.run();
        assertEquals(5, fake.requestCount(INBOX));
    }

    @Test
    void fiveFailuresPauseTheHost() {
        failFiveTimes();
        Instant now = clock.instant();
        HostHealth health = stores.deliveries().host(host());
        assertEquals(5, health.consecutiveFailures());
        assertEquals(Optional.of(now.plus(Duration.ofMinutes(1))), health.pausedUntil());

        Activity held = publish();
        scheduler.run();
        assertEquals(5, fake.requestCount(INBOX), "a paused host is not sent to");
        Delivery waiting = deliveries.only(held.id());
        assertEquals(DeliveryState.PENDING, waiting.state());
        assertEquals(0, waiting.attempts());
        assertEquals(health.pausedUntil().get(), waiting.nextAttempt());

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        scheduler.run();
        assertTrue(fake.requestCount(INBOX) > 5, "a probe goes out after the pause");
        assertEquals(1, deliveries.only(held.id()).attempts());
    }

    @Test
    void successResetsHost() {
        failFiveTimes();
        assertTrue(stores.deliveries().host(host()).pausedUntil().isPresent());

        fake.inboxStatus(202);
        clock.advance(Duration.ofMinutes(2));
        scheduler.run();

        HostHealth health = stores.deliveries().host(host());
        assertEquals(0, health.consecutiveFailures());
        assertEquals(Optional.empty(), health.pausedUntil());
        assertEquals(Optional.of(clock.instant()), health.lastSuccess());
    }

    @Test
    void deadHostGivesUpNewDeliveriesExceptOneProbePerPause() {
        Instant now = clock.instant();
        stores.deliveries().recordHostResult(host(), true, now.minus(Duration.ofDays(31)));
        for (int i = 0; i < 6; i++) {
            stores.deliveries().recordHostResult(host(), false, now);
        }
        stores.deliveries().pauseHost(host(), now.plus(Duration.ofMinutes(5)));

        Activity during = publish();
        Delivery givenUp = deliveries.only(during.id());
        assertEquals(DeliveryState.GIVEN_UP, givenUp.state());
        assertTrue(givenUp.lastError().isPresent());

        clock.advance(Duration.ofMinutes(5));
        Activity probe = publish();
        assertEquals(DeliveryState.PENDING, deliveries.only(probe.id()).state());
        scheduler.run();
        assertEquals(List.of(DeliveryState.DELIVERED),
                deliveries.forActivity(probe.id()).stream().map(Delivery::state).toList());
        assertEquals(0, stores.deliveries().host(host()).consecutiveFailures());
    }
}

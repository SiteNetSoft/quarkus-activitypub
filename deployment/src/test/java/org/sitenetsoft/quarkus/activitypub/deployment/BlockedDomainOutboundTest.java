package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.ActivityPubException;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.keys.ActorKeyResolver;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

/** {@code blocked-domains} names the fake's host: nothing of ours goes out to it, deliveries or fetches. */
public class BlockedDomainOutboundTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, RecordingDeliveryStore.class,
            MutableClock.class, OutboundTestSupport.class)
            .overrideConfigKey("quarkus.activitypub.blocked-domains", "127.0.0.1")
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1h");

    static final FakeFediverseServer fake = new FakeFediverseServer();

    @Inject
    ActivityPub activityPub;

    @Inject
    ActivityPubClient client;

    @Inject
    ActorKeyResolver keys;

    @Inject
    DeliveryScheduler scheduler;

    @Inject
    RecordingDeliveryStore deliveries;

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
    void clear() {
        fake.clear();
    }

    @Test
    void blockedDomainFollowerReceivesNothing() {
        OutboundTestSupport.onlyFollowerDave(stores, fake);
        Activity create = activityPub.create(alice.get(), followersNote());
        scheduler.run();
        assertTrue(deliveries.forActivity(create.id()).stream().allMatch(d -> d.state() == DeliveryState.FAILED),
                "no delivery to the blocked follower's inbox is queued");
        assertEquals(List.of(), fake.received());
    }

    @Test
    void blockedRecipientIsRecordedAsFailed() {
        FakeActor carol = fake.actor("carol");
        Note note = new Note();
        note.content("<p>hi carol</p>");
        note.to(ObjectOrLink.of(carol.id()));
        Activity create = activityPub.create(alice.get(), note);
        scheduler.run();
        Delivery delivery = deliveries.only(create.id());
        assertEquals(DeliveryState.FAILED, delivery.state());
        assertEquals(Optional.of("blocked domain"), delivery.lastError());
        assertEquals(List.of(), fake.received(), "the recipient is not even fetched");
    }

    @Test
    void queuedDeliveryToNowBlockedDomainIsFailed() {
        Delivery queued = Delivery.create(fake.actor("erin").inbox(),
                URI.create(LoopbackTests.BASE + "/activities/queued-before-block"), "{}".getBytes(StandardCharsets.UTF_8),
                LoopbackTests.BASE + "/users/alice#main-key", java.time.Instant.parse("2025-12-31T00:00:00Z"));
        deliveries.save(List.of(queued));
        scheduler.run();
        Delivery done = deliveries.only(queued.activityId());
        assertEquals(DeliveryState.FAILED, done.state());
        assertEquals(Optional.of("blocked domain"), done.lastError());
        assertEquals(0, done.attempts());
        assertEquals(List.of(), fake.received());
    }

    @Test
    void blockedDomainKeyIsNotFetched() {
        FakeActor dan = fake.actor("dan");
        assertTrue(keys.resolve(dan.keyId()).await().atMost(Duration.ofSeconds(15)).isEmpty());
        assertEquals(0, fake.requestCount("/users/dan"));
    }

    @Test
    void fetchFromBlockedDomainIsRefused() {
        FakeActor fay = fake.actor("fay");
        ActivityPubException e = assertThrows(ActivityPubException.class, () -> client.fetch(fay.id()));
        assertEquals(ActivityPubException.Reason.BLOCKED, e.reason());
        assertEquals(List.of(), fake.received());
    }
}

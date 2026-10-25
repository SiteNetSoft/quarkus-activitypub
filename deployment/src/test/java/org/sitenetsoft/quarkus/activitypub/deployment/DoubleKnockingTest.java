package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;
import org.sitenetsoft.quarkus.activitypub.test.ReceivedRequest;
import org.sitenetsoft.quarkus.httpsignatures.client.SchemeMemory;

import io.quarkus.test.QuarkusUnitTest;

public class DoubleKnockingTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, RecordingDeliveryStore.class,
            OutboundTestSupport.class);

    static final FakeFediverseServer fake = new FakeFediverseServer();
    static final String INBOX = "/users/dave/inbox";

    @Inject
    ActivityPub activityPub;

    @Inject
    DeliveryScheduler scheduler;

    @Inject
    RecordingDeliveryStore deliveries;

    @Inject
    SchemeMemory memory;

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

    @Test
    void cavageRejectedThenRfc9421Remembered() {
        OutboundTestSupport.onlyFollowerDave(stores, fake);
        fake.rejectCavage(true);
        fake.clear();

        Activity first = activityPub.create(alice.get(), followersNote());
        scheduler.run();

        List<ReceivedRequest> posts = fake.received().stream().filter(r -> r.path().equals(INBOX)).toList();
        assertEquals(2, posts.size(), () -> posts.toString());
        assertTrue(posts.get(0).header("Signature-Input").isEmpty(), "first knock is Cavage");
        assertFalse(posts.get(0).verified());
        assertTrue(posts.get(1).header("Signature-Input").isPresent(), "second knock is RFC 9421");
        assertTrue(posts.get(1).verified());
        assertEquals(DeliveryState.DELIVERED, deliveries.only(first.id()).state());
        assertEquals(Optional.of(Scheme.RFC9421), memory.preferredFor(fake.baseUri().getHost()));

        fake.clear();
        Activity second = activityPub.create(alice.get(), followersNote());
        scheduler.run();
        assertEquals(1, fake.requestCount(INBOX));
        assertEquals(DeliveryState.DELIVERED, deliveries.only(second.id()).state());
    }
}

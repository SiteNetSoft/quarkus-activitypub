package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

/**
 * With an application config key present ({@code hook}, the only one, so the config provider's fallback key), a
 * delivery whose actor key is gone must fail, never go out signed with the config key.
 */
public class DeliverySigningKeyTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTestWithResources(new String[] { "keys/hook.pem" },
            AliceActor.class, RecordingDeliveryStore.class, MutableClock.class, OutboundTestSupport.class)
            .overrideConfigKey("quarkus.http-signatures.keys.hook.location", "keys/hook.pem")
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1h");

    static final FakeFediverseServer fake = new FakeFediverseServer();

    @Inject
    ActivityPub activityPub;

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

    @Test
    void deliveryWithMissingActorKeyIsFailedNotSignedWithConfigKey() {
        OutboundTestSupport.onlyFollowerDave(stores, fake);
        LocalActor actor = alice.get();
        // the actor (and its key pair) is deleted while its deliveries are queued
        stores.actors().delete(actor.id());
        fake.clear();
        Activity create = activityPub.create(actor, followersNote());
        scheduler.run();

        Delivery delivery = deliveries.only(create.id());
        assertEquals(DeliveryState.FAILED, delivery.state());
        assertTrue(delivery.lastError().orElseThrow().contains("Cannot sign"), delivery.lastError().orElseThrow());
        assertEquals(0, fake.requestCount("/"), "nothing was sent");
    }
}

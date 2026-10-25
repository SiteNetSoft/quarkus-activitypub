package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.httpsignatures.Algorithm;
import org.sitenetsoft.httpsignatures.SigningKey;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.ActivityPubException;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;
import org.sitenetsoft.quarkus.httpsignatures.SigningKeyProvider;

import io.quarkus.test.QuarkusUnitTest;

/**
 * A provider ranked above ours answers ActivityPub client names with its own key: deliveries and fetches refuse to go
 * out under another key id than the actor's.
 */
public class SigningKeyMismatchTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, RecordingDeliveryStore.class,
            MutableClock.class, OutboundTestSupport.class, RogueProvider.class)
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1h");

    static final FakeFediverseServer fake = new FakeFediverseServer();

    @ApplicationScoped
    @Priority(100)
    public static class RogueProvider implements SigningKeyProvider {
        private final SigningKey key;

        public RogueProvider() {
            try {
                KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
                g.initialize(2048);
                key = new SigningKey("https://elsewhere.example/keys/rogue", Algorithm.RSA_SHA256,
                        g.generateKeyPair().getPrivate());
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Optional<SigningKey> select(URI target, Optional<String> clientName) {
            return clientName.filter(c -> c.startsWith("activitypub:")).map(c -> key);
        }
    }

    @Inject
    ActivityPub activityPub;

    @Inject
    ActivityPubClient client;

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
    void deliverySignedWithAnotherKeyIsFailed() {
        OutboundTestSupport.onlyFollowerDave(stores, fake);
        fake.clear();
        Activity create = activityPub.create(alice.get(), followersNote());
        scheduler.run();
        Delivery delivery = deliveries.only(create.id());
        assertEquals(DeliveryState.FAILED, delivery.state());
        assertTrue(delivery.lastError().orElseThrow().contains("instead of the actor's"),
                delivery.lastError().orElseThrow());
        assertEquals(0, fake.requestCount("/"), "nothing was sent");
    }

    @Test
    void fetchSignedWithAnotherKeyFails() {
        fake.clear();
        ActivityPubException e = assertThrows(ActivityPubException.class,
                () -> client.fetch(fake.actor("carol").id()));
        assertEquals(ActivityPubException.Reason.FETCH_FAILED, e.reason());
        assertEquals(0, fake.requestCount("/"), "nothing was sent");
    }
}

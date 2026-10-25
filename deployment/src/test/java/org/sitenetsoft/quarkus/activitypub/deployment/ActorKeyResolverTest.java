package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.httpsignatures.VerificationKey;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.keys.ActorKeyResolver;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;
import org.sitenetsoft.quarkus.safefetch.AddressPolicy;

import io.quarkus.test.QuarkusUnitTest;

public class ActorKeyResolverTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class, BlockOneAddress.class);

    /** Allows loopback (the fake) but refuses {@code 127.0.0.2}, standing in for a private address. */
    @ApplicationScoped
    public static class BlockOneAddress implements AddressPolicy {
        @Override
        public boolean allows(InetAddress address) {
            return !"127.0.0.2".equals(address.getHostAddress());
        }
    }

    /** Named {@code localhost} so a key id can spell the host in another case. */
    static final FakeFediverseServer fake = new FakeFediverseServer("localhost");

    @Inject
    ActorKeyResolver resolver;

    @Inject
    ActivityPubUrls urls;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    private Optional<VerificationKey> resolve(String keyId) {
        return resolver.resolve(keyId).await().atMost(Duration.ofSeconds(15));
    }

    @Test
    void remoteKeyResolves() {
        FakeActor carol = fake.actor("carol");
        Optional<VerificationKey> key = resolve(carol.keyId());
        assertTrue(key.isPresent());
        assertEquals(carol.keyId(), key.get().keyId());
        assertEquals("RSA", key.get().key().getAlgorithm());
        assertEquals(carol.publicKey(), key.get().key());
        assertEquals(Optional.of(carol.id()), resolver.ownerOf(carol.keyId()));
    }

    @Test
    void publicKeyArrayAccepted() {
        FakeActor dan = fake.actor("dan").publicKeyAsArray(true);
        assertTrue(resolve(dan.keyId()).isPresent());
    }

    @Test
    void ownerMismatchRejected() {
        FakeActor mallory = fake.actor("mallory").publicKeyOwner(fake.actor("carol").id());
        assertTrue(resolve(mallory.keyId()).isEmpty());
        assertTrue(resolver.ownerOf(mallory.keyId()).isEmpty());
    }

    @Test
    void hostCaseDifferenceAccepted() {
        FakeActor frank = fake.actor("frank");
        String upper = frank.keyId().replace("//localhost:", "//LOCALHOST:");
        Optional<VerificationKey> key = resolve(upper);
        assertTrue(key.isPresent());
        assertEquals(upper, key.get().keyId());
    }

    @Test
    void unknownKeyIsEmpty() {
        assertTrue(resolve(fake.baseUri() + "/users/nobody#main-key").isEmpty());
        assertTrue(resolve(fake.actor("carol").id() + "#other-key").isEmpty());
        assertTrue(resolve("not a uri").isEmpty());
        // safe-fetch refuses the address: still empty, not a failure
        assertTrue(resolve("http://127.0.0.2:" + fake.port() + "/users/carol#main-key").isEmpty());
    }

    @Test
    void localKeyResolvesWithoutFetch() {
        fake.clear();
        String keyId = urls.keyId("alice").toString();
        Optional<VerificationKey> key = resolve(keyId);
        assertTrue(key.isPresent());
        assertEquals("RSA", key.get().key().getAlgorithm());
        assertEquals(Optional.of(urls.actor("alice")), resolver.ownerOf(keyId));
        assertTrue(resolve(LoopbackTests.BASE + "/users/nobody#main-key").isEmpty());
        assertTrue(fake.received().isEmpty());
    }
}

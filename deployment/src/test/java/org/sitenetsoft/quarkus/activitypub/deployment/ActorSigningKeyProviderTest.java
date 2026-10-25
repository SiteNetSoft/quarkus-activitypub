package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Optional;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.httpsignatures.Algorithm;
import org.sitenetsoft.httpsignatures.SigningKey;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.keys.ActorSigningKeyProvider;
import org.sitenetsoft.quarkus.httpsignatures.client.RequestSigner;

import io.quarkus.test.QuarkusUnitTest;

public class ActorSigningKeyProviderTest {

    static final URI TARGET = URI.create("https://remote.example/inbox");

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(TestActors.class)
                    .addAsResource("keys/hook.pem"))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE)
            .overrideConfigKey("quarkus.http-signatures.keys.hook.location", "keys/hook.pem");

    @Inject
    ActorSigningKeyProvider provider;

    @Inject
    RequestSigner requestSigner;

    @Inject
    ActivityPubUrls urls;

    @Test
    void selectsLocalActorKey() {
        String keyId = urls.keyId("alice").toString();
        Optional<SigningKey> key = provider.select(TARGET, Optional.of("activitypub:" + keyId));
        assertTrue(key.isPresent());
        assertEquals(keyId, key.get().keyId());
        assertEquals(Algorithm.RSA_SHA256, key.get().algorithm());
        assertEquals("RSA", key.get().key().getAlgorithm());
        // RequestSigner asks this provider before the config one
        assertEquals(keyId, requestSigner.keyFor(TARGET, Optional.of("activitypub:" + keyId)).keyId());
    }

    @Test
    void otherClientNamesAreEmpty() {
        assertTrue(provider.select(TARGET, Optional.empty()).isEmpty());
        assertTrue(provider.select(TARGET, Optional.of("hook")).isEmpty());
        assertTrue(provider.select(TARGET, Optional.of("activitypub:https://other.example/users/x#main-key")).isEmpty());
        assertTrue(provider.select(TARGET, Optional.of("activitypub:" + urls.actor("nobody") + "#main-key")).isEmpty());
        assertTrue(provider.select(TARGET, Optional.of("activitypub:" + urls.actor("alice") + "#other-key")).isEmpty());
        assertTrue(provider.select(TARGET, Optional.of("activitypub:not a uri")).isEmpty());
    }

    @Test
    void configKeysStillWork() {
        assertEquals("hook", requestSigner.keyFor(TARGET, Optional.empty()).keyId());
    }
}

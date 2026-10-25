package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeys;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;
import org.sitenetsoft.quarkus.activitypub.store.ActorStore;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.FollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.ObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryActorStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryDeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryFollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryObjectStore;
import org.sitenetsoft.quarkus.activitypub.test.StoreContractTest;

import io.quarkus.test.QuarkusUnitTest;

public class InMemoryStoresTest extends StoreContractTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.activitypub.base-url", "https://example.org");

    @Inject
    Stores stores;

    @Inject
    LocalActors localActors;

    @Override
    protected ActorStore actors() {
        return stores.actors();
    }

    @Override
    protected ObjectStore objects() {
        return stores.objects();
    }

    @Override
    protected FollowerStore followers() {
        return stores.followers();
    }

    @Override
    protected DeliveryStore deliveries() {
        return stores.deliveries();
    }

    @Test
    void defaultsAreInMemory() {
        assertInstanceOf(InMemoryActorStore.class, stores.actors());
        assertInstanceOf(InMemoryObjectStore.class, stores.objects());
        assertInstanceOf(InMemoryFollowerStore.class, stores.followers());
        assertInstanceOf(InMemoryDeliveryStore.class, stores.deliveries());
    }

    @Test
    void reactiveViewAdaptsBlockingDefault() {
        LocalActor actor = LocalActor.builder("reactive-view").id(java.net.URI.create("https://example.org/users/rv")).build();
        stores.actors().save(actor, ActorKeys.generateRsa2048());
        assertEquals(Optional.of(actor),
                stores.reactiveActors().findByUsername("reactive-view").await().atMost(Duration.ofSeconds(5)));
        assertEquals(true, stores.reactiveObjects().markSeen(java.net.URI.create("https://r.example/a/rv"), Duration.ofDays(1))
                .await().atMost(Duration.ofSeconds(5)));
    }

    @Test
    void localActorsRegister() {
        LocalActor alice = localActors.register("alice-la", b -> b.name("Alice").type("Service"));
        assertEquals(java.net.URI.create("https://example.org/users/alice-la"), alice.id());
        assertEquals("Service", alice.type());
        assertEquals(Optional.of(alice), localActors.find("alice-la"));
        assertEquals("RSA", ActorKeys.publicKey(stores.actors().keyPair(alice.id()).orElseThrow()).getAlgorithm());
        assertThrows(IllegalStateException.class, () -> localActors.register("alice-la", b -> {
        }));
    }
}

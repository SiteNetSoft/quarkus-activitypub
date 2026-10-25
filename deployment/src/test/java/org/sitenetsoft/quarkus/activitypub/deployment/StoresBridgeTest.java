package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeys;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.store.ReactiveActorStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryActorStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryObjectStore;

import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

/**
 * An app that provides only a reactive {@link ReactiveActorStore}: the blocking view must adapt it rather than fall back
 * to the in-memory default; the other stores keep their defaults.
 */
public class StoresBridgeTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(AppReactiveActorStore.class))
            .overrideConfigKey("quarkus.activitypub.base-url", "https://example.org");

    @Inject
    Stores stores;

    @Inject
    AppReactiveActorStore app;

    @Test
    void blockingViewAdaptsAppReactiveStore() {
        assertFalse(stores.actors() instanceof InMemoryActorStore);
        LocalActor actor = LocalActor.builder("bob").id(URI.create("https://example.org/users/bob")).build();
        stores.actors().save(actor, ActorKeys.generateRsa2048());
        assertEquals(Optional.of(actor), app.delegate.findByUsername("bob"), "write reached the app's reactive store");
        assertEquals(Optional.of(actor), stores.actors().findById(actor.id()));
        assertSame(app, stores.reactiveActors());
    }

    @Test
    void otherStoresKeepDefaults() {
        assertInstanceOf(InMemoryObjectStore.class, stores.objects());
    }

    @Singleton // no client proxy, so the test can read the delegate field
    public static class AppReactiveActorStore implements ReactiveActorStore {

        final InMemoryActorStore delegate = new InMemoryActorStore();

        @Override
        public Uni<Optional<LocalActor>> findByUsername(String username) {
            return Uni.createFrom().item(() -> delegate.findByUsername(username));
        }

        @Override
        public Uni<Optional<LocalActor>> findById(URI id) {
            return Uni.createFrom().item(() -> delegate.findById(id));
        }

        @Override
        public Uni<Long> countLocalActors() {
            return Uni.createFrom().item(delegate::countLocalActors);
        }

        @Override
        public Uni<List<LocalActor>> list(int limit) {
            return Uni.createFrom().item(() -> delegate.list(limit));
        }

        @Override
        public Uni<Optional<ActorKeyPair>> keyPair(URI actorId) {
            return Uni.createFrom().item(() -> delegate.keyPair(actorId));
        }

        @Override
        public Uni<Void> save(LocalActor actor, ActorKeyPair keys) {
            return Uni.createFrom().item(() -> {
                delegate.save(actor, keys);
                return null;
            });
        }

        @Override
        public Uni<Void> delete(URI actorId) {
            return Uni.createFrom().item(() -> {
                delegate.delete(actorId);
                return null;
            });
        }
    }
}

package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateDeliveryStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateFollowerStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryActorStore;

import io.quarkus.test.QuarkusUnitTest;

/** An application's own {@code ActorStore} (an alternative) replaces only the Hibernate actor store. */
public class ApplicationActorStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(UsersTableActorStore.class);

    /** Stands for a store over the application's own users table. */
    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class UsersTableActorStore extends InMemoryActorStore {
    }

    @Inject
    Stores stores;

    @Test
    void partialReplacement() {
        assertInstanceOf(UsersTableActorStore.class, stores.actors());
        assertInstanceOf(HibernateObjectStore.class, stores.objects());
        assertInstanceOf(HibernateFollowerStore.class, stores.followers());
        assertInstanceOf(HibernateDeliveryStore.class, stores.deliveries());
    }
}

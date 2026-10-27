package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateActorStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateDeliveryStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateFollowerStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateObjectStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.JtaTransactionRunner;
import org.sitenetsoft.quarkus.activitypub.store.ActorStore;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.FollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.ObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.store.TransactionRunner;
import org.sitenetsoft.quarkus.activitypub.test.StoreContractTest;

import io.quarkus.test.QuarkusUnitTest;

/** The store contract against the Hibernate stores on H2. */
public class HibernateStoresTest extends StoreContractTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest();

    @Inject
    Stores stores;

    @Inject
    TransactionRunner transactions;

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

    @Override
    protected TransactionRunner transactions() {
        return transactions;
    }

    @Test
    void hibernateStoresReplaceTheInMemoryDefaults() {
        assertInstanceOf(HibernateActorStore.class, stores.actors());
        assertInstanceOf(HibernateObjectStore.class, stores.objects());
        assertInstanceOf(HibernateFollowerStore.class, stores.followers());
        assertInstanceOf(HibernateDeliveryStore.class, stores.deliveries());
        assertInstanceOf(JtaTransactionRunner.class, transactions);
    }

    @Test
    void newHostRecordedInsideOuterNewTransaction() {
        String host = "outer-" + System.nanoTime() + ".example";
        java.time.Instant t = java.time.Instant.parse("2026-01-01T00:00:00Z");
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            stores.deliveries().recordHostResult(host, false, t);
            stores.deliveries().recordHostResult(host, false, t.plusSeconds(1));
        });
        org.junit.jupiter.api.Assertions.assertEquals(2, stores.deliveries().host(host).consecutiveFailures());
    }
}

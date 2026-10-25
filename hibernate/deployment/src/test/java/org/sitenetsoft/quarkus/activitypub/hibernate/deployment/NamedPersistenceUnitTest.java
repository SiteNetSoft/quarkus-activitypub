package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeys;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApLocalActor;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusUnitTest;

/** The {@code ap_} tables in a named persistence unit on its own datasource. */
public class NamedPersistenceUnitTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.activitypub.base-url", "https://example.org")
            .overrideConfigKey("quarkus.datasource.\"fediverse\".db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.\"fediverse\".jdbc.url", "jdbc:h2:mem:fediverse;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.hibernate-orm.\"fediverse\".datasource", "fediverse")
            .overrideConfigKey("quarkus.hibernate-orm.\"fediverse\".packages",
                    "org.sitenetsoft.quarkus.activitypub.hibernate.entity")
            .overrideConfigKey("quarkus.hibernate-orm.\"fediverse\".schema-management.strategy", "drop-and-create")
            .overrideConfigKey("quarkus.activitypub.hibernate.persistence-unit", "fediverse")
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false");

    @Inject
    Stores stores;

    @Inject
    @PersistenceUnit("fediverse")
    EntityManager fediverse;

    @Test
    void storesUseTheConfiguredUnit() {
        LocalActor actor = LocalActor.builder("named").id(URI.create("https://example.org/users/named")).build();
        stores.actors().save(actor, ActorKeys.generateRsa2048());

        assertEquals(Optional.of(actor), stores.actors().findByUsername("named"));
        assertNotNull(QuarkusTransaction.requiringNew()
                .call(() -> fediverse.find(ApLocalActor.class, "https://example.org/users/named")));
    }
}

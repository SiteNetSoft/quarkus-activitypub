package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.hibernate.RetentionJob;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusUnitTest;

/** A retention of {@code 0} disables that part of {@link RetentionJob}. */
public class RetentionDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest()
            .overrideConfigKey("quarkus.activitypub.remote-objects.retention", "0")
            .overrideConfigKey("quarkus.activitypub.dedup.retention", "0");

    @Inject
    Stores stores;

    @Inject
    EntityManager em;

    @Inject
    RetentionJob job;

    @Test
    void zeroRetentionKeepsEverything() {
        URI author = URI.create("https://remote.example/users/" + UUID.randomUUID());
        URI id = URI.create("https://remote.example/notes/" + UUID.randomUUID());
        URI seen = URI.create("https://remote.example/activities/" + UUID.randomUUID());
        stores.objects().putRemote(new Note().id(id).attributedTo(ObjectOrLink.of(author)), author);
        stores.objects().markSeen(seen, Duration.ofDays(7));
        Instant ancient = Instant.parse("2000-01-01T00:00:00Z");
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("update ApObject o set o.created = :at where o.id = :id").setParameter("at", ancient)
                    .setParameter("id", id.toString()).executeUpdate();
            em.createQuery("update ApSeenActivity s set s.seenAt = :at where s.id = :id").setParameter("at", ancient)
                    .setParameter("id", seen.toString()).executeUpdate();
        });

        job.run();

        assertTrue(stores.objects().find(id).isPresent());
        assertEquals(1L, (long) QuarkusTransaction.requiringNew().call(() -> em
                .createQuery("select count(s) from ApSeenActivity s where s.id = :id", Long.class)
                .setParameter("id", seen.toString()).getSingleResult()));
    }
}

package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.hibernate.RetentionJob;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApSeenActivity;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusUnitTest;

/** {@link RetentionJob} with the default retentions: remote objects 90 days, seen activity ids 7 days. */
public class RetentionJobTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest();

    @Inject
    Stores stores;

    @Inject
    EntityManager em;

    @Inject
    RetentionJob job;

    private static URI remote(String kind) {
        return URI.create("https://remote.example/" + kind + "/" + UUID.randomUUID());
    }

    private static URI local(String kind) {
        return URI.create(LoopbackTests.BASE + "/" + kind + "/" + UUID.randomUUID());
    }

    private static ASObject note(URI id, URI author) {
        return new Note().id(id).attributedTo(ObjectOrLink.of(author)).content("kept for a while");
    }

    /** Moves the stored row's {@code created} into the past. */
    private void age(URI objectId, Duration by) {
        QuarkusTransaction.requiringNew().run(() -> assertEquals(1,
                em.createQuery("update ApObject o set o.created = :at where o.id = :id")
                        .setParameter("at", Instant.now().minus(by)).setParameter("id", objectId.toString())
                        .executeUpdate()));
    }

    private void ageSeen(URI activityId, Duration by) {
        QuarkusTransaction.requiringNew().run(() -> assertEquals(1,
                em.createQuery("update ApSeenActivity s set s.seenAt = :at where s.id = :id")
                        .setParameter("at", Instant.now().minus(by)).setParameter("id", activityId.toString())
                        .executeUpdate()));
    }

    private ApSeenActivity seenRow(URI activityId) {
        return QuarkusTransaction.requiringNew().call(() -> em.find(ApSeenActivity.class, activityId.toString()));
    }

    @Test
    void expiredRemoteObjectsAndSeenIdsAreDeleted() {
        URI author = remote("users");
        URI oldRemote = remote("notes");
        URI freshRemote = remote("notes");
        URI oldLocal = local("objects");
        stores.objects().putRemote(note(oldRemote, author), author);
        stores.objects().putRemote(note(freshRemote, author), author);
        stores.objects().putLocal(note(oldLocal, URI.create(LoopbackTests.BASE + "/users/alice")), true);
        age(oldRemote, Duration.ofDays(100));
        age(freshRemote, Duration.ofDays(10));
        age(oldLocal, Duration.ofDays(1000));

        URI oldSeen = remote("activities");
        URI freshSeen = remote("activities");
        assertTrue(stores.objects().markSeen(oldSeen, Duration.ofDays(7)));
        assertTrue(stores.objects().markSeen(freshSeen, Duration.ofDays(7)));
        ageSeen(oldSeen, Duration.ofDays(10));
        ageSeen(freshSeen, Duration.ofDays(1));

        job.run();

        assertEquals(Optional.empty(), stores.objects().find(oldRemote), "remote object older than 90 days");
        assertTrue(stores.objects().find(freshRemote).isPresent());
        assertTrue(stores.objects().find(oldLocal).isPresent(), "local objects never expire");
        assertNull(seenRow(oldSeen), "seen id older than 7 days");
        assertNotNull(seenRow(freshSeen));
    }
}

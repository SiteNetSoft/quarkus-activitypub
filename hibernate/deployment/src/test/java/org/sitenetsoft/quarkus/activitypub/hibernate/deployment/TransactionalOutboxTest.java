package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxLog;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The stores join the caller's JTA transaction: an application's rollback discards what {@code ActivityPub} stored and
 * queued, and the inbox handles a {@code Follow} atomically. The delivery poll is slowed down so queued deliveries stay
 * pending.
 */
public class TransactionalOutboxTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, InboxFixtures.class,
            FailAfterAddFollowerStore.class, FetchOutsideTransactionGuard.class)
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1h");

    static final FakeFediverseServer fake = new FakeFediverseServer();
    static final Duration WAIT = Duration.ofSeconds(15);
    static final URI FOLLOWER_INBOX = URI.create("https://remote.invalid/inbox");

    @Inject
    ActivityPub activityPub;

    @Inject
    Stores stores;

    @Inject
    AliceActor alice;

    @Inject
    InboxLog inboxLog;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    /** Alice gets one more follower, so a post to her followers queues a delivery. */
    private void addFollower() {
        stores.followers().add(AliceActor.ID, URI.create("https://remote.invalid/users/" + UUID.randomUUID()),
                FOLLOWER_INBOX, FollowState.ACCEPTED, null);
    }

    private static Note note() {
        Note note = new Note();
        note.content("<p>transactional</p>");
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        note.cc(ObjectOrLink.of(AliceActor.FOLLOWERS));
        return note;
    }

    private long expectedDeliveries() {
        return stores.followers().followerInboxes(AliceActor.ID).size();
    }

    @Test
    void rollbackDiscardsActivityAndDeliveries() {
        addFollower();
        long pending = stores.deliveries().pendingCount();
        Activity[] created = new Activity[1];
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            created[0] = activityPub.create(alice.get(), note());
            throw new IllegalStateException("application failure (test)");
        }));
        assertNotNull(created[0]);
        assertEquals(Optional.empty(), stores.objects().find(created[0].id()));
        assertEquals(Optional.empty(), stores.objects().find(created[0].objectUri().orElseThrow()));
        assertEquals(pending, stores.deliveries().pendingCount());
    }

    @Test
    void commitKeepsThem() {
        addFollower();
        long pending = stores.deliveries().pendingCount();
        Activity created = QuarkusTransaction.requiringNew().call(() -> activityPub.create(alice.get(), note()));
        assertTrue(stores.objects().find(created.id()).isPresent());
        assertTrue(stores.objects().find(created.objectUri().orElseThrow()).isPresent());
        assertEquals(pending + expectedDeliveries(), stores.deliveries().pendingCount());
    }

    @Test
    void inboxFollowIsAtomic() {
        FakeActor pia = fake.actor("pia");
        Follow follow = InboxFixtures.follow(pia, AliceActor.ID);
        FailAfterAddFollowerStore.fail = true;
        try {
            assertEquals(202, pia.post(InboxFixtures.ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
            await().atMost(WAIT).until(() -> inboxLog.entries().stream()
                    .anyMatch(e -> pia.id().equals(e.actor()) && InboxLog.ERROR.equals(e.result())));
        } finally {
            FailAfterAddFollowerStore.fail = false;
        }
        assertEquals(Optional.empty(), stores.followers().follower(AliceActor.ID, pia.id()), "the add rolled back");
        long pending = stores.deliveries().pendingCount();

        // the seen marker went with it: a redelivery of the same Follow is processed, not dropped as a duplicate
        assertEquals(202, pia.post(InboxFixtures.ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
        await().atMost(WAIT).until(() -> stores.followers().follower(AliceActor.ID, pia.id())
                .filter(r -> r.state() == FollowState.ACCEPTED).isPresent());
        assertTrue(inboxLog.entries().stream()
                .noneMatch(e -> pia.id().equals(e.actor()) && InboxLog.DUPLICATE.equals(e.result())));
        await().atMost(WAIT).until(() -> stores.deliveries().pendingCount() == pending + 1);
    }

    @Test
    void followerIsFetchedOutsideTheInboxTransaction() {
        FakeActor quinn = fake.actor("quinn");
        Follow follow = InboxFixtures.follow(quinn, AliceActor.ID);
        assertEquals(202, quinn.post(InboxFixtures.ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
        await().atMost(WAIT).until(() -> stores.followers().follower(AliceActor.ID, quinn.id())
                .filter(r -> r.state() == FollowState.ACCEPTED).isPresent());

        // FollowHandler looks the follower up with fetchActor: in prepare, before the transaction
        List<FetchOutsideTransactionGuard.Call> calls = FetchOutsideTransactionGuard.callsFor(quinn.id());
        List<FetchOutsideTransactionGuard.Call> lookups = calls.stream().filter(c -> c.method().equals("fetchActor"))
                .toList();
        assertTrue(!lookups.isEmpty() && lookups.stream().noneMatch(FetchOutsideTransactionGuard.Call::inTransaction),
                "follower fetched before the transaction: " + calls);
        // the Accept's recipient lookup runs inside it, and finds the follower in the cache: no remote I/O
        assertEquals(List.of(), calls.stream().filter(c -> c.inTransaction() && !c.cached()).toList(),
                "no remote fetch inside the inbox transaction");
    }
}

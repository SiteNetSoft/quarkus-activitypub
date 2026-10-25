package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.create;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.follow;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.note;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

public class InboxEventsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class, InboxFixtures.class,
            RecordingActivityPub.class, EventCollector.class);

    static final FakeFediverseServer fake = new FakeFediverseServer();
    static final Duration WAIT = Duration.ofSeconds(10);

    @Inject
    EventCollector events;

    @Inject
    Stores stores;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    @Test
    void followEventCarriesContext() {
        FakeActor carol = fake.actor("carol");
        Follow follow = follow(carol, ALICE);
        assertEquals(202, carol.post(ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
        await().atMost(WAIT).until(() -> events.find("follow", follow.id()).isPresent());
        EventCollector.Seen seen = events.find("follow", follow.id()).get();
        assertEquals(ALICE, seen.recipient().orElseThrow().id());
        assertEquals(carol.id(), seen.signerActor());
    }

    @Test
    void supertypeObserverSeesAll() {
        FakeActor dave = fake.actor("dave");
        Follow follow = follow(dave, ALICE);
        Create create = create(dave, note(dave, ALICE));
        assertEquals(202, dave.post(ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
        assertEquals(202, dave.post(ALICE_INBOX, create, Scheme.CAVAGE).statusCode());
        await().atMost(WAIT).until(() -> events.find("any", follow.id()).isPresent()
                && events.find("any", create.id()).isPresent());
        await().atMost(WAIT).until(() -> events.find("create", create.id()).isPresent());
    }

    @Test
    void asyncObserverRuns() {
        FakeActor erin = fake.actor("erin");
        Create create = create(erin, note(erin, ALICE));
        assertEquals(202, erin.post(ALICE_INBOX, create, Scheme.CAVAGE).statusCode());
        await().atMost(WAIT).until(() -> events.find("async", create.id()).isPresent());
        EventCollector.Seen async = events.find("async", create.id()).get();
        assertEquals(ALICE, async.recipient().orElseThrow().id());
        assertEquals(erin.id(), async.signerActor());
        // async observers get their own copy
        await().atMost(WAIT).until(() -> events.find("create", create.id()).isPresent());
        assertNotSame(events.find("create", create.id()).get().activity(), async.activity());
    }

    @Test
    void observerExceptionDoesNotBreakProcessing() {
        FakeActor frank = fake.actor("frank");
        EventCollector.failOnFollow = true;
        try {
            assertEquals(202, frank.post(ALICE_INBOX, follow(frank, ALICE), Scheme.CAVAGE).statusCode());
            await().atMost(WAIT).until(() -> stores.followers().followers(ALICE, 1, 100).items().contains(frank.id()));
        } finally {
            EventCollector.failOnFollow = false;
        }
    }
}

package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.follow;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxLog;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

/** {@code blocked-domains} names the fake's host. */
public class InboxPolicyTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class, InboxFixtures.class,
            RecordingActivityPub.class, EventCollector.class)
            .overrideConfigKey("quarkus.activitypub.blocked-domains", "127.0.0.1");

    static final FakeFediverseServer fake = new FakeFediverseServer();

    @Inject
    EventCollector events;

    @Inject
    Stores stores;

    @Inject
    RecordingActivityPub activityPub;

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

    @Test
    void blockedDomainIs202AndSilent() {
        FakeActor carol = fake.actor("carol");
        Follow follow = follow(carol, ALICE);
        assertEquals(202, carol.post(ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
        // the decision is taken before the answer: nothing is left to run
        assertTrue(inboxLog.entries().stream()
                .anyMatch(e -> carol.id().equals(e.actor()) && InboxLog.BLOCKED.equals(e.result())));
        assertEquals(java.util.List.of(), events.all());
        assertEquals(0, stores.followers().followerCount(ALICE));
        assertTrue(activityPub.published(ALICE).isEmpty());
    }

    @Test
    void unmappableBodyFromBlockedDomainIsStillDropped() {
        // the screen only peeks at type and actor: a body the full model rejects is still screened, never verified
        FakeActor dora = fake.actor("dora");
        fake.clear();
        byte[] body = InboxFixtures.withUnmappableField(follow(dora, ALICE));
        assertEquals(202, InboxFixtures.send(dora, ALICE_INBOX, body, InboxFixtures.ACTIVITY_JSON).statusCode());
        assertTrue(inboxLog.entries().stream()
                .anyMatch(e -> dora.id().equals(e.actor()) && InboxLog.BLOCKED.equals(e.result())));
        assertEquals(0, fake.requestCount("/users/dora"), "no key fetch from a blocked host");
    }
}

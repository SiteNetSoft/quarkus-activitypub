package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.follow;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Reject;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.inbox.FollowPolicy;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

public class InboxCustomFollowPolicyTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class, InboxFixtures.class,
            RecordingActivityPub.class, EventCollector.class, RejectEveryone.class);

    @ApplicationScoped
    public static class RejectEveryone implements FollowPolicy {
        @Override
        public FollowDecision decide(LocalActor local, Follow follow, InboundActivityContext ctx) {
            return FollowDecision.REJECT;
        }
    }

    static final FakeFediverseServer fake = new FakeFediverseServer();

    @Inject
    RecordingActivityPub activityPub;

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
    void customFollowPolicyRejects() {
        FakeActor carol = fake.actor("carol");
        Follow follow = follow(carol, ALICE);
        assertEquals(202, carol.post(ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
        await().atMost(Duration.ofSeconds(10))
                .until(() -> !activityPub.published(ALICE, Reject.class, carol.id()).isEmpty());
        Reject reject = activityPub.published(ALICE, Reject.class, carol.id()).get(0);
        assertEquals(follow.id(), reject.objectUri().orElseThrow());
        assertTrue(activityPub.published(ALICE, Accept.class, carol.id()).isEmpty());
        assertEquals(0, stores.followers().followerCount(ALICE));
    }
}

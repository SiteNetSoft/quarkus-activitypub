package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.follow;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityHandler;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

public class InboxCustomHandlerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class, InboxFixtures.class,
            RecordingActivityPub.class, EventCollector.class, MyFollowHandler.class);

    @ApplicationScoped
    @Priority(10)
    public static class MyFollowHandler implements InboundActivityHandler<Follow> {
        private final List<URI> handled = new CopyOnWriteArrayList<>();

        List<URI> handled() {
            return List.copyOf(handled);
        }

        @Override
        public Class<Follow> type() {
            return Follow.class;
        }

        @Override
        public void handle(Follow activity, InboundActivityContext ctx) {
            handled.add(activity.id());
        }
    }

    static final FakeFediverseServer fake = new FakeFediverseServer();

    @Inject
    MyFollowHandler handler;

    @Inject
    RecordingActivityPub activityPub;

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
    void customHandlerReplacesBuiltIn() {
        FakeActor carol = fake.actor("carol");
        Follow follow = follow(carol, ALICE);
        assertEquals(202, carol.post(ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
        await().atMost(Duration.ofSeconds(10)).until(() -> events.find("follow", follow.id()).isPresent());
        assertEquals(List.of(follow.id()), handler.handled());
        assertEquals(0, stores.followers().followerCount(ALICE));
        assertTrue(activityPub.published(ALICE).isEmpty());
    }
}

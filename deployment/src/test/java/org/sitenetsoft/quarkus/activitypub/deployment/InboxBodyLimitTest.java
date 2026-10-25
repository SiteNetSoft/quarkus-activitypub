package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE_INBOX;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

public class InboxBodyLimitTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class, InboxFixtures.class,
            RecordingActivityPub.class, EventCollector.class)
            .overrideConfigKey("quarkus.http-signatures.verify.max-body-size", "1k");

    static final FakeFediverseServer fake = new FakeFediverseServer();

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    @Test
    void bodyTooLargeIs413() {
        FakeActor carol = fake.actor("carol");
        Note note = InboxFixtures.note(carol, ALICE);
        note.content("x".repeat(2048));
        assertEquals(413, carol.post(ALICE_INBOX, InboxFixtures.create(carol, note), Scheme.CAVAGE).statusCode());
    }
}

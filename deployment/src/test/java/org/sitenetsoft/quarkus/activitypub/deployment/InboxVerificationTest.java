package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.SHARED_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.follow;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.send;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

public class InboxVerificationTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class, InboxFixtures.class,
            RecordingActivityPub.class, EventCollector.class);

    static final FakeFediverseServer fake = new FakeFediverseServer();

    @Inject
    EventCollector events;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    private static byte[] json(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void unsignedIs401() {
        Follow follow = follow(fake.actor("carol"), ALICE);
        HttpResponse<byte[]> response = send(null, ALICE_INBOX,
                json("{\"type\":\"Follow\",\"actor\":\"" + fake.actor("carol").id() + "\",\"object\":\"" + ALICE + "\"}"),
                InboxFixtures.ACTIVITY_JSON);
        assertEquals(401, response.statusCode());
        assertEquals(Optional.of("Signature"), response.headers().firstValue("WWW-Authenticate"));
        assertTrue(events.find("follow", follow.id()).isEmpty());
    }

    @Test
    void signedByFakeIs202() {
        FakeActor carol = fake.actor("carol");
        Follow follow = follow(carol, ALICE);
        assertEquals(202, carol.post(ALICE_INBOX, follow, Scheme.CAVAGE).statusCode());
        await().atMost(Duration.ofSeconds(10)).until(() -> events.find("follow", follow.id()).isPresent());
    }

    @Test
    void rfc9421SignatureAlsoAccepted() {
        FakeActor dave = fake.actor("dave");
        Follow follow = follow(dave, ALICE);
        assertEquals(202, dave.post(ALICE_INBOX, follow, Scheme.RFC9421).statusCode());
        await().atMost(Duration.ofSeconds(10)).until(() -> events.find("follow", follow.id()).isPresent());
    }

    @Test
    void actorMismatchIs401() {
        FakeActor carol = fake.actor("carol");
        Follow forged = follow(fake.actor("dave"), ALICE);
        HttpResponse<byte[]> response = carol.post(ALICE_INBOX, forged, Scheme.CAVAGE);
        assertEquals(401, response.statusCode());
        assertTrue(events.find("follow", forged.id()).isEmpty());
    }

    @Test
    void wrongContentTypeIs415() {
        FakeActor carol = fake.actor("carol");
        byte[] body = json("{\"type\":\"Follow\",\"actor\":\"" + carol.id() + "\",\"object\":\"" + ALICE + "\"}");
        assertEquals(415, send(carol, ALICE_INBOX, body, "text/plain").statusCode());
    }

    @Test
    void jsonArrayBodyIs400() {
        FakeActor carol = fake.actor("carol");
        assertEquals(400, send(carol, ALICE_INBOX, json("[]"), InboxFixtures.ACTIVITY_JSON).statusCode());
        assertEquals(400, send(carol, ALICE_INBOX, json("\"Follow\""), InboxFixtures.ACTIVITY_JSON).statusCode());
        assertEquals(400, send(carol, ALICE_INBOX, json("{not json"), InboxFixtures.ACTIVITY_JSON).statusCode());
    }

    @Test
    void nonActivityIs400() {
        FakeActor carol = fake.actor("carol");
        Note note = InboxFixtures.note(carol, ALICE);
        assertEquals(400, carol.post(ALICE_INBOX, note, Scheme.CAVAGE).statusCode());
        byte[] unknown = json("{\"type\":\"Frobnicate\",\"id\":\"" + carol.id() + "/x\",\"actor\":\"" + carol.id() + "\"}");
        assertEquals(400, send(carol, ALICE_INBOX, unknown, InboxFixtures.ACTIVITY_JSON).statusCode());
    }

    @Test
    void sharedInboxAccepted() {
        FakeActor carol = fake.actor("carol");
        Follow follow = follow(carol, ALICE);
        assertEquals(202, carol.post(SHARED_INBOX, follow, Scheme.CAVAGE).statusCode());
        await().atMost(Duration.ofSeconds(10)).until(() -> events.find("follow", follow.id()).isPresent());
        assertTrue(events.find("follow", follow.id()).get().recipient().isEmpty());
    }

    @Test
    void unknownUserInboxIs404() {
        FakeActor carol = fake.actor("carol");
        Follow follow = follow(carol, ALICE);
        URI nobody = URI.create(LoopbackTests.BASE + "/users/nobody/inbox");
        assertEquals(404, carol.post(nobody, follow, Scheme.CAVAGE).statusCode());
    }

    @Test
    void getOnInboxIs405() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(ALICE_INBOX).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(405, response.statusCode());
        assertEquals(Optional.of("POST"), response.headers().firstValue("Allow"));
    }
}

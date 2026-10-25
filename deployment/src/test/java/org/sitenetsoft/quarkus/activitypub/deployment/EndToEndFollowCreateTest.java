package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.ASJson;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Person;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;
import org.sitenetsoft.quarkus.activitypub.test.ReceivedRequest;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.QuarkusUnitTest;

/**
 * The milestone's acceptance flow against the fake, with the in-memory stores and the scheduled delivery poll (no
 * manual triggering): carol discovers alice, follows her and gets an {@code Accept}; alice posts and carol receives it;
 * carol replies and alice's server keeps the reply.
 */
public class EndToEndFollowCreateTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, EventCollector.class,
            InboxFixtures.class)
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1s");

    static final FakeFediverseServer fake = new FakeFediverseServer();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final Duration WAIT = Duration.ofSeconds(15);

    @Inject
    ActivityPub activityPub;

    @Inject
    ActivityPubJson json;

    @Inject
    Stores stores;

    @Inject
    EventCollector events;

    @Inject
    AliceActor alice;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    private static HttpResponse<String> get(URI uri, String accept) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(uri).header("Accept", accept).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Optional<Activity> receivedAt(String path, Class<? extends Activity> type) {
        return fake.received().stream().filter(r -> r.method().equals("POST") && r.path().equals(path))
                .filter(ReceivedRequest::verified)
                .map(r -> json.read(r.body())).filter(type::isInstance).map(Activity.class::cast).findFirst();
    }

    @Test
    void followCreateReply() throws Exception {
        FakeActor carol = fake.actor("carol");

        // carol discovers alice through WebFinger and fetches her actor document
        String resource = "acct:alice@127.0.0.1:" + LoopbackTests.PORT;
        HttpResponse<String> jrd = get(URI.create(LoopbackTests.BASE + "/.well-known/webfinger?resource="
                + URLEncoder.encode(resource, StandardCharsets.UTF_8)), "application/jrd+json");
        assertEquals(200, jrd.statusCode(), jrd.body());
        JsonNode self = null;
        for (JsonNode link : ASJson.mapper().readTree(jrd.body()).path("links")) {
            if ("self".equals(link.path("rel").asText())) {
                self = link;
            }
        }
        URI actorUrl = URI.create(self.path("href").asText());
        assertEquals(AliceActor.ID, actorUrl);
        HttpResponse<String> actorDoc = get(actorUrl, "application/activity+json");
        assertEquals(200, actorDoc.statusCode());
        Person aliceDoc = ASJson.mapper().readValue(actorDoc.body(), Person.class);
        URI aliceInbox = aliceDoc.inbox();

        // carol follows alice; alice's server answers with a signed Accept to carol's (shared) inbox
        Follow follow = InboxFixtures.follow(carol, aliceDoc.id());
        assertEquals(202, carol.post(aliceInbox, follow, Scheme.CAVAGE).statusCode());
        Accept accept = (Accept) await().atMost(WAIT).until(() -> receivedAt("/inbox", Accept.class),
                Optional::isPresent).orElseThrow();
        assertEquals(follow.id(), accept.objectUri().orElseThrow());
        assertEquals(AliceActor.ID, accept.actorUri().orElseThrow());
        assertTrue(stores.followers().followers(AliceActor.ID, 1, 10).items().contains(carol.id()));

        // alice posts; carol receives the Create, signed by alice
        Note note = new Note();
        note.content("<p>hello fediverse</p>");
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        note.cc(ObjectOrLink.of(AliceActor.FOLLOWERS));
        Activity create = activityPub.create(alice.get(), note);
        Create received = (Create) await().atMost(WAIT).until(() -> receivedAt("/inbox", Create.class),
                Optional::isPresent).orElseThrow();
        assertEquals(create.id(), received.id());
        URI noteId = create.objectUri().orElseThrow();
        assertEquals(noteId, received.embeddedObject().orElseThrow().id());
        List<ReceivedRequest> creates = fake.received().stream()
                .filter(r -> r.bodyAsString().contains("\"" + create.id() + "\"")).toList();
        assertEquals(1, creates.size());
        assertTrue(creates.get(0).verified());
        assertEquals(LoopbackTests.BASE + "/users/alice#main-key", creates.get(0).keyId());

        // carol replies; alice's server keeps the reply and notifies the application
        Note reply = InboxFixtures.note(carol, AliceActor.ID);
        reply.inReplyTo(ObjectOrLink.of(noteId));
        Create replyCreate = InboxFixtures.create(carol, reply);
        assertEquals(202, carol.post(aliceInbox, replyCreate, Scheme.CAVAGE).statusCode());
        await().atMost(WAIT).until(() -> events.find("create", replyCreate.id()).isPresent());
        await().atMost(WAIT).until(() -> stores.objects().find(reply.id()).isPresent());
        Note stored = assertInstanceOf(Note.class, stores.objects().find(reply.id()).orElseThrow());
        assertEquals(noteId, stored.inReplyTo().get(0).uri().orElseThrow());
    }
}

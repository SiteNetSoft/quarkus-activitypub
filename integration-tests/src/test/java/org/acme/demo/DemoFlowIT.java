package org.acme.demo;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sitenetsoft.activitystreams.ASJson;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Person;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;
import org.sitenetsoft.quarkus.activitypub.test.ReceivedRequest;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * The sample app, packaged and started on port 8081 (it must be free): carol, on a fake fediverse server, discovers
 * alice through WebFinger, fetches her actor, follows her and receives a signed {@code Accept}; then
 * {@code POST /demo/post} (with the test profile's bearer token) makes alice post and carol receives the signed {@code Create}. Everything goes over HTTP: an
 * integration test cannot inject the app's beans.
 */
@QuarkusIntegrationTest
class DemoFlowIT {

    static final String APP = "http://localhost:8081";
    static final FakeFediverseServer fake = new FakeFediverseServer("127.0.0.1");
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final Duration WAIT = Duration.ofSeconds(30);
    /** {@code %test.demo.token} in application.properties. */
    static final String TOKEN = "demo-test-token";

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return HTTP.send(request.timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Optional<ASObject> receivedAtSharedInbox(Class<? extends ASObject> type) {
        return fake.received().stream().filter(r -> r.method().equals("POST") && r.path().equals("/inbox"))
                .filter(ReceivedRequest::verified)
                .map(r -> read(r.bodyAsString())).filter(type::isInstance).findFirst();
    }

    private static ASObject read(String json) {
        try {
            return ASJson.mapper().readValue(json, ASObject.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void webFingerFollowAcceptPostCreate() throws Exception {
        FakeActor carol = fake.actor("carol");

        // carol discovers alice through WebFinger and fetches her actor document
        String resource = URLEncoder.encode("acct:alice@localhost:8081", StandardCharsets.UTF_8);
        HttpResponse<String> jrd = send(HttpRequest.newBuilder(URI.create(APP + "/.well-known/webfinger?resource="
                + resource)).header("Accept", "application/jrd+json"));
        assertEquals(200, jrd.statusCode(), jrd.body());
        URI actorUrl = null;
        for (JsonNode link : ASJson.mapper().readTree(jrd.body()).path("links")) {
            if ("self".equals(link.path("rel").asText())) {
                actorUrl = URI.create(link.path("href").asText());
            }
        }
        assertEquals(URI.create(APP + "/users/alice"), actorUrl);
        HttpResponse<String> actorDoc = send(HttpRequest.newBuilder(actorUrl).header("Accept",
                "application/activity+json"));
        assertEquals(200, actorDoc.statusCode(), actorDoc.body());
        Person alice = ASJson.mapper().readValue(actorDoc.body(), Person.class);
        assertNotNull(alice.inbox());

        // carol follows alice; the app answers with a signed Accept to carol's shared inbox
        Follow follow = new Follow();
        follow.id(URI.create(fake.baseUri() + "/activities/" + UUID.randomUUID()));
        follow.actor(ObjectOrLink.of(carol.id())).object(ObjectOrLink.of(alice.id()));
        assertEquals(202, carol.post(alice.inbox(), follow, Scheme.CAVAGE).statusCode());
        Accept accept = (Accept) await().atMost(WAIT).until(() -> receivedAtSharedInbox(Accept.class),
                Optional::isPresent).orElseThrow();
        assertEquals(follow.id(), accept.objectUri().orElseThrow());
        assertEquals(alice.id(), accept.actorUri().orElseThrow());

        HttpResponse<String> log = send(HttpRequest.newBuilder(URI.create(APP + "/demo/inbox-log")));
        assertEquals(200, log.statusCode(), log.body());
        assertTrue(log.body().contains("Follow"), log.body());

        // the demo endpoint needs the bearer token
        URI postUri = URI.create(APP + "/demo/post?user=alice&text="
                + URLEncoder.encode("hello <b>fediverse</b> & co", StandardCharsets.UTF_8));
        assertEquals(401, send(HttpRequest.newBuilder(postUri).POST(HttpRequest.BodyPublishers.noBody()))
                .statusCode());
        assertEquals(401, send(HttpRequest.newBuilder(postUri).header("Authorization", "Bearer wrong")
                .POST(HttpRequest.BodyPublishers.noBody())).statusCode());

        // alice posts through the demo resource; carol receives the signed Create, with the text HTML-escaped
        HttpResponse<String> posted = send(HttpRequest.newBuilder(postUri).header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertEquals(200, posted.statusCode(), posted.body());
        URI createId = URI.create(posted.body().strip());
        Create create = (Create) await().atMost(WAIT).until(() -> receivedAtSharedInbox(Create.class),
                Optional::isPresent).orElseThrow();
        assertEquals(createId, create.id());
        Note note = (Note) create.embeddedObject().orElseThrow();
        assertEquals("<p>hello &lt;b&gt;fediverse&lt;/b&gt; &amp; co</p>", note.content());
    }
}

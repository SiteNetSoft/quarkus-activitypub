package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.follow;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.ASJson;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.keys.ActorKeyResolver;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Bogus signatures naming a real remote key id must not make us fetch that key again and again (we would reflect
 * requests at the key's host); unknown key ids are remembered as unknown; rotation still works after the interval.
 */
public class KeyRefetchThrottleTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class, InboxFixtures.class,
            MutableClock.class);

    static final FakeFediverseServer fake = new FakeFediverseServer();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Inject
    ActorKeyResolver keys;

    @Inject
    MutableClock clock;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    /** POSTs {@code activity} signed by {@code from} with its key id, but with a corrupted signature value. */
    private static int postWithBadSignature(FakeActor from, URI inbox, Activity activity) throws Exception {
        byte[] body = ASJson.mapper().writeValueAsBytes(activity);
        Map<String, String> signed = from.sign("POST", inbox, body, Scheme.CAVAGE).headers();
        HttpRequest.Builder request = HttpRequest.newBuilder(inbox).timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        signed.forEach((name, value) -> {
            if (name.equalsIgnoreCase("Signature")) {
                int at = value.indexOf("signature=\"") + "signature=\"".length();
                char c = value.charAt(at);
                value = value.substring(0, at) + (c == 'A' ? 'B' : 'A') + value.substring(at + 1);
            }
            if (!name.equalsIgnoreCase("Host")) {
                request.header(name, value);
            }
        });
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray()).statusCode();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** GETs of {@code path} (actor document fetches), ignoring deliveries to the fake. */
    private static long fetches(String path) {
        return fake.received().stream().filter(r -> r.method().equals("GET") && r.path().startsWith(path)).count();
    }

    private static int post(FakeActor from, Activity activity) {
        return from.post(ALICE_INBOX, activity, Scheme.CAVAGE).statusCode();
    }

    @Test
    void bogusSignaturesDoNotRefetchWithinInterval() throws Exception {
        FakeActor carol = fake.actor("carol");
        assertEquals(202, post(carol, follow(carol, ALICE)), "a valid request resolves carol's key");
        fake.clear();
        for (int i = 0; i < 10; i++) {
            assertEquals(401, postWithBadSignature(carol, ALICE_INBOX, follow(carol, ALICE)));
        }
        assertTrue(fetches("/users/carol") <= 1, "at most one refetch, got " + fetches("/users/carol"));
    }

    @Test
    void unknownKeyIdIsNegativelyCached() {
        String keyId = fake.baseUri() + "/users/nobody-" + System.nanoTime() + "#main-key";
        fake.clear();
        assertTrue(keys.resolve(keyId).await().atMost(Duration.ofSeconds(15)).isEmpty());
        assertTrue(keys.resolve(keyId).await().atMost(Duration.ofSeconds(15)).isEmpty());
        assertEquals(1, fetches("/users/nobody-"));
    }

    @Test
    void rotationRefetchesAfterInterval() {
        FakeActor rosa = fake.actor("rosa");
        assertEquals(202, post(rosa, follow(rosa, ALICE)));
        rosa.rotateKey();
        fake.clear();
        assertEquals(401, post(rosa, follow(rosa, ALICE)), "within the refetch interval the old key is reused");
        assertEquals(0, fetches("/users/rosa"));
        clock.advance(Duration.ofSeconds(61));
        assertEquals(202, post(rosa, follow(rosa, ALICE)), "after the interval the rotated key is fetched");
    }
}

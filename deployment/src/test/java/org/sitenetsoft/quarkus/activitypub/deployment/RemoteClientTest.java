package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Person;
import org.sitenetsoft.quarkus.activitypub.ActivityPubException;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.client.RemoteCache;
import org.sitenetsoft.quarkus.activitypub.keys.ActorKeyResolver;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;
import org.sitenetsoft.quarkus.activitypub.test.ReceivedRequest;

import io.quarkus.test.QuarkusUnitTest;

public class RemoteClientTest {

    static final int FAKE_PORT = LoopbackTests.reservedPort("RemoteClientTest");

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(TestActors.class)
            .overrideConfigKey("quarkus.webfinger.client.base-url-override", "http://127.0.0.1:" + FAKE_PORT);

    static final FakeFediverseServer fake = new FakeFediverseServer();

    /** Another origin (same loopback, other host name and port) that a redirect can lead to. */
    static final FakeFediverseServer other = new FakeFediverseServer("localhost");

    @Inject
    ActivityPubClient client;

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Inject
    RemoteCache cache;

    @Inject
    ActorKeyResolver keys;

    @BeforeAll
    static void startFake() {
        fake.start(FAKE_PORT);
        other.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
        other.stop();
    }

    @Test
    void fetchesActorFromFake() {
        Actor actor = client.fetchActor(fake.actor("bob").id());
        assertInstanceOf(Person.class, actor);
        assertEquals(fake.actor("bob").id(), actor.id());
        assertFalse(actor.publicKey().isEmpty());
        assertEquals(fake.actor("bob").keyId(), actor.publicKey().get(0).id().toString());
    }

    /** The fake verifies the signature by fetching our instance actor, as Mastodon's secure mode does. */
    @Test
    void fetchIsSignedByInstanceActor() {
        client.fetch(fake.actor("signed").id());
        List<ReceivedRequest> received = fake.received();
        ReceivedRequest last = received.get(received.size() - 1);
        assertEquals("GET", last.method());
        assertEquals("/users/signed", last.path());
        assertTrue(last.verified(), "fake could not verify our signature");
        assertTrue(last.keyId().endsWith("/users/instance#main-key"), last.keyId());
        assertEquals(LoopbackTests.BASE + "/users/instance#main-key", last.keyId());
    }

    @Test
    void resolveActorByAcct() {
        URI bob = fake.actor("bob").id();
        Actor actor = client.resolveActor("bob@127.0.0.1:" + fake.port());
        assertEquals(bob, actor.id());
        assertTrue(fake.requestCount("/.well-known/webfinger") > 0);
        // an actor URI resolves directly
        assertEquals(bob, client.resolveActor(bob.toString()).id());
    }

    @Test
    void idMismatchIsRejected() {
        URI evil = fake.actor("evil").withId(URI.create("https://other.example/users/evil")).id();
        ActivityPubException e = assertThrows(ActivityPubException.class, () -> client.fetch(evil));
        assertEquals(ActivityPubException.Reason.ID_MISMATCH, e.reason());
    }

    /**
     * An open redirect on the victim's host must not let another host speak for it: the attacker's document claims the
     * victim's id and key, and would pass a check against the requested host.
     */
    @Test
    void crossHostRedirectIsRefused() {
        URI victim = fake.baseUri().resolve("/users/redirector");
        URI attacker = other.actor("forger").withId(victim).id();
        fake.redirect("redirector", attacker);

        ActivityPubException e = assertThrows(ActivityPubException.class, () -> client.fetchActor(victim));
        assertEquals(ActivityPubException.Reason.FETCH_FAILED, e.reason());
        assertTrue(e.getMessage().contains("redirected to another host"), e.getMessage());
        assertTrue(cache.get(victim).isEmpty());
        assertTrue(keys.resolve(victim + "#main-key").await().atMost(Duration.ofSeconds(15)).isEmpty());
        assertTrue(keys.ownerOf(victim + "#main-key").isEmpty());
        assertTrue(other.requestCount("/users/forger") > 0, "the redirect was followed");
    }

    @Test
    void sameHostRedirectIsFollowed() {
        URI old = fake.baseUri().resolve("/users/old");
        URI alice = fake.actor("alice").id();
        fake.redirect("old", alice);
        assertEquals(alice, client.fetchActor(old).id());
        assertEquals(alice, cache.get(old).orElseThrow().id());
    }

    @Test
    void secondFetchIsCached() {
        URI erin = fake.actor("erin").id();
        client.fetchActor(erin);
        int count = fake.requestCount("/users/erin");
        assertEquals(1, count);
        assertEquals(erin, client.fetchActor(erin).id());
        assertEquals(count, fake.requestCount("/users/erin"));
    }

    @Test
    void notFoundIs404Reason() {
        ActivityPubException e = assertThrows(ActivityPubException.class,
                () -> client.fetch(fake.baseUri().resolve("/objects/missing")));
        assertEquals(ActivityPubException.Reason.NOT_FOUND, e.reason());
        e = assertThrows(ActivityPubException.class, () -> client.fetchActor(fake.baseUri().resolve("/users/nobody")));
        assertEquals(ActivityPubException.Reason.NOT_FOUND, e.reason());
    }

    @Test
    void fetchedObjectComesFromFake() {
        Note note = new Note();
        note.content("remote hello").attributedTo(ObjectOrLink.of(fake.actor("bob").id()));
        URI id = fake.actor("bob").addObject(note).id();
        ASObject fetched = client.fetch(id);
        assertInstanceOf(Note.class, fetched);
        assertEquals("remote hello", fetched.content());
        // the wrong type is an invalid document, not a cast failure
        ActivityPubException e = assertThrows(ActivityPubException.class, () -> client.fetchActor(id));
        assertEquals(ActivityPubException.Reason.INVALID_DOCUMENT, e.reason());
    }

    @Test
    void localIdNeedsNoFetch() {
        Note note = new Note();
        note.id(urls.object("local-1")).content("local");
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        stores.objects().putLocal(note, true);
        fake.clear();

        Actor alice = client.fetchActor(urls.actor("alice"));
        assertEquals("alice", alice.preferredUsername());
        assertEquals(urls.keyId("alice"), alice.publicKey().get(0).id());
        assertEquals("local", client.fetch(urls.object("local-1")).content());
        ActivityPubException e = assertThrows(ActivityPubException.class, () -> client.fetch(urls.object("absent")));
        assertEquals(ActivityPubException.Reason.NOT_FOUND, e.reason());
        assertTrue(fake.received().isEmpty());
    }
}

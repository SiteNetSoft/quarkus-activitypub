package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Delete;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.OrderedCollection;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.activitystreams.Undo;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;
import org.sitenetsoft.quarkus.activitypub.test.ReceivedRequest;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

public class OutboundPublishTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, RecordingDeliveryStore.class,
            TrackingTransactionRunner.class, FetchOutsideTransactionGuard.class);

    static final FakeFediverseServer fake = new FakeFediverseServer();
    static final URI ALICE = AliceActor.ID;
    static final URI ALICE_FOLLOWERS = AliceActor.FOLLOWERS;

    @Inject
    ActivityPub activityPub;

    @Inject
    DeliveryScheduler scheduler;

    @Inject
    RecordingDeliveryStore deliveries;

    @Inject
    Stores stores;

    @Inject
    AliceActor alice;

    @Inject
    ActivityPubJson json;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    /** carol and erin share the fake's inbox; dave has only his own. */
    @BeforeEach
    void seedFollowers() {
        for (String name : List.of("carol", "erin")) {
            FakeActor a = fake.actor(name);
            stores.followers().add(ALICE, a.id(), fake.sharedInbox(), FollowState.ACCEPTED,
                    URI.create(a.id() + "/follows/1"));
        }
        FakeActor dave = fake.actor("dave");
        stores.followers().add(ALICE, dave.id(), dave.inbox(), FollowState.ACCEPTED, URI.create(dave.id() + "/follows/1"));
        fake.clear();
    }

    private LocalActor alice() {
        return alice.get();
    }

    private static Note note(URI... to) {
        Note note = new Note();
        note.content("<p>hello</p>");
        note.to(java.util.Arrays.stream(to).map(ObjectOrLink::of).toArray(ObjectOrLink[]::new));
        return note;
    }

    /** POSTs the fake received carrying the activity {@code id}. */
    private List<ReceivedRequest> receivedOf(URI id) {
        return fake.received().stream()
                .filter(r -> r.method().equals("POST") && r.bodyAsString().contains("\"" + id + "\"")).toList();
    }

    private Activity parse(ReceivedRequest request) {
        return (Activity) json.read(request.body());
    }

    /** No remote fetch (recipient lookup) ran inside the publishing transaction. */
    private static void noFetchInTransaction(URI expectedFetch) {
        assertTrue(FetchOutsideTransactionGuard.fetched(expectedFetch), "the recipient was looked up");
        assertEquals(List.of(), FetchOutsideTransactionGuard.callsInTransaction());
    }

    @Test
    void createToFollowersDeliversOncePerInbox() {
        FakeActor kim = fake.actor("kim"); // not cached yet; shares carol's and erin's inbox
        Activity create = activityPub.create(alice(), note(ALICE_FOLLOWERS, kim.id()));
        noFetchInTransaction(kim.id());
        scheduler.run();

        List<ReceivedRequest> posts = receivedOf(create.id());
        assertEquals(2, posts.size(), () -> posts.toString());
        assertEquals(List.of("/inbox", "/users/dave/inbox"), posts.stream().map(ReceivedRequest::path).sorted().toList());
        for (ReceivedRequest post : posts) {
            assertTrue(post.verified(), () -> "unverified " + post);
            assertEquals(LoopbackTests.BASE + "/users/alice#main-key", post.keyId());
            assertTrue(post.header("Content-Type").orElseThrow().startsWith("application/activity+json"));
            Create received = assertInstanceOf(Create.class, parse(post));
            assertEquals(create.id(), received.id());
            assertEquals(ALICE, received.actorUri().orElseThrow());
            Note note = assertInstanceOf(Note.class, received.embeddedObject().orElseThrow());
            assertTrue(note.id().toString().startsWith(LoopbackTests.BASE + "/objects/"), note.id().toString());
            assertEquals(ALICE, note.attributedTo().get(0).uri().orElseThrow());
        }
        assertTrue(create.id().toString().startsWith(LoopbackTests.BASE + "/activities/"));
        assertTrue(deliveries.forActivity(create.id()).stream().allMatch(d -> d.state() == DeliveryState.DELIVERED));
    }

    @Test
    void bccStrippedFromWire() {
        FakeActor hidden = fake.actor("hidden");
        Note note = note(ALICE_FOLLOWERS);
        note.bcc(ObjectOrLink.of(hidden.id()));
        Create create = new Create();
        create.to(ObjectOrLink.of(ALICE_FOLLOWERS));
        create.bcc(ObjectOrLink.of(hidden.id()));
        create.object(ObjectOrLink.of(note));

        Activity returned = activityPub.publish(alice(), create);
        scheduler.run();

        assertEquals(List.of(hidden.id()), returned.bcc().stream().map(o -> o.uri().orElseThrow()).toList());
        List<ReceivedRequest> posts = receivedOf(returned.id());
        assertEquals(2, posts.size(), () -> posts.toString());
        for (ReceivedRequest post : posts) {
            assertFalse(post.bodyAsString().contains("bcc"), post.bodyAsString());
            assertFalse(post.bodyAsString().contains(hidden.id().toString()), post.bodyAsString());
        }
    }

    @Test
    void publicCreateIsInOutbox() {
        RestAssured.registerParser("application/activity+json", Parser.JSON);
        Activity create = activityPub.create(alice(), note(Audience.PUBLIC, ALICE_FOLLOWERS));
        given().accept("application/activity+json").get("/users/alice/outbox?page=1")
                .then().statusCode(200)
                .body("orderedItems.id", hasItem(create.id().toString()));
    }

    @Test
    void followSendsFollowAndStoresPending() {
        FakeActor gus = fake.actor("gus");
        Activity follow = activityPub.follow(alice(), gus.id());
        noFetchInTransaction(gus.id());
        assertEquals(Optional.of(FollowState.PENDING), stores.followers().followingState(ALICE, gus.id()));
        scheduler.run();

        List<ReceivedRequest> posts = receivedOf(follow.id());
        assertEquals(1, posts.size(), () -> posts.toString());
        assertEquals("/inbox", posts.get(0).path());
        assertTrue(posts.get(0).verified());
        Follow received = assertInstanceOf(Follow.class, parse(posts.get(0)));
        assertEquals(gus.id(), received.objectUri().orElseThrow());
        assertEquals(ALICE, received.actorUri().orElseThrow());
    }

    @Test
    void undoFollowSendsUndo() {
        FakeActor hal = fake.actor("hal");
        Activity follow = activityPub.follow(alice(), hal.id());
        Activity undo = activityPub.undoFollow(alice(), hal.id());
        assertEquals(Optional.empty(), stores.followers().followingState(ALICE, hal.id()));
        scheduler.run();

        List<ReceivedRequest> posts = receivedOf(undo.id());
        assertEquals(1, posts.size(), () -> posts.toString());
        Undo received = assertInstanceOf(Undo.class, parse(posts.get(0)));
        assertEquals(follow.id(), received.objectUri().orElseThrow());
        assertEquals(ALICE, received.actorUri().orElseThrow());
    }

    @Test
    void deleteSendsTombstone() {
        Activity create = activityPub.create(alice(), note(Audience.PUBLIC, ALICE_FOLLOWERS));
        URI noteId = create.objectUri().orElseThrow();
        Activity delete = activityPub.delete(alice(), noteId);
        assertInstanceOf(Tombstone.class, stores.objects().find(noteId).orElseThrow());
        scheduler.run();

        List<ReceivedRequest> posts = receivedOf(delete.id());
        assertEquals(2, posts.size(), () -> posts.toString());
        for (ReceivedRequest post : posts) {
            assertTrue(post.verified());
            Delete received = assertInstanceOf(Delete.class, parse(post));
            Tombstone tombstone = assertInstanceOf(Tombstone.class, received.embeddedObject().orElseThrow());
            assertEquals(noteId, tombstone.id());
        }
    }

    @Test
    void deleteOfAnotherActorsObjectIsRefused() {
        Note bobs = note(Audience.PUBLIC);
        bobs.id(URI.create(LoopbackTests.BASE + "/objects/bobs-note"));
        bobs.attributedTo(ObjectOrLink.of(URI.create(LoopbackTests.BASE + "/users/bob")));
        stores.objects().putLocal(bobs, true);
        URI remote = URI.create(fake.actor("carol").id() + "/notes/1");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> activityPub.delete(alice(), bobs.id()));
        assertTrue(e.getMessage().contains(bobs.id().toString()), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> activityPub.delete(alice(), remote));
        assertInstanceOf(Note.class, stores.objects().find(bobs.id()).orElseThrow());
        scheduler.run();
        assertEquals(0, fake.received().stream().filter(r -> r.method().equals("POST")).count());
    }

    @Test
    void acceptFollowFromPendingDelivers() {
        FakeActor ivy = fake.actor("ivy");
        URI followId = URI.create(ivy.id() + "/follows/42");
        stores.followers().add(ALICE, ivy.id(), fake.sharedInbox(), FollowState.PENDING, followId);

        activityPub.acceptFollow(alice(), ivy.id());
        assertEquals(FollowState.ACCEPTED, stores.followers().follower(ALICE, ivy.id()).orElseThrow().state());
        scheduler.run();

        List<ReceivedRequest> posts = fake.received().stream()
                .filter(r -> r.method().equals("POST") && parse(r) instanceof Accept).toList();
        assertEquals(1, posts.size(), () -> posts.toString());
        assertTrue(posts.get(0).verified());
        Accept accept = (Accept) parse(posts.get(0));
        assertEquals(followId, accept.objectUri().orElseThrow());
        assertTrue(accept.audienceUris().contains(ivy.id()));
    }

    @Test
    void unresolvableRecipientIsFailedButOthersDeliver() {
        URI nobody = URI.create("https://127.0.0.1:1/users/nobody");
        Activity create = activityPub.create(alice(), note(ALICE_FOLLOWERS, nobody));
        scheduler.run();

        List<Delivery> all = deliveries.forActivity(create.id());
        assertEquals(3, all.size(), () -> all.toString());
        List<Delivery> failed = all.stream().filter(d -> d.state() == DeliveryState.FAILED).toList();
        assertEquals(1, failed.size(), () -> all.toString());
        assertEquals(nobody, failed.get(0).inbox());
        assertTrue(failed.get(0).lastError().isPresent());
        assertEquals(2, all.stream().filter(d -> d.state() == DeliveryState.DELIVERED).count(), () -> all.toString());
        assertEquals(2, receivedOf(create.id()).size());
    }

    @Test
    void remoteCollectionIsSkipped() {
        URI collection = fake.actor("carol").addObject(new OrderedCollection()).id();
        Activity create = activityPub.create(alice(), note(collection, ALICE_FOLLOWERS));
        scheduler.run();

        List<Delivery> all = deliveries.forActivity(create.id());
        assertEquals(List.of(fake.sharedInbox(), fake.actor("dave").inbox()), all.stream().map(Delivery::inbox).toList());
        assertTrue(all.stream().allMatch(d -> d.state() == DeliveryState.DELIVERED), () -> all.toString());
        assertEquals(0, fake.received().stream().filter(r -> r.method().equals("POST") && r.path().startsWith("/objects"))
                .count());
        ASObject stored = stores.objects().find(create.id()).orElseThrow();
        assertEquals(create.id(), stored.id());
    }
}

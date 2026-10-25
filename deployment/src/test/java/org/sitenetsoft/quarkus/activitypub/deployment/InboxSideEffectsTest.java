package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.ALICE_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.BOB;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.BOB_INBOX;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.create;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.delete;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.follow;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.note;
import static org.sitenetsoft.quarkus.activitypub.deployment.InboxFixtures.undo;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Reject;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.activitystreams.Undo;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxLog;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.FollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryFollowerStore;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.QuarkusUnitTest;

public class InboxSideEffectsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(Actors.class, RecordingFollowerStore.class, SimpleRegistry.class,
            InboxFixtures.class, RecordingActivityPub.class, EventCollector.class);

    static final FakeFediverseServer fake = new FakeFediverseServer();
    static final Duration WAIT = Duration.ofSeconds(10);

    /** {@code alice} accepts followers; {@code bob} approves them manually. */
    @ApplicationScoped
    public static class Actors {
        @Inject
        LocalActors actors;

        void register(@Observes StartupEvent event) {
            actors.register("alice", b -> {
            });
            actors.register("bob", b -> b.manuallyApprovesFollowers(true));
        }
    }

    /** A backend for the Micrometer composite registry, which has none in tests (its counters would be no-ops). */
    @jakarta.inject.Singleton
    public static class SimpleRegistry {
        @jakarta.enterprise.inject.Produces
        @jakarta.inject.Singleton
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }
    }

    /** The in-memory store, remembering the state of every follower (the SPI only lists accepted ones). */
    @ApplicationScoped
    @Typed({ FollowerStore.class, RecordingFollowerStore.class })
    public static class RecordingFollowerStore extends InMemoryFollowerStore {
        final Map<List<URI>, FollowState> states = new ConcurrentHashMap<>();

        @Override
        public void add(URI local, URI remote, URI remoteInbox, FollowState state, URI followActivityId) {
            super.add(local, remote, remoteInbox, state, followActivityId);
            states.put(List.of(local, remote), state);
        }

        @Override
        public void remove(URI local, URI remote) {
            super.remove(local, remote);
            states.remove(List.of(local, remote));
        }

        @Override
        public void removeAll(URI remote) {
            super.removeAll(remote);
            states.keySet().removeIf(k -> k.get(1).equals(remote));
        }

        FollowState state(URI local, URI remote) {
            return states.get(List.of(local, remote));
        }
    }

    @Inject
    Stores stores;

    @Inject
    RecordingFollowerStore followerStates;

    @Inject
    RecordingActivityPub activityPub;

    @Inject
    EventCollector events;

    @Inject
    InboxLog inboxLog;

    @Inject
    jakarta.enterprise.inject.Instance<MeterRegistry> registry;

    @BeforeAll
    static void startFake() {
        fake.start();
    }

    @AfterAll
    static void stopFake() {
        fake.stop();
    }

    private static void post(FakeActor from, URI inbox, org.sitenetsoft.activitystreams.Activity activity) {
        assertEquals(202, from.post(inbox, activity, Scheme.CAVAGE).statusCode());
    }

    private boolean follows(URI local, FakeActor remote) {
        return stores.followers().followers(local, 1, 100).items().contains(remote.id());
    }

    private void becomeFollower(FakeActor remote) {
        post(remote, ALICE_INBOX, follow(remote, ALICE));
        await().atMost(WAIT).until(() -> follows(ALICE, remote));
    }

    @Test
    void followIsAcceptedAndStored() {
        FakeActor carol = fake.actor("carol");
        Follow follow = follow(carol, ALICE);
        post(carol, ALICE_INBOX, follow);
        await().atMost(WAIT).until(() -> !activityPub.published(ALICE, Accept.class, carol.id()).isEmpty());
        assertEquals(FollowState.ACCEPTED, followerStates.state(ALICE, carol.id()));
        assertTrue(stores.followers().followerInboxes(ALICE).contains(fake.sharedInbox()));
        Accept accept = activityPub.published(ALICE, Accept.class, carol.id()).get(0);
        assertEquals(ALICE, accept.actorUri().orElseThrow());
        assertInstanceOf(Follow.class, accept.embeddedObject().orElseThrow());
        assertEquals(follow.id(), accept.objectUri().orElseThrow());
    }

    @Test
    void followPendingWhenManualApproval() {
        FakeActor erin = fake.actor("erin");
        post(erin, BOB_INBOX, follow(erin, BOB));
        await().atMost(WAIT).until(() -> followerStates.state(BOB, erin.id()) != null);
        assertEquals(FollowState.PENDING, followerStates.state(BOB, erin.id()));
        assertFalse(follows(BOB, erin));
        assertTrue(activityPub.published(BOB, Accept.class, erin.id()).isEmpty());
        assertTrue(activityPub.published(BOB, Reject.class, erin.id()).isEmpty());
    }

    @Test
    void undoFollowRemoves() {
        FakeActor frank = fake.actor("frank");
        Follow follow = follow(frank, ALICE);
        post(frank, ALICE_INBOX, follow);
        await().atMost(WAIT).until(() -> follows(ALICE, frank));
        post(frank, ALICE_INBOX, undo(frank, follow));
        await().atMost(WAIT).until(() -> !follows(ALICE, frank));
    }

    @Test
    void undoOfSomeoneElsesFollowIsIgnored() {
        FakeActor gus = fake.actor("gus");
        FakeActor hal = fake.actor("hal");
        Follow follow = follow(gus, ALICE);
        becomeFollower(gus);
        Undo undo = undo(hal, follow);
        post(hal, ALICE_INBOX, undo);
        await().atMost(WAIT).until(() -> events.find("any", undo.id()).isPresent());
        assertTrue(follows(ALICE, gus));
    }

    @Test
    void acceptOfOurFollowMarksFollowing() {
        FakeActor grace = fake.actor("grace");
        URI followId = URI.create(LoopbackTests.BASE + "/activities/follow-grace");
        stores.followers().addFollowing(ALICE, grace.id(), FollowState.PENDING, followId);
        Follow ours = new Follow();
        ours.id(followId);
        ours.actor(ObjectOrLink.of(ALICE)).object(ObjectOrLink.of(grace.id()));
        Accept accept = new Accept();
        accept.id(InboxFixtures.idOf(grace, "activities"));
        accept.actor(ObjectOrLink.of(grace.id())).object(ObjectOrLink.of(ours));
        post(grace, ALICE_INBOX, accept);
        await().atMost(WAIT).until(() -> stores.followers().followingState(ALICE, grace.id())
                .equals(java.util.Optional.of(FollowState.ACCEPTED)));
    }

    @Test
    void createAddressedToAliceIsStored() {
        FakeActor heidi = fake.actor("heidi");
        Note note = note(heidi, ALICE);
        post(heidi, ALICE_INBOX, create(heidi, note));
        await().atMost(WAIT).until(() -> stores.objects().find(note.id()).isPresent());
        assertEquals("<p>hello</p>", stores.objects().find(note.id()).get().content());
    }

    @Test
    void createToFollowersOnlyIsNotStored() {
        FakeActor ivan = fake.actor("ivan");
        Note note = note(ivan, URI.create(ivan.id() + "/followers"));
        var create = create(ivan, note);
        post(ivan, InboxFixtures.SHARED_INBOX, create);
        await().atMost(WAIT).until(() -> events.find("create", create.id()).isPresent());
        assertTrue(stores.objects().find(note.id()).isEmpty());
    }

    @Test
    void replyToLocalObjectIsStored() {
        FakeActor judy = fake.actor("judy");
        Note local = new Note();
        local.id(URI.create(LoopbackTests.BASE + "/objects/local-1"));
        local.attributedTo(ObjectOrLink.of(ALICE));
        local.to(ObjectOrLink.of(Audience.PUBLIC));
        stores.objects().putLocal(local, true);
        Note reply = note(judy, Audience.PUBLIC);
        reply.inReplyTo(ObjectOrLink.of(local.id()));
        post(judy, InboxFixtures.SHARED_INBOX, create(judy, reply));
        await().atMost(WAIT).until(() -> stores.objects().find(reply.id()).isPresent());
    }

    @Test
    void deleteOfHeldObjectTombstones() {
        FakeActor kim = fake.actor("kim");
        Note note = note(kim, ALICE);
        post(kim, ALICE_INBOX, create(kim, note));
        await().atMost(WAIT).until(() -> stores.objects().find(note.id()).isPresent());
        post(kim, ALICE_INBOX, delete(kim, note.id()));
        await().atMost(WAIT).until(() -> stores.objects().find(note.id()).orElseThrow() instanceof Tombstone);
    }

    @Test
    void deleteOfOtherServersObjectIgnored() {
        FakeActor lou = fake.actor("lou");
        Note held = new Note();
        held.id(URI.create("https://elsewhere.example/objects/1"));
        held.attributedTo(ObjectOrLink.of(URI.create("https://elsewhere.example/users/x")));
        stores.objects().putRemote(held, held.id());
        var delete = delete(lou, held.id());
        post(lou, ALICE_INBOX, delete);
        await().atMost(WAIT).until(() -> events.find("any", delete.id()).isPresent());
        assertInstanceOf(Note.class, stores.objects().find(held.id()).orElseThrow());
    }

    @Test
    void deleteOfUnknownActorDoesNotFetch() {
        FakeActor leo = fake.actor("leo");
        // the first signed request resolves leo's key; later ones use the key cache
        var warmUp = delete(leo, InboxFixtures.idOf(leo, "objects"));
        post(leo, ALICE_INBOX, warmUp);
        await().atMost(WAIT).until(() -> events.find("any", warmUp.id()).isPresent());
        fake.clear();
        var unknownObject = delete(leo, InboxFixtures.idOf(leo, "objects"));
        var self = delete(leo, leo.id());
        post(leo, ALICE_INBOX, unknownObject);
        post(leo, InboxFixtures.SHARED_INBOX, self);
        await().atMost(WAIT).until(() -> events.find("any", unknownObject.id()).isPresent()
                && events.find("any", self.id()).isPresent());
        assertEquals(List.of(), fake.received());
    }

    @Test
    void deleteOfKnownActorRemovesFollower() {
        FakeActor mia = fake.actor("mia");
        becomeFollower(mia);
        Note note = note(mia, ALICE);
        post(mia, ALICE_INBOX, create(mia, note));
        await().atMost(WAIT).until(() -> stores.objects().find(note.id()).isPresent());
        post(mia, InboxFixtures.SHARED_INBOX, delete(mia, mia.id()));
        await().atMost(WAIT).until(() -> !follows(ALICE, mia));
        await().atMost(WAIT).until(() -> stores.objects().find(note.id()).isEmpty());
    }

    @Test
    void attributedToMismatchIgnored() {
        FakeActor nick = fake.actor("nick");
        Note note = note(nick, ALICE);
        note.attributedTo(ObjectOrLink.of(URI.create("https://other.example/users/someone")));
        var create = create(nick, note);
        post(nick, ALICE_INBOX, create);
        await().atMost(WAIT).until(() -> events.find("create", create.id()).isPresent());
        assertTrue(stores.objects().find(note.id()).isEmpty());
    }

    @Test
    void activityWithoutIdIsProcessed() {
        FakeActor olga = fake.actor("olga");
        stores.followers().add(ALICE, olga.id(), fake.sharedInbox(), FollowState.ACCEPTED,
                InboxFixtures.idOf(olga, "activities"));
        assertTrue(follows(ALICE, olga));
        Follow follow = follow(olga, ALICE);
        follow.id(null);
        post(olga, ALICE_INBOX, InboxFixtures.withoutId(undo(olga, follow)));
        await().atMost(WAIT).until(() -> !follows(ALICE, olga));
    }

    @Test
    void duplicateIdIgnored() {
        FakeActor pia = fake.actor("pia");
        Follow follow = follow(pia, ALICE);
        post(pia, ALICE_INBOX, follow);
        await().atMost(WAIT).until(() -> !activityPub.published(ALICE, Accept.class, pia.id()).isEmpty());
        post(pia, ALICE_INBOX, follow);
        assertTrue(inboxLog.entries().stream()
                .anyMatch(e -> pia.id().equals(e.actor()) && InboxLog.DUPLICATE.equals(e.result())));
        assertEquals(1, activityPub.published(ALICE, Accept.class, pia.id()).size());
    }

    @Test
    void outcomesAreLoggedAndCounted() {
        FakeActor quinn = fake.actor("quinn");
        Follow follow = follow(quinn, ALICE);
        post(quinn, ALICE_INBOX, follow);
        await().atMost(WAIT).until(() -> inboxLog.entries().stream()
                .anyMatch(e -> quinn.id().equals(e.actor()) && InboxLog.ACCEPTED.equals(e.result())));
        post(quinn, ALICE_INBOX, follow);
        List<InboxLog.InboxEntry> mine = inboxLog.entries().stream().filter(e -> quinn.id().equals(e.actor())).toList();
        InboxLog.InboxEntry entry = mine.get(mine.size() - 1);
        assertEquals("Follow", entry.type());
        assertEquals(InboxLog.DUPLICATE, entry.result());
        assertTrue(registry.isResolvable());
        assertTrue(registry.get().counter("activitypub.inbox.received", "type", "Follow", "result", "duplicate")
                .count() >= 1);
    }
}

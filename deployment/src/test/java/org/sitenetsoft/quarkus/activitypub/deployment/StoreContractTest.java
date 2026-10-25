package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeys;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.store.ActorStore;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.FollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.HostHealth;
import org.sitenetsoft.quarkus.activitypub.store.ObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.Page;

/**
 * Behaviour every store implementation must have. Each test uses fresh random ids, so implementations may share state
 * across tests. Time-dependent operations receive explicit {@link Instant}s.
 */
public abstract class StoreContractTest {

    private static final ActorKeyPair KEYS = ActorKeys.generateRsa2048();

    protected abstract ActorStore actors();

    protected abstract ObjectStore objects();

    protected abstract FollowerStore followers();

    protected abstract DeliveryStore deliveries();

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static URI local(String path) {
        return URI.create("https://example.org/" + path);
    }

    private static URI remote(String path) {
        return URI.create("https://remote.example/" + path);
    }

    private static LocalActor actor(String username, String name) {
        return LocalActor.builder(username).id(local("users/" + username)).name(name).build();
    }

    @Test
    void actorSaveFindAndKeys() {
        String username = "u" + unique();
        LocalActor actor = actor(username, "Alice");
        long before = actors().countLocalActors();
        actors().save(actor, KEYS);

        assertEquals(Optional.of(actor), actors().findByUsername(username));
        assertEquals(Optional.of(actor), actors().findById(actor.id()));
        assertEquals(before + 1, actors().countLocalActors());
        ActorKeyPair keys = actors().keyPair(actor.id()).orElseThrow();
        assertEquals(KEYS.publicKeyPem(), keys.publicKeyPem());
        assertEquals(KEYS.privateKeyPem(), keys.privateKeyPem());
        assertEquals("RSA", ActorKeys.publicKey(keys).getAlgorithm());
        assertEquals("RSA", ActorKeys.privateKey(keys).getAlgorithm());

        assertEquals(Optional.empty(), actors().findByUsername("missing" + unique()));
        assertEquals(Optional.empty(), actors().keyPair(local("users/missing" + unique())));

        actors().delete(actor.id());
        assertEquals(Optional.empty(), actors().findByUsername(username));
        assertEquals(Optional.empty(), actors().findById(actor.id()));
        assertEquals(Optional.empty(), actors().keyPair(actor.id()));
        assertEquals(before, actors().countLocalActors());
    }

    @Test
    void actorSaveTwiceUpdates() {
        String username = "u" + unique();
        actors().save(actor(username, "First"), KEYS);
        long count = actors().countLocalActors();
        ActorKeyPair other = ActorKeys.generateRsa2048();
        actors().save(actor(username, "Second"), other);

        assertEquals(count, actors().countLocalActors());
        LocalActor found = actors().findByUsername(username).orElseThrow();
        assertEquals(Optional.of("Second"), found.name());
        assertEquals(other.publicKeyPem(), actors().keyPair(found.id()).orElseThrow().publicKeyPem());
    }

    @Test
    void objectPutFindTombstone() {
        URI id = local("objects/" + unique());
        Note note = new Note();
        note.id(id).content("hello").to(ObjectOrLink.of(Audience.PUBLIC));
        objects().putLocal(note, true);

        ASObject found = objects().find(id).orElseThrow();
        assertInstanceOf(Note.class, found);
        assertEquals("hello", found.content());
        found.content("mutated");
        assertEquals("hello", objects().find(id).orElseThrow().content(), "find returns a copy");

        objects().tombstone(id);
        Tombstone tombstone = assertInstanceOf(Tombstone.class, objects().find(id).orElseThrow());
        assertEquals(id, tombstone.id());
        assertEquals("Note", tombstone.formerType());
        assertNotNull(tombstone.deleted());

        assertEquals(Optional.empty(), objects().find(local("objects/missing" + unique())));
    }

    @Test
    void deleteByActorRemovesObjects() {
        URI author = remote("users/" + unique());
        URI otherAuthor = remote("users/" + unique());
        URI mine = remote("notes/" + unique());
        URI theirs = remote("notes/" + unique());
        objects().putRemote(new Note().id(mine).attributedTo(ObjectOrLink.of(author)), remote("inbox"));
        objects().putRemote(new Note().id(theirs).attributedTo(ObjectOrLink.of(otherAuthor)), remote("inbox"));

        objects().deleteByActor(author);

        assertEquals(Optional.empty(), objects().find(mine));
        assertTrue(objects().find(theirs).isPresent());
    }

    @Test
    void markSeenIsIdempotent() {
        URI activity = remote("activities/" + unique());
        assertTrue(objects().markSeen(activity, Duration.ofDays(7)));
        assertFalse(objects().markSeen(activity, Duration.ofDays(7)));
        assertTrue(objects().markSeen(remote("activities/" + unique()), Duration.ofDays(7)));
    }

    @Test
    void outboxPagingNewestFirst() {
        URI actor = local("users/" + unique());
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            Activity create = new Create().actor(ObjectOrLink.of(actor));
            create.id(local("activities/" + unique())).published(t0.plusSeconds(i)).name("a" + i);
            objects().putLocal(create, true);
        }
        objects().putLocal(new Note().id(local("objects/" + unique())).attributedTo(ObjectOrLink.of(actor)), true);
        objects().putLocal(new Create().actor(ObjectOrLink.of(local("users/" + unique()))).id(local("activities/" + unique())),
                true);

        assertEquals(5, objects().outboxCount(actor));
        Page<Activity> first = objects().outbox(actor, 1, 2);
        assertEquals(List.of("a4", "a3"), first.items().stream().map(ASObject::name).toList());
        assertEquals(1, first.page());
        assertEquals(2, first.size());
        assertEquals(5, first.total());
        assertTrue(first.hasNext());
        Page<Activity> last = objects().outbox(actor, 3, 2);
        assertEquals(List.of("a0"), last.items().stream().map(ASObject::name).toList());
        assertFalse(last.hasNext());
        assertTrue(objects().outbox(actor, 4, 2).items().isEmpty());
    }

    @Test
    void followersAddRemovePageCount() {
        URI me = local("users/" + unique());
        URI a = remote("users/a" + unique());
        URI b = remote("users/b" + unique());
        URI c = remote("users/c" + unique());
        followers().add(me, a, remote("inbox"), FollowState.ACCEPTED, remote("follows/1"));
        followers().add(me, b, remote("inbox"), FollowState.ACCEPTED, remote("follows/2"));
        followers().add(me, c, remote("inbox"), FollowState.PENDING, remote("follows/3"));

        assertEquals(2, followers().followerCount(me));
        Page<URI> page = followers().followers(me, 1, 10);
        assertEquals(List.of(b, a), page.items());
        assertEquals(2, page.total());
        assertFalse(page.hasNext());
        Page<URI> small = followers().followers(me, 1, 1);
        assertEquals(List.of(b), small.items());
        assertTrue(small.hasNext());

        followers().remove(me, a);
        assertEquals(List.of(b), followers().followers(me, 1, 10).items());
        assertEquals(1, followers().followerCount(me));
    }

    @Test
    void followerInboxesDistinct() {
        URI me = local("users/" + unique());
        URI shared = remote("inbox");
        followers().add(me, remote("users/a" + unique()), shared, FollowState.ACCEPTED, remote("f/1"));
        followers().add(me, remote("users/b" + unique()), shared, FollowState.ACCEPTED, remote("f/2"));
        URI own = remote("users/c/inbox");
        followers().add(me, remote("users/c" + unique()), own, FollowState.ACCEPTED, remote("f/3"));
        followers().add(me, remote("users/d" + unique()), remote("users/d/inbox"), FollowState.PENDING, remote("f/4"));

        List<URI> inboxes = followers().followerInboxes(me);
        assertEquals(2, inboxes.size());
        assertTrue(inboxes.containsAll(List.of(shared, own)));
    }

    @Test
    void removeAllClearsBothDirections() {
        URI me = local("users/" + unique());
        URI other = local("users/" + unique());
        URI gone = remote("users/" + unique());
        followers().add(me, gone, remote("inbox"), FollowState.ACCEPTED, remote("f/1"));
        followers().add(other, gone, remote("inbox"), FollowState.ACCEPTED, remote("f/2"));
        followers().addFollowing(me, gone, FollowState.ACCEPTED, local("activities/" + unique()));

        followers().removeAll(gone);

        assertEquals(0, followers().followerCount(me));
        assertEquals(0, followers().followerCount(other));
        assertEquals(0, followers().followingCount(me));
        assertEquals(Optional.empty(), followers().followingState(me, gone));
    }

    @Test
    void followingState() {
        URI me = local("users/" + unique());
        URI a = remote("users/a" + unique());
        URI b = remote("users/b" + unique());
        followers().addFollowing(me, a, FollowState.PENDING, local("activities/" + unique()));
        assertEquals(Optional.of(FollowState.PENDING), followers().followingState(me, a));
        assertEquals(0, followers().followingCount(me));

        followers().addFollowing(me, a, FollowState.ACCEPTED, local("activities/" + unique()));
        followers().addFollowing(me, b, FollowState.ACCEPTED, local("activities/" + unique()));
        assertEquals(Optional.of(FollowState.ACCEPTED), followers().followingState(me, a));
        assertEquals(2, followers().followingCount(me));
        assertEquals(List.of(b, a), followers().following(me, 1, 10).items());

        followers().removeFollowing(me, a);
        assertEquals(Optional.empty(), followers().followingState(me, a));
        assertEquals(List.of(b), followers().following(me, 1, 10).items());
    }

    private static Delivery delivery(Instant now) {
        return Delivery.create(remote("inbox/" + unique()), local("activities/" + unique()),
                "{}".getBytes(StandardCharsets.UTF_8), "https://example.org/users/alice#main-key", now);
    }

    @Test
    void deliverySaveClaimUpdate() {
        // far-future base so rows left behind by other tests (due "now") never sort first
        Instant now = Instant.parse("2100-01-01T00:00:00Z");
        Delivery due = delivery(now.minusSeconds(10));
        Delivery dueLater = delivery(now.minusSeconds(5));
        Delivery future = delivery(now.plusSeconds(60));
        Delivery delivered = delivery(now.minusSeconds(20)).withState(DeliveryState.DELIVERED);
        deliveries().save(List.of(due, dueLater, future, delivered));

        List<String> ids = List.of(due.id(), dueLater.id(), future.id(), delivered.id());
        List<Delivery> claimed = deliveries().claimDue(now, 1000).stream().filter(d -> ids.contains(d.id())).toList();
        assertEquals(List.of(due.id(), dueLater.id()), claimed.stream().map(Delivery::id).toList());
        assertEquals(0, claimed.get(0).attempts());
        assertEquals(DeliveryState.PENDING, claimed.get(0).state());

        assertTrue(deliveries().claimDue(now.plusSeconds(30), 1000).stream().noneMatch(d -> ids.contains(d.id())),
                "claimed rows are skipped for 60s");

        deliveries().update(claimed.get(0).withAttempts(1).withNextAttempt(now.plusSeconds(1))
                .withLastError(Optional.of("503")));
        List<Delivery> again = deliveries().claimDue(now.plusSeconds(2), 1000).stream()
                .filter(d -> ids.contains(d.id())).toList();
        assertEquals(List.of(due.id()), again.stream().map(Delivery::id).toList(), "update clears the claim");
        assertEquals(1, again.get(0).attempts());
        assertEquals(Optional.of("503"), again.get(0).lastError());

        List<Delivery> expired = deliveries().claimDue(now.plusSeconds(120), 1000).stream()
                .filter(d -> ids.contains(d.id())).toList();
        assertEquals(List.of(dueLater.id(), due.id(), future.id()), expired.stream().map(Delivery::id).toList(),
                "a claim older than 60s is released; ordered by next attempt");

        for (Delivery d : expired) {
            deliveries().update(d.withState(DeliveryState.DELIVERED));
        }
        assertTrue(deliveries().claimDue(now.plusSeconds(1000), 1000).stream().noneMatch(d -> ids.contains(d.id())));
    }

    @Test
    void deliveryClaimRespectsLimit() {
        Instant now = Instant.parse("2200-01-01T00:00:00Z");
        deliveries().save(List.of(delivery(now.minusSeconds(3)), delivery(now.minusSeconds(2)), delivery(now.minusSeconds(1))));
        assertEquals(2, deliveries().claimDue(now, 2).size());
    }

    @Test
    void pendingCountAndOldest() {
        long before = deliveries().pendingCount();
        Instant oldest = Instant.parse("2000-01-01T00:00:00Z");
        Delivery a = delivery(oldest);
        Delivery b = delivery(oldest.plusSeconds(60));
        deliveries().save(List.of(a, b));

        assertEquals(before + 2, deliveries().pendingCount());
        assertEquals(Optional.of(oldest), deliveries().oldestPending());

        deliveries().update(a.withState(DeliveryState.GIVEN_UP));
        deliveries().update(b.withState(DeliveryState.FAILED));
        assertEquals(before, deliveries().pendingCount());
        assertFalse(deliveries().oldestPending().filter(oldest::equals).isPresent());
    }

    @Test
    void hostHealthFailuresAndReset() {
        String host = unique() + ".example";
        HostHealth unknown = deliveries().host(host);
        assertEquals(host, unknown.host());
        assertEquals(0, unknown.consecutiveFailures());
        assertEquals(Optional.empty(), unknown.lastFailure());

        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            deliveries().recordHostResult(host, false, t.plusSeconds(i));
        }
        HostHealth failing = deliveries().host(host);
        assertEquals(5, failing.consecutiveFailures());
        assertEquals(Optional.of(t.plusSeconds(4)), failing.lastFailure());
        assertEquals(Optional.empty(), failing.lastSuccess());

        deliveries().pauseHost(host, t.plusSeconds(3600));
        assertEquals(Optional.of(t.plusSeconds(3600)), deliveries().host(host).pausedUntil());

        deliveries().recordHostResult(host, true, t.plusSeconds(10));
        HostHealth healthy = deliveries().host(host);
        assertEquals(0, healthy.consecutiveFailures());
        assertEquals(Optional.of(t.plusSeconds(10)), healthy.lastSuccess());
        assertEquals(Optional.empty(), healthy.pausedUntil());
    }
}

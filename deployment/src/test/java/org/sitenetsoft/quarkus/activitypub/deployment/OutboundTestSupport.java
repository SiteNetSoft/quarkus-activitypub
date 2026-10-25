package org.sitenetsoft.quarkus.activitypub.deployment;

import java.net.URI;
import java.time.Duration;

import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

/** Shared steps of the clock-driven delivery tests: one follower ({@code dave}, own inbox) on the fake. */
final class OutboundTestSupport {

    private OutboundTestSupport() {
    }

    /** Makes {@code dave} alice's only follower. */
    static FakeActor onlyFollowerDave(Stores stores, FakeFediverseServer fake) {
        FakeActor dave = fake.actor("dave");
        stores.followers().add(AliceActor.ID, dave.id(), dave.inbox(), FollowState.ACCEPTED,
                URI.create(dave.id() + "/follows/1"));
        return dave;
    }

    /**
     * Starts a test from a clean slate: earlier tests' pending deliveries are delivered (clock moved past every
     * backoff), the host's health is reset by that success, and the fake forgets its requests.
     */
    static void flush(MutableClock clock, DeliveryScheduler scheduler, Stores stores, FakeFediverseServer fake) {
        fake.inboxStatus(202);
        clock.advance(Duration.ofDays(3));
        scheduler.run();
        stores.deliveries().recordHostResult(fake.baseUri().getHost(), true, clock.instant());
        fake.clear();
    }

    static Note followersNote() {
        Note note = new Note();
        note.content("<p>hello</p>");
        note.to(ObjectOrLink.of(AliceActor.FOLLOWERS));
        return note;
    }
}

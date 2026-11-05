package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryEvents.DeliveriesSubmitted;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryEvents.DeliveryFailed;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

/** {@code DeliveriesSubmitted}, and {@code DeliveryFailed} for deliveries that never get an attempt. */
public class DeliveryAccountingTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, RecordingDeliveryStore.class,
            DeliveryEventCollector.class, MutableClock.class, OutboundTestSupport.class,
            DeliveryAccountingTest.SubmittedCollector.class)
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1h");

    static final FakeFediverseServer fake = new FakeFediverseServer();

    /** Records {@link DeliveriesSubmitted}. */
    @ApplicationScoped
    public static class SubmittedCollector {
        private final List<DeliveriesSubmitted> submitted = new CopyOnWriteArrayList<>();

        void on(@Observes DeliveriesSubmitted event) {
            submitted.add(event);
        }

        List<DeliveriesSubmitted> of(URI activityId) {
            return submitted.stream().filter(e -> e.activityId().equals(activityId)).toList();
        }
    }

    @Inject
    ActivityPub activityPub;

    @Inject
    DeliveryScheduler scheduler;

    @Inject
    SubmittedCollector submitted;

    @Inject
    DeliveryEventCollector events;

    @Inject
    MutableClock clock;

    @Inject
    Stores stores;

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

    @BeforeEach
    void cleanSlate() {
        OutboundTestSupport.onlyFollowerDave(stores, fake);
        FakeActor erin = fake.actor("erin");
        stores.followers().add(AliceActor.ID, erin.id(), erin.inbox(), FollowState.ACCEPTED,
                URI.create(erin.id() + "/follows/1"));
        OutboundTestSupport.flush(clock, scheduler, stores, fake);
    }

    /** To the followers (dave, erin) and to an actor whose document the fake answers with 404. */
    private Activity publishWithUnreachableRecipient() {
        Note note = followersNote();
        note.to(ObjectOrLink.of(AliceActor.FOLLOWERS), ObjectOrLink.of(fake.baseUri().resolve("/users/ghost")));
        return activityPub.create(alice.get(), note);
    }

    @Test
    void submittedListsEveryDelivery() {
        Activity create = publishWithUnreachableRecipient();

        List<DeliveriesSubmitted> fired = submitted.of(create.id());
        assertEquals(1, fired.size());
        List<Delivery> deliveries = fired.get(0).deliveries();
        assertEquals(3, deliveries.size());
        assertEquals(2, deliveries.stream().filter(d -> d.state() == DeliveryState.PENDING).count());
        assertEquals(1, deliveries.stream().filter(d -> d.state() == DeliveryState.FAILED).count());
    }

    @Test
    void failedAtSubmitFiresDeliveryFailed() {
        Activity create = publishWithUnreachableRecipient();

        List<DeliveryFailed> failed = events.failed(create.id());
        assertEquals(1, failed.size(), "before the scheduler ran");
        assertFalse(failed.get(0).willRetry());
        assertEquals(DeliveryState.FAILED, failed.get(0).delivery().state());
        assertEquals(failed.get(0).delivery().lastError().orElseThrow(), failed.get(0).error());
    }

    @Test
    void deadHostGivenUpFiresDeliveryFailed() {
        String host = fake.baseUri().getHost();
        Instant now = clock.instant();
        stores.deliveries().recordHostResult(host, true, now.minus(Duration.ofDays(31)));
        for (int i = 0; i < 6; i++) {
            stores.deliveries().recordHostResult(host, false, now);
        }
        stores.deliveries().pauseHost(host, now.plus(Duration.ofMinutes(5)));

        Activity create = activityPub.create(alice.get(), followersNote());

        List<Delivery> deliveries = submitted.of(create.id()).get(0).deliveries();
        assertEquals(2, deliveries.size());
        assertTrue(deliveries.stream().allMatch(d -> d.state() == DeliveryState.GIVEN_UP));
        List<DeliveryFailed> failed = events.failed(create.id());
        assertEquals(2, failed.size());
        assertTrue(failed.stream().allMatch(f -> !f.willRetry() && f.delivery().state() == DeliveryState.GIVEN_UP
                && f.delivery().lastError().orElseThrow().equals(f.error())));
    }

    @Test
    void noRecipientsFiresEmptySubmitted() {
        Note note = new Note();
        note.content("<p>to nobody</p>");
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        Activity create = activityPub.create(alice.get(), note);

        List<DeliveriesSubmitted> fired = submitted.of(create.id());
        assertEquals(1, fired.size());
        assertEquals(List.of(), fired.get(0).deliveries());
        assertEquals(List.of(), events.failed(create.id()));
    }
}

package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

public class DeliveryHealthCheckTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, MutableClock.class,
            OutboundTestSupport.class)
            // only the test drives the scheduler, so a due delivery stays unprocessed
            .overrideConfigKey("quarkus.activitypub.delivery.poll-interval", "1h");

    static final FakeFediverseServer fake = new FakeFediverseServer();
    static final String CHECK = "checks.find { it.name == 'activitypub-delivery' }";

    @Inject
    ActivityPub activityPub;

    @Inject
    DeliveryScheduler scheduler;

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

    @Test
    void readinessStaysUpWhileDeliveriesWaitForTheirBackoff() {
        given().get("/q/health/ready").then().statusCode(200)
                .body("checks.name", hasItem("activitypub-delivery"))
                .body(CHECK + ".status", is("UP"))
                .body(CHECK + ".data.pending", is(0));

        // the remote host is down: every attempt fails and the delivery waits 1m, 5m, 30m, then 2h
        OutboundTestSupport.onlyFollowerDave(stores, fake);
        fake.inboxStatus(500);
        Instant created = clock.instant();
        activityPub.create(alice.get(), followersNote());
        scheduler.run();
        for (Duration wait : List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30))) {
            clock.advance(wait);
            scheduler.run();
        }
        // more than max-backlog-age (1h) after the delivery was created, its next attempt still 2h away
        clock.advance(Duration.ofHours(1));
        given().get("/q/health/ready").then().statusCode(200)
                .body(CHECK + ".status", is("UP"))
                .body(CHECK + ".data.pending", greaterThanOrEqualTo(1))
                .body(CHECK + ".data.oldest", is(created.toString()))
                .body(CHECK + ".data.oldestDue", nullValue());
    }

    @Test
    void readinessTurnsDownWhenDueWorkIsNotProcessed() {
        // a delivery due for two hours that no scheduler run has picked up
        Instant due = clock.instant().minus(Duration.ofHours(2));
        Delivery stuck = Delivery.create(URI.create(fake.baseUri() + "/users/stuck/inbox"),
                URI.create(LoopbackTests.BASE + "/activities/stuck"), "{}".getBytes(StandardCharsets.UTF_8),
                LoopbackTests.BASE + "/users/alice#main-key", due);
        stores.deliveries().save(List.of(stuck));
        try {
            given().get("/q/health/ready").then().statusCode(503)
                    .body(CHECK + ".status", is("DOWN"))
                    .body(CHECK + ".data.oldestDue", is(due.toString()));
        } finally {
            stores.deliveries().update(stuck.withState(DeliveryState.FAILED));
        }
    }
}

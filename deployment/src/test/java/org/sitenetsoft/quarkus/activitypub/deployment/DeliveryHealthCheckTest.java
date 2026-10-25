package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.sitenetsoft.quarkus.activitypub.deployment.OutboundTestSupport.followersNote;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.test.FakeFediverseServer;

import io.quarkus.test.QuarkusUnitTest;

public class DeliveryHealthCheckTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class, MutableClock.class,
            OutboundTestSupport.class);

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
    void readinessReportsTheBacklogAndTurnsDownWhenItIsOld() {
        given().get("/q/health/ready").then().statusCode(200)
                .body("checks.name", hasItem("activitypub-delivery"))
                .body(CHECK + ".status", is("UP"))
                .body(CHECK + ".data.pending", is(0));

        OutboundTestSupport.onlyFollowerDave(stores, fake);
        fake.inboxStatus(500);
        activityPub.create(alice.get(), followersNote());
        scheduler.run();

        given().get("/q/health/ready").then().statusCode(200)
                .body(CHECK + ".status", is("UP"))
                .body(CHECK + ".data.pending", greaterThanOrEqualTo(1))
                .body(CHECK + ".data.oldest", is(clock.instant().toString()));

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        given().get("/q/health/ready").then().statusCode(503)
                .body(CHECK + ".status", is("DOWN"));
    }
}

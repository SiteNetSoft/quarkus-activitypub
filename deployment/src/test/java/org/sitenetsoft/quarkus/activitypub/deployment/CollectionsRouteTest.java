package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;
import java.time.Instant;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;

public class CollectionsRouteTest {

    static final String OUTBOX = TestActors.ALICE + "/outbox";
    static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(TestActors.class))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE);

    @Inject
    Stores stores;

    @BeforeAll
    static void parsers() {
        TestActors.jsonParsers();
    }

    /** 25 public activities by alice (ids replace on repeat, so seeding per test is idempotent). */
    @BeforeEach
    void seed() {
        for (int i = 0; i < 25; i++) {
            Create create = new Create();
            create.actor(ObjectOrLink.of(TestActors.ALICE)).id(URI.create(TestActors.BASE + "/activities/" + i));
            create.to(ObjectOrLink.of(Audience.PUBLIC)).published(T0.plusSeconds(60L * i));
            stores.objects().putLocal(create, true);
        }
        stores.followers().add(URI.create(TestActors.BOB), URI.create("https://remote.example/users/carol"),
                URI.create("https://remote.example/inbox"), FollowState.ACCEPTED, URI.create("https://remote.example/f/1"));
    }

    @Test
    void outboxCollectionHasTotal() {
        given().accept(TestActors.AP).when().get("/users/alice/outbox")
                .then().statusCode(200)
                .header("Content-Type", "application/activity+json; charset=utf-8")
                .header("Vary", "Accept")
                .body("id", equalTo(OUTBOX))
                .body("type", equalTo("OrderedCollection"))
                .body("totalItems", equalTo(25))
                .body("first", equalTo(OUTBOX + "?page=1"));
    }

    @Test
    void outboxPageOrdersNewestFirst() {
        given().accept(TestActors.AP).queryParam("page", 1).when().get("/users/alice/outbox")
                .then().statusCode(200)
                .body("type", equalTo("OrderedCollectionPage"))
                .body("id", equalTo(OUTBOX + "?page=1"))
                .body("partOf", equalTo(OUTBOX))
                .body("orderedItems", hasSize(20))
                .body("orderedItems[0].id", equalTo(TestActors.BASE + "/activities/24"))
                .body("orderedItems[0].type", equalTo("Create"))
                .body("orderedItems[19].id", equalTo(TestActors.BASE + "/activities/5"))
                .body("next", equalTo(OUTBOX + "?page=2"))
                .body("prev", nullValue());
        given().accept(TestActors.AP).queryParam("page", 2).when().get("/users/alice/outbox")
                .then().statusCode(200)
                .body("orderedItems", hasSize(5))
                .body("orderedItems[4].id", equalTo(TestActors.BASE + "/activities/0"))
                .body("prev", equalTo(OUTBOX + "?page=1"))
                .body("next", nullValue());
    }

    @Test
    void invalidPageIs400() {
        given().accept(TestActors.AP).queryParam("page", 0).when().get("/users/alice/outbox").then().statusCode(400);
        given().accept(TestActors.AP).queryParam("page", "x").when().get("/users/alice/outbox").then().statusCode(400);
    }

    @Test
    void unknownActorCollectionIs404() {
        given().accept(TestActors.AP).when().get("/users/nobody/outbox").then().statusCode(404);
        given().accept(TestActors.AP).when().get("/users/nobody/followers").then().statusCode(404);
    }

    @Test
    void followersPublicListsAcceptedFollowers() {
        given().accept(TestActors.AP).when().get("/users/bob/followers")
                .then().statusCode(200)
                .body("type", equalTo("OrderedCollection"))
                .body("totalItems", equalTo(1))
                .body("first", equalTo(TestActors.BOB + "/followers?page=1"));
        given().accept(TestActors.AP).queryParam("page", 1).when().get("/users/bob/followers")
                .then().statusCode(200)
                .body("orderedItems", contains("https://remote.example/users/carol"));
        given().accept(TestActors.AP).when().get("/users/bob/following")
                .then().statusCode(200)
                .body("totalItems", equalTo(0));
    }
}

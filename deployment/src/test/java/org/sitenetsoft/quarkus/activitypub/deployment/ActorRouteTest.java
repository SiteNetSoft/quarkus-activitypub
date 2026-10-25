package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.response.Response;

public class ActorRouteTest {

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

    @Test
    void actorJsonForActivityAccept() {
        given().accept(TestActors.AP).when().get("/users/alice")
                .then().statusCode(200)
                .header("Content-Type", "application/activity+json; charset=utf-8")
                .header("Vary", "Accept")
                .body("'@context'", notNullValue())
                .body("id", equalTo(TestActors.ALICE))
                .body("type", equalTo("Person"))
                .body("preferredUsername", equalTo("alice"))
                .body("name", equalTo("Alice"))
                .body("summary", equalTo("<p>Hi</p>"))
                .body("url", equalTo(TestActors.ALICE_URL.toString()))
                .body("icon.type", equalTo("Image"))
                .body("icon.url", equalTo(TestActors.BASE + "/alice.png"))
                .body("inbox", equalTo(TestActors.ALICE + "/inbox"))
                .body("outbox", equalTo(TestActors.ALICE + "/outbox"))
                .body("followers", equalTo(TestActors.ALICE + "/followers"))
                .body("following", equalTo(TestActors.ALICE + "/following"))
                .body("endpoints.sharedInbox", equalTo(TestActors.BASE + "/inbox"))
                .body("publicKey.id", equalTo(TestActors.ALICE + "#main-key"))
                .body("publicKey.owner", equalTo(TestActors.ALICE))
                .body("manuallyApprovesFollowers", equalTo(false))
                .body("discoverable", equalTo(true))
                .body("published", notNullValue());
    }

    @Test
    void actorJsonForLdJsonProfile() {
        given().accept(TestActors.LD).when().get("/users/alice")
                .then().statusCode(200)
                .header("Content-Type", "application/activity+json; charset=utf-8")
                .body("id", equalTo(TestActors.ALICE));
    }

    @Test
    void htmlAcceptRedirectsToUrl() {
        given().redirects().follow(false).accept("text/html,*/*;q=0.8").when().get("/users/alice")
                .then().statusCode(302)
                .header("Location", TestActors.ALICE_URL.toString())
                .header("Vary", "Accept");
    }

    @Test
    void htmlAcceptWithoutUrlIs406() {
        given().redirects().follow(false).accept("text/html").when().get("/users/bob")
                .then().statusCode(406);
        // */* alone is what browsers send: it does not select the JSON document
        given().redirects().follow(false).accept("*/*").when().get("/users/bob")
                .then().statusCode(406);
    }

    @Test
    void unknownUserIs404() {
        given().accept(TestActors.AP).when().get("/users/nobody").then().statusCode(404);
    }

    @Test
    void publicKeyMatchesStoredPem() {
        String pem = given().accept(TestActors.AP).when().get("/users/alice")
                .then().statusCode(200).extract().path("publicKey.publicKeyPem");
        String stored = stores.actors().keyPair(URI.create(TestActors.ALICE)).orElseThrow().publicKeyPem();
        assertEquals(stored, pem);
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"), pem);
    }

    @Test
    void etagRoundTrip() {
        Response first = given().accept(TestActors.AP).when().get("/users/alice");
        first.then().statusCode(200).header("Cache-Control", "max-age=300");
        String etag = first.header("ETag");
        assertTrue(etag != null && etag.matches("\"[0-9a-f]{64}\""), etag);
        given().accept(TestActors.AP).header("If-None-Match", etag).when().get("/users/alice")
                .then().statusCode(304).header("ETag", etag).body(emptyString());
        given().accept(TestActors.AP).header("If-None-Match", "\"other\"").when().get("/users/alice")
                .then().statusCode(200);
    }

    @Test
    void instanceActorServedAtActorPath() {
        given().accept(TestActors.AP).when().get("/users/instance")
                .then().statusCode(200)
                .body("type", equalTo("Application"))
                .body("id", equalTo(TestActors.BASE + "/users/instance"))
                .body("preferredUsername", equalTo("instance"))
                .body("publicKey.id", equalTo(TestActors.BASE + "/users/instance#main-key"));
    }

    @Test
    void usernameWithSpaceIsLookedUpExactly() {
        given().urlEncodingEnabled(false).accept(TestActors.AP).when().get("/users/a%20b")
                .then().statusCode(200)
                .body("id", equalTo(TestActors.BASE + "/users/a%20b"))
                .body("preferredUsername", equalTo("a b"));
        given().urlEncodingEnabled(false).accept(TestActors.AP).when().get("/users/A%20B")
                .then().statusCode(404);
        given().accept(TestActors.AP).when().get("/users/Alice").then().statusCode(404);
    }

    @Test
    void putIsNotAllowed() {
        given().accept(TestActors.AP).when().put("/users/alice").then().statusCode(405);
    }

    @Test
    void nodeInfoCountsLocalActorsWithoutInstanceActor() {
        given().when().get("/nodeinfo/2.1")
                .then().statusCode(200)
                .body("protocols", hasItem("activitypub"))
                .body("usage.users.total", equalTo(3));
    }
}

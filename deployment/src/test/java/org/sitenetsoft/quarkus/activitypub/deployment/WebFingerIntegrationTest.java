package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class WebFingerIntegrationTest {

    static final String PROFILE_PAGE = "http://webfinger.net/rel/profile-page";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(TestActors.class))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE);

    @BeforeAll
    static void parsers() {
        TestActors.jsonParsers();
    }

    @Test
    void acctResolvesToSelfLink() {
        given().queryParam("resource", "acct:alice@example.org").when().get("/.well-known/webfinger")
                .then().statusCode(200)
                .body("subject", equalTo("acct:alice@example.org"))
                .body("aliases", contains(TestActors.ALICE, TestActors.ALICE_URL.toString()))
                .body("links.rel", contains("self", PROFILE_PAGE))
                .body("links[0].type", equalTo("application/activity+json"))
                .body("links[0].href", equalTo(TestActors.ALICE))
                .body("links[1].type", equalTo("text/html"))
                .body("links[1].href", equalTo(TestActors.ALICE_URL.toString()));
    }

    @Test
    void actorWithoutUrlHasNoProfilePage() {
        given().queryParam("resource", "acct:bob@EXAMPLE.org").when().get("/.well-known/webfinger")
                .then().statusCode(200)
                .body("subject", equalTo("acct:bob@example.org"))
                .body("aliases", contains(TestActors.BOB))
                .body("links.rel", contains("self"));
    }

    @Test
    void actorUriResolvesToo() {
        given().queryParam("resource", TestActors.ALICE).when().get("/.well-known/webfinger")
                .then().statusCode(200)
                .body("subject", equalTo("acct:alice@example.org"))
                .body("links[0].href", equalTo(TestActors.ALICE));
    }

    @Test
    void foreignHostIsEmpty() {
        given().queryParam("resource", "acct:alice@other.example").when().get("/.well-known/webfinger")
                .then().statusCode(404);
        given().queryParam("resource", "https://other.example/users/alice").when().get("/.well-known/webfinger")
                .then().statusCode(404);
    }

    @Test
    void unknownUserIs404() {
        given().queryParam("resource", "acct:nobody@example.org").when().get("/.well-known/webfinger")
                .then().statusCode(404);
    }

    @Test
    void acctUserIsPercentDecodedAndExact() {
        given().queryParam("resource", "acct:a%20b@example.org").when().get("/.well-known/webfinger")
                .then().statusCode(200)
                .body("subject", equalTo("acct:a%20b@example.org"))
                .body("links[0].href", equalTo(TestActors.BASE + "/users/a%20b"));
        given().queryParam("resource", "acct:A%20B@example.org").when().get("/.well-known/webfinger")
                .then().statusCode(404);
    }
}

package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * {@code authorized-fetch=true}: every ActivityPub GET needs a valid signature (one configuration per QuarkusUnitTest,
 * hence its own class).
 */
public class ActorRouteAuthorizedFetchTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(TestActors.class))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE)
            .overrideConfigKey("quarkus.activitypub.authorized-fetch", "true");

    @BeforeAll
    static void parsers() {
        TestActors.jsonParsers();
    }

    @Test
    void authorizedFetchRejectsUnsigned() {
        given().accept(TestActors.AP).when().get("/users/alice").then().statusCode(401);
        given().accept(TestActors.AP).when().get("/users/alice/outbox").then().statusCode(401);
        given().accept(TestActors.AP).when().get("/objects/anything").then().statusCode(401);
    }

    @Test
    void invalidSignatureIs401() {
        given().accept(TestActors.AP).header("Signature", "keyId=\"https://remote.example/k\",signature=\"AAAA\"")
                .when().get("/users/alice").then().statusCode(401);
    }

    /** Remote servers fetch the instance actor to verify our signed fetches, so it stays readable (as in Mastodon). */
    @Test
    void instanceActorStaysReadable() {
        given().accept(TestActors.AP).when().get("/users/instance")
                .then().statusCode(200).body("type", equalTo("Application"));
    }
}

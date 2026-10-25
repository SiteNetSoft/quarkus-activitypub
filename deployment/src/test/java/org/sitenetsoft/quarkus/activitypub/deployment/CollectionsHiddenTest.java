package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/** {@code followers-visibility=hidden} (one configuration per QuarkusUnitTest, hence its own class). */
public class CollectionsHiddenTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(TestActors.class))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE)
            .overrideConfigKey("quarkus.activitypub.collections.followers-visibility", "hidden");

    @Test
    void followersHiddenIs404() {
        given().accept(TestActors.AP).when().get("/users/bob/followers").then().statusCode(404);
        given().accept(TestActors.AP).queryParam("page", 1).when().get("/users/bob/followers").then().statusCode(404);
        given().accept(TestActors.AP).when().get("/users/bob/following").then().statusCode(404);
        // the outbox is not affected
        given().accept(TestActors.AP).when().get("/users/bob/outbox").then().statusCode(200);
    }
}

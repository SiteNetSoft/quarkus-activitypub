package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;

/** {@code followers-visibility=count-only} (one configuration per QuarkusUnitTest, hence its own class). */
public class CollectionsCountOnlyTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(TestActors.class))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE)
            .overrideConfigKey("quarkus.activitypub.collections.followers-visibility", "count-only");

    @Inject
    Stores stores;

    @BeforeAll
    static void parsers() {
        TestActors.jsonParsers();
    }

    @Test
    void followersCountOnlyHidesItems() {
        stores.followers().add(URI.create(TestActors.BOB), URI.create("https://remote.example/users/carol"),
                URI.create("https://remote.example/inbox"), FollowState.ACCEPTED, URI.create("https://remote.example/f/1"));
        given().accept(TestActors.AP).when().get("/users/bob/followers")
                .then().statusCode(200)
                .body("type", equalTo("OrderedCollection"))
                .body("totalItems", equalTo(1))
                .body("first", nullValue());
        given().accept(TestActors.AP).queryParam("page", 1).when().get("/users/bob/followers").then().statusCode(403);
        given().accept(TestActors.AP).queryParam("page", 1).when().get("/users/bob/following").then().statusCode(403);
    }
}

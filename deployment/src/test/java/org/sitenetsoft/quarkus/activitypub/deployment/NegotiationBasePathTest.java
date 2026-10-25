package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.server.ActivityPubContentNegotiation;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.ext.web.Router;

/**
 * A {@code base-url} with a path: the negotiation helper keeps the base path in the id it looks up (one configuration
 * per QuarkusUnitTest, hence its own class).
 */
public class NegotiationBasePathTest {

    static final String BASE = "https://example.org/social";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(TestActors.class, CustomRoutes.class))
            .overrideConfigKey("quarkus.activitypub.base-url", BASE);

    @ApplicationScoped
    public static class CustomRoutes {

        @Inject
        ActivityPubContentNegotiation negotiation;

        void register(@Observes Router router) {
            router.get("/posts/:n").handler(negotiation)
                    .handler(ctx -> ctx.response().putHeader("Content-Type", "text/html").end("<p>html</p>"));
        }
    }

    @Inject
    Stores stores;

    @Inject
    ActivityPubUrls urls;

    @BeforeAll
    static void parsers() {
        TestActors.jsonParsers();
    }

    static Note publicNote(String id) {
        Note note = new Note();
        note.id(URI.create(id));
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        return note;
    }

    @Test
    void idKeepsTheBasePath() {
        stores.objects().putLocal(publicNote(BASE + "/posts/1"), true);
        given().accept(TestActors.AP).when().get("/posts/1")
                .then().statusCode(200).body("id", equalTo(BASE + "/posts/1"));
    }

    @Test
    void idWithoutTheBasePathIsNotServed() {
        stores.objects().putLocal(publicNote("https://example.org/posts/2"), true);
        given().accept(TestActors.AP).when().get("/posts/2")
                .then().statusCode(200).body(equalTo("<p>html</p>"));
    }

    @Test
    void idForPathKeepsBaseAndRejectsNetworkPaths() {
        assertEquals(Optional.of(URI.create(BASE + "/posts/1")), urls.idForPath("/posts/1"));
        assertEquals(Optional.empty(), urls.idForPath("//other.example/x"));
        assertEquals(Optional.empty(), urls.idForPath("posts/1"));
        assertEquals(Optional.empty(), urls.idForPath(null));
    }
}

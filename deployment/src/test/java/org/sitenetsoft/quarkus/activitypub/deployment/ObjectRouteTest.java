package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.server.ActivityPubContentNegotiation;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;
import io.vertx.ext.web.Router;

public class ObjectRouteTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestActors.class, TestSignatures.class, TestSignatures.RemoteKeys.class,
                            CustomRoutes.class))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE);

    /** An app route with its own ids, fronted by the negotiation helper. */
    @ApplicationScoped
    public static class CustomRoutes {

        @Inject
        ActivityPubContentNegotiation negotiation;

        void register(@Observes Router router) {
            router.get("/custom/:n").handler(negotiation)
                    .handler(ctx -> ctx.response().putHeader("Content-Type", "text/html").end("<p>html</p>"));
            // a catch-all style mount that also sees "//host/..." request paths
            router.getWithRegex(".*other\\.example/x").handler(negotiation)
                    .handler(ctx -> ctx.response().putHeader("Content-Type", "text/html").end("<p>app</p>"));
        }
    }

    @Inject
    Stores stores;

    @BeforeAll
    static void parsers() {
        TestActors.jsonParsers();
    }

    static Note note(String id, URI... to) {
        Note note = new Note();
        note.id(URI.create(id)).content("hello").attributedTo(ObjectOrLink.of(TestActors.ALICE));
        note.to(java.util.Arrays.stream(to).map(ObjectOrLink::of).toArray(ObjectOrLink[]::new));
        return note;
    }

    @Test
    void publicObjectServed() {
        stores.objects().putLocal(note(TestActors.BASE + "/objects/p1", Audience.PUBLIC), true);
        given().accept(TestActors.AP).when().get("/objects/p1")
                .then().statusCode(200)
                .header("Content-Type", "application/activity+json; charset=utf-8")
                .header("Vary", "Accept")
                .body("id", equalTo(TestActors.BASE + "/objects/p1"))
                .body("type", equalTo("Note"))
                .body("content", equalTo("hello"))
                .header("Cache-Control", nullValue());

        Create create = new Create();
        create.actor(ObjectOrLink.of(TestActors.ALICE)).id(URI.create(TestActors.BASE + "/activities/a1"));
        create.to(ObjectOrLink.of(Audience.PUBLIC));
        stores.objects().putLocal(create, true);
        given().accept(TestActors.AP).when().get("/activities/a1")
                .then().statusCode(200)
                .body("type", equalTo("Create"));
        // an object id is not served on the activities route
        given().accept(TestActors.AP).when().get("/activities/p1").then().statusCode(404);
    }

    @Test
    void tombstoneIs410() {
        String id = TestActors.BASE + "/objects/gone";
        stores.objects().putLocal(note(id, Audience.PUBLIC), true);
        stores.objects().tombstone(URI.create(id));
        given().accept(TestActors.AP).when().get("/objects/gone")
                .then().statusCode(410)
                .header("Content-Type", "application/activity+json; charset=utf-8")
                .body("type", equalTo("Tombstone"))
                .body("id", equalTo(id))
                .body("formerType", equalTo("Note"));
    }

    @Test
    void privateObjectUnsignedIs404() {
        stores.objects().putLocal(note(TestActors.BASE + "/objects/secret", URI.create(TestSignatures.CAROL)), false);
        given().accept(TestActors.AP).when().get("/objects/secret").then().statusCode(404);
    }

    @Test
    void privateObjectSignedByAudienceIs200() {
        stores.objects().putLocal(note(TestActors.BASE + "/objects/dm", URI.create(TestSignatures.CAROL)), false);
        TestSignatures.signedGet(TestSignatures.CAROL_KEY, "/objects/dm").when().get("/objects/dm")
                .then().statusCode(200).body("id", equalTo(TestActors.BASE + "/objects/dm"))
                // varies by signer: never stored by a shared cache
                .header("Cache-Control", "private")
                .header("Vary", "Accept, Signature");
        // a valid signature from an actor outside the audience does not reveal it
        TestSignatures.signedGet(TestSignatures.DAVE_KEY, "/objects/dm").when().get("/objects/dm").then().statusCode(404);
    }

    @Test
    void missingIs404() {
        given().accept(TestActors.AP).when().get("/objects/nothing").then().statusCode(404);
    }

    @Test
    void negotiationHelperServesJsonAndPassesHtml() {
        stores.objects().putLocal(note(TestActors.BASE + "/custom/1", Audience.PUBLIC), true);
        given().accept(TestActors.AP).when().get("/custom/1")
                .then().statusCode(200)
                .header("Content-Type", "application/activity+json; charset=utf-8")
                .body("id", equalTo(TestActors.BASE + "/custom/1"));
        given().accept("text/html").when().get("/custom/1")
                .then().statusCode(200).body(equalTo("<p>html</p>"));
        // no stored object: the app's own route answers
        given().accept(TestActors.AP).when().get("/custom/2")
                .then().statusCode(200).body(equalTo("<p>html</p>"));
    }

    @Test
    void doubleSlashPathIsNotLookedUp() {
        // a cached public remote object must not be served under this origin through "//other.example/x"
        Note remote = note("https://other.example/x", Audience.PUBLIC);
        stores.objects().putRemote(remote, URI.create("https://other.example/inbox"));
        given().urlEncodingEnabled(false).accept(TestActors.AP).when().get("//other.example/x")
                .then().statusCode(200).body(equalTo("<p>app</p>"));
    }
}

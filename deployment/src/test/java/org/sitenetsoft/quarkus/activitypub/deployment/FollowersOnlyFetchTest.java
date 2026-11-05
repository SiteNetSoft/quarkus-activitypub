package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;

public class FollowersOnlyFetchTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> org.jboss.shrinkwrap.api.ShrinkWrap
                    .create(org.jboss.shrinkwrap.api.spec.JavaArchive.class)
                    .addClasses(TestActors.class, TestSignatures.class, TestSignatures.RemoteKeys.class))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE);

    static final URI ALICE_FOLLOWERS = URI.create(TestActors.ALICE + "/followers");
    static final URI CAROL = URI.create(TestSignatures.CAROL);
    static final URI DAVE = URI.create("https://remote.example/users/dave");
    static final URI INBOX = URI.create("https://remote.example/inbox");

    @Inject
    Stores stores;

    @BeforeAll
    static void parsers() {
        TestActors.jsonParsers();
    }

    private void followersNote(String id, URI... to) {
        Note note = new Note();
        note.id(URI.create(TestActors.BASE + "/objects/" + id)).content("for followers")
                .attributedTo(ObjectOrLink.of(TestActors.ALICE));
        note.to(java.util.Arrays.stream(to).map(ObjectOrLink::of).toArray(ObjectOrLink[]::new));
        stores.objects().putLocal(note, false);
    }

    @Test
    void followerCanFetchFollowersOnlyNote() {
        followersNote("fo1", ALICE_FOLLOWERS);
        stores.followers().add(URI.create(TestActors.ALICE), CAROL, INBOX, FollowState.ACCEPTED,
                URI.create(CAROL + "/follows/1"));
        TestSignatures.signedGet(TestSignatures.CAROL_KEY, "/objects/fo1").when().get("/objects/fo1")
                .then().statusCode(200)
                .header("Cache-Control", "private")
                .body("id", equalTo(TestActors.BASE + "/objects/fo1"));
    }

    @Test
    void pendingFollowerGets404() {
        followersNote("fo2", ALICE_FOLLOWERS);
        stores.followers().add(URI.create(TestActors.ALICE), DAVE, INBOX, FollowState.PENDING,
                URI.create(DAVE + "/follows/1"));
        TestSignatures.signedGet(TestSignatures.DAVE_KEY, "/objects/fo2").when().get("/objects/fo2")
                .then().statusCode(404);
    }

    @Test
    void nonFollowerGets404() {
        followersNote("fo3", ALICE_FOLLOWERS);
        stores.followers().remove(URI.create(TestActors.ALICE), CAROL);
        TestSignatures.signedGet(TestSignatures.CAROL_KEY, "/objects/fo3").when().get("/objects/fo3")
                .then().statusCode(404);
    }

    @Test
    void unsignedGets404() {
        followersNote("fo4", ALICE_FOLLOWERS);
        given().accept(TestActors.AP).when().get("/objects/fo4").then().statusCode(404);
    }

    @Test
    void directAddresseeStillWorks() {
        followersNote("fo5", CAROL);
        stores.followers().remove(URI.create(TestActors.ALICE), CAROL);
        TestSignatures.signedGet(TestSignatures.CAROL_KEY, "/objects/fo5").when().get("/objects/fo5")
                .then().statusCode(200).header("Cache-Control", "private");
    }
}

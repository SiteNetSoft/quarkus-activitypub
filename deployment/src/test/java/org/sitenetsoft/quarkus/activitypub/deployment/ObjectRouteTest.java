package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.httpsignatures.Algorithm;
import org.sitenetsoft.httpsignatures.Headers;
import org.sitenetsoft.httpsignatures.HttpMessageView;
import org.sitenetsoft.httpsignatures.HttpSigner;
import org.sitenetsoft.httpsignatures.KeyResolver;
import org.sitenetsoft.httpsignatures.SignatureSpec;
import org.sitenetsoft.httpsignatures.SigningKey;
import org.sitenetsoft.httpsignatures.VerificationKey;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeys;
import org.sitenetsoft.quarkus.activitypub.server.ActivityPubContentNegotiation;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.vertx.ext.web.Router;

public class ObjectRouteTest {

    static final String CAROL = "https://remote.example/users/carol";
    static final String CAROL_KEY = CAROL + "#main-key";
    static final String DAVE_KEY = "https://remote.example/users/dave#main-key";
    static final ActorKeyPair REMOTE_KEYS = ActorKeys.generateRsa2048();

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestActors.class, CustomRoutes.class, RemoteKeys.class))
            .overrideConfigKey("quarkus.activitypub.base-url", TestActors.BASE);

    /** An app route with its own ids, fronted by the negotiation helper. */
    @ApplicationScoped
    public static class CustomRoutes {

        @Inject
        ActivityPubContentNegotiation negotiation;

        void register(@Observes Router router) {
            router.get("/custom/:n").handler(negotiation)
                    .handler(ctx -> ctx.response().putHeader("Content-Type", "text/html").end("<p>html</p>"));
        }
    }

    /** Resolves the remote test keys (carol and dave share one key pair). */
    @ApplicationScoped
    public static class RemoteKeys implements KeyResolver {
        @Override
        public Optional<VerificationKey> resolve(String keyId) {
            if (CAROL_KEY.equals(keyId) || DAVE_KEY.equals(keyId)) {
                return Optional.of(new VerificationKey(keyId, Optional.of(Algorithm.RSA_SHA256),
                        ActorKeys.publicKey(REMOTE_KEYS)));
            }
            return Optional.empty();
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

    /** A GET signed with Cavage {@code rsa-sha256} by {@code keyId}. */
    static RequestSpecification signedGet(String keyId, String path) {
        URI uri = URI.create("http://localhost:" + RestAssured.port + path);
        String host = uri.getHost() + ":" + uri.getPort();
        HttpMessageView msg = HttpMessageView.of("GET", uri, Headers.of(Map.of("Host", List.of(host))), null);
        PrivateKey key = ActorKeys.privateKey(REMOTE_KEYS);
        var signed = new HttpSigner().sign(msg, SignatureSpec.cavage(keyId, Algorithm.RSA_SHA256, Instant.now(), false),
                new SigningKey(keyId, Algorithm.RSA_SHA256, key));
        Map<String, String> headers = new LinkedHashMap<>(signed.headers());
        RequestSpecification spec = given().accept(TestActors.AP).header("Host", host);
        headers.forEach(spec::header);
        return spec;
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
                .body("content", equalTo("hello"));

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
        stores.objects().putLocal(note(TestActors.BASE + "/objects/secret", URI.create(CAROL)), false);
        given().accept(TestActors.AP).when().get("/objects/secret").then().statusCode(404);
    }

    @Test
    void privateObjectSignedByAudienceIs200() {
        stores.objects().putLocal(note(TestActors.BASE + "/objects/dm", URI.create(CAROL)), false);
        signedGet(CAROL_KEY, "/objects/dm").when().get("/objects/dm")
                .then().statusCode(200).body("id", equalTo(TestActors.BASE + "/objects/dm"));
        // a valid signature from an actor outside the audience does not reveal it
        signedGet(DAVE_KEY, "/objects/dm").when().get("/objects/dm").then().statusCode(404);
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
}

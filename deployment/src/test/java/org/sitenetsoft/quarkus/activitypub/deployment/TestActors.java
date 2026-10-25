package org.sitenetsoft.quarkus.activitypub.deployment;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;

import io.quarkus.runtime.StartupEvent;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;

/**
 * Registers the test actors at startup: {@code alice} (with a profile page and an avatar), {@code bob} (bare) and
 * {@code "a b"} (a username that needs percent-encoding).
 */
@ApplicationScoped
public class TestActors {

    static final String BASE = "https://example.org";
    static final String ALICE = BASE + "/users/alice";
    static final String BOB = BASE + "/users/bob";
    static final URI ALICE_URL = URI.create(BASE + "/@alice");
    static final String AP = "application/activity+json";
    static final String LD = "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"";

    @Inject
    LocalActors actors;

    void register(@Observes StartupEvent event) {
        actors.register("alice", b -> b.name("Alice").summary("<p>Hi</p>").url(ALICE_URL)
                .icon(URI.create(BASE + "/alice.png")));
        actors.register("bob", b -> {
        });
        actors.register("a b", b -> {
        });
    }

    /** Lets REST Assured parse ActivityPub and JRD bodies as JSON. */
    static void jsonParsers() {
        RestAssured.registerParser(AP, Parser.JSON);
        RestAssured.registerParser("application/jrd+json", Parser.JSON);
    }
}

package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;

import io.quarkus.runtime.StartupEvent;

/** Registers the local actor {@code alice} at startup, for the outbound tests (URLs under {@link LoopbackTests#BASE}). */
@ApplicationScoped
public class AliceActor {

    static final URI ID = URI.create(LoopbackTests.BASE + "/users/alice");
    static final URI FOLLOWERS = URI.create(LoopbackTests.BASE + "/users/alice/followers");

    @Inject
    LocalActors actors;

    void register(@Observes StartupEvent event) {
        actors.register("alice", b -> {
        });
    }

    LocalActor get() {
        return actors.find("alice").orElseThrow();
    }
}

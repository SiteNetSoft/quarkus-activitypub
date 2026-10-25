package org.acme.demo;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;

import io.quarkus.runtime.Startup;

/** Registers the sample's actor {@code alice} at startup, unless it exists. */
@Startup
@ApplicationScoped
public class DemoActors {

    @Inject
    LocalActors actors;

    @PostConstruct
    void registerAlice() {
        if (actors.find("alice").isEmpty()) {
            actors.register("alice", a -> a.name("Alice").summary("The quarkus-activitypub sample actor"));
        }
    }
}

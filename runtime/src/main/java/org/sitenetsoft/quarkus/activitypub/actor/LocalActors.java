package org.sitenetsoft.quarkus.activitypub.actor;

import java.util.Optional;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * Convenience over {@code ActorStore} for provisioning local actors.
 */
@ApplicationScoped
public class LocalActors {

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    /**
     * Creates a local actor with a fresh RSA 2048 key pair. The id is always {@code ActivityPubUrls.actor(username)}.
     *
     * @throws IllegalStateException when the username is taken
     */
    public LocalActor register(String username, Consumer<LocalActor.Builder> customizer) {
        if (stores.actors().findByUsername(username).isPresent()) {
            throw new IllegalStateException("Local actor '" + username + "' already exists");
        }
        LocalActor.Builder builder = LocalActor.builder(username);
        customizer.accept(builder);
        LocalActor actor = builder.id(urls.actor(username)).build();
        stores.actors().save(actor, ActorKeys.generateRsa2048());
        return actor;
    }

    public Optional<LocalActor> find(String username) {
        return stores.actors().findByUsername(username);
    }
}

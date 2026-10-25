package org.sitenetsoft.quarkus.activitypub.actor;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.runtime.StartupEvent;

/**
 * The instance actor: an {@code Application} named {@code quarkus.activitypub.instance-actor.username} that signs
 * outbound fetches. Created in {@code ActorStore} at startup when {@code instance-actor.enabled}; its key pair is
 * generated once and kept by the store.
 */
@ApplicationScoped
public class InstanceActor {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    ActivityPubConfig config;

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    private volatile LocalActor actor;

    void onStart(@Observes StartupEvent event) {
        if (config.instanceActor().enabled()) {
            get();
        }
    }

    /**
     * The instance actor, created on first use.
     *
     * @throws IllegalStateException when {@code quarkus.activitypub.instance-actor.enabled=false}
     */
    public LocalActor get() {
        if (!config.instanceActor().enabled()) {
            throw new IllegalStateException("The instance actor is disabled: quarkus.activitypub.instance-actor.enabled=false");
        }
        LocalActor current = actor;
        if (current == null) {
            synchronized (this) {
                current = actor;
                if (current == null) {
                    current = findOrCreate();
                    actor = current;
                }
            }
        }
        return current;
    }

    /** The instance actor, empty when it is disabled. */
    public Optional<LocalActor> ifEnabled() {
        return config.instanceActor().enabled() ? Optional.of(get()) : Optional.empty();
    }

    private LocalActor findOrCreate() {
        String username = config.instanceActor().username();
        Optional<LocalActor> existing = stores.actors().findByUsername(username);
        LocalActor found = existing.orElseGet(() -> LocalActor.builder(username).type("Application")
                .id(urls.actor(username)).manuallyApprovesFollowers(true).discoverable(false).build());
        if (existing.isEmpty() || stores.actors().keyPair(found.id()).isEmpty()) {
            stores.actors().save(found, ActorKeys.generateRsa2048());
            LOG.infof("Created the instance actor %s", found.id());
        }
        return found;
    }
}

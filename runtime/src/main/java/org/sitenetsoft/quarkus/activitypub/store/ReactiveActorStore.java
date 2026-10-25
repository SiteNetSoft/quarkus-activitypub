package org.sitenetsoft.quarkus.activitypub.store;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

import io.smallrye.mutiny.Uni;

/**
 * Reactive twin of {@link ActorStore}.
 */
public interface ReactiveActorStore {

    Uni<Optional<LocalActor>> findByUsername(String username);

    Uni<Optional<LocalActor>> findById(URI id);

    Uni<Long> countLocalActors();

    Uni<List<LocalActor>> list(int limit);

    Uni<Optional<ActorKeyPair>> keyPair(URI actorId);

    Uni<Void> save(LocalActor actor, ActorKeyPair keys);

    Uni<Void> delete(URI actorId);
}

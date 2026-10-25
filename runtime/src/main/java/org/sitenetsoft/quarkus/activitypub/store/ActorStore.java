package org.sitenetsoft.quarkus.activitypub.store;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

/**
 * Local actors and their keys. Blocking twin of {@link ReactiveActorStore}; provide either.
 */
public interface ActorStore {

    /** Exact-match lookup. */
    Optional<LocalActor> findByUsername(String username);

    Optional<LocalActor> findById(URI id);

    long countLocalActors();

    /** Up to {@code limit} local actors, ordered by username. */
    List<LocalActor> list(int limit);

    Optional<ActorKeyPair> keyPair(URI actorId);

    /** Creates or updates (by {@link LocalActor#id()}). */
    void save(LocalActor actor, ActorKeyPair keys);

    void delete(URI actorId);
}

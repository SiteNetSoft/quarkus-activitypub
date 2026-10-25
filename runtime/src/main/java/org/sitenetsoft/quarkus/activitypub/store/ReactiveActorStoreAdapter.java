package org.sitenetsoft.quarkus.activitypub.store;

import static org.sitenetsoft.quarkus.activitypub.store.Bridge.offload;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

import io.smallrye.mutiny.Uni;

/** {@link ReactiveActorStore} over an {@link ActorStore}, run on worker threads. */
final class ReactiveActorStoreAdapter implements ReactiveActorStore {

    private final ActorStore d;

    ReactiveActorStoreAdapter(ActorStore delegate) {
        this.d = delegate;
    }

    @Override
    public Uni<Optional<LocalActor>> findByUsername(String username) {
        return offload(() -> d.findByUsername(username));
    }

    @Override
    public Uni<Optional<LocalActor>> findById(URI id) {
        return offload(() -> d.findById(id));
    }

    @Override
    public Uni<Long> countLocalActors() {
        return offload(d::countLocalActors);
    }

    @Override
    public Uni<List<LocalActor>> list(int limit) {
        return offload(() -> d.list(limit));
    }

    @Override
    public Uni<Optional<ActorKeyPair>> keyPair(URI actorId) {
        return offload(() -> d.keyPair(actorId));
    }

    @Override
    public Uni<Void> save(LocalActor actor, ActorKeyPair keys) {
        return offload(() -> d.save(actor, keys));
    }

    @Override
    public Uni<Void> delete(URI actorId) {
        return offload(() -> d.delete(actorId));
    }
}

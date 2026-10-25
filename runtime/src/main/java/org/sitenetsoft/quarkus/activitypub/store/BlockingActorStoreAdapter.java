package org.sitenetsoft.quarkus.activitypub.store;

import static org.sitenetsoft.quarkus.activitypub.store.Bridge.await;

import java.net.URI;
import java.util.Optional;

import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

/** {@link ActorStore} over a {@link ReactiveActorStore}. */
final class BlockingActorStoreAdapter implements ActorStore {

    private final ReactiveActorStore d;

    BlockingActorStoreAdapter(ReactiveActorStore delegate) {
        this.d = delegate;
    }

    @Override
    public Optional<LocalActor> findByUsername(String username) {
        return await(d.findByUsername(username));
    }

    @Override
    public Optional<LocalActor> findById(URI id) {
        return await(d.findById(id));
    }

    @Override
    public long countLocalActors() {
        return await(d.countLocalActors());
    }

    @Override
    public Optional<ActorKeyPair> keyPair(URI actorId) {
        return await(d.keyPair(actorId));
    }

    @Override
    public void save(LocalActor actor, ActorKeyPair keys) {
        await(d.save(actor, keys));
    }

    @Override
    public void delete(URI actorId) {
        await(d.delete(actorId));
    }
}

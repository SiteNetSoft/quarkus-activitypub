package org.sitenetsoft.quarkus.activitypub.store.memory;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.store.ActorStore;

import io.quarkus.arc.DefaultBean;

/** Default {@link ActorStore}: process memory, lost on restart. */
@DefaultBean
@ApplicationScoped
public class InMemoryActorStore implements ActorStore {

    private record Entry(LocalActor actor, ActorKeyPair keys) {
    }

    private final Map<URI, Entry> byId = new ConcurrentHashMap<>();
    private final Map<String, URI> idByUsername = new ConcurrentHashMap<>();

    @PostConstruct
    void warnIfProduction() {
        ProductionWarning.warnIfProduction("ActorStore");
    }

    @Override
    public Optional<LocalActor> findByUsername(String username) {
        return Optional.ofNullable(idByUsername.get(username)).flatMap(this::findById);
    }

    @Override
    public Optional<LocalActor> findById(URI id) {
        return Optional.ofNullable(byId.get(id)).map(Entry::actor);
    }

    @Override
    public long countLocalActors() {
        return byId.size();
    }

    @Override
    public List<LocalActor> list(int limit) {
        return byId.values().stream().map(Entry::actor).sorted(Comparator.comparing(LocalActor::username))
                .limit(Math.max(limit, 0)).toList();
    }

    @Override
    public Optional<ActorKeyPair> keyPair(URI actorId) {
        return Optional.ofNullable(byId.get(actorId)).map(Entry::keys);
    }

    @Override
    public synchronized void save(LocalActor actor, ActorKeyPair keys) {
        Entry previous = byId.put(actor.id(), new Entry(actor, keys));
        if (previous != null && !previous.actor().username().equals(actor.username())) {
            idByUsername.remove(previous.actor().username(), actor.id());
        }
        idByUsername.put(actor.username(), actor.id());
    }

    @Override
    public synchronized void delete(URI actorId) {
        Entry removed = byId.remove(actorId);
        if (removed != null) {
            idByUsername.remove(removed.actor().username(), actorId);
        }
    }
}

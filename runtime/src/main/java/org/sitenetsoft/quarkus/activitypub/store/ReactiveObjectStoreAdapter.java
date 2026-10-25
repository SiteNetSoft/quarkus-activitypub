package org.sitenetsoft.quarkus.activitypub.store;

import static org.sitenetsoft.quarkus.activitypub.store.Bridge.offload;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;

import io.smallrye.mutiny.Uni;

/** {@link ReactiveObjectStore} over an {@link ObjectStore}, run on worker threads. */
final class ReactiveObjectStoreAdapter implements ReactiveObjectStore {

    private final ObjectStore d;

    ReactiveObjectStoreAdapter(ObjectStore delegate) {
        this.d = delegate;
    }

    @Override
    public Uni<Void> putLocal(ASObject object, boolean isPublic) {
        return offload(() -> d.putLocal(object, isPublic));
    }

    @Override
    public Uni<Void> putRemote(ASObject object, URI receivedFrom) {
        return offload(() -> d.putRemote(object, receivedFrom));
    }

    @Override
    public Uni<Optional<ASObject>> find(URI id) {
        return offload(() -> d.find(id));
    }

    @Override
    public Uni<Void> tombstone(URI id) {
        return offload(() -> d.tombstone(id));
    }

    @Override
    public Uni<Void> deleteByActor(URI actorId) {
        return offload(() -> d.deleteByActor(actorId));
    }

    @Override
    public Uni<Boolean> markSeen(URI activityId, Duration retention) {
        return offload(() -> d.markSeen(activityId, retention));
    }

    @Override
    public Uni<Void> forgetSeen(URI activityId) {
        return offload(() -> d.forgetSeen(activityId));
    }

    @Override
    public Uni<Page<Activity>> outbox(URI actorId, int page, int size) {
        return offload(() -> d.outbox(actorId, page, size));
    }

    @Override
    public Uni<Long> outboxCount(URI actorId) {
        return offload(() -> d.outboxCount(actorId));
    }
}

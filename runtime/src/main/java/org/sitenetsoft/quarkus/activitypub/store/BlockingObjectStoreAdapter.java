package org.sitenetsoft.quarkus.activitypub.store;

import static org.sitenetsoft.quarkus.activitypub.store.Bridge.await;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;

/** {@link ObjectStore} over a {@link ReactiveObjectStore}. */
final class BlockingObjectStoreAdapter implements ObjectStore {

    private final ReactiveObjectStore d;

    BlockingObjectStoreAdapter(ReactiveObjectStore delegate) {
        this.d = delegate;
    }

    @Override
    public void putLocal(ASObject object, boolean isPublic) {
        await(d.putLocal(object, isPublic));
    }

    @Override
    public void putRemote(ASObject object, URI receivedFrom) {
        await(d.putRemote(object, receivedFrom));
    }

    @Override
    public Optional<ASObject> find(URI id) {
        return await(d.find(id));
    }

    @Override
    public void tombstone(URI id) {
        await(d.tombstone(id));
    }

    @Override
    public void deleteByActor(URI actorId) {
        await(d.deleteByActor(actorId));
    }

    @Override
    public boolean markSeen(URI activityId, Duration retention) {
        return await(d.markSeen(activityId, retention));
    }

    @Override
    public Page<Activity> outbox(URI actorId, int page, int size) {
        return await(d.outbox(actorId, page, size));
    }

    @Override
    public long outboxCount(URI actorId) {
        return await(d.outboxCount(actorId));
    }
}

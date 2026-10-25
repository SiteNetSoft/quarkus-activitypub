package org.sitenetsoft.quarkus.activitypub.store;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;

import io.smallrye.mutiny.Uni;

/**
 * Reactive twin of {@link ObjectStore}.
 */
public interface ReactiveObjectStore {

    Uni<Void> putLocal(ASObject object, boolean isPublic);

    Uni<Void> putRemote(ASObject object, URI receivedFrom);

    Uni<Optional<ASObject>> find(URI id);

    Uni<Void> tombstone(URI id);

    Uni<Void> deleteByActor(URI actorId);

    Uni<Boolean> markSeen(URI activityId, Duration retention);

    Uni<Page<Activity>> outbox(URI actorId, int page, int size);

    Uni<Long> outboxCount(URI actorId);
}

package org.sitenetsoft.quarkus.activitypub.store;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;

/**
 * Local objects and activities, kept remote objects and inbound de-duplication. Blocking twin of
 * {@link ReactiveObjectStore}; provide either.
 */
public interface ObjectStore {

    /** Stores (or replaces, by id) a local object or activity. */
    void putLocal(ASObject object, boolean isPublic);

    /** Stores (or replaces, by id) a remote object received through the inbox. */
    void putRemote(ASObject object, URI receivedFrom);

    /** A copy of the stored object, a {@code Tombstone} once deleted. */
    Optional<ASObject> find(URI id);

    /** Replaces the object by a {@code Tombstone} with the same id, its former type and the deletion time. */
    void tombstone(URI id);

    /** Removes the remote objects attributed to {@code actorId}. */
    void deleteByActor(URI actorId);

    /** True the first time {@code activityId} is seen within {@code retention}, false for a repeat. */
    boolean markSeen(URI activityId, Duration retention);

    /**
     * Forgets that {@code activityId} was seen, so a redelivery of it is processed again; called when processing it
     * failed. The default does nothing (a failed activity then stays deduplicated).
     */
    default void forgetSeen(URI activityId) {
    }

    /** Public local activities by {@code actorId}, newest first; {@code page} is 1-based. */
    Page<Activity> outbox(URI actorId, int page, int size);

    long outboxCount(URI actorId);
}

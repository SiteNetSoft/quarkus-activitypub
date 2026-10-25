package org.sitenetsoft.quarkus.activitypub;

import java.net.URI;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

/**
 * Publishes activities on behalf of local actors (blocking; do not call on the event loop). Twin of
 * {@link ReactiveActivityPub}, whose default implementation runs this one on a worker thread.
 */
public interface ActivityPub {

    /** Assigns ids where missing, stores the activity, adds it to the outbox and queues its delivery. */
    Activity publish(LocalActor actor, Activity activity);

    /** Wraps {@code object} in a {@code Create} with the object's audience copied, then publishes it. */
    Activity create(LocalActor actor, ASObject object);

    Activity update(LocalActor actor, ASObject object);

    /** Replaces the object by a {@code Tombstone} and publishes a {@code Delete}. */
    Activity delete(LocalActor actor, URI objectId);

    Activity follow(LocalActor actor, URI remoteActor);

    Activity undoFollow(LocalActor actor, URI remoteActor);

    /** Accepts a {@code PENDING} follow request from {@code follower}. */
    void acceptFollow(LocalActor actor, URI follower);

    /** Rejects a {@code PENDING} follow request from {@code follower}. */
    void rejectFollow(LocalActor actor, URI follower);

    Activity announce(LocalActor actor, URI objectId);

    Activity like(LocalActor actor, URI objectId);

    /** Undoes one of the actor's own activities. */
    Activity undo(LocalActor actor, URI activityId);
}

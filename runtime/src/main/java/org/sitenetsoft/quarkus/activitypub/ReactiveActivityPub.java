package org.sitenetsoft.quarkus.activitypub;

import java.net.URI;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

import io.smallrye.mutiny.Uni;

/**
 * Mutiny twin of {@link ActivityPub}.
 */
public interface ReactiveActivityPub {

    Uni<Activity> publish(LocalActor actor, Activity activity);

    Uni<Activity> create(LocalActor actor, ASObject object);

    Uni<Activity> update(LocalActor actor, ASObject object);

    Uni<Activity> delete(LocalActor actor, URI objectId);

    Uni<Activity> follow(LocalActor actor, URI remoteActor);

    Uni<Activity> undoFollow(LocalActor actor, URI remoteActor);

    Uni<Void> acceptFollow(LocalActor actor, URI follower);

    Uni<Void> rejectFollow(LocalActor actor, URI follower);

    Uni<Activity> announce(LocalActor actor, URI objectId);

    Uni<Activity> like(LocalActor actor, URI objectId);

    Uni<Activity> undo(LocalActor actor, URI activityId);
}

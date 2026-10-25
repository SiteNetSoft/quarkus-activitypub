package org.sitenetsoft.quarkus.activitypub.outbound;

import java.net.URI;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.ReactiveActivityPub;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * The Mutiny outbound API: each call runs {@link ActivityPubImpl} on the default worker pool (store writes and remote
 * fetches block). One class cannot implement both interfaces, whose methods differ by return type only.
 */
@ApplicationScoped
public class ReactiveActivityPubImpl implements ReactiveActivityPub {

    @Inject
    ActivityPubImpl delegate;

    private static <T> Uni<T> offload(Supplier<T> work) {
        return Uni.createFrom().item(work).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private static Uni<Void> offloadVoid(Runnable work) {
        return Uni.createFrom().<Void> item(() -> {
            work.run();
            return null;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Activity> publish(LocalActor actor, Activity activity) {
        return offload(() -> delegate.publish(actor, activity));
    }

    @Override
    public Uni<Activity> create(LocalActor actor, ASObject object) {
        return offload(() -> delegate.create(actor, object));
    }

    @Override
    public Uni<Activity> update(LocalActor actor, ASObject object) {
        return offload(() -> delegate.update(actor, object));
    }

    @Override
    public Uni<Activity> delete(LocalActor actor, URI objectId) {
        return offload(() -> delegate.delete(actor, objectId));
    }

    @Override
    public Uni<Activity> follow(LocalActor actor, URI remoteActor) {
        return offload(() -> delegate.follow(actor, remoteActor));
    }

    @Override
    public Uni<Activity> undoFollow(LocalActor actor, URI remoteActor) {
        return offload(() -> delegate.undoFollow(actor, remoteActor));
    }

    @Override
    public Uni<Void> acceptFollow(LocalActor actor, URI follower) {
        return offloadVoid(() -> delegate.acceptFollow(actor, follower));
    }

    @Override
    public Uni<Void> rejectFollow(LocalActor actor, URI follower) {
        return offloadVoid(() -> delegate.rejectFollow(actor, follower));
    }

    @Override
    public Uni<Activity> announce(LocalActor actor, URI objectId) {
        return offload(() -> delegate.announce(actor, objectId));
    }

    @Override
    public Uni<Activity> like(LocalActor actor, URI objectId) {
        return offload(() -> delegate.like(actor, objectId));
    }

    @Override
    public Uni<Activity> undo(LocalActor actor, URI activityId) {
        return offload(() -> delegate.undo(actor, activityId));
    }
}

package org.sitenetsoft.quarkus.activitypub.deployment;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Reject;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

/**
 * Stands in for the outbound API in inbox tests: records what local actors publish instead of delivering it.
 * {@code ActivityPub} only: it cannot also implement {@code ReactiveActivityPub}, whose methods differ by return type
 * alone.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class RecordingActivityPub implements ActivityPub {

    private final Map<URI, List<Activity>> published = new ConcurrentHashMap<>();

    /** What {@code localActor} published, oldest first. */
    public List<Activity> published(URI localActor) {
        return List.copyOf(published.getOrDefault(localActor, List.of()));
    }

    /** What {@code localActor} published of {@code type}, addressed to {@code to}. */
    public <T extends Activity> List<T> published(URI localActor, Class<T> type, URI to) {
        return published(localActor).stream().filter(type::isInstance).map(type::cast)
                .filter(a -> a.audienceUris().contains(to)).toList();
    }

    @Override
    public Activity publish(LocalActor actor, Activity activity) {
        if (activity.id() == null) {
            activity.id(URI.create(actor.id() + "/activities/" + UUID.randomUUID()));
        }
        published.computeIfAbsent(actor.id(), k -> new CopyOnWriteArrayList<>()).add(activity);
        return activity;
    }

    @Override
    public void acceptFollow(LocalActor actor, URI follower) {
        publish(actor, (Activity) new Accept().actor(ObjectOrLink.of(actor.id())).to(ObjectOrLink.of(follower)));
    }

    @Override
    public void rejectFollow(LocalActor actor, URI follower) {
        publish(actor, (Activity) new Reject().actor(ObjectOrLink.of(actor.id())).to(ObjectOrLink.of(follower)));
    }

    @Override
    public Activity create(LocalActor actor, ASObject object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Activity update(LocalActor actor, ASObject object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Activity delete(LocalActor actor, URI objectId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Activity follow(LocalActor actor, URI remoteActor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Activity undoFollow(LocalActor actor, URI remoteActor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Activity announce(LocalActor actor, URI objectId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Activity like(LocalActor actor, URI objectId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Activity undo(LocalActor actor, URI activityId) {
        throw new UnsupportedOperationException();
    }
}

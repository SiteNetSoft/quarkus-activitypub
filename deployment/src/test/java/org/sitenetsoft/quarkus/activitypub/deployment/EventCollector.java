package org.sitenetsoft.quarkus.activitypub.deployment;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.inbox.Inbound;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;

/**
 * Records the {@code @Inbound} events inbox tests receive, with the {@link InboundActivityContext} seen by the
 * observer.
 */
@ApplicationScoped
public class EventCollector {

    /** Makes {@link #failing} throw, for the observer-exception test. */
    static volatile boolean failOnFollow;

    public record Seen(String observer, Activity activity, Optional<LocalActor> recipient, URI signerActor,
            String thread) {
    }

    @Inject
    InboundActivityContext ctx;

    private final List<Seen> seen = new CopyOnWriteArrayList<>();

    void onFollow(@Observes @Inbound Follow follow) {
        record("follow", follow);
    }

    void onCreate(@Observes @Inbound Create create) {
        record("create", create);
    }

    void onAny(@Observes @Inbound Activity activity) {
        record("any", activity);
    }

    void onAsync(@ObservesAsync @Inbound Create create) {
        record("async", create);
    }

    void failing(@Observes @Inbound Follow follow) {
        if (failOnFollow) {
            throw new IllegalStateException("observer failure (test)");
        }
    }

    private void record(String observer, Activity activity) {
        seen.add(new Seen(observer, activity, ctx.recipient(), ctx.signerActor(), Thread.currentThread().getName()));
    }

    /** What {@code observer} saw of the activity {@code id}. */
    public Optional<Seen> find(String observer, URI id) {
        return seen.stream().filter(s -> s.observer().equals(observer) && id.equals(s.activity().id())).findFirst();
    }

    public List<Seen> all() {
        return List.copyOf(seen);
    }
}

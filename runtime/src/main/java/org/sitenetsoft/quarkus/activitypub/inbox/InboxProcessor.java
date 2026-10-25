package org.sitenetsoft.quarkus.activitypub.inbox;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.store.TransactionRunner;

import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * Processes an accepted inbound activity after the {@code 202}, on a worker thread: fills
 * {@link InboundActivityContext} in a fresh request context, runs the matching {@link InboundActivityHandler} and the
 * synchronous {@link Inbound} observers in one store transaction, then notifies the {@code @ObservesAsync} observers
 * with a copy of the activity (the request context stays active for them and ends when they are done). Failures are
 * logged and recorded in {@link InboxLog}, never rethrown.
 */
@ApplicationScoped
public class InboxProcessor {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    @All
    List<InboundActivityHandler<?>> handlers;

    @Inject
    InboundActivityContext context;

    @Inject
    Event<Activity> event;

    @Inject
    TransactionRunner transactions;

    @Inject
    InboxLog log;

    /** Runs {@link #process} on the default worker pool. */
    public void submit(Optional<LocalActor> recipient, byte[] rawJson, Activity activity, String keyId, URI signerActor) {
        Infrastructure.getDefaultWorkerPool().execute(() -> process(recipient, rawJson, activity, keyId, signerActor));
    }

    /** Must run on a worker thread: handlers and synchronous observers may block. */
    public void process(Optional<LocalActor> recipient, byte[] rawJson, Activity activity, String keyId, URI signerActor) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        ContextState state = requestContext.getState();
        String type = activity.getClass().getSimpleName();
        URI actor = activity.actorUri().orElse(null);
        CompletionStage<?> async = CompletableFuture.completedFuture(null);
        boolean failed = false;
        try {
            context.set(recipient, rawJson, keyId, signerActor, activity);
            failed = transactions.inTransaction(() -> handleAndNotify(activity));
            async = selected(activity).fireAsync((Activity) activity.copy(),
                    NotificationOptions.ofExecutor(withRequestContext(requestContext, state)));
        } catch (RuntimeException e) {
            failed = true;
            LOG.warnf(e, "Inbound %s %s from %s failed", type, activity.id(), actor);
        } finally {
            requestContext.deactivate();
        }
        log.record(type, actor, failed ? InboxLog.ERROR : InboxLog.ACCEPTED);
        async.whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOG.warnf(failure, "Async observer of inbound %s %s from %s failed", type, activity.id(), actor);
            }
            requestContext.activate(state);
            requestContext.terminate();
        });
    }

    /**
     * Runs the handler, then the synchronous observers; an observer failure is logged and does not undo the handler's
     * work. Returns whether an observer failed. A handler failure propagates, so the transaction rolls back.
     */
    private boolean handleAndNotify(Activity activity) {
        handlerFor(activity).ifPresent(h -> invoke(h, activity));
        try {
            selected(activity).fire(activity);
            return false;
        } catch (RuntimeException e) {
            LOG.warnf(e, "Observer of inbound %s %s failed", activity.getClass().getSimpleName(), activity.id());
            return true;
        }
    }

    /** The highest-priority handler for exactly the activity's class, else for a supertype of it. */
    Optional<InboundActivityHandler<?>> handlerFor(Activity activity) {
        Class<?> type = activity.getClass();
        for (InboundActivityHandler<?> h : handlers) {
            if (h.type() == type) {
                return Optional.of(h);
            }
        }
        for (InboundActivityHandler<?> h : handlers) {
            if (h.type().isAssignableFrom(type)) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void invoke(InboundActivityHandler handler, Activity activity) {
        handler.handle(activity, context);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Event<Activity> selected(Activity activity) {
        return event.select((Class) activity.getClass(), Inbound.Literal.INSTANCE);
    }

    /** Worker-pool executor that runs each task inside the processing's request context. */
    private static Executor withRequestContext(ManagedContext requestContext, ContextState state) {
        return task -> Infrastructure.getDefaultWorkerPool().execute(() -> {
            requestContext.activate(state);
            try {
                task.run();
            } finally {
                requestContext.deactivate();
            }
        });
    }
}

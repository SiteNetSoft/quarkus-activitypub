package org.sitenetsoft.quarkus.activitypub.inbox;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.store.TransactionRunner;

import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;

/**
 * Processes an accepted inbound activity after the {@code 202}, on a virtual thread of its own (at most
 * {@code quarkus.activitypub.inbox.max-concurrency} at a time; further activities wait for a slot on their virtual
 * thread, never on the event loop or the shared worker pool, so slow remote fetches cannot starve the application): fills
 * {@link InboundActivityContext} in a fresh request context, runs the matching {@link InboundActivityHandler}'s
 * {@code prepare} (outside any transaction: remote fetches), then its {@code handle} and the synchronous
 * {@link Inbound} observers in one store transaction, then notifies the {@code @ObservesAsync} observers
 * with a copy of the activity (the request context stays active for them and ends when they are done). Failures are
 * logged and recorded in {@link InboxLog}, never rethrown; when the activity was deduplicated
 * ({@code ObjectStore.markSeen} on receipt), a failure forgets its id so a redelivery is processed again.
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

    @Inject
    Stores stores;

    @Inject
    ActivityPubConfig config;

    private ExecutorService threads;
    private Semaphore slots;

    @PostConstruct
    void init() {
        threads = Executors.newVirtualThreadPerTaskExecutor();
        slots = new Semaphore(Math.max(1, config.inbox().maxConcurrency()));
    }

    @PreDestroy
    void shutdown() {
        threads.shutdown();
    }

    /**
     * Runs {@link #process} on a new virtual thread once one of the {@code inbox.max-concurrency} slots is free; returns
     * at once (the slot is awaited on the virtual thread).
     */
    public void submit(Optional<LocalActor> recipient, byte[] rawJson, Activity activity, String keyId, URI signerActor) {
        try {
            threads.execute(() -> {
                slots.acquireUninterruptibly();
                try {
                    process(recipient, rawJson, activity, keyId, signerActor);
                } finally {
                    slots.release();
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.warnf("Inbound %s %s dropped: shutting down", activity.getClass().getSimpleName(), activity.id());
        }
    }

    /** Must run on a worker or virtual thread: handlers and synchronous observers may block. */
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
            Optional<InboundActivityHandler<?>> handler = handlerFor(activity);
            handler.ifPresent(h -> prepare(h, activity));
            failed = transactions.inTransaction(() -> handleAndNotify(handler, activity));
            async = selected(activity).fireAsync((Activity) activity.copy(),
                    NotificationOptions.ofExecutor(withRequestContext(threads, requestContext, state)));
        } catch (RuntimeException e) {
            failed = true;
            LOG.warnf(e, "Inbound %s %s from %s failed", type, activity.id(), actor);
            forgetSeen(activity, signerActor);
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
    private boolean handleAndNotify(Optional<InboundActivityHandler<?>> handler, Activity activity) {
        handler.ifPresent(h -> invoke(h, activity));
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

    /**
     * Lets a redelivery of a failed activity through again. Mirrors {@code InboxHandler}: only ids on the signer's
     * host are deduplicated, and forgetting another host's id could re-open a replay of that host's activity.
     */
    private void forgetSeen(Activity activity, URI signerActor) {
        URI id = activity.id();
        if (id == null || id.getHost() == null || signerActor == null
                || !id.getHost().equalsIgnoreCase(signerActor.getHost())) {
            return;
        }
        try {
            stores.objects().forgetSeen(id);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Could not forget the failed inbound activity %s; a redelivery will be dropped", id);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void prepare(InboundActivityHandler handler, Activity activity) {
        handler.prepare(activity, context);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void invoke(InboundActivityHandler handler, Activity activity) {
        handler.handle(activity, context);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Event<Activity> selected(Activity activity) {
        return event.select((Class) activity.getClass(), Inbound.Literal.INSTANCE);
    }

    /** Runs each task on a virtual thread inside the processing's request context. */
    private static Executor withRequestContext(ExecutorService threads, ManagedContext requestContext,
            ContextState state) {
        return task -> threads.execute(() -> {
            requestContext.activate(state);
            try {
                task.run();
            } finally {
                requestContext.deactivate();
            }
        });
    }
}

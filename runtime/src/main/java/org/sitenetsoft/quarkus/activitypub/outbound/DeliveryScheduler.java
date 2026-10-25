package org.sitenetsoft.quarkus.activitypub.outbound;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryEvents.DeliveryCompleted;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryEvents.DeliveryFailed;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliverySender.Outcome;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.HostHealth;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.scheduler.Scheduled;

/**
 * Sends due deliveries: every {@code delivery.poll-interval} it claims up to {@code batch-size} of them and sends them
 * on virtual threads, at most {@code parallelism} at a time, then records each outcome:
 * <ul>
 * <li>2xx: {@code DELIVERED};</li>
 * <li>408, 429, 5xx, transport errors, and 401/403 that double knocking did not fix: retried after
 * {@link Backoff#forAttempt(int)} of the attempt count, until {@code max-attempts}, then {@code GIVEN_UP};</li>
 * <li>any other status, or a blocked address: {@code FAILED}, not retried.</li>
 * </ul>
 * Retryable outcomes count as host failures, any answer that is not retryable as a host success. After
 * {@value #PAUSE_AFTER_FAILURES} consecutive failures a host is paused for
 * {@code Backoff.forAttempt(consecutiveFailures - 4)}; its claimed deliveries wait for the end of the pause, when the
 * next poll sends them as the probe. Times come from the injected {@link Clock}.
 */
@ApplicationScoped
public class DeliveryScheduler {

    static final int PAUSE_AFTER_FAILURES = 5;

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    Stores stores;

    @Inject
    ActivityPubConfig config;

    @Inject
    DeliverySender sender;

    @Inject
    DeliveryMetrics metrics;

    @Inject
    Clock clock;

    @Inject
    Event<DeliveryCompleted> completed;

    @Inject
    Event<DeliveryFailed> failed;

    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    /** How long a host with {@code consecutiveFailures} (at least {@value #PAUSE_AFTER_FAILURES}) is paused. */
    static Duration pauseFor(int consecutiveFailures) {
        return Backoff.forAttempt(consecutiveFailures - (PAUSE_AFTER_FAILURES - 1));
    }

    @Scheduled(identity = "activitypub-delivery", every = "{quarkus.activitypub.delivery.poll-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        run();
    }

    /**
     * One poll: claims the due deliveries and returns once all of them are sent and recorded. Runs are serialised,
     * so calling it directly (tests, a "retry now" action) never overlaps the scheduled poll.
     */
    public synchronized void run() {
        Instant now = clock.instant();
        DeliveryStore store = stores.deliveries();
        List<Delivery> due = store.claimDue(now, config.delivery().batchSize());
        if (due.isEmpty()) {
            return;
        }
        Map<String, HostHealth> hosts = new HashMap<>();
        Semaphore permits = new Semaphore(config.delivery().parallelism());
        List<Future<?>> running = new ArrayList<>(due.size());
        for (Delivery d : due) {
            Optional<Instant> paused = hosts.computeIfAbsent(d.host(), store::host).pausedUntil().filter(now::isBefore);
            if (paused.isPresent()) {
                store.update(d.withNextAttempt(paused.get()));
                continue;
            }
            permits.acquireUninterruptibly();
            try {
                running.add(threads.submit(() -> {
                    try {
                        attempt(d);
                    } finally {
                        permits.release();
                    }
                }));
            } catch (RuntimeException e) { // rejected: shutting down
                permits.release();
                throw e;
            }
        }
        for (Future<?> f : running) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                LOG.warnf(e.getCause(), "Delivery attempt failed unexpectedly");
            }
        }
    }

    private void attempt(Delivery d) {
        Outcome outcome;
        try {
            outcome = sender.deliver(d);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Delivery %s to %s failed unexpectedly", d.id(), d.inbox());
            outcome = new Outcome(-1, Optional.of(e.getClass().getSimpleName() + ": " + e.getMessage()), false);
        }
        DeliveryStore store = stores.deliveries();
        Instant at = clock.instant();
        Delivery tried = d.withAttempts(d.attempts() + 1);
        if (outcome.delivered()) {
            Delivery done = tried.withState(DeliveryState.DELIVERED).withLastError(Optional.empty());
            store.update(done);
            store.recordHostResult(d.host(), true, at);
            LOG.debugf("Delivery %s of %s to %s: %s", d.id(), d.activityId(), d.inbox(), outcome.describe());
            metrics.attempt("delivered");
            fire(completed, new DeliveryCompleted(done));
            return;
        }
        String error = outcome.describe();
        if (outcome.permanent() || !retryable(outcome.status())) {
            Delivery done = tried.withState(DeliveryState.FAILED).withLastError(Optional.of(error));
            store.update(done);
            if (!outcome.permanent()) {
                store.recordHostResult(d.host(), true, at); // the host answered
            }
            LOG.debugf("Delivery %s of %s to %s failed: %s", d.id(), d.activityId(), d.inbox(), error);
            metrics.attempt("failed");
            fire(failed, new DeliveryFailed(done, error, false));
            return;
        }
        store.recordHostResult(d.host(), false, at);
        int hostFailures = store.host(d.host()).consecutiveFailures();
        if (hostFailures >= PAUSE_AFTER_FAILURES) {
            store.pauseHost(d.host(), at.plus(pauseFor(hostFailures)));
        }
        boolean retry = tried.attempts() < config.delivery().maxAttempts();
        Delivery done = retry
                ? tried.withNextAttempt(at.plus(Backoff.forAttempt(tried.attempts()))).withLastError(Optional.of(error))
                : tried.withState(DeliveryState.GIVEN_UP).withLastError(Optional.of(error));
        store.update(done);
        LOG.debugf("Delivery %s of %s to %s, attempt %d: %s%s", d.id(), d.activityId(), d.inbox(), tried.attempts(),
                error, retry ? "; retry at " + done.nextAttempt() : "; given up");
        metrics.attempt(retry ? "retry" : "given_up");
        fire(failed, new DeliveryFailed(done, error, retry));
    }

    /** 408, 429, 5xx, transport errors, and 401/403 left after double knocking. */
    static boolean retryable(int status) {
        return status == -1 || status == 401 || status == 403 || status == 408 || status == 429 || status >= 500;
    }

    private static <T> void fire(Event<T> event, T payload) {
        try {
            event.fire(payload);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Observer of %s failed", payload.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        threads.shutdown();
    }
}

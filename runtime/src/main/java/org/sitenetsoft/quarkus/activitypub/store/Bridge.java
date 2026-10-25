package org.sitenetsoft.quarkus.activitypub.store;

import java.time.Duration;
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * The two conversions the store adapters use.
 */
final class Bridge {

    static final Duration AWAIT = Duration.ofSeconds(30);

    private Bridge() {
    }

    /** Blocking view of a reactive call. */
    static <T> T await(Uni<T> uni) {
        return uni.await().atMost(AWAIT);
    }

    /** Reactive view of a blocking call, run on a worker thread. */
    static <T> Uni<T> offload(Supplier<T> work) {
        return Uni.createFrom().item(work).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    static Uni<Void> offload(Runnable work) {
        return offload(() -> {
            work.run();
            return null;
        });
    }
}

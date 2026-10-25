package org.sitenetsoft.quarkus.activitypub.deployment;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.quarkus.activitypub.store.TransactionRunner;

/** Runs the work inline like the in-memory default, and knows whether the current thread is inside it. */
@ApplicationScoped
public class TrackingTransactionRunner implements TransactionRunner {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    static boolean inTransaction() {
        return DEPTH.get() > 0;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            return work.get();
        } finally {
            DEPTH.set(DEPTH.get() - 1);
        }
    }
}

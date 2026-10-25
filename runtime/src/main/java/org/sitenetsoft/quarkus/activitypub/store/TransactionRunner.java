package org.sitenetsoft.quarkus.activitypub.store;

import java.util.function.Supplier;

/**
 * Runs a unit of store work in one transaction, joining the caller's when one is active. The in-memory default runs
 * the work inline without any transaction.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}

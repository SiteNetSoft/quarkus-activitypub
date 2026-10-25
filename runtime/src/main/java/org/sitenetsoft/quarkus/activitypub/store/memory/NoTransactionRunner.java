package org.sitenetsoft.quarkus.activitypub.store.memory;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.quarkus.activitypub.store.TransactionRunner;

import io.quarkus.arc.DefaultBean;

/** Runs the work inline: the in-memory stores have no transactions. */
@DefaultBean
@ApplicationScoped
public class NoTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}

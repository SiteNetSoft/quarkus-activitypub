package org.sitenetsoft.quarkus.activitypub.hibernate;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.quarkus.activitypub.store.TransactionRunner;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Runs store work in the caller's JTA transaction, or in a new one when none is active. */
@ApplicationScoped
public class JtaTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return QuarkusTransaction.joiningExisting().call(work::get);
    }
}

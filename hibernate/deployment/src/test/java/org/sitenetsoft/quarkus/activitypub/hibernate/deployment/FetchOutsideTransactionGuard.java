package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.client.RemoteCache;

/**
 * Records every {@link ActivityPubClient} call, whether a JTA transaction was active, and whether the document was
 * already cached (a cached call does no I/O). A call inside a transaction that is not cached would hold the database
 * transaction open over remote I/O.
 */
@Decorator
@Priority(1)
public abstract class FetchOutsideTransactionGuard implements ActivityPubClient {

    record Call(String method, URI uri, boolean inTransaction, boolean cached) {
    }

    static final List<Call> CALLS = new CopyOnWriteArrayList<>();

    @Inject
    @Delegate
    ActivityPubClient delegate;

    @Inject
    RemoteCache cache;

    @Inject
    TransactionManager transactions;

    private boolean inTransaction() {
        try {
            return transactions.getStatus() != Status.STATUS_NO_TRANSACTION;
        } catch (SystemException e) {
            throw new IllegalStateException(e);
        }
    }

    private void record(String method, URI uri) {
        CALLS.add(new Call(method, uri, inTransaction(), cache.get(uri).isPresent()));
    }

    static List<Call> callsFor(URI uri) {
        return CALLS.stream().filter(c -> c.uri().equals(uri)).toList();
    }

    @Override
    public ASObject fetch(URI id) {
        record("fetch", id);
        return delegate.fetch(id);
    }

    @Override
    public <T extends ASObject> T fetch(URI id, Class<T> type) {
        record("fetch", id);
        return delegate.fetch(id, type);
    }

    @Override
    public Actor fetchActor(URI id) {
        record("fetchActor", id);
        return delegate.fetchActor(id);
    }

    @Override
    public Actor resolveActor(String acctOrUri) {
        CALLS.add(new Call("resolveActor", URI.create("acct:" + acctOrUri), inTransaction(), false));
        return delegate.resolveActor(acctOrUri);
    }
}

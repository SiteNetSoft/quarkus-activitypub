package org.sitenetsoft.quarkus.activitypub.deployment;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;

/**
 * Records every {@link ActivityPubClient} call and whether it happened inside {@link TrackingTransactionRunner}'s
 * transaction (it must not: remote I/O would hold the transaction open).
 */
@Decorator
@Priority(1)
public abstract class FetchOutsideTransactionGuard implements ActivityPubClient {

    record Call(URI uri, boolean inTransaction) {
    }

    static final List<Call> CALLS = new CopyOnWriteArrayList<>();

    @Inject
    @Delegate
    ActivityPubClient delegate;

    private static void record(URI uri) {
        CALLS.add(new Call(uri, TrackingTransactionRunner.inTransaction()));
    }

    static List<Call> callsInTransaction() {
        return CALLS.stream().filter(Call::inTransaction).toList();
    }

    static boolean fetched(URI uri) {
        return CALLS.stream().anyMatch(c -> c.uri().equals(uri));
    }

    @Override
    public ASObject fetch(URI id) {
        record(id);
        return delegate.fetch(id);
    }

    @Override
    public <T extends ASObject> T fetch(URI id, Class<T> type) {
        record(id);
        return delegate.fetch(id, type);
    }

    @Override
    public Actor fetchActor(URI id) {
        record(id);
        return delegate.fetchActor(id);
    }

    @Override
    public Actor resolveActor(String acctOrUri) {
        CALLS.add(new Call(URI.create("acct:" + acctOrUri), TrackingTransactionRunner.inTransaction()));
        return delegate.resolveActor(acctOrUri);
    }
}

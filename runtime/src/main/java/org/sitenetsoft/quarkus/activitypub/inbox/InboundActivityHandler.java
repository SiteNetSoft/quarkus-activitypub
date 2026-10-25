package org.sitenetsoft.quarkus.activitypub.inbox;

import org.sitenetsoft.activitystreams.Activity;

/**
 * The side effect of an inbound activity, run on a worker thread inside the store transaction, before the
 * {@link Inbound} events. Per activity only one handler runs: among the handlers whose {@link #type()} is exactly the
 * activity's class (else, assignable from it), the one with the highest {@code @Priority}. The built-in handlers have
 * {@code @Priority(0)}, so an application bean with a higher priority replaces one.
 * <p>
 * Remote I/O (fetching an actor or an object) belongs in {@link #prepare}, which runs first, outside the transaction,
 * so a slow remote server never holds a database transaction open. Both run in the same request context, so a
 * {@code @RequestScoped} bean can carry what {@code prepare} found to {@code handle}.
 */
public interface InboundActivityHandler<T extends Activity> {

    Class<T> type();

    /**
     * Runs before {@link #handle}, outside the store transaction: the place for remote fetches. A failure here fails
     * the processing like a failing {@code handle}. The default does nothing.
     */
    default void prepare(T activity, InboundActivityContext ctx) {
    }

    /** Runs inside the store transaction; must not do remote I/O. */
    void handle(T activity, InboundActivityContext ctx);
}

package org.sitenetsoft.quarkus.activitypub.inbox;

import org.sitenetsoft.activitystreams.Activity;

/**
 * The side effect of an inbound activity, run on a worker thread inside the store transaction, before the
 * {@link Inbound} events. Per activity only one handler runs: among the handlers whose {@link #type()} is exactly the
 * activity's class (else, assignable from it), the one with the highest {@code @Priority}. The built-in handlers have
 * {@code @Priority(0)}, so an application bean with a higher priority replaces one.
 */
public interface InboundActivityHandler<T extends Activity> {

    Class<T> type();

    void handle(T activity, InboundActivityContext ctx);
}

package org.sitenetsoft.quarkus.activitypub.inbox;

import org.sitenetsoft.activitystreams.ASObject;

/**
 * Decides whether a remote object from a {@code Create} or {@code Update} is kept in {@code ObjectStore}. Runs on a
 * worker thread.
 */
public interface RemoteObjectPolicy {

    enum Keep {
        KEEP,
        IGNORE
    }

    Keep decide(ASObject object, InboundActivityContext ctx);
}

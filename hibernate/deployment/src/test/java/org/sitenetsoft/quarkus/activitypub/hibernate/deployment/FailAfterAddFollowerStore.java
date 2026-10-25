package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import java.net.URI;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.FollowerStore;

/** Fails {@link FollowerStore#add} after the write while {@link #fail} is set, to roll the inbox transaction back. */
@Decorator
@Priority(1)
public abstract class FailAfterAddFollowerStore implements FollowerStore {

    static volatile boolean fail;

    @Inject
    @Delegate
    FollowerStore delegate;

    @Override
    public void add(URI local, URI remote, URI remoteInbox, FollowState state, URI followActivityId) {
        delegate.add(local, remote, remoteInbox, state, followActivityId);
        if (fail) {
            throw new IllegalStateException("failure after FollowerStore.add (test)");
        }
    }
}

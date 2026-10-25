package org.sitenetsoft.quarkus.activitypub.inbox;

/**
 * Decides whether activities from a host are processed. A blocked host still gets {@code 202} (it is never told) and
 * its activity is dropped. Called on the event loop: must not block.
 */
public interface DomainPolicy {

    enum Decision {
        ALLOW,
        BLOCK
    }

    Decision allow(String host);
}

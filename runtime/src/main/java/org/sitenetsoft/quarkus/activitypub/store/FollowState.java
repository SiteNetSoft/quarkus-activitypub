package org.sitenetsoft.quarkus.activitypub.store;

/**
 * State of a follow relationship.
 */
public enum FollowState {
    PENDING,
    ACCEPTED,
    REJECTED
}

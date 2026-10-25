package org.sitenetsoft.quarkus.activitypub.store;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Follow relationships in both directions. Blocking twin of {@link ReactiveFollowerStore}; provide either.
 * Collections and counts include {@link FollowState#ACCEPTED} relationships only, newest first; pages are 1-based.
 */
public interface FollowerStore {

    /** Records (or replaces) {@code remote} following {@code local}; {@code remoteInbox} is where to deliver to it. */
    void add(URI local, URI remote, URI remoteInbox, FollowState state, URI followActivityId);

    void remove(URI local, URI remote);

    /** Forgets {@code remote} as follower and as followed actor of every local actor. */
    void removeAll(URI remote);

    Page<URI> followers(URI local, int page, int size);

    long followerCount(URI local);

    /** Distinct inboxes of the accepted followers of {@code local}. */
    List<URI> followerInboxes(URI local);

    /** Records (or replaces) {@code local} following {@code remote}. */
    void addFollowing(URI local, URI remote, FollowState state, URI followActivityId);

    void removeFollowing(URI local, URI remote);

    Page<URI> following(URI local, int page, int size);

    long followingCount(URI local);

    /** State of {@code local} following {@code remote}, whatever it is. */
    Optional<FollowState> followingState(URI local, URI remote);

    /** {@code remote} following {@code local}, in any state (pending requests included). */
    Optional<FollowRelation> follower(URI local, URI remote);

    /** {@code local} following {@code remote}, in any state. */
    Optional<FollowRelation> followingRelation(URI local, URI remote);

    /** Whether {@code remote} follows or is followed by any local actor, in any state. */
    boolean knowsRemote(URI remote);
}

package org.sitenetsoft.quarkus.activitypub.hibernate.entity;

import java.io.Serializable;
import java.util.Objects;

/** The primary key {@code (local, remote)} of {@link ApFollower} and {@link ApFollowing}. */
public class FollowKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String local;
    private String remote;

    public FollowKey() {
    }

    public FollowKey(String local, String remote) {
        this.local = local;
        this.remote = remote;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FollowKey k && Objects.equals(local, k.local) && Objects.equals(remote, k.remote);
    }

    @Override
    public int hashCode() {
        return Objects.hash(local, remote);
    }
}

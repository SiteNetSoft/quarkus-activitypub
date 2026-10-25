package org.sitenetsoft.quarkus.activitypub.hibernate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.sitenetsoft.quarkus.activitypub.store.FollowState;

/** A remote actor following a local one ({@code ap_follower}). */
@Entity
@Table(name = "ap_follower", indexes = {
        @Index(name = "ap_follower_list_idx", columnList = "local, state, since"),
        @Index(name = "ap_follower_remote_idx", columnList = "remote") })
@IdClass(FollowKey.class)
public class ApFollower {

    @Id
    @Column(name = "local", length = 2048)
    private String local;

    @Id
    @Column(name = "remote", length = 2048)
    private String remote;

    /** Where deliveries to the follower go (its shared inbox when it has one). */
    @Column(name = "remote_inbox", nullable = false, length = 2048)
    private String remoteInbox;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private FollowState state;

    @Column(name = "follow_activity_id", length = 2048)
    private String followActivityId;

    /** When the relationship was last written; collections list newest first. */
    @Column(name = "since", nullable = false)
    private Instant since;

    public ApFollower() {
    }

    public ApFollower(String local, String remote) {
        this.local = local;
        this.remote = remote;
    }

    public String getLocal() {
        return local;
    }

    public String getRemote() {
        return remote;
    }

    public String getRemoteInbox() {
        return remoteInbox;
    }

    public void setRemoteInbox(String remoteInbox) {
        this.remoteInbox = remoteInbox;
    }

    public FollowState getState() {
        return state;
    }

    public void setState(FollowState state) {
        this.state = state;
    }

    public String getFollowActivityId() {
        return followActivityId;
    }

    public void setFollowActivityId(String followActivityId) {
        this.followActivityId = followActivityId;
    }

    public Instant getSince() {
        return since;
    }

    public void setSince(Instant since) {
        this.since = since;
    }
}

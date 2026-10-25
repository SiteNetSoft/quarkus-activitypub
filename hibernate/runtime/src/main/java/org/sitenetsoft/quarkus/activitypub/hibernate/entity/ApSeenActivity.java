package org.sitenetsoft.quarkus.activitypub.hibernate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** An inbound activity id already processed ({@code ap_seen_activity}), for de-duplication. */
@Entity
@Table(name = "ap_seen_activity", indexes = @Index(name = "ap_seen_activity_seen_at_idx", columnList = "seen_at"))
public class ApSeenActivity {

    @Id
    @Column(name = "id", length = 2048)
    private String id;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    public ApSeenActivity() {
    }

    public ApSeenActivity(String id, Instant seenAt) {
        this.id = id;
        this.seenAt = seenAt;
    }

    public String getId() {
        return id;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public void setSeenAt(Instant seenAt) {
        this.seenAt = seenAt;
    }
}

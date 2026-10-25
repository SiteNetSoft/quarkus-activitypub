package org.sitenetsoft.quarkus.activitypub.hibernate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.hibernate.Length;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;

/** One activity to one inbox ({@code ap_delivery}): the outbox queue. */
@Entity
@Table(name = "ap_delivery", indexes = @Index(name = "ap_delivery_due_idx", columnList = "state, next_attempt"))
public class ApDelivery {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "inbox", nullable = false, length = 2048)
    private String inbox;

    @Column(name = "activity_id", nullable = false, length = 2048)
    private String activityId;

    /** The serialised activity, signed per attempt. */
    @Column(name = "body", nullable = false, length = Length.LONG32)
    private byte[] body;

    @Column(name = "key_id", nullable = false, length = 2048)
    private String keyId;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt", nullable = false)
    private Instant nextAttempt;

    @Column(name = "last_error", length = Length.LONG32)
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private DeliveryState state;

    @Column(name = "created", nullable = false)
    private Instant created;

    /** When a delivery poll claimed the row; claims older than 60 s are void. */
    @Column(name = "claimed_at")
    private Instant claimedAt;

    public ApDelivery() {
    }

    public ApDelivery(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getInbox() {
        return inbox;
    }

    public void setInbox(String inbox) {
        this.inbox = inbox;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttempt() {
        return nextAttempt;
    }

    public void setNextAttempt(Instant nextAttempt) {
        this.nextAttempt = nextAttempt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public DeliveryState getState() {
        return state;
    }

    public void setState(DeliveryState state) {
        this.state = state;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }
}

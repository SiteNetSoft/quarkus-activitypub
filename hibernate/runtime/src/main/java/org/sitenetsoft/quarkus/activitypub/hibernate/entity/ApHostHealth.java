package org.sitenetsoft.quarkus.activitypub.hibernate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Delivery health of one remote host ({@code ap_host_health}). */
@Entity
@Table(name = "ap_host_health")
public class ApHostHealth {

    @Id
    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_failure")
    private Instant lastFailure;

    @Column(name = "last_success")
    private Instant lastSuccess;

    @Column(name = "paused_until")
    private Instant pausedUntil;

    public ApHostHealth() {
    }

    public ApHostHealth(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public Instant getLastFailure() {
        return lastFailure;
    }

    public void setLastFailure(Instant lastFailure) {
        this.lastFailure = lastFailure;
    }

    public Instant getLastSuccess() {
        return lastSuccess;
    }

    public void setLastSuccess(Instant lastSuccess) {
        this.lastSuccess = lastSuccess;
    }

    public Instant getPausedUntil() {
        return pausedUntil;
    }

    public void setPausedUntil(Instant pausedUntil) {
        this.pausedUntil = pausedUntil;
    }
}

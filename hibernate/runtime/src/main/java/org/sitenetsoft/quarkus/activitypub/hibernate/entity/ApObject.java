package org.sitenetsoft.quarkus.activitypub.hibernate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.hibernate.Length;

/**
 * A stored object or activity ({@code ap_object}): local ones (served, and the outbox), and remote ones kept from the
 * inbox. The document itself is {@code body_json}; the other columns are what the store queries by.
 */
@Entity
@Table(name = "ap_object", indexes = {
        @Index(name = "ap_object_outbox_idx", columnList = "actor, is_local, is_public, is_activity"),
        @Index(name = "ap_object_created_idx", columnList = "is_local, created") })
public class ApObject {

    @Id
    @Column(name = "id", length = 2048)
    private String id;

    @Column(name = "primary_type", nullable = false, length = 64)
    private String primaryType;

    /** The {@code actor} of an activity, else the first {@code attributedTo} of the object. */
    @Column(name = "actor", length = 2048)
    private String actor;

    @Column(name = "is_local", nullable = false)
    private boolean local;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    /** Whether the document is an activity (outbox entries are). */
    @Column(name = "is_activity", nullable = false)
    private boolean activity;

    @Column(name = "published")
    private Instant published;

    /** The actor that sent a remote object to us. */
    @Column(name = "received_from", length = 2048)
    private String receivedFrom;

    @Column(name = "body_json", nullable = false, length = Length.LONG32)
    private String bodyJson;

    @Column(name = "tombstoned_at")
    private Instant tombstonedAt;

    /** When the row was last written; remote objects expire from it. */
    @Column(name = "created", nullable = false)
    private Instant created;

    public ApObject() {
    }

    public ApObject(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getPrimaryType() {
        return primaryType;
    }

    public void setPrimaryType(String primaryType) {
        this.primaryType = primaryType;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public boolean isActivity() {
        return activity;
    }

    public void setActivity(boolean activity) {
        this.activity = activity;
    }

    public Instant getPublished() {
        return published;
    }

    public void setPublished(Instant published) {
        this.published = published;
    }

    public String getReceivedFrom() {
        return receivedFrom;
    }

    public void setReceivedFrom(String receivedFrom) {
        this.receivedFrom = receivedFrom;
    }

    public String getBodyJson() {
        return bodyJson;
    }

    public void setBodyJson(String bodyJson) {
        this.bodyJson = bodyJson;
    }

    public Instant getTombstonedAt() {
        return tombstonedAt;
    }

    public void setTombstonedAt(Instant tombstonedAt) {
        this.tombstonedAt = tombstonedAt;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }
}

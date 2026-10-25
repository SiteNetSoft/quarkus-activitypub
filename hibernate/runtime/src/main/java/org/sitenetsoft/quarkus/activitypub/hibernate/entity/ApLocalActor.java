package org.sitenetsoft.quarkus.activitypub.hibernate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.Length;

/** A local actor and its key pair ({@code ap_local_actor}). Written only through {@code HibernateActorStore}. */
@Entity
@Table(name = "ap_local_actor")
public class ApLocalActor {

    @Id
    @Column(name = "id", length = 2048)
    private String id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "name", length = Length.LONG32)
    private String name;

    @Column(name = "summary", length = Length.LONG32)
    private String summary;

    @Column(name = "url", length = 2048)
    private String url;

    @Column(name = "icon", length = 2048)
    private String icon;

    @Column(name = "image", length = 2048)
    private String image;

    @Column(name = "manually_approves", nullable = false)
    private boolean manuallyApproves;

    @Column(name = "discoverable", nullable = false)
    private boolean discoverable;

    /** Nanosecond precision where the database has it, so the actor reads back equal to the one saved. */
    @Column(name = "published", nullable = false, secondPrecision = 9)
    private Instant published;

    /** {@code LocalActor.extra} as a JSON object. */
    @Column(name = "extra_json", nullable = false, length = Length.LONG32)
    private String extraJson;

    @Column(name = "public_key_pem", nullable = false, length = Length.LONG32)
    private String publicKeyPem;

    @Column(name = "private_key_pem", nullable = false, length = Length.LONG32)
    private String privateKeyPem;

    public ApLocalActor() {
    }

    public ApLocalActor(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public boolean isManuallyApproves() {
        return manuallyApproves;
    }

    public void setManuallyApproves(boolean manuallyApproves) {
        this.manuallyApproves = manuallyApproves;
    }

    public boolean isDiscoverable() {
        return discoverable;
    }

    public void setDiscoverable(boolean discoverable) {
        this.discoverable = discoverable;
    }

    public Instant getPublished() {
        return published;
    }

    public void setPublished(Instant published) {
        this.published = published;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    /** Never prints the key material. */
    @Override
    public String toString() {
        return "ApLocalActor[id=" + id + ", username=" + username + "]";
    }
}

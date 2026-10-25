package org.sitenetsoft.activitystreams;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.sitenetsoft.activitystreams.json.SingleOrList;

/** Common actor properties (ActivityPub actor plus Mastodon extensions). */
public abstract class Actor extends ASObject {

    private URI inbox;
    private URI outbox;
    private URI followers;
    private URI following;
    private URI liked;
    private URI featured;
    private String preferredUsername;
    private Endpoints endpoints;
    @SingleOrList
    private List<PublicKey> publicKey = new ArrayList<>();
    private Boolean manuallyApprovesFollowers;
    private Boolean discoverable;
    @SingleOrList
    private List<URI> alsoKnownAs = new ArrayList<>();
    private URI movedTo;

    public URI inbox() { return inbox; }
    public Actor inbox(URI v) { this.inbox = v; return this; }

    public URI outbox() { return outbox; }
    public Actor outbox(URI v) { this.outbox = v; return this; }

    public URI followers() { return followers; }
    public Actor followers(URI v) { this.followers = v; return this; }

    public URI following() { return following; }
    public Actor following(URI v) { this.following = v; return this; }

    public URI liked() { return liked; }
    public Actor liked(URI v) { this.liked = v; return this; }

    public URI featured() { return featured; }
    public Actor featured(URI v) { this.featured = v; return this; }

    public String preferredUsername() { return preferredUsername; }
    public Actor preferredUsername(String v) { this.preferredUsername = v; return this; }

    public Endpoints endpoints() { return endpoints; }
    public Actor endpoints(Endpoints v) { this.endpoints = v; return this; }

    public List<PublicKey> publicKey() { return publicKey; }
    public Actor publicKey(PublicKey... v) { this.publicKey = new ArrayList<>(List.of(v)); return this; }

    public Boolean manuallyApprovesFollowers() { return manuallyApprovesFollowers; }
    public Actor manuallyApprovesFollowers(Boolean v) { this.manuallyApprovesFollowers = v; return this; }

    public Boolean discoverable() { return discoverable; }
    public Actor discoverable(Boolean v) { this.discoverable = v; return this; }

    public List<URI> alsoKnownAs() { return alsoKnownAs; }
    public Actor alsoKnownAs(URI... v) { this.alsoKnownAs = new ArrayList<>(List.of(v)); return this; }

    public URI movedTo() { return movedTo; }
    public Actor movedTo(URI v) { this.movedTo = v; return this; }

    /** The first public key, if any. */
    public Optional<PublicKey> mainKey() {
        return publicKey.stream().findFirst();
    }

    /** The shared inbox when the actor advertises one, otherwise its own inbox (possibly null). */
    public URI deliveryInbox() {
        return endpoints != null && endpoints.sharedInbox() != null ? endpoints.sharedInbox() : inbox;
    }
}

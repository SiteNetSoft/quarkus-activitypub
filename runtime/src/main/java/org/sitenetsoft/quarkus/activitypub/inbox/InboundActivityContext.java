package org.sitenetsoft.quarkus.activitypub.inbox;

import java.net.URI;
import java.util.Optional;

import jakarta.enterprise.context.RequestScoped;

import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;

/**
 * The inbound activity being processed, for handlers and {@link Inbound} observers (also {@code @ObservesAsync} ones).
 */
@RequestScoped
public class InboundActivityContext {

    private Optional<LocalActor> recipient = Optional.empty();
    private byte[] rawJson;
    private String signerKeyId;
    private URI signerActor;
    private Activity activity;

    /** The local actor whose inbox received the activity; empty for the shared inbox. */
    public Optional<LocalActor> recipient() {
        return recipient;
    }

    /** The request body as received (and verified). */
    public byte[] rawJson() {
        return rawJson == null ? null : rawJson.clone();
    }

    /** The key id of the verified HTTP signature. */
    public String signerKeyId() {
        return signerKeyId;
    }

    /** The actor owning the signing key, which is also the activity's {@code actor}. */
    public URI signerActor() {
        return signerActor;
    }

    /** The activity as received. */
    public Activity activity() {
        return activity;
    }

    void set(Optional<LocalActor> recipient, byte[] rawJson, String signerKeyId, URI signerActor, Activity activity) {
        this.recipient = recipient;
        this.rawJson = rawJson;
        this.signerKeyId = signerKeyId;
        this.signerActor = signerActor;
        this.activity = activity;
    }
}

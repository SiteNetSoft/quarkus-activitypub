package org.sitenetsoft.quarkus.activitypub.store;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.sitenetsoft.quarkus.activitypub.Ids;

/**
 * One activity to one inbox.
 *
 * @param body the serialized activity, computed once and signed per attempt
 */
public record Delivery(String id, URI inbox, URI activityId, byte[] body, String keyId, int attempts, Instant nextAttempt,
        Optional<String> lastError, DeliveryState state, Instant created) {

    public Delivery {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inbox, "inbox");
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(nextAttempt, "nextAttempt");
        Objects.requireNonNull(lastError, "lastError");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(created, "created");
    }

    /** A new pending delivery, due {@code now}. */
    public static Delivery create(URI inbox, URI activityId, byte[] body, String keyId, Instant now) {
        return new Delivery(Ids.ulid(), inbox, activityId, body, keyId, 0, now, Optional.empty(), DeliveryState.PENDING, now);
    }

    public Delivery withAttempts(int attempts) {
        return new Delivery(id, inbox, activityId, body, keyId, attempts, nextAttempt, lastError, state, created);
    }

    public Delivery withNextAttempt(Instant nextAttempt) {
        return new Delivery(id, inbox, activityId, body, keyId, attempts, nextAttempt, lastError, state, created);
    }

    public Delivery withLastError(Optional<String> lastError) {
        return new Delivery(id, inbox, activityId, body, keyId, attempts, nextAttempt, lastError, state, created);
    }

    public Delivery withState(DeliveryState state) {
        return new Delivery(id, inbox, activityId, body, keyId, attempts, nextAttempt, lastError, state, created);
    }

    /** The inbox host, the unit of {@link HostHealth}. */
    public String host() {
        return inbox.getHost();
    }
}

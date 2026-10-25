package org.sitenetsoft.quarkus.activitypub;

/**
 * A federation failure with a machine-readable {@link Reason}.
 */
public class ActivityPubException extends RuntimeException {

    public enum Reason {
        ID_MISMATCH,
        FETCH_FAILED,
        NOT_FOUND,
        INVALID_DOCUMENT,
        BLOCKED,
        UNSIGNED
    }

    private final Reason reason;

    public ActivityPubException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ActivityPubException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

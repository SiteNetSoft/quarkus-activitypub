package org.sitenetsoft.quarkus.activitypub.keys;

import java.util.Optional;

/**
 * How a local actor's key id travels through {@code RequestSigner} as a client name: {@code "activitypub:" + keyId},
 * picked up by {@link ActorSigningKeyProvider}.
 */
public final class KeyIds {

    public static final String CLIENT_PREFIX = "activitypub:";

    private KeyIds() {
    }

    /** The client name that makes {@code RequestSigner} sign with the local key {@code keyId}. */
    public static String clientName(String keyId) {
        return CLIENT_PREFIX + keyId;
    }

    /** The key id a client name stands for; empty unless it starts with {@link #CLIENT_PREFIX}. */
    public static Optional<String> keyIdOf(Optional<String> clientName) {
        return clientName.filter(c -> c.startsWith(CLIENT_PREFIX) && c.length() > CLIENT_PREFIX.length())
                .map(c -> c.substring(CLIENT_PREFIX.length()));
    }
}

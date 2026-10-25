package org.sitenetsoft.quarkus.activitypub.actor;

import java.util.Objects;

/**
 * A local actor's key pair as PEM: X.509 {@code PUBLIC KEY} and PKCS#8 {@code PRIVATE KEY}.
 */
public record ActorKeyPair(String publicKeyPem, String privateKeyPem) {

    public ActorKeyPair {
        Objects.requireNonNull(publicKeyPem, "publicKeyPem");
        Objects.requireNonNull(privateKeyPem, "privateKeyPem");
    }

    @Override
    public String toString() {
        return "ActorKeyPair[publicKeyPem=" + publicKeyPem.length() + " chars, privateKeyPem=<redacted>]";
    }
}

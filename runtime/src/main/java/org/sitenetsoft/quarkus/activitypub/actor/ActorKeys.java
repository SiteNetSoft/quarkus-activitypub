package org.sitenetsoft.quarkus.activitypub.actor;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import org.sitenetsoft.httpsignatures.Keys;

/**
 * Key helpers for local actors.
 */
public final class ActorKeys {

    private static final Base64.Encoder PEM = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));

    private ActorKeys() {
    }

    /** A fresh RSA 2048 key pair, the key type every Fediverse server verifies. */
    public static ActorKeyPair generateRsa2048() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new ActorKeyPair(pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                    pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key generation unavailable", e);
        }
    }

    public static PublicKey publicKey(ActorKeyPair keys) {
        return parse(keys.publicKeyPem(), PublicKey.class);
    }

    public static PrivateKey privateKey(ActorKeyPair keys) {
        return parse(keys.privateKeyPem(), PrivateKey.class);
    }

    private static <K extends Key> K parse(String pem, Class<K> type) {
        Key key = Keys.parsePem(pem);
        if (!type.isInstance(key)) {
            throw new IllegalArgumentException("PEM does not hold a " + type.getSimpleName());
        }
        return type.cast(key);
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n" + PEM.encodeToString(der) + "\n-----END " + label + "-----\n";
    }
}

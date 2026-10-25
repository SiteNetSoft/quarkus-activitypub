package org.sitenetsoft.quarkus.activitypub.keys;

import java.util.Map;
import java.util.Optional;

import org.sitenetsoft.httpsignatures.CavageSignature;
import org.sitenetsoft.httpsignatures.StructuredFields;

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

    /**
     * The key id named by signature headers {@code RequestSigner} produced: {@code keyid} of the first
     * {@code Signature-Input} entry (RFC 9421), else {@code keyId} of {@code Signature} (Cavage). Empty when neither
     * parses.
     */
    public static Optional<String> signedKeyId(Map<String, String> signatureHeaders) {
        try {
            String input = header(signatureHeaders, "Signature-Input");
            if (input != null) {
                for (Object v : StructuredFields.parseDictionary(input).values()) {
                    if (v instanceof StructuredFields.InnerList l && l.params().get("keyid") instanceof String s) {
                        return Optional.of(s);
                    }
                }
                return Optional.empty();
            }
            String cavage = header(signatureHeaders, "Signature");
            return cavage == null ? Optional.empty() : Optional.ofNullable(CavageSignature.parse(cavage).keyId());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> h : headers.entrySet()) {
            if (h.getKey().equalsIgnoreCase(name)) {
                return h.getValue();
            }
        }
        return null;
    }

    /** The key id a client name stands for; empty unless it starts with {@link #CLIENT_PREFIX}. */
    public static Optional<String> keyIdOf(Optional<String> clientName) {
        return clientName.filter(c -> c.startsWith(CLIENT_PREFIX) && c.length() > CLIENT_PREFIX.length())
                .map(c -> c.substring(CLIENT_PREFIX.length()));
    }
}

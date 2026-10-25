package org.sitenetsoft.activitystreams;

import java.net.URI;

/**
 * Security-vocabulary public key. Unlike the other classes it sets no {@code type}: Mastodon and friends write the key
 * object as {@code {id, owner, publicKeyPem}} and a default type would break lossless round trips.
 */
public class PublicKey extends ASObject {

    private URI owner;
    private String publicKeyPem;

    public URI owner() { return owner; }
    public PublicKey owner(URI v) { this.owner = v; return this; }

    public String publicKeyPem() { return publicKeyPem; }
    public PublicKey publicKeyPem(String v) { this.publicKeyPem = v; return this; }
}

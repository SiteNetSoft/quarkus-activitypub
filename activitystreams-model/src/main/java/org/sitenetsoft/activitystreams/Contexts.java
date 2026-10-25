package org.sitenetsoft.activitystreams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Well-known JSON-LD contexts. */
public final class Contexts {

    public static final String AS = "https://www.w3.org/ns/activitystreams";
    public static final String SECURITY = "https://w3id.org/security/v1";

    /**
     * The {@code @context} written on a root object that has none: ActivityStreams, the security vocabulary and the
     * Mastodon extension terms. Do not mutate.
     */
    public static final JsonNode DEFAULT;

    static {
        try {
            DEFAULT = new ObjectMapper().readTree("""
                    ["https://www.w3.org/ns/activitystreams","https://w3id.org/security/v1",{
                      "toot":"http://joinmastodon.org/ns#",
                      "Hashtag":"as:Hashtag",
                      "sensitive":"as:sensitive",
                      "manuallyApprovesFollowers":"as:manuallyApprovesFollowers",
                      "discoverable":"toot:discoverable",
                      "featured":{"@id":"toot:featured","@type":"@id"},
                      "alsoKnownAs":{"@id":"as:alsoKnownAs","@type":"@id"},
                      "movedTo":{"@id":"as:movedTo","@type":"@id"}}]
                    """);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Contexts() {
    }
}

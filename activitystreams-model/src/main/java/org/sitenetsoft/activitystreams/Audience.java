package org.sitenetsoft.activitystreams;

import java.net.URI;

/** Audience helpers. */
public final class Audience {

    /** The special public collection. */
    public static final URI PUBLIC = URI.create("https://www.w3.org/ns/activitystreams#Public");

    private Audience() {
    }

    /** True for the public collection in any of its spellings: the full URI, {@code as:Public} or {@code Public}. */
    public static boolean isPublic(URI u) {
        if (u == null) {
            return false;
        }
        String s = u.toString();
        return PUBLIC.toString().equals(s) || "as:Public".equals(s) || "Public".equals(s);
    }
}

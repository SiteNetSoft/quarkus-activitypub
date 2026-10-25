package org.sitenetsoft.quarkus.activitypub;

import java.util.Locale;

/**
 * ActivityPub media types.
 */
public final class ActivityPubMediaTypes {

    /** {@code application/activity+json}. */
    public static final String ACTIVITY_JSON = "application/activity+json";
    /** JSON-LD with the Activity Streams profile. */
    public static final String LD_JSON_ACTIVITYSTREAMS = "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"";
    /** {@code Accept} header value for fetching ActivityPub documents. */
    public static final String ACCEPT = ACTIVITY_JSON + ", " + LD_JSON_ACTIVITYSTREAMS;

    private ActivityPubMediaTypes() {
    }

    /**
     * True for {@code application/activity+json} and {@code application/ld+json} (with any parameters).
     */
    public static boolean isActivityPub(String contentType) {
        if (contentType == null) {
            return false;
        }
        int semi = contentType.indexOf(';');
        String base = (semi < 0 ? contentType : contentType.substring(0, semi)).strip().toLowerCase(Locale.ROOT);
        return base.equals(ACTIVITY_JSON) || base.equals("application/ld+json");
    }
}

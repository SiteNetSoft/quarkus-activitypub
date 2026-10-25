package org.sitenetsoft.activitystreams;

import java.time.Instant;

/** Activity Streams {@code Tombstone}: stands in for a deleted object. */
public class Tombstone extends ASObject {

    private String formerType;
    private Instant deleted;

    public Tombstone() {
        type("Tombstone");
    }

    public String formerType() { return formerType; }
    public Tombstone formerType(String v) { this.formerType = v; return this; }

    public Instant deleted() { return deleted; }
    public Tombstone deleted(Instant v) { this.deleted = v; return this; }
}

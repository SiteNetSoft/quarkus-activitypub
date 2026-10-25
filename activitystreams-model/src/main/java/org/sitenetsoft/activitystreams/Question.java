package org.sitenetsoft.activitystreams;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.sitenetsoft.activitystreams.json.SingleOrList;

/** Activity Streams {@code Question}: a poll. */
public class Question extends IntransitiveActivity {

    @SingleOrList
    private List<ObjectOrLink> oneOf = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> anyOf = new ArrayList<>();
    private Instant closed;

    public Question() {
        type("Question");
    }

    public List<ObjectOrLink> oneOf() { return oneOf; }
    public Question oneOf(ObjectOrLink... v) { this.oneOf = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> anyOf() { return anyOf; }
    public Question anyOf(ObjectOrLink... v) { this.anyOf = new ArrayList<>(List.of(v)); return this; }

    public Instant closed() { return closed; }
    public Question closed(Instant v) { this.closed = v; return this; }
}

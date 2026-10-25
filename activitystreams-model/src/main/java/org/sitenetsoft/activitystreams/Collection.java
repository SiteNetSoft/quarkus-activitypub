package org.sitenetsoft.activitystreams;

import java.util.ArrayList;
import java.util.List;
import org.sitenetsoft.activitystreams.json.SingleOrList;

/** Activity Streams {@code Collection}. */
public class Collection extends ASObject {

    private Long totalItems;
    private ObjectOrLink first;
    private ObjectOrLink last;
    private ObjectOrLink current;
    @SingleOrList
    private List<ObjectOrLink> items = new ArrayList<>();

    public Collection() {
        type("Collection");
    }

    public Long totalItems() { return totalItems; }
    public Collection totalItems(Long v) { this.totalItems = v; return this; }

    public ObjectOrLink first() { return first; }
    public Collection first(ObjectOrLink v) { this.first = v; return this; }

    public ObjectOrLink last() { return last; }
    public Collection last(ObjectOrLink v) { this.last = v; return this; }

    public ObjectOrLink current() { return current; }
    public Collection current(ObjectOrLink v) { this.current = v; return this; }

    public List<ObjectOrLink> items() { return items; }
    public Collection items(ObjectOrLink... v) { this.items = new ArrayList<>(List.of(v)); return this; }
}

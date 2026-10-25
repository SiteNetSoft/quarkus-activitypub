package org.sitenetsoft.activitystreams;

import java.util.ArrayList;
import java.util.List;
import org.sitenetsoft.activitystreams.json.SingleOrList;

/** Activity Streams {@code OrderedCollection}; members live in {@link #orderedItems()}. */
public class OrderedCollection extends Collection {

    @SingleOrList
    private List<ObjectOrLink> orderedItems = new ArrayList<>();

    public OrderedCollection() {
        type("OrderedCollection");
    }

    public List<ObjectOrLink> orderedItems() { return orderedItems; }
    public OrderedCollection orderedItems(ObjectOrLink... v) {
        this.orderedItems = new ArrayList<>(List.of(v));
        return this;
    }
}

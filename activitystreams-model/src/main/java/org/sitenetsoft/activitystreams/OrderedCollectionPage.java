package org.sitenetsoft.activitystreams;

import java.util.ArrayList;
import java.util.List;
import org.sitenetsoft.activitystreams.json.SingleOrList;

/** Activity Streams {@code OrderedCollectionPage}; members live in {@link #orderedItems()}. */
public class OrderedCollectionPage extends CollectionPage {

    @SingleOrList
    private List<ObjectOrLink> orderedItems = new ArrayList<>();

    public OrderedCollectionPage() {
        type("OrderedCollectionPage");
    }

    public List<ObjectOrLink> orderedItems() { return orderedItems; }
    public OrderedCollectionPage orderedItems(ObjectOrLink... v) {
        this.orderedItems = new ArrayList<>(List.of(v));
        return this;
    }
}

package org.sitenetsoft.activitystreams;

/** Activity Streams {@code CollectionPage}. */
public class CollectionPage extends Collection {

    private ObjectOrLink partOf;
    private ObjectOrLink next;
    private ObjectOrLink prev;
    private Integer startIndex;

    public CollectionPage() {
        type("CollectionPage");
    }

    public ObjectOrLink partOf() { return partOf; }
    public CollectionPage partOf(ObjectOrLink v) { this.partOf = v; return this; }

    public ObjectOrLink next() { return next; }
    public CollectionPage next(ObjectOrLink v) { this.next = v; return this; }

    public ObjectOrLink prev() { return prev; }
    public CollectionPage prev(ObjectOrLink v) { this.prev = v; return this; }

    public Integer startIndex() { return startIndex; }
    public CollectionPage startIndex(Integer v) { this.startIndex = v; return this; }
}

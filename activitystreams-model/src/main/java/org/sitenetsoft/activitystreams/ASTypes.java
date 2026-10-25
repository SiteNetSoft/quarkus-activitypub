package org.sitenetsoft.activitystreams;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registry mapping Activity Streams type names to Java classes. */
public final class ASTypes {

    private final Map<String, Class<? extends ASObject>> byName = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> byClass = new ConcurrentHashMap<>();

    /** A registry holding the built-in types. */
    public static ASTypes defaults() {
        return new ASTypes()
                .register("Object", ASObject.class)
                .register("Link", ASLink.class)
                .register("Activity", Activity.class)
                .register("IntransitiveActivity", IntransitiveActivity.class)
                .register("Collection", Collection.class)
                .register("OrderedCollection", OrderedCollection.class)
                .register("CollectionPage", CollectionPage.class)
                .register("OrderedCollectionPage", OrderedCollectionPage.class)
                .register("Person", Person.class)
                .register("Application", Application.class)
                .register("Group", Group.class)
                .register("Organization", Organization.class)
                .register("Service", Service.class)
                .register("PublicKey", PublicKey.class)
                .register("Note", Note.class)
                .register("Article", Article.class)
                .register("Document", Document.class)
                .register("Image", Image.class)
                .register("Video", Video.class)
                .register("Audio", Audio.class)
                .register("Page", Page.class)
                .register("Event", Event.class)
                .register("Place", Place.class)
                .register("Profile", Profile.class)
                .register("Tombstone", Tombstone.class)
                .register("Relationship", Relationship.class)
                .register("Mention", Mention.class)
                .register("Hashtag", Hashtag.class)
                .register("Question", Question.class)
                .register("Accept", Accept.class)
                .register("Add", Add.class)
                .register("Announce", Announce.class)
                .register("Block", Block.class)
                .register("Create", Create.class)
                .register("Delete", Delete.class)
                .register("Dislike", Dislike.class)
                .register("Flag", Flag.class)
                .register("Follow", Follow.class)
                .register("Ignore", Ignore.class)
                .register("Invite", Invite.class)
                .register("Join", Join.class)
                .register("Leave", Leave.class)
                .register("Like", Like.class)
                .register("Listen", Listen.class)
                .register("Move", Move.class)
                .register("Offer", Offer.class)
                .register("Read", Read.class)
                .register("Reject", Reject.class)
                .register("Remove", Remove.class)
                .register("TentativeAccept", TentativeAccept.class)
                .register("TentativeReject", TentativeReject.class)
                .register("Undo", Undo.class)
                .register("Update", Update.class)
                .register("View", View.class)
                .register("Arrive", Arrive.class)
                .register("Travel", Travel.class);
    }

    public ASTypes register(String type, Class<? extends ASObject> cls) {
        byName.put(type, cls);
        byClass.putIfAbsent(cls, type);
        return this;
    }

    /** Class for a type name; a prefixed name such as {@code toot:Emoji} also matches its local name {@code Emoji}. */
    public Optional<Class<? extends ASObject>> classFor(String type) {
        if (type == null) {
            return Optional.empty();
        }
        Class<? extends ASObject> cls = byName.get(type);
        if (cls == null) {
            int colon = type.indexOf(':');
            if (colon >= 0 && colon < type.length() - 1) {
                cls = byName.get(type.substring(colon + 1));
            }
        }
        return Optional.ofNullable(cls);
    }

    /** The type name a class was first registered under. */
    public Optional<String> typeFor(Class<?> cls) {
        return Optional.ofNullable(byClass.get(cls));
    }
}

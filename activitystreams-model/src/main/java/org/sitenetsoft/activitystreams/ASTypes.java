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
                .register("Link", ASLink.class);
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

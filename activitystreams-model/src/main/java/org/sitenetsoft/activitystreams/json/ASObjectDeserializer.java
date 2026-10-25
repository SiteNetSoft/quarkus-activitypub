package org.sitenetsoft.activitystreams.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.ASTypes;

/**
 * Type-driven deserializer for {@link ASObject} and its subclasses.
 * <p>
 * {@link org.sitenetsoft.activitystreams.ASModule} installs it through a {@code BeanDeserializerModifier}, wrapping the
 * regular {@code BeanDeserializer} of every {@code ASObject} class. It buffers the JSON object as a tree, picks the
 * first {@code type} entry known to {@link ASTypes} (prefixed names such as {@code toot:Emoji} also match the local
 * name) whose class is assignable to the requested class, and hands the tree to that class's plain bean deserializer
 * (unwrapping it from this wrapper, so there is no recursion). Unknown or missing types fall back to the requested
 * class, i.e. {@code ASObject} when reading {@code ASObject}.
 */
public final class ASObjectDeserializer extends DelegatingDeserializer {

    private final ASTypes types;

    public ASObjectDeserializer(ASTypes types, JsonDeserializer<?> beanDeserializer) {
        super(beanDeserializer);
        this.types = types;
    }

    @Override
    protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
        return new ASObjectDeserializer(types, newDelegatee);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode tree = ctxt.readTree(p);
        JsonDeserializer<?> target = _delegatee;
        if (tree.isObject()) {
            Class<?> requested = handledType();
            Class<?> resolved = resolve(tree.get("type"), requested);
            if (resolved != requested) {
                JsonDeserializer<Object> found = ctxt.findRootValueDeserializer(ctxt.constructType(resolved));
                target = found instanceof ASObjectDeserializer wrapper ? wrapper.getDelegatee() : found;
            }
        }
        try (JsonParser tp = tree.traverse(p.getCodec())) {
            tp.nextToken();
            return target.deserialize(tp, ctxt);
        }
    }

    private Class<?> resolve(JsonNode typeNode, Class<?> requested) {
        for (String name : typeNames(typeNode)) {
            Class<? extends ASObject> cls = types.classFor(name).orElse(null);
            if (cls != null && requested.isAssignableFrom(cls)) {
                return cls;
            }
        }
        return requested;
    }

    private static List<String> typeNames(JsonNode typeNode) {
        List<String> names = new ArrayList<>();
        if (typeNode == null) {
            return names;
        }
        if (typeNode.isTextual()) {
            names.add(typeNode.asText());
        } else if (typeNode.isArray()) {
            typeNode.forEach(n -> {
                if (n.isTextual()) {
                    names.add(n.asText());
                }
            });
        }
        return names;
    }
}

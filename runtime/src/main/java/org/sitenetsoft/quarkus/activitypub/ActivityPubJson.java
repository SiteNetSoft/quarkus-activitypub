package org.sitenetsoft.quarkus.activitypub;

import java.io.IOException;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Activity Streams JSON through the Quarkus {@link ObjectMapper} (with {@code ASModule}, see
 * {@link ActivityPubObjectMapperCustomizer}). A written root object without {@code @context} gets the default one.
 */
@ApplicationScoped
public class ActivityPubJson {

    @Inject
    ObjectMapper mapper;

    public byte[] write(ASObject object) {
        try {
            return mapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + object.primaryType() + " " + object.id(), e);
        }
    }

    /**
     * Like {@link #write(ASObject)}, but each property in {@code singleValued}, at any depth, that holds exactly one
     * element is written as that element instead of a one-element array (the model writes multi-valued properties as arrays;
     * some consumers read e.g. an actor's {@code publicKey} only as an object).
     */
    public byte[] write(ASObject object, Set<String> singleValued) {
        byte[] bytes = write(object);
        try {
            JsonNode tree = mapper.readTree(bytes);
            unwrap(tree, singleValued);
            return mapper.writeValueAsBytes(tree);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialise " + object.primaryType() + " " + object.id(), e);
        }
    }

    private static void unwrap(JsonNode node, Set<String> singleValued) {
        if (node instanceof ObjectNode object) {
            for (String property : singleValued) {
                JsonNode value = object.get(property);
                if (value != null && value.isArray() && value.size() == 1) {
                    object.set(property, value.get(0));
                }
            }
            object.forEach(child -> unwrap(child, singleValued));
        } else if (node.isArray()) {
            node.forEach(child -> unwrap(child, singleValued));
        }
    }

    /** Parses an object of any type; the concrete class follows its {@code type}. */
    public ASObject read(byte[] json) {
        return read(json, ASObject.class);
    }

    /** @throws ActivityPubException with {@code INVALID_DOCUMENT} when the bytes are not such an object */
    public <T extends ASObject> T read(byte[] json, Class<T> type) {
        try {
            T value = mapper.readValue(json, type);
            if (value == null) {
                throw new ActivityPubException(ActivityPubException.Reason.INVALID_DOCUMENT, "Empty JSON document");
            }
            return value;
        } catch (IOException | IllegalArgumentException e) {
            throw new ActivityPubException(ActivityPubException.Reason.INVALID_DOCUMENT,
                    "Not an Activity Streams " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}

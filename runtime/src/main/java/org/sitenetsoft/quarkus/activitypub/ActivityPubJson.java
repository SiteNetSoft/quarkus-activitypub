package org.sitenetsoft.quarkus.activitypub;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

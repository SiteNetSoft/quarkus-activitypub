package org.sitenetsoft.activitystreams.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.ObjectOrLink;

/**
 * Reads a bare string as {@link ObjectOrLink.Ref} and an object as {@link ObjectOrLink.Embedded} (resolved to its
 * registered class through the {@link ASObject} deserializer). Arrays are handled by the enclosing {@code List} field.
 */
public final class ObjectOrLinkDeserializer extends StdDeserializer<ObjectOrLink> {

    public ObjectOrLinkDeserializer() {
        super(ObjectOrLink.class);
    }

    @Override
    public ObjectOrLink deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            String text = p.getText();
            try {
                return new ObjectOrLink.Ref(new URI(text));
            } catch (URISyntaxException e) {
                return (ObjectOrLink) ctxt.handleWeirdStringValue(ObjectOrLink.class, text, "not a URI: %s",
                        e.getMessage());
            }
        }
        if (t == JsonToken.START_OBJECT || t == JsonToken.FIELD_NAME) {
            return new ObjectOrLink.Embedded(ctxt.readValue(p, ASObject.class));
        }
        return (ObjectOrLink) ctxt.handleUnexpectedToken(ObjectOrLink.class, p);
    }
}

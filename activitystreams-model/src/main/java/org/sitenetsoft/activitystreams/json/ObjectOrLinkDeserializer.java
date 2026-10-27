package org.sitenetsoft.activitystreams.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.net.URI;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.ObjectOrLink;

/**
 * Reads a bare string as {@link ObjectOrLink.Ref} and an object as {@link ObjectOrLink.Embedded} (resolved to its
 * registered class through the {@link ASObject} deserializer). Arrays are handled by the enclosing {@code List} field.
 * <p>
 * A string that is not a valid URI is repaired leniently (illegal characters in the path, query and fragment are
 * percent-encoded, see {@link LenientUris}); if it still cannot be parsed the value is dropped (logged at DEBUG) and
 * this deserializer returns null: a list element is skipped, a single value stays null, the document still parses.
 */
public final class ObjectOrLinkDeserializer extends StdDeserializer<ObjectOrLink> {

    private static final System.Logger LOG = System.getLogger(ObjectOrLinkDeserializer.class.getName());

    public ObjectOrLinkDeserializer() {
        super(ObjectOrLink.class);
    }

    @Override
    public ObjectOrLink deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            String text = p.getText();
            URI uri = LenientUris.parse(text);
            if (uri == null) {
                LOG.log(System.Logger.Level.DEBUG, "Dropping unparseable URI value {0}", text);
                return null;
            }
            return new ObjectOrLink.Ref(uri);
        }
        if (t == JsonToken.START_OBJECT || t == JsonToken.FIELD_NAME) {
            return new ObjectOrLink.Embedded(ctxt.readValue(p, ASObject.class));
        }
        return (ObjectOrLink) ctxt.handleUnexpectedToken(ObjectOrLink.class, p);
    }
}

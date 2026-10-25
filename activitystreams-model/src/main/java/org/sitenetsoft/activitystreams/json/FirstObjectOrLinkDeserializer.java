package org.sitenetsoft.activitystreams.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import org.sitenetsoft.activitystreams.ObjectOrLink;

/** Like {@link ObjectOrLinkDeserializer}, but an array is accepted and reduced to its first element (empty gives null). */
public final class FirstObjectOrLinkDeserializer extends StdDeserializer<ObjectOrLink> {

    private final ObjectOrLinkDeserializer single = new ObjectOrLinkDeserializer();

    public FirstObjectOrLinkDeserializer() {
        super(ObjectOrLink.class);
    }

    @Override
    public ObjectOrLink deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            return single.deserialize(p, ctxt);
        }
        JsonNode array = ctxt.readTree(p);
        for (JsonNode element : array) {
            if (element.isNull()) {
                continue;
            }
            try (JsonParser ep = element.traverse(p.getCodec())) {
                ep.nextToken();
                return single.deserialize(ep, ctxt);
            }
        }
        return null;
    }
}

package org.sitenetsoft.activitystreams.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.sitenetsoft.activitystreams.ObjectOrLink;

/** Writes a {@link ObjectOrLink.Ref} as a bare string and an {@link ObjectOrLink.Embedded} as the object. */
public final class ObjectOrLinkSerializer extends StdSerializer<ObjectOrLink> {

    public ObjectOrLinkSerializer() {
        super(ObjectOrLink.class);
    }

    @Override
    public void serialize(ObjectOrLink value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        switch (value) {
            case ObjectOrLink.Ref r -> gen.writeString(r.href().toString());
            case ObjectOrLink.Embedded e -> provider.defaultSerializeValue(e.value(), gen);
        }
    }
}

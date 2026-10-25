package org.sitenetsoft.activitystreams;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.net.URI;
import java.util.Optional;
import org.sitenetsoft.activitystreams.json.FirstObjectOrLinkDeserializer;

/** An Activity Streams {@code Activity}: an {@link IntransitiveActivity} with a direct {@code object}. */
public class Activity extends IntransitiveActivity {

    @JsonDeserialize(using = FirstObjectOrLinkDeserializer.class)
    private ObjectOrLink object;

    public Activity() {
        type("Activity");
    }

    public ObjectOrLink object() { return object; }
    public Activity object(ObjectOrLink v) { this.object = v; return this; }

    @Override
    public Activity actor(ObjectOrLink v) { super.actor(v); return this; }

    public Optional<URI> objectUri() {
        return object == null ? Optional.empty() : object.uri();
    }

    public Optional<ASObject> embeddedObject() {
        return object == null ? Optional.empty() : object.object();
    }
}

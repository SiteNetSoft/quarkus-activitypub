package org.sitenetsoft.activitystreams;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.net.URI;
import java.util.Optional;
import org.sitenetsoft.activitystreams.json.FirstObjectOrLinkDeserializer;

/** An activity without a direct {@code object}. Array values on the wire are reduced to their first element. */
public class IntransitiveActivity extends ASObject {

    @JsonDeserialize(using = FirstObjectOrLinkDeserializer.class)
    private ObjectOrLink actor;
    @JsonDeserialize(using = FirstObjectOrLinkDeserializer.class)
    private ObjectOrLink target;
    @JsonDeserialize(using = FirstObjectOrLinkDeserializer.class)
    private ObjectOrLink result;
    @JsonDeserialize(using = FirstObjectOrLinkDeserializer.class)
    private ObjectOrLink origin;
    @JsonDeserialize(using = FirstObjectOrLinkDeserializer.class)
    private ObjectOrLink instrument;

    public IntransitiveActivity() {
        type("IntransitiveActivity");
    }

    public ObjectOrLink actor() { return actor; }
    public IntransitiveActivity actor(ObjectOrLink v) { this.actor = v; return this; }

    public ObjectOrLink target() { return target; }
    public IntransitiveActivity target(ObjectOrLink v) { this.target = v; return this; }

    public ObjectOrLink result() { return result; }
    public IntransitiveActivity result(ObjectOrLink v) { this.result = v; return this; }

    public ObjectOrLink origin() { return origin; }
    public IntransitiveActivity origin(ObjectOrLink v) { this.origin = v; return this; }

    public ObjectOrLink instrument() { return instrument; }
    public IntransitiveActivity instrument(ObjectOrLink v) { this.instrument = v; return this; }

    public Optional<URI> actorUri() {
        return actor == null ? Optional.empty() : actor.uri();
    }
}

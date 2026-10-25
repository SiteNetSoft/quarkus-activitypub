package org.sitenetsoft.activitystreams;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** The {@code endpoints} object of an actor. Endpoints other than {@code sharedInbox} are kept in {@link #extra()}. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class Endpoints {

    private URI sharedInbox;
    private transient Map<String, JsonNode> extra = new LinkedHashMap<>();

    public URI sharedInbox() { return sharedInbox; }
    public Endpoints sharedInbox(URI v) { this.sharedInbox = v; return this; }

    @JsonAnyGetter
    public Map<String, JsonNode> extra() { return extra; }

    @JsonAnySetter
    public Endpoints extra(String key, JsonNode value) { extra.put(key, value); return this; }
}

package org.sitenetsoft.activitystreams;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.sitenetsoft.activitystreams.json.SingleOrList;

/**
 * Base Activity Streams object. Tolerant on input (single value or array, bare URI or embedded object, unknown
 * properties kept in {@link #extra()}), canonical on output (nulls and empty values omitted, single {@code type}
 * written as a bare string).
 * <p>
 * Jackson binds fields directly (fluent accessors are not bean getters); subclasses inherit that visibility.
 * List-valued properties are never {@code null}: the accessor returns the live, mutable list.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({"@context", "id", "type"})
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class ASObject {

    @JsonProperty("@context")
    private JsonNode context;
    private URI id;
    @JsonFormat(with = {JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
            JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED})
    @JsonSetter(nulls = Nulls.AS_EMPTY, contentNulls = Nulls.SKIP)
    private List<String> type = new ArrayList<>();
    private String name;
    private Map<String, String> nameMap;
    private String content;
    private Map<String, String> contentMap;
    private String summary;
    private String mediaType;
    private Instant published;
    private Instant updated;
    @SingleOrList
    private List<ObjectOrLink> url = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> attributedTo = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> to = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> cc = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> bto = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> bcc = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> audience = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> inReplyTo = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> tag = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> attachment = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> icon = new ArrayList<>();
    @SingleOrList
    private List<ObjectOrLink> image = new ArrayList<>();
    private ObjectOrLink replies;
    private ObjectOrLink likes;
    private ObjectOrLink shares;
    private Boolean sensitive;
    private String duration;
    private Instant startTime;
    private Instant endTime;

    /** Unknown properties; {@code transient} so Jackson does not also bind it as a regular field. */
    private transient Map<String, JsonNode> extra = new LinkedHashMap<>();

    public JsonNode context() { return context; }
    public ASObject context(JsonNode v) { this.context = v; return this; }

    public URI id() { return id; }
    public ASObject id(URI v) { this.id = v; return this; }

    public List<String> type() { return type; }
    public ASObject type(String... v) { this.type = new ArrayList<>(List.of(v)); return this; }

    public String name() { return name; }
    public ASObject name(String v) { this.name = v; return this; }

    public Map<String, String> nameMap() { return nameMap; }
    public ASObject nameMap(Map<String, String> v) { this.nameMap = v; return this; }

    public String content() { return content; }
    public ASObject content(String v) { this.content = v; return this; }

    public Map<String, String> contentMap() { return contentMap; }
    public ASObject contentMap(Map<String, String> v) { this.contentMap = v; return this; }

    public String summary() { return summary; }
    public ASObject summary(String v) { this.summary = v; return this; }

    public String mediaType() { return mediaType; }
    public ASObject mediaType(String v) { this.mediaType = v; return this; }

    public Instant published() { return published; }
    public ASObject published(Instant v) { this.published = v; return this; }

    public Instant updated() { return updated; }
    public ASObject updated(Instant v) { this.updated = v; return this; }

    public List<ObjectOrLink> url() { return url; }
    public ASObject url(ObjectOrLink... v) { this.url = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> attributedTo() { return attributedTo; }
    public ASObject attributedTo(ObjectOrLink... v) { this.attributedTo = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> to() { return to; }
    public ASObject to(ObjectOrLink... v) { this.to = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> cc() { return cc; }
    public ASObject cc(ObjectOrLink... v) { this.cc = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> bto() { return bto; }
    public ASObject bto(ObjectOrLink... v) { this.bto = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> bcc() { return bcc; }
    public ASObject bcc(ObjectOrLink... v) { this.bcc = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> audience() { return audience; }
    public ASObject audience(ObjectOrLink... v) { this.audience = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> inReplyTo() { return inReplyTo; }
    public ASObject inReplyTo(ObjectOrLink... v) { this.inReplyTo = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> tag() { return tag; }
    public ASObject tag(ObjectOrLink... v) { this.tag = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> attachment() { return attachment; }
    public ASObject attachment(ObjectOrLink... v) { this.attachment = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> icon() { return icon; }
    public ASObject icon(ObjectOrLink... v) { this.icon = new ArrayList<>(List.of(v)); return this; }

    public List<ObjectOrLink> image() { return image; }
    public ASObject image(ObjectOrLink... v) { this.image = new ArrayList<>(List.of(v)); return this; }

    public ObjectOrLink replies() { return replies; }
    public ASObject replies(ObjectOrLink v) { this.replies = v; return this; }

    public ObjectOrLink likes() { return likes; }
    public ASObject likes(ObjectOrLink v) { this.likes = v; return this; }

    public ObjectOrLink shares() { return shares; }
    public ASObject shares(ObjectOrLink v) { this.shares = v; return this; }

    public Boolean sensitive() { return sensitive; }
    public ASObject sensitive(Boolean v) { this.sensitive = v; return this; }

    public String duration() { return duration; }
    public ASObject duration(String v) { this.duration = v; return this; }

    public Instant startTime() { return startTime; }
    public ASObject startTime(Instant v) { this.startTime = v; return this; }

    public Instant endTime() { return endTime; }
    public ASObject endTime(Instant v) { this.endTime = v; return this; }

    @JsonAnyGetter
    public Map<String, JsonNode> extra() { return extra; }

    @JsonAnySetter
    public ASObject extra(String key, JsonNode value) { extra.put(key, value); return this; }

    /** The first {@code type} entry, or {@code null} when the object has no type. */
    public String primaryType() {
        return type.isEmpty() ? null : type.get(0);
    }

    /** True when any audience property addresses the public collection (in any spelling). */
    public boolean isPublic() {
        return audienceUris().stream().anyMatch(Audience::isPublic);
    }

    /** URIs of {@code to}, {@code cc}, {@code bto}, {@code bcc} and {@code audience}; embedded entries without id skipped. */
    public Set<URI> audienceUris() {
        Set<URI> out = new LinkedHashSet<>();
        Stream.of(to, cc, bto, bcc, audience)
                .flatMap(List::stream)
                .forEach(o -> o.uri().ifPresent(out::add));
        return out;
    }

    /** Deep copy via a JSON round trip, keeping the runtime class. */
    public ASObject copy() {
        ObjectMapper m = ASJson.defaultMapper();
        ASObject copy = m.convertValue(m.valueToTree(this), getClass());
        copy.context = context; // the tree carries the default @context written for a root object; do not adopt it
        return copy;
    }
}

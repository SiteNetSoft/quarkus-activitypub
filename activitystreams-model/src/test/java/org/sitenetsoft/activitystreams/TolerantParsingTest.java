package org.sitenetsoft.activitystreams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TolerantParsingTest {

    /** Test-only subclass used to exercise registry-driven resolution. */
    static class Emoji extends ASObject {
    }

    private final ObjectMapper mapper = ASJson.mapper();

    private ASObject read(String json) throws Exception {
        return mapper.readValue(json, ASObject.class);
    }

    private JsonNode write(ASObject o) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(o));
    }

    @Test
    void singleValueBecomesList() throws Exception {
        ASObject o = read("{\"type\":\"Note\",\"to\":\"https://a/b\"}");
        assertEquals(1, o.to().size());
        assertEquals(URI.create("https://a/b"), o.to().get(0).uri().orElseThrow());
    }

    @Test
    void arrayStaysList() throws Exception {
        ASObject o = read("{\"type\":\"Note\",\"to\":[\"https://a/b\",\"https://a/c\"]}");
        assertEquals(2, o.to().size());
        assertEquals(Set.of(URI.create("https://a/b"), URI.create("https://a/c")), o.audienceUris());
    }

    @Test
    void stringObjectBecomesRef() throws Exception {
        ASObject o = read("{\"type\":\"Note\",\"inReplyTo\":\"https://x\"}");
        ObjectOrLink ref = o.inReplyTo().get(0);
        ObjectOrLink.Ref r = assertInstanceOf(ObjectOrLink.Ref.class, ref);
        assertEquals(URI.create("https://x"), r.href());
        assertTrue(ref.object().isEmpty());
    }

    @Test
    void embeddedObjectBecomesEmbedded() throws Exception {
        ASObject o = read("{\"type\":\"Note\",\"inReplyTo\":{\"id\":\"https://x/1\",\"type\":\"Note\",\"content\":\"hi\"}}");
        ObjectOrLink e = o.inReplyTo().get(0);
        ObjectOrLink.Embedded emb = assertInstanceOf(ObjectOrLink.Embedded.class, e);
        assertEquals("hi", emb.value().content());
        assertEquals(URI.create("https://x/1"), e.uri().orElseThrow());
    }

    @Test
    void asPublicShortFormIsPublic() throws Exception {
        assertTrue(read("{\"type\":\"Note\",\"to\":\"as:Public\"}").isPublic());
        assertTrue(read("{\"type\":\"Note\",\"cc\":[\"https://a/followers\",\"Public\"]}").isPublic());
    }

    @Test
    void fullPublicUriIsPublic() throws Exception {
        assertTrue(read("{\"type\":\"Note\",\"to\":\"https://www.w3.org/ns/activitystreams#Public\"}").isPublic());
        assertFalse(read("{\"type\":\"Note\",\"to\":\"https://a/followers\"}").isPublic());
    }

    @Test
    void unknownTypeKeepsTypeAndExtra() throws Exception {
        ASObject o = read("{\"type\":\"Whatever\",\"foo\":1}");
        assertSame(ASObject.class, o.getClass());
        assertEquals("Whatever", o.primaryType());
        assertEquals(1, o.extra().get("foo").asInt());
    }

    @Test
    void missingTypeIsObject() throws Exception {
        ASObject o = read("{\"id\":\"https://a/1\",\"name\":\"n\"}");
        assertSame(ASObject.class, o.getClass());
        assertNull(o.primaryType());
        assertEquals("n", o.name());
    }

    @Test
    void knownTypeResolvesRegisteredClass() throws Exception {
        ASObject o = read("{\"type\":\"Link\",\"href\":\"https://a/img.png\",\"mediaType\":\"image/png\"}");
        ASLink link = assertInstanceOf(ASLink.class, o);
        assertEquals(URI.create("https://a/img.png"), link.href());
        assertEquals("image/png", link.mediaType());

        ASObject note = read("{\"type\":\"Note\",\"tag\":{\"type\":\"Link\",\"href\":\"https://a/t\"}}");
        assertInstanceOf(ASLink.class, note.tag().get(0).object().orElseThrow());
    }

    @Test
    void prefixedTypeMatchesLocalName() throws Exception {
        ObjectMapper m = ASJson.mapper(ASTypes.defaults().register("Emoji", Emoji.class));
        ASObject o = m.readValue("{\"type\":[\"Unknown\",\"toot:Emoji\"],\"name\":\":blob:\"}", ASObject.class);
        assertInstanceOf(Emoji.class, o);
        assertEquals("Unknown", o.primaryType());
        assertEquals(":blob:", o.name());
    }

    @Test
    void unknownPropertiesRoundTrip() throws Exception {
        ASObject o = read("{\"type\":\"Whatever\",\"foo\":{\"bar\":[1,2]}}");
        JsonNode out = write(o);
        assertEquals(mapper.readTree("{\"bar\":[1,2]}"), out.get("foo"));
    }

    @Test
    void contextKeptVerbatim() throws Exception {
        String ctx = "[\"https://www.w3.org/ns/activitystreams\",{\"toot\":\"http://joinmastodon.org/ns#\"}]";
        ASObject o = read("{\"@context\":" + ctx + ",\"type\":\"Note\"}");
        JsonNode out = write(o);
        assertEquals(mapper.readTree(ctx), out.get("@context"));
        assertFalse(o.extra().containsKey("@context"));
    }

    @Test
    void singleTypeSerialisesAsString() throws Exception {
        JsonNode out = write(read("{\"type\":[\"Note\"]}"));
        assertTrue(out.get("type").isTextual());
        assertEquals("Note", out.get("type").asText());
        assertTrue(write(new ASObject().type("Note", "Other")).get("type").isArray());
    }

    @Test
    void emptyListsOmitted() throws Exception {
        ASObject o = new ASObject().id(URI.create("https://a/1"));
        JsonNode out = write(o);
        assertEquals(List.of("id"), iterableToList(out.fieldNames()));
    }

    @Test
    void canonicalOrderAndIsoInstant() throws Exception {
        ASObject o = new ASObject().published(java.time.Instant.parse("2026-10-25T10:00:00Z"))
                .type("Note").id(URI.create("https://a/1"))
                .context(mapper.readTree("\"https://www.w3.org/ns/activitystreams\""));
        String json = mapper.writeValueAsString(o);
        assertEquals("{\"@context\":\"https://www.w3.org/ns/activitystreams\",\"id\":\"https://a/1\","
                + "\"type\":\"Note\",\"published\":\"2026-10-25T10:00:00Z\"}", json);
        assertEquals(o.published(), read(json).published());
    }

    @Test
    void copyIsDeep() throws Exception {
        ASObject o = read("{\"type\":\"Note\",\"to\":\"https://a/b\",\"foo\":{\"x\":1}}");
        ASObject c = o.copy();
        assertNotSame(o, c);
        assertEquals(write(o), write(c));
        c.to().clear();
        assertEquals(1, o.to().size());
    }

    private static List<String> iterableToList(java.util.Iterator<String> it) {
        List<String> l = new java.util.ArrayList<>();
        it.forEachRemaining(l::add);
        return l;
    }
}

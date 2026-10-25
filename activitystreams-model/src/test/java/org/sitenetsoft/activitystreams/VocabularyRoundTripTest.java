package org.sitenetsoft.activitystreams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VocabularyRoundTripTest {

    private final ObjectMapper mapper = ASJson.mapper();

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("mastodon-actor.json", Person.class),
                Arguments.of("mastodon-note-create.json", Create.class),
                Arguments.of("follow.json", Follow.class),
                Arguments.of("accept-follow.json", Accept.class),
                Arguments.of("undo-follow.json", Undo.class),
                Arguments.of("delete-actor.json", Delete.class),
                Arguments.of("announce.json", Announce.class),
                Arguments.of("question.json", Question.class),
                Arguments.of("ordered-collection-page.json", OrderedCollectionPage.class),
                Arguments.of("spec-example-person.json", Person.class),
                Arguments.of("spec-example-create.json", Create.class));
    }

    private JsonNode fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(in, name);
            return mapper.readTree(in);
        }
    }

    /**
     * Canonical form for comparison. Output omits nulls and empty values and writes multi-valued properties as arrays,
     * so a null or empty value is dropped and a one-element array is equated with its element on both sides. Key order
     * is irrelevant (object nodes compare unordered); numbers compare by value, everything else, including {@code @context}, must match exactly.
     */
    static JsonNode normalize(JsonNode n) {
        if (n.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            n.fields().forEachRemaining(e -> {
                JsonNode v = normalize(e.getValue());
                if (!v.isNull() && !(v.isContainerNode() && v.isEmpty())) {
                    out.set(e.getKey(), v);
                }
            });
            return out;
        }
        if (n.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            n.forEach(e -> {
                JsonNode v = normalize(e);
                if (!v.isNull()) {
                    out.add(v);
                }
            });
            return out.size() == 1 ? out.get(0) : out;
        }
        return n.isIntegralNumber() ? JsonNodeFactory.instance.numberNode(n.longValue()) : n;
    }

    @ParameterizedTest(name = "{0} parses to {1}")
    @MethodSource("fixtures")
    void fixtureParsesToExpectedClass(String file, Class<? extends ASObject> expected) throws Exception {
        ASObject parsed = mapper.treeToValue(fixture(file), ASObject.class);
        assertEquals(expected, parsed.getClass());
    }

    @ParameterizedTest(name = "{0} round-trips")
    @MethodSource("fixtures")
    void fixtureRoundTripsWithoutLoss(String file, Class<? extends ASObject> expected) throws Exception {
        JsonNode original = fixture(file);
        ASObject parsed = mapper.treeToValue(original, ASObject.class);
        JsonNode written = mapper.readTree(mapper.writeValueAsString(parsed));
        ASObject reparsed = mapper.treeToValue(written, ASObject.class);
        assertEquals(normalize(original), normalize(written));
        assertEquals(normalize(original), normalize(mapper.valueToTree(reparsed)));
        if (original.has("@context")) {
            assertEquals(original.get("@context"), written.get("@context"));
        }
    }

    @Test
    void mastodonActorFieldsBound() throws Exception {
        Person p = (Person) mapper.treeToValue(fixture("mastodon-actor.json"), ASObject.class);
        assertEquals("alice", p.preferredUsername());
        assertEquals(Boolean.TRUE, p.discoverable());
        assertEquals(Boolean.FALSE, p.manuallyApprovesFollowers());
        assertEquals(URI.create("https://mastodon.example/users/alice/collections/featured"), p.featured());
        assertEquals(2, p.attachment().size());
        assertEquals("PropertyValue", p.attachment().get(0).object().orElseThrow().primaryType());
        assertInstanceOf(Image.class, p.icon().get(0).object().orElseThrow());
        assertEquals("https://mastodon.example/users/alice#main-key", p.mainKey().orElseThrow().id().toString());
        assertTrue(p.extra().containsKey("devices"));
    }

    @Test
    void mastodonNoteVocabulary() throws Exception {
        Create c = (Create) mapper.treeToValue(fixture("mastodon-note-create.json"), ASObject.class);
        Note note = assertInstanceOf(Note.class, c.embeddedObject().orElseThrow());
        assertEquals(Boolean.TRUE, note.sensitive());
        assertEquals(Map.of("en", note.contentMap().get("en")), note.contentMap());
        assertInstanceOf(Mention.class, note.tag().get(0).object().orElseThrow());
        assertInstanceOf(Hashtag.class, note.tag().get(1).object().orElseThrow());
        assertInstanceOf(Collection.class, note.replies().object().orElseThrow());
        assertTrue(note.inReplyTo().isEmpty());
        assertTrue(note.extra().containsKey("atomUri"));
        assertTrue(note.extra().containsKey("conversation"));
        assertTrue(c.isPublic());
    }

    @Test
    void activityHelpers() throws Exception {
        Delete d = (Delete) mapper.treeToValue(fixture("delete-actor.json"), ASObject.class);
        assertEquals(uri("https://mastodon.example/users/alice"), d.actorUri().orElseThrow());
        assertEquals(d.actorUri(), d.objectUri());
        assertTrue(d.embeddedObject().isEmpty());
    }

    private static URI uri(String s) {
        return URI.create(s);
    }

    @Test
    void actorArrayTakesFirstElement() throws Exception {
        Follow f = (Follow) mapper.readValue(
                "{\"type\":\"Follow\",\"actor\":[\"https://a/1\",\"https://a/2\"],\"object\":[{\"id\":\"https://b\","
                        + "\"type\":\"Person\"}]}", ASObject.class);
        assertEquals(URI.create("https://a/1"), f.actorUri().orElseThrow());
        assertInstanceOf(Person.class, f.embeddedObject().orElseThrow());
    }

    @Test
    void publicKeyArrayAccepted() throws Exception {
        Person p = (Person) mapper.readValue("""
                {"type":"Person","id":"https://a/u","publicKey":[{"id":"https://a/u#k","owner":"https://a/u",
                "publicKeyPem":"PEM"}]}""", ASObject.class);
        assertEquals("PEM", p.mainKey().orElseThrow().publicKeyPem());
    }

    @Test
    void endpointsSharedInboxPreferred() throws Exception {
        Person with = (Person) mapper.readValue("""
                {"type":"Person","inbox":"https://a/inbox","endpoints":{"sharedInbox":"https://a/shared",
                "oauthAuthorizationEndpoint":"https://a/oauth"}}""", ASObject.class);
        assertEquals(URI.create("https://a/shared"), with.deliveryInbox());
        assertTrue(with.endpoints().extra().containsKey("oauthAuthorizationEndpoint"));
        Person without = (Person) mapper.readValue("{\"type\":\"Person\",\"inbox\":\"https://a/inbox\"}",
                ASObject.class);
        assertEquals(URI.create("https://a/inbox"), without.deliveryInbox());
    }

    @Test
    void defaultContextWrittenOnRoot() throws Exception {
        JsonNode out = mapper.readTree(mapper.writeValueAsString(new Note()));
        assertEquals(Contexts.DEFAULT, out.get("@context"));
        assertEquals("Note", out.get("type").asText());
    }

    @Test
    void explicitContextIsNotOverridden() throws Exception {
        Note n = new Note();
        n.context(mapper.getNodeFactory().textNode(Contexts.AS));
        assertEquals(Contexts.AS, mapper.readTree(mapper.writeValueAsString(n)).get("@context").asText());
    }

    @Test
    void nestedObjectHasNoContext() throws Exception {
        Create create = new Create();
        create.object(ObjectOrLink.of(new Note().id(URI.create("https://a/n"))));
        JsonNode out = mapper.readTree(mapper.writeValueAsString(create));
        assertEquals(Contexts.DEFAULT, out.get("@context"));
        assertEquals("Note", out.get("object").get("type").asText());
        assertFalse(out.get("object").has("@context"));
    }

    @Test
    void listElementsAreNotRoot() throws Exception {
        JsonNode out = mapper.readTree(mapper.writeValueAsString(List.of(new Note())));
        assertFalse(out.get(0).has("@context"));
    }

    @Test
    void copyDoesNotAdoptDefaultContext() {
        assertNull(new Note().copy().context());
    }

    @Test
    void tootPrefixedTypeResolves() throws Exception {
        ASObject emoji = mapper.readValue("{\"type\":\"toot:Emoji\",\"name\":\":x:\"}", ASObject.class);
        assertEquals(ASObject.class, emoji.getClass());
        assertEquals("toot:Emoji", emoji.primaryType());
        ASObject person = mapper.readValue("{\"type\":[\"Person\",\"toot:Actor\"]}", ASObject.class);
        assertEquals(Person.class, person.getClass());
    }

    @Test
    void tombstoneDeletedInstant() throws Exception {
        Tombstone t = (Tombstone) mapper.readValue(
                "{\"type\":\"Tombstone\",\"formerType\":\"Note\",\"deleted\":\"2026-10-25T10:00:00Z\"}",
                ASObject.class);
        assertEquals("Note", t.formerType());
        assertEquals(Instant.parse("2026-10-25T10:00:00Z"), t.deleted());
        JsonNode out = mapper.readTree(mapper.writeValueAsString(t));
        assertEquals("2026-10-25T10:00:00Z", out.get("deleted").asText());
    }

    @Test
    void everyVocabularyClassIsRegisteredAndSelfTyped() throws Exception {
        List<ASObject> all = new ArrayList<>(List.of(new Note(), new Person(), new Follow(), new Arrive(),
                new Question(), new OrderedCollectionPage(), new Hashtag(), new Tombstone(), new TentativeAccept()));
        for (ASObject o : all) {
            ASObject back = mapper.readValue(mapper.writeValueAsString(o), ASObject.class);
            assertEquals(o.getClass(), back.getClass());
            assertEquals(o.getClass().getSimpleName(), o.primaryType());
        }
    }
}

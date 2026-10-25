package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Optional;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;

import io.quarkus.test.QuarkusUnitTest;

public class ActivityPubUrlsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.activitypub.base-url", "https://example.org");

    @Inject
    ActivityPubUrls urls;

    @Test
    void actorUrlUsesTemplate() {
        assertEquals(URI.create("https://example.org/users/alice"), urls.actor("alice"));
        assertEquals(URI.create("https://example.org/users/alice/inbox"), urls.inbox("alice"));
        assertEquals(URI.create("https://example.org/users/alice/outbox"), urls.outbox("alice"));
        assertEquals(URI.create("https://example.org/users/alice/followers"), urls.followers("alice"));
        assertEquals(URI.create("https://example.org/users/alice/following"), urls.following("alice"));
        assertEquals(URI.create("https://example.org/inbox"), urls.sharedInbox());
        assertEquals(URI.create("https://example.org/objects/01ABC"), urls.object("01ABC"));
        assertEquals(URI.create("https://example.org/activities/01ABC"), urls.activity("01ABC"));
        assertEquals(Optional.of("01ABC"), urls.objectIdOf(urls.object("01ABC")));
        assertEquals(Optional.of("01ABC"), urls.activityIdOf(urls.activity("01ABC")));
        assertEquals(Optional.empty(), urls.objectIdOf(urls.activity("01ABC")));
        assertEquals("example.org", urls.host());
    }

    @Test
    void usernameIsEncoded() {
        URI actor = urls.actor("a b");
        assertTrue(actor.toString().endsWith("/users/a%20b"), actor.toString());
        assertEquals(Optional.of("a b"), urls.usernameOf(actor));
        assertEquals(Optional.of("a+b"), urls.usernameOf(urls.actor("a+b")));
        assertEquals(Optional.empty(), urls.usernameOf(URI.create("https://example.org/users/alice/inbox")));
    }

    @Test
    void usernameOfForeignHostIsEmpty() {
        assertEquals(Optional.empty(), urls.usernameOf(URI.create("https://other.example/users/alice")));
        assertEquals(Optional.empty(), urls.usernameOf(URI.create("https://example.org:8443/users/alice")));
    }

    @Test
    void isLocalIgnoresHostCase() {
        assertTrue(urls.isLocal(URI.create("https://EXAMPLE.org/users/alice")));
        assertTrue(urls.isLocal(URI.create("https://example.org:443/objects/1")));
        assertFalse(urls.isLocal(URI.create("https://example.org:8443/objects/1")));
        assertFalse(urls.isLocal(URI.create("https://evil.example/users/alice")));
        assertEquals(Optional.of("alice"), urls.usernameOf(URI.create("https://Example.ORG/users/alice")));
    }

    @Test
    void keyIdHasFragment() {
        assertEquals(URI.create("https://example.org/users/alice#main-key"), urls.keyId("alice"));
    }
}

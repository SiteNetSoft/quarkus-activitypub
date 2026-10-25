package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Optional;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;

import io.quarkus.test.QuarkusUnitTest;

/**
 * A non-default actor template (one QuarkusUnitTest per configuration, hence its own class).
 */
public class ActivityPubUrlsCustomTemplateTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.activitypub.base-url", "https://example.org")
            .overrideConfigKey("quarkus.activitypub.paths.actor", "/@{username}");

    @Inject
    ActivityPubUrls urls;

    @Test
    void customTemplateHonoured() {
        assertEquals(URI.create("https://example.org/@alice"), urls.actor("alice"));
        assertEquals(URI.create("https://example.org/@alice#main-key"), urls.keyId("alice"));
        assertEquals(Optional.of("alice"), urls.usernameOf(URI.create("https://example.org/@alice")));
        assertEquals(Optional.empty(), urls.usernameOf(URI.create("https://example.org/users/alice")));
    }
}

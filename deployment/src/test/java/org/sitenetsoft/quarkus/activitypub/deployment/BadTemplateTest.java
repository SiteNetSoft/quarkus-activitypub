package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class BadTemplateTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.activitypub.base-url", "https://example.org")
            .overrideConfigKey("quarkus.activitypub.paths.actor", "/users")
            .assertException(t -> {
                for (Throwable c = t; c != null; c = c.getCause()) {
                    if (c instanceof IllegalStateException) {
                        assertTrue(c.getMessage().contains("quarkus.activitypub.paths.actor"), c.getMessage());
                        return;
                    }
                }
                fail("expected an IllegalStateException, got " + t);
            });

    @Test
    void startupFails() {
        fail("startup should have failed");
    }
}

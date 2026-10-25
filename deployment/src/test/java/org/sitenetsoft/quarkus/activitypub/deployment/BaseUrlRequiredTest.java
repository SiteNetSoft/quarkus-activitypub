package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class BaseUrlRequiredTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(t -> {
                for (Throwable c = t; c != null; c = c.getCause()) {
                    if (c.getMessage() != null && c.getMessage().contains("quarkus.activitypub.base-url")) {
                        return;
                    }
                }
                fail("expected a startup failure naming quarkus.activitypub.base-url, got " + t);
            });

    @Test
    void startupFails() {
        fail("startup should have failed");
    }
}

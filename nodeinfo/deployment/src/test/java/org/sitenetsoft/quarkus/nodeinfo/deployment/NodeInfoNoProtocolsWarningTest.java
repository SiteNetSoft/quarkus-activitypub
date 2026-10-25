package org.sitenetsoft.quarkus.nodeinfo.deployment;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Without any protocol the document is schema-invalid; the extension says so with a WARN.
 */
public class NodeInfoNoProtocolsWarningTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .setLogRecordPredicate(r -> r.getLevel().intValue() >= Level.WARNING.intValue()
                    && r.getLoggerName().startsWith("org.sitenetsoft.quarkus.nodeinfo"))
            .assertLogRecords(records -> assertTrue(records.stream()
                    .anyMatch(r -> r.getMessage().contains("no protocols")), "expected a 'no protocols' WARN"));

    @Test
    void documentIsStillServed() {
        given().when().get("/nodeinfo/2.1").then().statusCode(200);
    }
}

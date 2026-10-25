package org.sitenetsoft.quarkus.nodeinfo.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Standalone use: with only configuration (no contributor bean) the documents are schema-valid.
 */
public class NodeInfoProtocolsConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(NodeInfoSchemas.class)
                    .addAsResource("nodeinfo-2.0.schema.json").addAsResource("nodeinfo-2.1.schema.json"))
            .overrideConfigKey("quarkus.application.name", "standalone")
            .overrideConfigKey("quarkus.nodeinfo.protocols", "activitypub");

    @Test
    void configOnlyDocument21ValidatesAgainstSchema() throws Exception {
        String body = given().when().get("/nodeinfo/2.1")
                .then().statusCode(200)
                .body("protocols", contains("activitypub"))
                .extract().asString();
        NodeInfoSchemas.assertValid("nodeinfo-2.1.schema.json", body);
    }

    @Test
    void configOnlyDocument20ValidatesAgainstSchema() throws Exception {
        String body = given().when().get("/nodeinfo/2.0").then().statusCode(200).extract().asString();
        NodeInfoSchemas.assertValid("nodeinfo-2.0.schema.json", body);
    }
}

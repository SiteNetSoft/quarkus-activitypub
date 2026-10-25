package org.sitenetsoft.quarkus.nodeinfo.deployment;

import static io.restassured.RestAssured.given;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class NodeInfoDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.nodeinfo.enabled", "false");

    @Test
    void routesAreAbsent() {
        given().when().get("/.well-known/nodeinfo").then().statusCode(404);
        given().when().get("/nodeinfo/2.1").then().statusCode(404);
    }
}

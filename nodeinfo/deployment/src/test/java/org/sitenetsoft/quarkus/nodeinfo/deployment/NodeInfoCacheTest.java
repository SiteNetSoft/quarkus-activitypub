package org.sitenetsoft.quarkus.nodeinfo.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfo;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoProvider;

import io.quarkus.test.QuarkusUnitTest;

/**
 * The merged NodeInfo is computed once per {@code cache-ttl}; an app provider replaces the config-based default.
 */
public class NodeInfoCacheTest {

    static final AtomicInteger CALLS = new AtomicInteger();

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(CountingProvider.class));

    @ApplicationScoped
    public static class CountingProvider implements NodeInfoProvider {
        @Override
        public NodeInfo nodeInfo() {
            CALLS.incrementAndGet();
            return NodeInfo.builder(NodeInfo.Software.of("counting", "1.0")).withProtocol("activitypub").build();
        }
    }

    @Test
    void providerIsCalledOncePerTtl() {
        given().when().get("/nodeinfo/2.1").then().statusCode(200).body("software.name", equalTo("counting"));
        given().when().get("/nodeinfo/2.0").then().statusCode(200).body("software.name", equalTo("counting"));
        assertEquals(1, CALLS.get());
    }
}

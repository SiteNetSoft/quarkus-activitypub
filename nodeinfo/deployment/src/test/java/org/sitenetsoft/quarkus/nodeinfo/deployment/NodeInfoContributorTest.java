package org.sitenetsoft.quarkus.nodeinfo.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfo;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoContributor;
import org.sitenetsoft.quarkus.nodeinfo.ReactiveNodeInfoContributor;

import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

/**
 * Contributors from both SPIs are merged; the higher {@code @Priority} has the last word on usage numbers.
 */
public class NodeInfoContributorTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Low.class, High.class));

    @ApplicationScoped
    @Priority(1)
    public static class Low implements ReactiveNodeInfoContributor {
        @Override
        public Uni<NodeInfo> contribute(NodeInfo current) {
            return Uni.createFrom().item(current.toBuilder().withProtocol("a").withUsersTotal(1).withUsersActiveMonth(5).build());
        }
    }

    @ApplicationScoped
    @Priority(2)
    public static class High implements NodeInfoContributor {
        @Override
        public NodeInfo contribute(NodeInfo current) {
            return current.toBuilder().withProtocol("b").withUsersTotal(2).build();
        }
    }

    @Test
    void contributionsAreMergedAndHigherPriorityWins() {
        given().when().get("/nodeinfo/2.1")
                .then().statusCode(200)
                .body("protocols", containsInAnyOrder("a", "b"))
                .body("usage.users.total", equalTo(2))
                .body("usage.users.activeMonth", equalTo(5));
    }
}

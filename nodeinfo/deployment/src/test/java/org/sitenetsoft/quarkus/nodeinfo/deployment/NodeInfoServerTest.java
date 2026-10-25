package org.sitenetsoft.quarkus.nodeinfo.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfo;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoContributor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class NodeInfoServerTest {

    static final String SCHEMA_20 = "http://nodeinfo.diaspora.software/ns/schema/2.0";
    static final String SCHEMA_21 = "http://nodeinfo.diaspora.software/ns/schema/2.1";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(ActivityPubContributor.class)
                    .addAsResource("nodeinfo-2.0.schema.json").addAsResource("nodeinfo-2.1.schema.json"))
            .overrideConfigKey("quarkus.application.name", "demo")
            .overrideConfigKey("quarkus.application.version", "1.2.3")
            .overrideConfigKey("quarkus.nodeinfo.software.repository", "https://example.com/demo.git")
            .overrideConfigKey("quarkus.nodeinfo.software.homepage", "https://example.com/demo")
            .overrideConfigKey("quarkus.nodeinfo.metadata.nodeName", "Demo Node")
            .overrideConfigKey("quarkus.http.proxy.proxy-address-forwarding", "true")
            .overrideConfigKey("quarkus.http.proxy.enable-forwarded-host", "true");

    @ApplicationScoped
    public static class ActivityPubContributor implements NodeInfoContributor {
        @Override
        public NodeInfo contribute(NodeInfo current) {
            return current.toBuilder().withProtocol("activitypub").withUsersTotal(3).withLocalPosts(7).build();
        }
    }

    @Test
    void wellKnownListsBothSchemas() {
        String base = "http://localhost:" + RestAssured.port;
        given().when().get("/.well-known/nodeinfo")
                .then().statusCode(200)
                .header("Content-Type", equalTo("application/json"))
                .body("links.rel", contains(SCHEMA_20, SCHEMA_21))
                .body("links.href", contains(base + "/nodeinfo/2.0", base + "/nodeinfo/2.1"));
    }

    @Test
    void wellKnownHonoursForwardedHeaders() {
        given().header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "social.example")
                .when().get("/.well-known/nodeinfo")
                .then().statusCode(200)
                .body("links.href", contains("https://social.example/nodeinfo/2.0", "https://social.example/nodeinfo/2.1"));
    }

    @Test
    void document21ValidatesAgainstSchema() throws Exception {
        String body = given().when().get("/nodeinfo/2.1")
                .then().statusCode(200)
                .header("Content-Type", equalTo("application/json; profile=\"" + SCHEMA_21 + "#\""))
                .body("version", equalTo("2.1"))
                .body("software.repository", equalTo("https://example.com/demo.git"))
                .body("software.homepage", equalTo("https://example.com/demo"))
                .body("protocols", contains("activitypub"))
                .body("usage.users.total", equalTo(3))
                .body("usage.localPosts", equalTo(7))
                .extract().asString();
        assertValid("nodeinfo-2.1.schema.json", body);
    }

    @Test
    void document20ValidatesAgainstSchema() throws Exception {
        String body = given().when().get("/nodeinfo/2.0")
                .then().statusCode(200)
                .header("Content-Type", equalTo("application/json; profile=\"" + SCHEMA_20 + "#\""))
                .body("version", equalTo("2.0"))
                .body("software", not(hasKey("repository")))
                .body("software", not(hasKey("homepage")))
                .extract().asString();
        assertValid("nodeinfo-2.0.schema.json", body);
    }

    @Test
    void softwareDefaultsFromApplicationConfig() {
        given().when().get("/nodeinfo/2.1")
                .then().statusCode(200)
                .body("software.name", equalTo("demo"))
                .body("software.version", equalTo("1.2.3"))
                .body("openRegistrations", equalTo(false));
    }

    @Test
    void metadataFromConfig() {
        given().when().get("/nodeinfo/2.1")
                .then().statusCode(200)
                .body("metadata.nodeName", equalTo("Demo Node"));
    }

    private static void assertValid(String schemaResource, String body) throws Exception {
        JsonSchema schema;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(schemaResource)) {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4).getSchema(in);
        }
        JsonNode doc = new ObjectMapper().readTree(body);
        Set<ValidationMessage> errors = schema.validate(doc);
        assertTrue(errors.isEmpty(), () -> "schema violations: " + errors + " in " + body);
        assertEquals(doc.get("version").asText(), schemaResource.contains("2.0") ? "2.0" : "2.1");
    }
}

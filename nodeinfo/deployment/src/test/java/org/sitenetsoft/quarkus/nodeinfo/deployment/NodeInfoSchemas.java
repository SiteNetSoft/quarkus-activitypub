package org.sitenetsoft.quarkus.nodeinfo.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Validates a NodeInfo document against the bundled jhass/nodeinfo schemas (add them to the test archive).
 */
final class NodeInfoSchemas {

    private NodeInfoSchemas() {
    }

    static void assertValid(String schemaResource, String body) throws Exception {
        JsonSchema schema;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(schemaResource)) {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4).getSchema(in);
        }
        Set<ValidationMessage> errors = schema.validate(new ObjectMapper().readTree(body));
        assertTrue(errors.isEmpty(), () -> "schema violations: " + errors + " in " + body);
    }
}

package org.sitenetsoft.quarkus.nodeinfo.devui;

import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.nodeinfo.NodeInfoDocuments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.smallrye.mutiny.Uni;

/**
 * Dev UI backend: the card label and the document preview.
 */
public class NodeInfoJsonRpcService {

    @Inject
    NodeInfoDocuments documents;

    public Uni<String> software() {
        return documents.current().map(i -> i.software().name() + " " + i.software().version());
    }

    public Uni<JsonNode> document(String version) {
        if (!NodeInfoDocuments.VERSIONS.contains(version)) {
            return Uni.createFrom().item(JsonNodeFactory.instance.objectNode().put("error", "unsupported version " + version));
        }
        documents.invalidate();
        return documents.document(version).map(d -> (JsonNode) d)
                .onFailure().recoverWithItem(t -> JsonNodeFactory.instance.objectNode()
                        .put("error", t.getClass().getSimpleName() + ": " + t.getMessage()));
    }
}

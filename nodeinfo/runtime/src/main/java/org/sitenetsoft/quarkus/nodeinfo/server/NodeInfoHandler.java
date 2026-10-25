package org.sitenetsoft.quarkus.nodeinfo.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoDocuments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Serves {@code /.well-known/nodeinfo} (variant {@code discovery:{path}}) and the documents (variants {@code 2.0},
 * {@code 2.1}). Scheme and host come from the request, so {@code X-Forwarded-*} is honoured when Quarkus proxy
 * forwarding is enabled.
 */
@ApplicationScoped
public class NodeInfoHandler implements BeanHandler.VariantHandler {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.nodeinfo");
    public static final String DISCOVERY_VARIANT_PREFIX = "discovery:";

    private final NodeInfoDocuments documents;
    private final ObjectMapper mapper;

    @Inject
    public NodeInfoHandler(NodeInfoDocuments documents, ObjectMapper mapper) {
        this.documents = documents;
        this.mapper = mapper;
    }

    @Override
    public Handler<RoutingContext> variant(String variant) {
        if (variant.startsWith(DISCOVERY_VARIANT_PREFIX)) {
            String path = variant.substring(DISCOVERY_VARIANT_PREFIX.length());
            return ctx -> discovery(ctx, path);
        }
        return ctx -> document(ctx, variant);
    }

    private void discovery(RoutingContext ctx, String path) {
        String host = ctx.request().host();
        String base = ctx.request().scheme() + "://" + (host == null ? "localhost" : host) + path + "/";
        ObjectNode body = mapper.createObjectNode();
        ArrayNode links = body.putArray("links");
        for (String version : new String[] { NodeInfoDocuments.V2_0, NodeInfoDocuments.V2_1 }) {
            links.addObject().put("rel", NodeInfoDocuments.schema(version)).put("href", base + version);
        }
        write(ctx, "application/json", body);
    }

    private void document(RoutingContext ctx, String version) {
        documents.document(version).subscribe().with(doc -> write(ctx, NodeInfoDocuments.contentType(version), doc),
                failure -> {
                    LOG.errorf(failure, "Failed to build NodeInfo %s", version);
                    ctx.response().setStatusCode(500).end();
                });
    }

    private void write(RoutingContext ctx, String contentType, ObjectNode body) {
        try {
            ctx.response().putHeader("Content-Type", contentType)
                    .end(mapper.writeValueAsString(body));
        } catch (Exception e) {
            LOG.error("Failed to serialize NodeInfo", e);
            ctx.response().setStatusCode(500).end();
        }
    }
}

package org.sitenetsoft.quarkus.nodeinfo;

import org.sitenetsoft.quarkus.nodeinfo.server.BeanHandler;
import org.sitenetsoft.quarkus.nodeinfo.server.NodeInfoHandler;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class NodeInfoRecorder {

    public Handler<RoutingContext> discoveryHandler(String documentsPath) {
        return new BeanHandler(NodeInfoHandler.class.getName(), NodeInfoHandler.DISCOVERY_VARIANT_PREFIX + documentsPath);
    }

    public Handler<RoutingContext> documentHandler(String version) {
        return new BeanHandler(NodeInfoHandler.class.getName(), version);
    }
}

package org.sitenetsoft.quarkus.nodeinfo.deployment;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.nodeinfo.ConfigNodeInfoProvider;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoBuildTimeConfig;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoContributor;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoDocuments;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoProvider;
import org.sitenetsoft.quarkus.nodeinfo.NodeInfoRecorder;
import org.sitenetsoft.quarkus.nodeinfo.ReactiveNodeInfoContributor;
import org.sitenetsoft.quarkus.nodeinfo.ReactiveNodeInfoProvider;
import org.sitenetsoft.quarkus.nodeinfo.server.NodeInfoHandler;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

class NodeInfoProcessor {

    static final String FEATURE = "nodeinfo";
    static final String WELL_KNOWN_PATH = "/.well-known/nodeinfo";

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.nodeinfo");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(NodeInfoHandler.class, NodeInfoDocuments.class, ConfigNodeInfoProvider.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    UnremovableBeanBuildItem unremovableSpi() {
        return UnremovableBeanBuildItem.beanTypes(NodeInfoProvider.class, ReactiveNodeInfoProvider.class,
                NodeInfoContributor.class, ReactiveNodeInfoContributor.class);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerRoutes(NodeInfoBuildTimeConfig config, HttpRootPathBuildItem rootPath, NodeInfoRecorder recorder,
            BuildProducer<RouteBuildItem> routes) {
        if (!config.enabled()) {
            LOG.debug("NodeInfo routes not registered: quarkus.nodeinfo.enabled=false");
            return;
        }
        String path = documentsPath(config.path());
        if (!"/".equals(rootPath.getRootPath())) {
            LOG.warnf("quarkus.http.root-path is '%s'; NodeInfo still serves %s and %s/2.x at the server root because "
                    + "well-known paths must sit at the domain root", rootPath.getRootPath(), WELL_KNOWN_PATH, path);
        }
        // HttpRootPathBuildItem's builder types a leading-slash path outside the root path as an absolute route
        routes.produce(rootPath.routeBuilder().route(WELL_KNOWN_PATH).handler(recorder.discoveryHandler(path))
                .displayOnNotFoundPage("NodeInfo discovery").build());
        for (String version : new String[] { NodeInfoDocuments.V2_0, NodeInfoDocuments.V2_1 }) {
            routes.produce(rootPath.routeBuilder().route(path + "/" + version).handler(recorder.documentHandler(version))
                    .displayOnNotFoundPage("NodeInfo " + version).build());
        }
    }

    /**
     * The configured path with a leading slash and no trailing one.
     */
    static String documentsPath(String configured) {
        String p = configured.strip();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return "/".equals(p) ? "" : p;
    }
}

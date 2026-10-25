package org.sitenetsoft.quarkus.nodeinfo.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class NodeInfoProcessor {

    static final String FEATURE = "nodeinfo";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}

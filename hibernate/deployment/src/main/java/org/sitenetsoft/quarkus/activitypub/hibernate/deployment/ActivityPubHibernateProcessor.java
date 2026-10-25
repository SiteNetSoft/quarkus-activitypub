package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class ActivityPubHibernateProcessor {

    static final String FEATURE = "activitypub-hibernate";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}

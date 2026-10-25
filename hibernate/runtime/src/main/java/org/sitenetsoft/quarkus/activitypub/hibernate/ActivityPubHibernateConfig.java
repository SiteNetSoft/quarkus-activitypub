package org.sitenetsoft.quarkus.activitypub.hibernate;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of the Hibernate ORM stores.
 */
@ConfigMapping(prefix = "quarkus.activitypub.hibernate")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface ActivityPubHibernateConfig {

    /**
     * The persistence unit holding the {@code ap_} tables. With a named unit, also assign the entities to it:
     * {@code quarkus.hibernate-orm."<name>".packages=org.sitenetsoft.quarkus.activitypub.hibernate.entity}.
     */
    @WithDefault("<default>")
    String persistenceUnit();
}

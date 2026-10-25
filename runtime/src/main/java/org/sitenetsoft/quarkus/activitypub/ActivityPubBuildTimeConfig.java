package org.sitenetsoft.quarkus.activitypub;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.activitypub")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface ActivityPubBuildTimeConfig {

    /**
     * Enable the ActivityPub extension.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Server settings.
     */
    Server server();

    /**
     * Dev UI settings.
     */
    DevUi devui();

    interface Server {
        /**
         * Register the actor, inbox, outbox, collection and object routes.
         */
        @WithDefault("true")
        boolean enabled();
    }

    interface DevUi {
        /**
         * Show the ActivityPub Dev UI card.
         */
        @WithDefault("true")
        boolean enabled();
    }
}

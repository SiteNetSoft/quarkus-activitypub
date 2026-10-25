package org.sitenetsoft.quarkus.activitypub;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.activitypub")
/**
 * Build-time settings. {@code BUILD_AND_RUN_TIME_FIXED} because the route paths are registered at build time and
 * {@code ActivityPubUrls} reads the same templates at runtime.
 */
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
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

    /**
     * Path templates, relative to {@code base-url}. Build-time: the deployment module registers the routes from them.
     */
    Paths paths();

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

    interface Paths {
        /**
         * Actor document.
         */
        @WithDefault("/users/{username}")
        String actor();

        /**
         * Actor inbox.
         */
        @WithDefault("/users/{username}/inbox")
        String inbox();

        /**
         * Actor outbox.
         */
        @WithDefault("/users/{username}/outbox")
        String outbox();

        /**
         * Followers collection.
         */
        @WithDefault("/users/{username}/followers")
        String followers();

        /**
         * Following collection.
         */
        @WithDefault("/users/{username}/following")
        String following();

        /**
         * Shared inbox.
         */
        @WithDefault("/inbox")
        String sharedInbox();

        /**
         * Local objects.
         */
        @WithDefault("/objects/{id}")
        String objects();

        /**
         * Local activities.
         */
        @WithDefault("/activities/{id}")
        String activities();
    }
}

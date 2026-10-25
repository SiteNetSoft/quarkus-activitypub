package org.sitenetsoft.quarkus.nodeinfo;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.nodeinfo")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface NodeInfoConfig {

    /**
     * Server software advertised by the default provider.
     */
    Software software();

    /**
     * Protocols advertised by the default provider, e.g. {@code activitypub}. NodeInfo requires at least one; a
     * contributor may add them instead.
     */
    Optional<Set<String>> protocols();

    /**
     * Whether this server allows open self-registration.
     */
    @WithDefault("false")
    boolean openRegistrations();

    /**
     * Free-form metadata entries (string values).
     */
    @ConfigDocMapKey("key")
    Map<String, String> metadata();

    /**
     * How long the merged NodeInfo (provider plus contributors) is cached.
     */
    @WithDefault("5m")
    Duration cacheTtl();

    interface Software {
        /**
         * Software name; NodeInfo requires {@code ^[a-z0-9-]+$}.
         */
        @WithDefault("${quarkus.application.name:quarkus}")
        String name();

        /**
         * Software version.
         */
        @WithDefault("${quarkus.application.version:0}")
        String version();

        /**
         * Source repository URL (2.1 only).
         */
        Optional<URI> repository();

        /**
         * Homepage URL (2.1 only).
         */
        Optional<URI> homepage();
    }
}

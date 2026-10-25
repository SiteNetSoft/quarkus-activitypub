package org.sitenetsoft.quarkus.activitypub;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.httpsignatures.SchemeConverter;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.configuration.MemorySize;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.activitypub")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface ActivityPubConfig {

    /**
     * Public base URL of this server, for example {@code https://example.com}. Every actor and object id starts with it.
     */
    URI baseUrl();

    /**
     * The instance actor, an {@code Application} that signs outbound fetches.
     */
    InstanceActor instanceActor();

    /**
     * Require a valid HTTP signature on every ActivityPub GET.
     */
    @WithDefault("false")
    boolean authorizedFetch();

    /**
     * Domains whose activities are dropped; matched by suffix ({@code example.com} also blocks {@code sub.example.com}).
     */
    Optional<List<String>> blockedDomains();

    /**
     * Outbox, followers and following collections.
     */
    Collections collections();

    /**
     * {@code Cache-Control} values.
     */
    CacheControl cacheControl();

    /**
     * Remote fetching.
     */
    Fetch fetch();

    /**
     * Outbound delivery.
     */
    Delivery delivery();

    /**
     * Readiness check.
     */
    Health health();

    /**
     * {@code User-Agent} for outbound requests; defaults to {@code quarkus-activitypub/{version} (+{base-url})}.
     */
    Optional<String> userAgent();

    /**
     * Inbound activity de-duplication.
     */
    Dedup dedup();

    /**
     * Remote objects kept by the inbox.
     */
    RemoteObjects remoteObjects();

    interface InstanceActor {
        /**
         * Create and serve the instance actor.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Username of the instance actor.
         */
        @WithDefault("instance")
        String username();
    }

    interface Collections {
        /**
         * Items per collection page.
         */
        @WithDefault("20")
        int pageSize();

        /**
         * Who sees the followers and following collections.
         */
        @WithDefault("public")
        FollowersVisibility followersVisibility();
    }

    enum FollowersVisibility {
        /** Count and items. */
        PUBLIC,
        /** Count only, no pages. */
        COUNT_ONLY,
        /** Not served (404). */
        HIDDEN
    }

    interface CacheControl {
        /**
         * {@code Cache-Control} of actor documents.
         */
        @WithDefault("max-age=300")
        String actor();
    }

    interface Fetch {
        /**
         * Per-request timeout.
         */
        @WithDefault("10s")
        Duration timeout();

        /**
         * Larger documents are rejected.
         */
        @WithDefault("1M")
        MemorySize maxSize();

        /**
         * How long fetched objects are cached.
         */
        @WithDefault("10m")
        Duration cacheTtl();

        /**
         * How long fetched actors are cached.
         */
        @WithDefault("1h")
        Duration actorCacheTtl();
    }

    interface Delivery {
        /**
         * How often the delivery job polls for due deliveries.
         */
        @WithDefault("5s")
        Duration pollInterval();

        /**
         * Deliveries claimed per poll.
         */
        @WithDefault("50")
        int batchSize();

        /**
         * Concurrent sends per poll.
         */
        @WithDefault("8")
        int parallelism();

        /**
         * Attempts before a delivery is given up.
         */
        @WithDefault("8")
        int maxAttempts();

        /**
         * Signature scheme for deliveries: {@code cavage} or {@code rfc9421}.
         */
        @WithDefault("cavage")
        @WithConverter(SchemeConverter.class)
        Scheme signatureScheme();

        /**
         * A host failing continuously for this long gets new deliveries given up immediately.
         */
        @WithDefault("30d")
        Duration deadAfter();
    }

    interface Health {
        /**
         * Readiness turns DOWN when the oldest pending delivery is older than this.
         */
        @WithDefault("1h")
        Duration maxBacklogAge();
    }

    interface Dedup {
        /**
         * How long inbound activity ids are remembered.
         */
        @WithDefault("7d")
        Duration retention();
    }

    interface RemoteObjects {
        /**
         * How long kept remote objects live; {@code 0} disables expiry.
         */
        @WithDefault("90d")
        Duration retention();
    }
}

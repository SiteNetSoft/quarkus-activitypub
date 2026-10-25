package org.sitenetsoft.quarkus.activitypub.client;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

/**
 * Fetched remote documents by URL: actors for {@code fetch.actor-cache-ttl}, other objects for {@code fetch.cache-ttl}.
 * Callers get copies, so a caller changing its object never changes the cache.
 */
@ApplicationScoped
public class RemoteCache {

    private static final int MAX_ENTRIES = 10_000;

    @Inject
    ActivityPubConfig config;

    private Cache<URI, ASObject> cache;

    @PostConstruct
    void init() {
        Duration objectTtl = config.fetch().cacheTtl();
        Duration actorTtl = config.fetch().actorCacheTtl();
        cache = Caffeine.newBuilder().maximumSize(MAX_ENTRIES).expireAfter(new Expiry<URI, ASObject>() {
            @Override
            public long expireAfterCreate(URI key, ASObject value, long currentTime) {
                return (value instanceof Actor ? actorTtl : objectTtl).toNanos();
            }

            @Override
            public long expireAfterUpdate(URI key, ASObject value, long currentTime, long currentDuration) {
                return expireAfterCreate(key, value, currentTime);
            }

            @Override
            public long expireAfterRead(URI key, ASObject value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        }).build();
    }

    public Optional<ASObject> get(URI id) {
        ASObject cached = cache.getIfPresent(id);
        return cached == null ? Optional.empty() : Optional.of(cached.copy());
    }

    public void put(URI id, ASObject object) {
        cache.put(id, object.copy());
    }

    public void invalidate(URI id) {
        cache.invalidate(id);
    }
}

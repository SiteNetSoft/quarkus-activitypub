package org.sitenetsoft.quarkus.activitypub.client;

import java.net.URI;
import java.time.Duration;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubException;

import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;

/**
 * {@link ActivityPubClient} awaiting {@link ActivityPubClientImpl}: at most {@code fetch.timeout} + 2 s per fetch, twice
 * the timeout + 2 s for {@link #resolveActor} (WebFinger, then the fetch).
 */
@ApplicationScoped
public class BlockingActivityPubClient implements ActivityPubClient {

    private static final Duration SLACK = Duration.ofSeconds(2);

    @Inject
    ReactiveActivityPubClient reactive;

    @Inject
    ActivityPubConfig config;

    @Override
    public ASObject fetch(URI id) {
        return await(() -> reactive.fetch(id), 1);
    }

    @Override
    public <T extends ASObject> T fetch(URI id, Class<T> type) {
        return await(() -> reactive.fetch(id, type), 1);
    }

    @Override
    public Actor fetchActor(URI id) {
        return await(() -> reactive.fetchActor(id), 1);
    }

    @Override
    public Actor resolveActor(String acctOrUri) {
        return await(() -> reactive.resolveActor(acctOrUri), 2);
    }

    private <T> T await(Supplier<Uni<T>> call, int requests) {
        Duration limit = config.fetch().timeout().multipliedBy(requests).plus(SLACK);
        try {
            return call.get().await().atMost(limit);
        } catch (TimeoutException e) {
            throw new ActivityPubException(ActivityPubException.Reason.FETCH_FAILED, "No answer within " + limit, e);
        }
    }
}

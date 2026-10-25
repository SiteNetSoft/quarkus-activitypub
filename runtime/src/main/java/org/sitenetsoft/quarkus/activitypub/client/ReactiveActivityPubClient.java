package org.sitenetsoft.quarkus.activitypub.client;

import java.net.URI;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Actor;

import io.smallrye.mutiny.Uni;

/**
 * Mutiny form of {@link ActivityPubClient}; safe on the event loop.
 */
public interface ReactiveActivityPubClient {

    Uni<ASObject> fetch(URI id);

    <T extends ASObject> Uni<T> fetch(URI id, Class<T> type);

    Uni<Actor> fetchActor(URI id);

    /** An actor by {@code user@host} (WebFinger, then fetch) or by its URL. */
    Uni<Actor> resolveActor(String acctOrUri);
}

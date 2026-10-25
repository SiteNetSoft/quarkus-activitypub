package org.sitenetsoft.quarkus.activitypub.client;

import java.net.URI;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Actor;

/**
 * Fetches ActivityPub documents (blocking; do not call on the event loop). Local ids are answered from the stores;
 * remote ones are fetched through safe-fetch, signed by the instance actor when it is enabled, and cached. Failures are
 * {@link org.sitenetsoft.quarkus.activitypub.ActivityPubException}s: {@code NOT_FOUND} (404, 410, unknown local id),
 * {@code ID_MISMATCH} (the document's {@code id} is on another host than the URL it was served from),
 * {@code INVALID_DOCUMENT} (unparsable, or not the requested type), {@code FETCH_FAILED} (a redirect to another host,
 * other statuses, blocked addresses, timeouts, connection errors).
 */
public interface ActivityPubClient {

    ASObject fetch(URI id);

    <T extends ASObject> T fetch(URI id, Class<T> type);

    Actor fetchActor(URI id);

    /** An actor by {@code user@host} (WebFinger, then fetch) or by its URL. */
    Actor resolveActor(String acctOrUri);
}

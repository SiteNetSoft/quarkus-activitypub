package org.sitenetsoft.quarkus.activitypub.outbound;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * Turns an activity's audience ({@code to}, {@code cc}, {@code bto}, {@code bcc}, {@code audience}) into one
 * {@link Delivery} per inbox, in two steps so no network I/O happens inside a store transaction:
 * {@link #prefetch} (network, before the transaction) looks up the remote recipients, {@link #resolve} (store only,
 * inside it) builds the deliveries.
 * <ul>
 * <li>the public collection: nobody to deliver to;</li>
 * <li>the actor's own followers collection: the inboxes of its accepted followers;</li>
 * <li>any other local URL (local actors, other collections): skipped, local delivery is not server-to-server;</li>
 * <li>a remote actor: fetched (cached) for its shared inbox, else its inbox;</li>
 * <li>a remote document that is not an actor (a collection): skipped, remote collections are not expanded;</li>
 * <li>a remote URL that cannot be fetched, or an actor without an inbox: a {@code FAILED} delivery to that URL with
 * the error, so the application can see who was not reached.</li>
 * </ul>
 * Inboxes are deduplicated. The body is the activity without {@code bto}/{@code bcc} (also removed from an embedded
 * object), serialised once and shared by every delivery.
 */
@ApplicationScoped
public class RecipientResolver {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");
    private static final ObjectOrLink[] NONE = {};

    /**
     * What the remote recipients of an activity resolved to: an inbox, or the reason they cannot be reached. Remote
     * recipients in neither map are skipped (not actors).
     */
    public record RemoteRecipients(Map<URI, URI> inboxes, Map<URI, String> failures) {

        public RemoteRecipients {
            inboxes = Map.copyOf(inboxes);
            failures = Map.copyOf(failures);
        }
    }

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Inject
    ActivityPubClient client;

    @Inject
    ActivityPubJson json;

    @Inject
    Clock clock;

    /**
     * Looks up the remote recipients of {@code activity} (blocking: up to {@code fetch.timeout} per recipient not yet
     * cached). Call it before opening the store transaction.
     */
    public RemoteRecipients prefetch(Activity activity) {
        Map<URI, URI> inboxes = new LinkedHashMap<>();
        Map<URI, String> failures = new LinkedHashMap<>();
        for (URI recipient : activity.audienceUris()) {
            if (Audience.isPublic(recipient) || urls.isLocal(recipient)) {
                continue;
            }
            ASObject document;
            try {
                document = client.fetch(recipient);
            } catch (RuntimeException e) {
                LOG.debugf("Activity %s: recipient %s not resolved: %s", activity.id(), recipient, e.getMessage());
                failures.put(recipient, "Recipient not resolved: " + e.getMessage());
                continue;
            }
            if (!(document instanceof Actor remote)) {
                LOG.debugf("Activity %s: recipient %s is a %s, not an actor; skipped", activity.id(), recipient,
                        document.primaryType());
            } else if (remote.deliveryInbox() == null) {
                failures.put(recipient, "Recipient has no inbox");
            } else {
                inboxes.put(recipient, remote.deliveryInbox());
            }
        }
        return new RemoteRecipients(inboxes, failures);
    }

    /** The deliveries of {@code activity}, from the store and {@code remote} only (no network). */
    public List<Delivery> resolve(LocalActor actor, Activity activity, RemoteRecipients remote) {
        byte[] body = json.write(withoutBlindAudience(activity));
        String keyId = urls.keyId(actor.username()).toString();
        URI followers = urls.followers(actor.username());
        Instant now = clock.instant();
        Map<URI, Delivery> byInbox = new LinkedHashMap<>();
        List<Delivery> failed = new ArrayList<>();
        for (URI recipient : activity.audienceUris()) {
            if (Audience.isPublic(recipient)) {
                continue;
            }
            if (recipient.equals(followers)) {
                for (URI inbox : stores.followers().followerInboxes(actor.id())) {
                    byInbox.computeIfAbsent(inbox, i -> Delivery.create(i, activity.id(), body, keyId, now));
                }
            } else if (urls.isLocal(recipient)) {
                LOG.debugf("Activity %s: local recipient %s skipped", activity.id(), recipient);
            } else if (remote.inboxes().containsKey(recipient)) {
                byInbox.computeIfAbsent(remote.inboxes().get(recipient),
                        i -> Delivery.create(i, activity.id(), body, keyId, now));
            } else if (remote.failures().containsKey(recipient)) {
                failed.add(Delivery.create(recipient, activity.id(), body, keyId, now).withState(DeliveryState.FAILED)
                        .withLastError(Optional.of(remote.failures().get(recipient))));
            }
        }
        List<Delivery> out = new ArrayList<>(byInbox.values());
        out.addAll(failed);
        return out;
    }

    /** A copy of {@code activity}, and of its embedded object, without {@code bto} and {@code bcc}. */
    static Activity withoutBlindAudience(Activity activity) {
        Activity copy = (Activity) activity.copy();
        copy.bto(NONE).bcc(NONE);
        copy.embeddedObject().ifPresent(o -> o.bto(NONE).bcc(NONE));
        return copy;
    }
}

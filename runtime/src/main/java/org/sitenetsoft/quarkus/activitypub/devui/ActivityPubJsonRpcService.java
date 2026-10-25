package org.sitenetsoft.quarkus.activitypub.devui;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.ActorDocuments;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.InstanceActor;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxLog;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryState;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.webfinger.ActivityPubWebFingerResolver;
import org.sitenetsoft.quarkus.webfinger.AcctUri;
import org.sitenetsoft.quarkus.webfinger.WebFingerRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * Dev UI backend. Payloads carry ids, public documents and queue metadata only: never private keys, signatures or
 * delivery bodies. Failures come back as {@code {"error": "..."}} so the pages can show them. Results are plain maps
 * and lists: the Dev UI serialiser would write a Jackson {@code JsonNode} as a bean.
 */
public class ActivityPubJsonRpcService {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    /** Rows listed per page, and the cap of the failed / given-up counts on the card. */
    static final int LIMIT = 200;

    @Inject
    ActivityPubUrls urls;

    @Inject
    InstanceActor instanceActor;

    @Inject
    Stores stores;

    @Inject
    ActorDocuments documents;

    @Inject
    ActivityPubWebFingerResolver webFinger;

    @Inject
    InboxLog inboxLog;

    @Inject
    ActivityPubClient client;

    @Inject
    DeliveryScheduler scheduler;

    @Inject
    ActivityPubJson json;

    @Inject
    ObjectMapper mapper;

    @Inject
    Clock clock;

    /** Base URL, instance actor, local actor count, delivery queue counts and paused hosts. */
    public Object summary() {
        DeliveryStore deliveries = stores.deliveries();
        ObjectNode n = mapper.createObjectNode();
        n.put("baseUrl", urls.baseUrl().toString());
        n.put("instanceActor", instanceActor.ifEnabled().map(a -> a.id().toString()).orElse(null));
        n.put("localActors", stores.actors().countLocalActors());
        n.put("pending", deliveries.pendingCount());
        n.put("failed", cappedCount(deliveries, DeliveryState.FAILED));
        n.put("givenUp", cappedCount(deliveries, DeliveryState.GIVEN_UP));
        ArrayNode paused = n.putArray("pausedHosts");
        Instant now = clock.instant();
        Set<String> hosts = new TreeSet<>();
        deliveries.list(DeliveryState.PENDING, LIMIT).forEach(d -> hosts.add(d.host()));
        for (String host : hosts) {
            deliveries.host(host).pausedUntil().filter(now::isBefore).ifPresent(until -> paused.addObject()
                    .put("host", host).put("until", until.toString()));
        }
        return plain(n);
    }

    /** {@code "200+"} beyond the cap. */
    private static String cappedCount(DeliveryStore deliveries, DeliveryState state) {
        int count = deliveries.list(state, LIMIT + 1).size();
        return count > LIMIT ? LIMIT + "+" : String.valueOf(count);
    }

    public Object actors() {
        ArrayNode list = mapper.createArrayNode();
        Optional<URI> instance = instanceActor.ifEnabled().map(LocalActor::id);
        for (LocalActor a : stores.actors().list(LIMIT)) {
            ObjectNode row = list.addObject();
            row.put("username", a.username());
            row.put("id", a.id().toString());
            row.put("type", a.type());
            row.put("name", a.name().orElse(null));
            row.put("manuallyApprovesFollowers", a.manuallyApprovesFollowers());
            row.put("instanceActor", instance.filter(a.id()::equals).isPresent());
        }
        return plain(list);
    }

    /** The actor document as served (public key only). */
    public Object actorDocument(String username) {
        Optional<LocalActor> actor = stores.actors().findByUsername(username);
        if (actor.isEmpty()) {
            return plain(error("No local actor '" + username + "'"));
        }
        // only the public half goes into the document
        ActorKeyPair keys = stores.actors().keyPair(actor.get().id()).orElse(null);
        return plain(tree(documents.toDocument(actor.get(), keys)));
    }

    /** The JRD the WebFinger endpoint answers for {@code acct:{username}@{host}}. */
    public Uni<Object> webfinger(String username) {
        URI resource = new AcctUri(username, urls.host()).toUri();
        return webFinger.resolve(new WebFingerRequest(resource, Set.of(), urls.host()))
                .map(jrd -> jrd.<JsonNode> map(mapper::valueToTree)
                        .orElseGet(() -> error("404: no local actor answers " + resource)))
                .onFailure().recoverWithItem(ActivityPubJsonRpcService::describeError)
                .map(this::plain);
    }

    /** The last inbox outcomes, newest first. */
    public Object inboxLog() {
        ArrayNode list = mapper.createArrayNode();
        List<InboxLog.InboxEntry> entries = new ArrayList<>(inboxLog.entries());
        for (int i = entries.size() - 1; i >= 0; i--) {
            InboxLog.InboxEntry e = entries.get(i);
            list.addObject().put("at", e.at().toString()).put("type", e.type())
                    .put("actor", e.actor() == null ? null : e.actor().toString()).put("result", e.result());
        }
        return plain(list);
    }

    /** Fetches a document by URL, or an actor by {@code user@host} (WebFinger first). */
    public Object fetch(String uriOrAcct) {
        String value = uriOrAcct == null ? "" : uriOrAcct.strip();
        if (value.isEmpty()) {
            return plain(error("Enter a URL or user@host"));
        }
        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return plain(tree(client.fetch(URI.create(value))));
            }
            String acct = value.startsWith("acct:") ? value.substring(5) : value;
            return plain(tree(client.resolveActor(acct.startsWith("@") ? acct.substring(1) : acct)));
        } catch (RuntimeException e) {
            return plain(describeError(e));
        }
    }

    /** Up to {@value #LIMIT} deliveries in {@code state} ({@code pending}, {@code delivered}, {@code failed}, {@code given_up}). */
    public Object deliveries(String state) {
        Optional<DeliveryState> parsed = state(state);
        if (parsed.isEmpty()) {
            return plain(error("Unknown delivery state '" + state + "'"));
        }
        ArrayNode list = mapper.createArrayNode();
        for (Delivery d : stores.deliveries().list(parsed.get(), LIMIT)) {
            list.addObject().put("id", d.id()).put("inbox", d.inbox().toString())
                    .put("activityId", d.activityId().toString()).put("keyId", d.keyId())
                    .put("attempts", d.attempts()).put("nextAttempt", d.nextAttempt().toString())
                    .put("lastError", d.lastError().orElse(null)).put("state", d.state().name())
                    .put("created", d.created().toString());
        }
        return plain(list);
    }

    /**
     * Makes a pending, failed or given-up delivery due now (state {@code PENDING}, attempts kept, so a given-up one
     * gets one more attempt) and runs a delivery poll on a worker thread.
     */
    public Object retryNow(String id) {
        DeliveryStore deliveries = stores.deliveries();
        Optional<Delivery> found = Optional.empty();
        for (DeliveryState s : List.of(DeliveryState.PENDING, DeliveryState.FAILED, DeliveryState.GIVEN_UP)) {
            found = deliveries.list(s, Integer.MAX_VALUE).stream().filter(d -> d.id().equals(id)).findFirst();
            if (found.isPresent()) {
                break;
            }
        }
        if (found.isEmpty()) {
            return plain(error("No pending, failed or given-up delivery '" + id + "'"));
        }
        deliveries.update(found.get().withState(DeliveryState.PENDING).withNextAttempt(clock.instant()));
        Infrastructure.getDefaultWorkerPool().execute(() -> {
            try {
                scheduler.run();
            } catch (RuntimeException e) {
                LOG.warnf(e, "Delivery poll triggered from the Dev UI failed");
            }
        });
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id).put("state", DeliveryState.PENDING.name());
        return plain(n);
    }

    private static Optional<DeliveryState> state(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(DeliveryState.valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** {@code node} as maps, lists and scalars. */
    private Object plain(JsonNode node) {
        return mapper.convertValue(node, Object.class);
    }

    private JsonNode tree(ASObject object) {
        try {
            return mapper.readTree(json.write(object));
        } catch (IOException e) {
            return describeError(e);
        }
    }

    private static JsonNode describeError(Throwable t) {
        return error(t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    private static JsonNode error(String message) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("error", message);
        return n;
    }
}

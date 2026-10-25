package org.sitenetsoft.quarkus.activitypub.outbound;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.httpsignatures.Headers;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubMediaTypes;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.keys.KeyIds;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.httpsignatures.client.RequestSigner;
import org.sitenetsoft.quarkus.safefetch.BlockedAddressException;
import org.sitenetsoft.quarkus.safefetch.FetchOptions;
import org.sitenetsoft.quarkus.safefetch.SafeFetchClient;
import org.sitenetsoft.quarkus.safefetch.SafeFetchException;

/**
 * POSTs one delivery, signed with the local actor's key (blocking: call it from a worker or virtual thread).
 * <p>
 * The first attempt uses the scheme the inbox host accepted before ({@code SchemeMemory}), else
 * {@code delivery.signature-scheme}. When the host answers {@code 401} or {@code 403}, the delivery is sent once more
 * with the other scheme ("double knocking"), and that scheme is remembered for the host when it succeeds.
 */
@ApplicationScoped
public class DeliverySender {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");
    private static final String CONTENT_TYPE = ActivityPubMediaTypes.ACTIVITY_JSON;

    /**
     * What an attempt came to: an HTTP {@code status}, or {@code status == -1} and an {@code error} for a transport
     * failure. {@code permanent} failures (a blocked address, no signing key) are never retried.
     */
    public record Outcome(int status, Optional<String> error, boolean permanent) {

        static Outcome status(int status) {
            return new Outcome(status, Optional.empty(), false);
        }

        static Outcome transportError(String error) {
            return new Outcome(-1, Optional.of(error), false);
        }

        static Outcome permanentError(String error) {
            return new Outcome(-1, Optional.of(error), true);
        }

        public boolean delivered() {
            return status >= 200 && status < 300;
        }

        boolean signatureRejected() {
            return status == 401 || status == 403;
        }

        /** {@code "HTTP 503"} or the transport error. */
        public String describe() {
            return error.orElse("HTTP " + status);
        }
    }

    @Inject
    ActivityPubConfig config;

    @Inject
    ActivityPubUrls urls;

    @Inject
    SafeFetchClient safeFetch;

    @Inject
    RequestSigner signer;

    /** Sends {@code delivery}, double knocking on {@code 401}/{@code 403}. */
    public Outcome deliver(Delivery delivery) {
        String host = delivery.host();
        Scheme first = signer.memory().preferredFor(host).orElse(config.delivery().signatureScheme());
        Outcome outcome = send(delivery, first);
        if (!outcome.signatureRejected()) {
            return outcome;
        }
        Scheme other = first == Scheme.CAVAGE ? Scheme.RFC9421 : Scheme.CAVAGE;
        LOG.debugf("Delivery %s to %s: %s with %s, retrying with %s", delivery.id(), delivery.inbox(),
                outcome.describe(), first, other);
        Outcome second = send(delivery, other);
        if (second.delivered()) {
            signer.memory().remember(host, other);
        }
        return second;
    }

    /** One signed POST of {@code delivery} with {@code scheme}. */
    public Outcome send(Delivery delivery, Scheme scheme) {
        FetchOptions options;
        try {
            options = options(delivery, scheme);
        } catch (RuntimeException e) {
            // no key for the actor (deleted?): no later attempt can do better
            return Outcome.permanentError("Cannot sign as " + delivery.keyId() + ": " + e.getMessage());
        }
        try {
            return Outcome.status(safeFetch.post(delivery.inbox(), delivery.body(), options).status());
        } catch (BlockedAddressException e) {
            return Outcome.permanentError("Blocked address: " + e.getMessage());
        } catch (SafeFetchException e) {
            return Outcome.transportError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private FetchOptions options(Delivery delivery, Scheme scheme) {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now().atOffset(ZoneOffset.UTC));
        Headers existing = Headers.of(Map.of("Content-Type", List.of(CONTENT_TYPE), "Date", List.of(date)));
        FetchOptions options = FetchOptions.defaults()
                .withTimeout(config.fetch().timeout())
                .withMaxResponseSize(config.fetch().maxSize().asLongValue())
                .withHeader("Content-Type", CONTENT_TYPE)
                .withHeader("Accept", ActivityPubMediaTypes.ACCEPT)
                .withHeader("User-Agent", userAgent())
                .withHeader("Date", date);
        for (Map.Entry<String, String> h : signer.sign("POST", delivery.inbox(), existing, delivery.body(),
                Optional.of(KeyIds.clientName(delivery.keyId())), scheme).headers().entrySet()) {
            if (!h.getKey().equalsIgnoreCase("Host")) {
                options = options.withHeader(h.getKey(), h.getValue());
            }
        }
        return options;
    }

    private String userAgent() {
        return config.userAgent().orElseGet(() -> {
            String version = DeliverySender.class.getPackage().getImplementationVersion();
            return "quarkus-activitypub/" + (version == null ? "dev" : version) + " (+" + urls.baseUrl() + ")";
        });
    }
}

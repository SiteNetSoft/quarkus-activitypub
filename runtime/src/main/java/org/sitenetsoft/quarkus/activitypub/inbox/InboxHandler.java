package org.sitenetsoft.quarkus.activitypub.inbox;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.httpsignatures.VerificationResult;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubMediaTypes;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.server.ActivityPubRouting;
import org.sitenetsoft.quarkus.activitypub.server.BeanHandler;
import org.sitenetsoft.quarkus.activitypub.server.SignerIdentity;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.httpsignatures.vertx.RequestVerifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

/**
 * {@code POST paths.inbox} and {@code POST paths.shared-inbox}. In order: unknown username → {@code 404}; no signature
 * or one that does not verify → {@code 401} ({@code WWW-Authenticate: Signature}); body over
 * {@code quarkus.http-signatures.verify.max-body-size} → {@code 413}; not an ActivityPub content type → {@code 415};
 * not a JSON object, or not an activity → {@code 400}; {@code actor} absent or not the signer → {@code 401}; actor's
 * host blocked by {@link DomainPolicy} → {@code 202}, dropped; {@code id} already seen → {@code 202}, dropped;
 * otherwise {@code 202} and {@link InboxProcessor} takes over on a worker thread. Nothing here blocks the event loop.
 */
@ApplicationScoped
public class InboxHandler implements BeanHandler.VariantHandler {

    /** Route variant for {@code paths.inbox}. */
    public static final String ACTOR = "actor";
    /** Route variant for {@code paths.shared-inbox}. */
    public static final String SHARED = "shared";

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");
    private static final String UNKNOWN = "unknown";

    @Inject
    RequestVerifier verifier;

    @Inject
    SignerIdentity signers;

    @Inject
    Stores stores;

    @Inject
    ObjectMapper mapper;

    @Inject
    DomainPolicy domains;

    @Inject
    ActivityPubConfig config;

    @Inject
    InboxProcessor processor;

    @Inject
    InboxLog log;

    @Override
    public Handler<RoutingContext> variant(String variant) {
        boolean shared = SHARED.equals(variant);
        return ctx -> {
            if (ctx.request().method() != HttpMethod.POST) {
                ctx.response().setStatusCode(405).putHeader("Allow", "POST").end();
                return;
            }
            if (shared) {
                receive(ctx, Optional.empty());
                return;
            }
            ActivityPubRouting.respond(ctx, stores.reactiveActors().findByUsername(ctx.pathParam("username")),
                    recipient -> {
                        if (recipient.isEmpty()) {
                            ctx.response().setStatusCode(404).end();
                        } else {
                            receive(ctx, recipient);
                        }
                    });
        };
    }

    private void receive(RoutingContext ctx, Optional<LocalActor> recipient) {
        if (!RequestVerifier.hasSignature(ctx)) {
            unauthorized(ctx, UNKNOWN, null);
            return;
        }
        onContext(verifier.verify(ctx)).subscribe().with(result -> {
            try {
                verified(ctx, recipient, result);
            } catch (RuntimeException e) {
                fail(ctx, e);
            }
        }, failure -> {
            if (failure instanceof RequestVerifier.BodyTooLargeException) {
                log.record(UNKNOWN, null, InboxLog.INVALID);
                ctx.response().setStatusCode(413).end();
            } else {
                LOG.debugf("Inbox %s: signature not accepted: %s", ctx.normalizedPath(), failure.getMessage());
                unauthorized(ctx, UNKNOWN, null);
            }
        });
    }

    private void verified(RoutingContext ctx, Optional<LocalActor> recipient, VerificationResult result) {
        if (!ActivityPubMediaTypes.isActivityPub(ctx.request().getHeader("Content-Type"))) {
            invalid(ctx, 415, UNKNOWN, null);
            return;
        }
        Buffer buffer = ctx.getBody();
        byte[] body = buffer == null ? new byte[0] : buffer.getBytes();
        Optional<Activity> parsed = parse(body);
        if (parsed.isEmpty()) {
            invalid(ctx, 400, UNKNOWN, null);
            return;
        }
        Activity activity = parsed.get();
        String type = activity.getClass().getSimpleName();
        Optional<URI> actor = activity.actorUri();
        Optional<URI> signer = signers.actorOf(result.keyId());
        if (actor.isEmpty() || signer.isEmpty() || !actor.get().equals(signer.get())) {
            LOG.debugf("Inbox %s: %s actor %s is not the signer %s", ctx.normalizedPath(), type, actor.orElse(null),
                    signer.orElse(null));
            unauthorized(ctx, type, actor.orElse(null));
            return;
        }
        URI actorId = actor.get();
        if (domains.allow(actorId.getHost()) == DomainPolicy.Decision.BLOCK) {
            LOG.debugf("Inbox %s: %s from blocked %s dropped", ctx.normalizedPath(), type, actorId);
            log.record(type, actorId, InboxLog.BLOCKED);
            accepted(ctx);
            return;
        }
        if (activity.id() == null) {
            accept(ctx, recipient, body, activity, result.keyId(), actorId);
            return;
        }
        ActivityPubRouting.respond(ctx, stores.reactiveObjects().markSeen(activity.id(), config.dedup().retention()),
                first -> {
                    if (first) {
                        accept(ctx, recipient, body, activity, result.keyId(), actorId);
                    } else {
                        log.record(type, actorId, InboxLog.DUPLICATE);
                        accepted(ctx);
                    }
                });
    }

    private void accept(RoutingContext ctx, Optional<LocalActor> recipient, byte[] body, Activity activity,
            String keyId, URI actor) {
        accepted(ctx);
        processor.submit(recipient, body, activity, keyId, actor);
    }

    /** The body as an activity of a known type; empty for anything else (not JSON, not an object, not an activity). */
    private Optional<Activity> parse(byte[] body) {
        try {
            JsonNode tree = mapper.readTree(body);
            if (tree == null || !tree.isObject()) {
                return Optional.empty();
            }
            ASObject object = mapper.treeToValue(tree, ASObject.class);
            return object instanceof Activity activity ? Optional.of(activity) : Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void accepted(RoutingContext ctx) {
        ctx.response().setStatusCode(202).end();
    }

    private void unauthorized(RoutingContext ctx, String type, URI actor) {
        log.record(type, actor, InboxLog.REJECTED);
        ctx.response().setStatusCode(401).putHeader("WWW-Authenticate", "Signature").end();
    }

    private void invalid(RoutingContext ctx, int status, String type, URI actor) {
        log.record(type, actor, InboxLog.INVALID);
        ctx.response().setStatusCode(status).end();
    }

    private static void fail(RoutingContext ctx, Throwable failure) {
        LOG.errorf(failure, "ActivityPub inbox %s failed", ctx.normalizedPath());
        if (!ctx.response().ended() && !ctx.response().headWritten()) {
            ctx.response().setStatusCode(500).end();
        }
    }

    /** Delivers {@code uni}'s outcome on the request's Vert.x context. */
    private static <T> Uni<T> onContext(Uni<T> uni) {
        Context context = Vertx.currentContext();
        return context == null ? uni : uni.emitOn(command -> context.runOnContext(v -> command.run()));
    }
}

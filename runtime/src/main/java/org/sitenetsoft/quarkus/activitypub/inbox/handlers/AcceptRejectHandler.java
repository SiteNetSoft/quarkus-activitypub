package org.sitenetsoft.quarkus.activitypub.inbox.handlers;

import java.net.URI;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Reject;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityHandler;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * {@code Accept} / {@code Reject} of a {@code Follow} a local actor sent to the answering actor: the "following"
 * relationship becomes {@code ACCEPTED} / {@code REJECTED}. The {@code Follow} is the embedded object or a reference
 * to our stored one (never fetched); answers to follows we have no record of are ignored.
 */
public final class AcceptRejectHandler {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    private AcceptRejectHandler() {
    }

    @Priority(0)
    @ApplicationScoped
    public static class OnAccept implements InboundActivityHandler<Accept> {

        @Inject
        ActivityPubUrls urls;

        @Inject
        Stores stores;

        @Override
        public Class<Accept> type() {
            return Accept.class;
        }

        @Override
        public void handle(Accept accept, InboundActivityContext ctx) {
            answer(urls, stores, accept, ctx.signerActor(), FollowState.ACCEPTED);
        }
    }

    @Priority(0)
    @ApplicationScoped
    public static class OnReject implements InboundActivityHandler<Reject> {

        @Inject
        ActivityPubUrls urls;

        @Inject
        Stores stores;

        @Override
        public Class<Reject> type() {
            return Reject.class;
        }

        @Override
        public void handle(Reject reject, InboundActivityContext ctx) {
            answer(urls, stores, reject, ctx.signerActor(), FollowState.REJECTED);
        }
    }

    static void answer(ActivityPubUrls urls, Stores stores, Activity answer, URI remote, FollowState state) {
        Optional<ASObject> object = answer.embeddedObject()
                .or(() -> answer.objectUri().filter(urls::isLocal).flatMap(id -> stores.objects().find(id)));
        if (object.isEmpty() || !(object.get() instanceof Follow follow)) {
            return;
        }
        Optional<URI> local = follow.actorUri().flatMap(urls::usernameOf).map(urls::actor);
        if (local.isEmpty() || !follow.objectUri().filter(remote::equals).isPresent()) {
            LOG.debugf("%s %s by %s is not about a follow of it by a local actor", answer.primaryType(), answer.id(),
                    remote);
            return;
        }
        if (stores.followers().followingState(local.get(), remote).isEmpty()) {
            LOG.debugf("%s %s by %s answers no follow we sent", answer.primaryType(), answer.id(), remote);
            return;
        }
        stores.followers().addFollowing(local.get(), remote, state, follow.id());
    }
}

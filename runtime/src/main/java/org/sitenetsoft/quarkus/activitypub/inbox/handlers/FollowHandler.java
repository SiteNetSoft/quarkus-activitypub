package org.sitenetsoft.quarkus.activitypub.inbox.handlers;

import java.net.URI;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Reject;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.inbox.FollowPolicy;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityHandler;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

/**
 * {@code Follow} of a local actor: {@link FollowPolicy} decides. {@code ACCEPT} stores the follower (with the inbox of
 * its fetched actor document) as {@code ACCEPTED} and publishes {@code Accept}; {@code PENDING} stores it as
 * {@code PENDING}; {@code REJECT} publishes {@code Reject}. The answer embeds the {@code Follow} as received and is
 * addressed to the follower. A {@code Follow} of anything else is ignored.
 * <p>
 * The follower's actor document is fetched in {@link #prepare}, outside the store transaction; {@link #handle} only
 * reads what it found. A follower that cannot be fetched (or has no inbox) fails {@code ACCEPT}/{@code PENDING}, not
 * {@code REJECT}.
 */
@Priority(0)
@ApplicationScoped
public class FollowHandler implements InboundActivityHandler<Follow> {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    ActivityPubUrls urls;

    @Inject
    LocalActors actors;

    @Inject
    Stores stores;

    @Inject
    ActivityPubClient client;

    @Inject
    FollowPolicy policy;

    @Inject
    Instance<ActivityPub> activityPub;

    @Inject
    PreparedFollower prepared;

    /** The follower's inbox as {@link #prepare} resolved it, for the {@code handle} of the same inbound activity. */
    @RequestScoped
    public static class PreparedFollower {

        private URI follower;
        private URI inbox;
        private RuntimeException failure;

        void resolved(URI follower, URI inbox, RuntimeException failure) {
            this.follower = follower;
            this.inbox = inbox;
            this.failure = failure;
        }

        URI inboxOf(URI actor) {
            if (!actor.equals(follower)) {
                throw new IllegalStateException("Follower " + actor + " was not prepared");
            }
            if (failure != null) {
                throw failure;
            }
            if (inbox == null) {
                throw new IllegalStateException("Follower " + actor + " has no inbox");
            }
            return inbox;
        }
    }

    @Override
    public Class<Follow> type() {
        return Follow.class;
    }

    /** Fetches the follower's actor document when the {@code Follow} is of a local actor. */
    @Override
    public void prepare(Follow follow, InboundActivityContext ctx) {
        if (localActor(follow).isEmpty()) {
            return;
        }
        URI follower = ctx.signerActor();
        try {
            prepared.resolved(follower, client.fetchActor(follower).deliveryInbox(), null);
        } catch (RuntimeException e) {
            prepared.resolved(follower, null, e);
        }
    }

    private Optional<LocalActor> localActor(Follow follow) {
        return follow.objectUri().flatMap(urls::usernameOf).flatMap(actors::find);
    }

    @Override
    public void handle(Follow follow, InboundActivityContext ctx) {
        Optional<LocalActor> local = localActor(follow);
        if (local.isEmpty()) {
            LOG.debugf("Follow %s of %s: not a local actor, ignored", follow.id(), follow.objectUri().orElse(null));
            return;
        }
        URI follower = ctx.signerActor();
        switch (policy.decide(local.get(), follow, ctx)) {
            case ACCEPT -> {
                store(local.get(), follower, FollowState.ACCEPTED, follow);
                answer(local.get(), new Accept(), follow, follower);
            }
            case PENDING -> store(local.get(), follower, FollowState.PENDING, follow);
            case REJECT -> answer(local.get(), new Reject(), follow, follower);
        }
    }

    private void store(LocalActor local, URI follower, FollowState state, Follow follow) {
        URI inbox = prepared.inboxOf(follower);
        stores.followers().add(local.id(), follower, inbox, state, follow.id());
    }

    private void answer(LocalActor local, Activity answer, Follow follow, URI follower) {
        if (!activityPub.isResolvable()) {
            LOG.warnf("No ActivityPub implementation; %s of %s not sent", answer.primaryType(), follow.id());
            return;
        }
        Follow embedded = (Follow) follow.copy();
        embedded.context(null);
        answer.actor(ObjectOrLink.of(local.id())).object(ObjectOrLink.of(embedded));
        answer.to(ObjectOrLink.of(follower));
        activityPub.get().publish(local, answer);
    }
}

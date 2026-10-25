package org.sitenetsoft.quarkus.activitypub.inbox.handlers;

import java.net.URI;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
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

    @Override
    public Class<Follow> type() {
        return Follow.class;
    }

    @Override
    public void handle(Follow follow, InboundActivityContext ctx) {
        Optional<LocalActor> local = follow.objectUri().flatMap(urls::usernameOf).flatMap(actors::find);
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
        URI inbox = client.fetchActor(follower).deliveryInbox();
        if (inbox == null) {
            throw new IllegalStateException("Follower " + follower + " has no inbox");
        }
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

package org.sitenetsoft.quarkus.activitypub.outbound;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Accept;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Announce;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Delete;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Like;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Reject;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.activitystreams.Undo;
import org.sitenetsoft.activitystreams.Update;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.Ids;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.outbound.RecipientResolver.RemoteRecipients;
import org.sitenetsoft.quarkus.activitypub.store.FollowRelation;
import org.sitenetsoft.quarkus.activitypub.store.FollowState;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.store.TransactionRunner;

/**
 * The outbound API (blocking). {@link ReactiveActivityPubImpl} runs it on a worker thread.
 * <p>
 * {@link #publish} first looks up the remote recipients (network, outside any transaction it opens), then works in
 * one store transaction (joining the caller's): it assigns missing ids
 * ({@code activities/{ulid}}, {@code objects/{ulid}}), the actor and {@code published}; stores the activity and the
 * object it creates or updates (both without {@code bto}/{@code bcc}, which are never served); resolves the recipients
 * and hands the deliveries to the {@link DeliveryExecutor}. The caller gets the activity back with {@code bto}/
 * {@code bcc} intact. Looking up the recipients can take up to {@code fetch.timeout} per remote recipient not yet
 * cached; the methods that also change other stores (follow, undo, delete, answers) do that lookup before their own
 * transaction too.
 */
@ApplicationScoped
public class ActivityPubImpl implements ActivityPub {

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Inject
    TransactionRunner transactions;

    @Inject
    RecipientResolver recipients;

    @Inject
    DeliveryExecutor executor;

    @Inject
    ActivityPubClient client;

    @Inject
    Clock clock;

    @Override
    public Activity publish(LocalActor actor, Activity activity) {
        RemoteRecipients remote = recipients.prefetch(activity);
        return transactions.inTransaction(() -> doPublish(actor, activity, remote));
    }

    /** The store part of {@link #publish}; runs inside the transaction, {@code remote} already looked up. */
    private Activity doPublish(LocalActor actor, Activity activity, RemoteRecipients remote) {
        Instant now = clock.instant();
        if (activity.id() == null) {
            activity.id(urls.activity(Ids.ulid()));
        }
        if (activity.actor() == null) {
            activity.actor(ObjectOrLink.of(actor.id()));
        }
        if (activity.published() == null) {
            activity.published(now);
        }
        boolean isPublic = activity.isPublic() || activity.embeddedObject().map(ASObject::isPublic).orElse(false);
        Optional<ASObject> object = activity.embeddedObject();
        boolean storeObject = false;
        if (object.isPresent() && object.get().id() == null) {
            ASObject o = object.get();
            o.id(urls.object(Ids.ulid()));
            if (o.attributedTo().isEmpty()) {
                o.attributedTo(ObjectOrLink.of(actor.id()));
            }
            if (o.published() == null) {
                o.published(now);
            }
            storeObject = true;
        } else if (object.isPresent() && (activity instanceof Create || activity instanceof Update)) {
            storeObject = urls.isLocal(object.get().id());
        }
        Activity stored = RecipientResolver.withoutBlindAudience(activity);
        if (storeObject) {
            stores.objects().putLocal(stored.embeddedObject().orElseThrow(), isPublic);
        }
        stores.objects().putLocal(stored, isPublic);
        executor.submit(recipients.resolve(actor, activity, remote));
        LOG.debugf("%s %s published by %s", activity.primaryType(), activity.id(), actor.id());
        return activity;
    }

    @Override
    public Activity create(LocalActor actor, ASObject object) {
        return publish(actor, wrap(new Create(), actor, object));
    }

    @Override
    public Activity update(LocalActor actor, ASObject object) {
        if (object.id() == null) {
            throw new IllegalArgumentException("Update needs the id of the object to update");
        }
        if (object.updated() == null) {
            object.updated(clock.instant());
        }
        return publish(actor, wrap(new Update(), actor, object));
    }

    /** {@code activity} by {@code actor} carrying {@code object}, with the object's audience. */
    private static Activity wrap(Activity activity, LocalActor actor, ASObject object) {
        activity.actor(ObjectOrLink.of(actor.id())).object(ObjectOrLink.of(object));
        copyAudience(object, activity);
        return activity;
    }

    private static void copyAudience(ASObject from, ASObject to) {
        to.to(from.to().toArray(ObjectOrLink[]::new));
        to.cc(from.cc().toArray(ObjectOrLink[]::new));
        to.bto(from.bto().toArray(ObjectOrLink[]::new));
        to.bcc(from.bcc().toArray(ObjectOrLink[]::new));
        to.audience(from.audience().toArray(ObjectOrLink[]::new));
    }

    /**
     * The object becomes a {@code Tombstone}; the {@code Delete} goes to the public and the actor's followers.
     *
     * @throws IllegalArgumentException unless {@code objectId} is a stored local object attributed to {@code actor}
     */
    @Override
    public Activity delete(LocalActor actor, URI objectId) {
        Tombstone tombstone = new Tombstone();
        tombstone.id(objectId);
        Delete delete = new Delete();
        delete.actor(ObjectOrLink.of(actor.id())).object(ObjectOrLink.of(tombstone));
        delete.to(ObjectOrLink.of(Audience.PUBLIC));
        delete.cc(ObjectOrLink.of(urls.followers(actor.username())));
        RemoteRecipients remote = recipients.prefetch(delete);
        return transactions.inTransaction(() -> {
            boolean own = urls.isLocal(objectId) && stores.objects().find(objectId)
                    .filter(o -> o.attributedTo().stream().anyMatch(a -> a.uri().filter(actor.id()::equals).isPresent()))
                    .isPresent();
            if (!own) {
                throw new IllegalArgumentException(objectId + " is not a local object attributed to " + actor.id());
            }
            stores.objects().tombstone(objectId);
            return doPublish(actor, delete, remote);
        });
    }

    /** Records the follow as {@code PENDING} until the remote actor accepts it. */
    @Override
    public Activity follow(LocalActor actor, URI remoteActor) {
        Follow follow = new Follow();
        follow.actor(ObjectOrLink.of(actor.id())).object(ObjectOrLink.of(remoteActor));
        follow.to(ObjectOrLink.of(remoteActor));
        RemoteRecipients remote = recipients.prefetch(follow);
        return transactions.inTransaction(() -> {
            doPublish(actor, follow, remote);
            stores.followers().addFollowing(actor.id(), remoteActor, FollowState.PENDING, follow.id());
            return follow;
        });
    }

    /**
     * Undoes the stored follow (embedded, so servers that do not dereference it still recognise it) and forgets it.
     *
     * @throws IllegalStateException when {@code actor} does not follow {@code remoteActor}
     */
    @Override
    public Activity undoFollow(LocalActor actor, URI remoteActor) {
        Undo undo = new Undo();
        undo.actor(ObjectOrLink.of(actor.id()));
        undo.to(ObjectOrLink.of(remoteActor));
        RemoteRecipients remote = recipients.prefetch(undo);
        return transactions.inTransaction(() -> {
            FollowRelation relation = stores.followers().followingRelation(actor.id(), remoteActor)
                    .orElseThrow(() -> new IllegalStateException(actor.id() + " does not follow " + remoteActor));
            undo.object(ObjectOrLink.of(storedFollow(relation.followActivityId(), actor.id(), remoteActor)));
            doPublish(actor, undo, remote);
            stores.followers().removeFollowing(actor.id(), remoteActor);
            return undo;
        });
    }

    @Override
    public void acceptFollow(LocalActor actor, URI follower) {
        answerFollow(actor, follower, new Accept(), FollowState.ACCEPTED);
    }

    @Override
    public void rejectFollow(LocalActor actor, URI follower) {
        answerFollow(actor, follower, new Reject(), FollowState.REJECTED);
    }

    /**
     * Stores the new state and sends {@code answer} embedding the follower's {@code Follow} (by its stored id).
     *
     * @throws IllegalStateException when {@code follower} never asked to follow {@code actor}
     */
    private void answerFollow(LocalActor actor, URI follower, Activity answer, FollowState state) {
        answer.actor(ObjectOrLink.of(actor.id()));
        answer.to(ObjectOrLink.of(follower));
        RemoteRecipients remote = recipients.prefetch(answer);
        transactions.inTransaction(() -> {
            FollowRelation relation = stores.followers().follower(actor.id(), follower)
                    .orElseThrow(() -> new IllegalStateException(follower + " has not asked to follow " + actor.id()));
            URI inbox = relation.inbox()
                    .orElseThrow(() -> new IllegalStateException("No inbox stored for follower " + follower));
            stores.followers().add(actor.id(), follower, inbox, state, relation.followActivityId().orElse(null));
            Follow follow = new Follow();
            follow.id(relation.followActivityId().orElse(null));
            follow.actor(ObjectOrLink.of(follower)).object(ObjectOrLink.of(actor.id()));
            answer.object(ObjectOrLink.of(follow));
            doPublish(actor, answer, remote);
        });
    }

    /** Public, to the actor's followers and the object's author. */
    @Override
    public Activity announce(LocalActor actor, URI objectId) {
        Announce announce = new Announce();
        announce.actor(ObjectOrLink.of(actor.id())).object(ObjectOrLink.of(objectId));
        announce.to(ObjectOrLink.of(Audience.PUBLIC));
        announce.cc(audience(urls.followers(actor.username()), authorOf(objectId)));
        return publish(actor, announce);
    }

    /** To the object's author. */
    @Override
    public Activity like(LocalActor actor, URI objectId) {
        Like like = new Like();
        like.actor(ObjectOrLink.of(actor.id())).object(ObjectOrLink.of(objectId));
        like.to(audience(null, authorOf(objectId)));
        return publish(actor, like);
    }

    /**
     * Undoes one of the actor's stored activities, embedded and with its audience; undoing a {@code Follow} also
     * forgets the follow.
     *
     * @throws IllegalArgumentException when {@code activityId} is not a stored activity of {@code actor}
     */
    @Override
    public Activity undo(LocalActor actor, URI activityId) {
        Activity original = stores.objects().find(activityId)
                .filter(Activity.class::isInstance).map(Activity.class::cast)
                .filter(a -> a.actorUri().filter(actor.id()::equals).isPresent())
                .orElseThrow(() -> new IllegalArgumentException(activityId + " is not an activity of " + actor.id()));
        Undo undo = new Undo();
        undo.actor(ObjectOrLink.of(actor.id())).object(ObjectOrLink.of(original));
        copyAudience(original, undo);
        RemoteRecipients remote = recipients.prefetch(undo);
        return transactions.inTransaction(() -> {
            doPublish(actor, undo, remote);
            if (original instanceof Follow follow) {
                follow.objectUri().ifPresent(followed -> stores.followers().removeFollowing(actor.id(), followed));
            }
            return undo;
        });
    }

    /** The stored {@code Follow}, else one rebuilt from the relationship. */
    private Follow storedFollow(Optional<URI> followId, URI local, URI remote) {
        return followId.flatMap(id -> stores.objects().find(id)).filter(Follow.class::isInstance).map(Follow.class::cast)
                .orElseGet(() -> {
                    Follow follow = new Follow();
                    follow.id(followId.orElse(null));
                    follow.actor(ObjectOrLink.of(local)).object(ObjectOrLink.of(remote));
                    return follow;
                });
    }

    /** The first {@code attributedTo} of the object (fetched, or read locally), empty when it cannot be found. */
    private Optional<URI> authorOf(URI objectId) {
        try {
            return client.fetch(objectId).attributedTo().stream().map(ObjectOrLink::uri).flatMap(Optional::stream)
                    .findFirst();
        } catch (RuntimeException e) {
            LOG.debugf("Author of %s unknown: %s", objectId, e.getMessage());
            return Optional.empty();
        }
    }

    private static ObjectOrLink[] audience(URI first, Optional<URI> second) {
        return Stream.concat(Stream.ofNullable(first), second.stream())
                .map(ObjectOrLink::of).toArray(ObjectOrLink[]::new);
    }
}

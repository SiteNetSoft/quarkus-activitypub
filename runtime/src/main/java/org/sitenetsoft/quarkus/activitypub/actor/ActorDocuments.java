package org.sitenetsoft.quarkus.activitypub.actor;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.Actor;
import org.sitenetsoft.activitystreams.Application;
import org.sitenetsoft.activitystreams.Endpoints;
import org.sitenetsoft.activitystreams.Group;
import org.sitenetsoft.activitystreams.Image;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Organization;
import org.sitenetsoft.activitystreams.Person;
import org.sitenetsoft.activitystreams.PublicKey;
import org.sitenetsoft.activitystreams.Service;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;

/**
 * Builds the actor document of a local actor.
 */
@ApplicationScoped
public class ActorDocuments {

    @Inject
    ActivityPubUrls urls;

    /**
     * The actor document: collections and inbox from the URL templates, {@code endpoints.sharedInbox}, the public key
     * as {@code {id: {actor}#main-key, owner, publicKeyPem}} (omitted when {@code keys} is null) and
     * {@link LocalActor#extra()} merged in.
     */
    public Actor toDocument(LocalActor actor, ActorKeyPair keys) {
        Actor doc = newActor(actor.type());
        doc.id(actor.id());
        doc.preferredUsername(actor.username())
                .inbox(urls.inbox(actor.username()))
                .outbox(urls.outbox(actor.username()))
                .followers(urls.followers(actor.username()))
                .following(urls.following(actor.username()))
                .endpoints(new Endpoints().sharedInbox(urls.sharedInbox()))
                .manuallyApprovesFollowers(actor.manuallyApprovesFollowers())
                .discoverable(actor.discoverable());
        actor.name().ifPresent(doc::name);
        actor.summary().ifPresent(doc::summary);
        actor.url().ifPresent(url -> doc.url(ObjectOrLink.of(url)));
        actor.icon().ifPresent(icon -> doc.icon(image(icon)));
        actor.image().ifPresent(image -> doc.image(image(image)));
        doc.published(actor.published());
        if (keys != null) {
            PublicKey key = new PublicKey().owner(actor.id()).publicKeyPem(keys.publicKeyPem());
            key.id(URI.create(actor.id() + "#main-key"));
            doc.publicKey(key);
        }
        actor.extra().forEach(doc::extra);
        return doc;
    }

    private static ObjectOrLink image(URI url) {
        Image image = new Image();
        image.url(ObjectOrLink.of(url));
        return ObjectOrLink.of(image);
    }

    /** The model class for an actor type; other types are written as given on a {@code Person} body. */
    private static Actor newActor(String type) {
        Actor actor = switch (type) {
            case "Application" -> new Application();
            case "Group" -> new Group();
            case "Organization" -> new Organization();
            case "Service" -> new Service();
            default -> new Person();
        };
        actor.type(type);
        return actor;
    }
}

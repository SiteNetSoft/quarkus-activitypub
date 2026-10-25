package org.acme.demo;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxLog;

/**
 * The sample's own endpoints: publish a public note as a local actor, and show what the inbox received.
 */
@Path("/demo")
public class DemoResource {

    @Inject
    LocalActors actors;

    @Inject
    ActivityPub activityPub;

    @Inject
    ActivityPubUrls urls;

    @Inject
    InboxLog inboxLog;

    /** Publishes {@code text} as a public note of {@code user} to its followers; answers the {@code Create} id. */
    @POST
    @Path("/post")
    @Produces(MediaType.TEXT_PLAIN)
    public String post(@QueryParam("user") String user, @QueryParam("text") String text) {
        if (user == null || text == null || text.isBlank()) {
            throw new BadRequestException("user and text are required");
        }
        LocalActor actor = actors.find(user).orElseThrow(() -> new NotFoundException("No local actor '" + user + "'"));
        Note note = new Note();
        note.content(text);
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        note.cc(ObjectOrLink.of(urls.followers(user)));
        Activity create = activityPub.create(actor, note);
        return create.id().toString();
    }

    /** The last inbox outcomes, oldest first. */
    @GET
    @Path("/inbox-log")
    @Produces(MediaType.APPLICATION_JSON)
    public List<InboxLog.InboxEntry> inboxLog() {
        return inboxLog.entries();
    }
}

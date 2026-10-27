package org.acme.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

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
 * <p>
 * {@code POST /demo/post} is protected by {@code demo.token}: when set, the request needs
 * {@code Authorization: Bearer <token>} (else 401); when unset (the default in the prod profile) the endpoint is
 * disabled and answers 404. The dev and test profiles set a fixed token.
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

    @ConfigProperty(name = "demo.token")
    Optional<String> token;

    /**
     * Publishes {@code text} (HTML-escaped, in a paragraph) as a public note of {@code user} to its followers; answers
     * the {@code Create} id.
     */
    @POST
    @Path("/post")
    @Produces(MediaType.TEXT_PLAIN)
    public String post(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization, @QueryParam("user") String user,
            @QueryParam("text") String text) {
        String expected = token.filter(t -> !t.isBlank()).orElseThrow(NotFoundException::new);
        if (!bearerMatches(authorization, expected)) {
            throw new NotAuthorizedException("Bearer");
        }
        if (user == null || text == null || text.isBlank()) {
            throw new BadRequestException("user and text are required");
        }
        LocalActor actor = actors.find(user).orElseThrow(() -> new NotFoundException("No local actor '" + user + "'"));
        Note note = new Note();
        note.content("<p>" + escapeHtml(text) + "</p>");
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        note.cc(ObjectOrLink.of(urls.followers(user)));
        Activity create = activityPub.create(actor, note);
        return create.id().toString();
    }

    private static boolean bearerMatches(String authorization, String expected) {
        String prefix = "Bearer ";
        if (authorization == null || !authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return false;
        }
        byte[] given = authorization.substring(prefix.length()).strip().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, expected.getBytes(StandardCharsets.UTF_8));
    }

    static String escapeHtml(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** The last inbox outcomes, oldest first. */
    @GET
    @Path("/inbox-log")
    @Produces(MediaType.APPLICATION_JSON)
    public List<InboxLog.InboxEntry> inboxLog() {
        return inboxLog.entries();
    }
}

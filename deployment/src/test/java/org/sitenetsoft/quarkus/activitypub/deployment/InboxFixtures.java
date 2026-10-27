package org.sitenetsoft.quarkus.activitypub.deployment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Create;
import org.sitenetsoft.activitystreams.Delete;
import org.sitenetsoft.activitystreams.Follow;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Undo;
import org.sitenetsoft.activitystreams.Update;
import org.sitenetsoft.httpsignatures.Scheme;
import org.sitenetsoft.quarkus.activitypub.test.FakeActor;

/**
 * Activities and requests for the inbox tests. Local URLs follow {@link LoopbackTests#BASE}.
 */
final class InboxFixtures {

    static final URI ALICE = URI.create(LoopbackTests.BASE + "/users/alice");
    static final URI ALICE_INBOX = URI.create(LoopbackTests.BASE + "/users/alice/inbox");
    static final URI BOB = URI.create(LoopbackTests.BASE + "/users/bob");
    static final URI BOB_INBOX = URI.create(LoopbackTests.BASE + "/users/bob/inbox");
    static final URI SHARED_INBOX = URI.create(LoopbackTests.BASE + "/inbox");
    static final String ACTIVITY_JSON = "application/activity+json";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private InboxFixtures() {
    }

    /** A fresh id under {@code actor}'s server. */
    static URI idOf(FakeActor actor, String kind) {
        return URI.create(actor.id().resolve("/") + kind + "/" + UUID.randomUUID());
    }

    static Follow follow(FakeActor from, URI object) {
        Follow follow = new Follow();
        follow.id(idOf(from, "activities"));
        follow.actor(ObjectOrLink.of(from.id())).object(ObjectOrLink.of(object));
        return follow;
    }

    static Undo undo(FakeActor from, ASObject object) {
        Undo undo = new Undo();
        undo.id(idOf(from, "activities"));
        undo.actor(ObjectOrLink.of(from.id())).object(ObjectOrLink.of(object));
        return undo;
    }

    /** A {@code Note} by {@code from}, addressed to {@code to}. */
    static Note note(FakeActor from, URI... to) {
        Note note = new Note();
        note.id(idOf(from, "objects"));
        note.attributedTo(ObjectOrLink.of(from.id()));
        note.content("<p>hello</p>");
        ObjectOrLink[] audience = new ObjectOrLink[to.length];
        for (int i = 0; i < to.length; i++) {
            audience[i] = ObjectOrLink.of(to[i]);
        }
        note.to(audience);
        return note;
    }

    static Create create(FakeActor from, ASObject object) {
        Create create = new Create();
        create.id(idOf(from, "activities"));
        create.actor(ObjectOrLink.of(from.id())).object(ObjectOrLink.of(object));
        create.to(object.to().toArray(ObjectOrLink[]::new));
        return create;
    }

    static Update update(FakeActor from, ASObject object) {
        Update update = new Update();
        update.id(idOf(from, "activities"));
        update.actor(ObjectOrLink.of(from.id())).object(ObjectOrLink.of(object));
        update.to(object.to().toArray(ObjectOrLink[]::new));
        return update;
    }

    static Delete delete(FakeActor from, URI object) {
        Delete delete = new Delete();
        delete.id(idOf(from, "activities"));
        delete.actor(ObjectOrLink.of(from.id())).object(ObjectOrLink.of(object));
        return delete;
    }

    static Activity withoutId(Activity activity) {
        activity.id(null);
        return activity;
    }

    /** POSTs {@code body} signed by {@code signer} (Cavage), with {@code contentType} instead of the ActivityPub one. */
    static HttpResponse<byte[]> send(FakeActor signer, URI target, byte[] body, String contentType) {
        HttpRequest.Builder request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (signer != null) {
            signer.sign("POST", target, body, Scheme.CAVAGE).headers().forEach((name, value) -> {
                if (!name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Content-Type")) {
                    request.header(name, value);
                }
            });
        }
        request.header("Content-Type", contentType);
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("POST to " + target + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** {@code activity} as JSON with {@code published} set to a value that does not map to an instant. */
    static byte[] withUnmappableField(ASObject activity) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode json = org.sitenetsoft.activitystreams.ASJson.mapper()
                    .valueToTree(activity);
            json.put("published", "not-a-date");
            return org.sitenetsoft.activitystreams.ASJson.mapper().writeValueAsBytes(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

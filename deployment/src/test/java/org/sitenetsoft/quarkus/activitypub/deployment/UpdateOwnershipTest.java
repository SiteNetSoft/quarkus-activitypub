package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.Audience;
import org.sitenetsoft.activitystreams.Note;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Update;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.test.QuarkusUnitTest;

public class UpdateOwnershipTest {

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest(AliceActor.class);

    @Inject
    ActivityPub activityPub;

    @Inject
    Stores stores;

    @Inject
    AliceActor alice;

    private static Note note(String path, URI author, String content) {
        Note note = new Note();
        note.id(URI.create(LoopbackTests.BASE + path)).content(content).attributedTo(ObjectOrLink.of(author));
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        return note;
    }

    @Test
    void updateOfOtherActorsObjectIsRefused() {
        URI bob = URI.create(LoopbackTests.BASE + "/users/bob");
        Note stored = note("/objects/upd-bobs", bob, "original");
        stores.objects().putLocal(stored, true);
        // the object passed in claims to be alice's; only the stored one counts
        Note forged = note("/objects/upd-bobs", AliceActor.ID, "hijacked");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> activityPub.update(alice.get(), forged));
        assertEquals(stored.id() + " is not a local object attributed to " + AliceActor.ID, e.getMessage());
        assertEquals("original", assertInstanceOf(Note.class, stores.objects().find(stored.id()).orElseThrow()).content());
    }

    @Test
    void updateOfRemoteIdIsRefused() {
        Note remote = note("/x", AliceActor.ID, "x");
        remote.id(URI.create("https://remote.example/objects/1"));
        assertThrows(IllegalArgumentException.class, () -> activityPub.update(alice.get(), remote));
    }

    @Test
    void updateOfOwnObjectStillWorks() {
        stores.objects().putLocal(note("/objects/upd-own", AliceActor.ID, "before"), true);
        Update update = assertInstanceOf(Update.class,
                activityPub.update(alice.get(), note("/objects/upd-own", AliceActor.ID, "after")));
        assertTrue(update.embeddedObject().isPresent());
        assertEquals("after", assertInstanceOf(Note.class,
                stores.objects().find(URI.create(LoopbackTests.BASE + "/objects/upd-own")).orElseThrow()).content());
    }
}

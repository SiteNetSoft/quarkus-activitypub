package org.sitenetsoft.quarkus.activitypub.inbox;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The last {@value #CAPACITY} inbox outcomes, newest last, for the Dev UI; each is also counted by
 * {@link InboxMetrics} when Micrometer is present.
 */
@ApplicationScoped
public class InboxLog {

    static final int CAPACITY = 100;

    /** Outcome results. */
    public static final String ACCEPTED = "accepted";
    public static final String DUPLICATE = "duplicate";
    public static final String BLOCKED = "blocked";
    /** A {@code Delete} of an actor we never knew: answered {@code 202} without verifying or processing it. */
    public static final String IGNORED = "ignored";
    public static final String REJECTED = "rejected";
    public static final String INVALID = "invalid";
    public static final String ERROR = "error";

    /**
     * @param type   the activity's type, {@code unknown} before it was parsed
     * @param actor  the activity's actor, null before it was parsed
     * @param result one of the constants above
     */
    public record InboxEntry(Instant at, String type, URI actor, String result) {
    }

    @Inject
    InboxMetrics metrics;

    private final Deque<InboxEntry> entries = new ArrayDeque<>(CAPACITY);

    public void record(String type, URI actor, String result) {
        InboxEntry entry = new InboxEntry(Instant.now(), type == null ? "unknown" : type, actor, result);
        synchronized (entries) {
            if (entries.size() == CAPACITY) {
                entries.removeFirst();
            }
            entries.addLast(entry);
        }
        metrics.count(entry.type(), result);
    }

    /** Oldest first. */
    public List<InboxEntry> entries() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }
}

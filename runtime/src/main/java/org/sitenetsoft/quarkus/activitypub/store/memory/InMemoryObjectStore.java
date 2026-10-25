package org.sitenetsoft.quarkus.activitypub.store.memory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.quarkus.activitypub.store.ObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.Page;

import io.quarkus.arc.DefaultBean;

/** Default {@link ObjectStore}: process memory, lost on restart. */
@DefaultBean
@ApplicationScoped
public class InMemoryObjectStore implements ObjectStore {

    private static final int PURGE_EVERY = 1024;

    private record Entry(ASObject object, boolean local, boolean isPublic, long seq) {

        Entry with(ASObject replacement) {
            return new Entry(replacement, local, isPublic, seq);
        }
    }

    private final Map<URI, Entry> objects = new ConcurrentHashMap<>();
    private final Map<URI, Instant> seen = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong markSeenCalls = new AtomicLong();

    public InMemoryObjectStore() {
        ProductionWarning.warnIfProduction("ObjectStore");
    }

    @Override
    public void putLocal(ASObject object, boolean isPublic) {
        objects.put(requireId(object), new Entry(object.copy(), true, isPublic, sequence.incrementAndGet()));
    }

    @Override
    public void putRemote(ASObject object, URI receivedFrom) {
        objects.put(requireId(object), new Entry(object.copy(), false, false, sequence.incrementAndGet()));
    }

    @Override
    public Optional<ASObject> find(URI id) {
        return Optional.ofNullable(objects.get(id)).map(e -> e.object().copy());
    }

    @Override
    public void tombstone(URI id) {
        objects.computeIfPresent(id, (key, e) -> e.object() instanceof Tombstone ? e
                : e.with(new Tombstone().formerType(e.object().primaryType()).deleted(Instant.now()).id(id)));
    }

    @Override
    public void deleteByActor(URI actorId) {
        objects.values().removeIf(e -> !e.local() && e.object().attributedTo().stream().findFirst()
                .flatMap(ObjectOrLink::uri).filter(actorId::equals).isPresent());
    }

    @Override
    public boolean markSeen(URI activityId, Duration retention) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(retention);
        if (markSeenCalls.incrementAndGet() % PURGE_EVERY == 0) {
            seen.values().removeIf(at -> !at.isAfter(cutoff));
        }
        boolean[] first = { false };
        seen.compute(activityId, (key, at) -> {
            if (at != null && at.isAfter(cutoff)) {
                return at;
            }
            first[0] = true;
            return now;
        });
        return first[0];
    }

    @Override
    public Page<Activity> outbox(URI actorId, int page, int size) {
        List<Activity> all = outboxEntries(actorId)
                .sorted(Comparator.comparing((Entry e) -> published(e.object())).thenComparingLong(Entry::seq).reversed())
                .map(e -> (Activity) e.object().copy())
                .toList();
        return Page.of(all, page, size);
    }

    @Override
    public long outboxCount(URI actorId) {
        return outboxEntries(actorId).count();
    }

    private Stream<Entry> outboxEntries(URI actorId) {
        return objects.values().stream()
                .filter(e -> e.local() && e.isPublic() && e.object() instanceof Activity a
                        && a.actorUri().filter(actorId::equals).isPresent());
    }

    private static Instant published(ASObject object) {
        return object.published() == null ? Instant.MIN : object.published();
    }

    private static URI requireId(ASObject object) {
        if (object.id() == null) {
            throw new IllegalArgumentException("Stored objects need an id");
        }
        return object.id();
    }
}

package org.sitenetsoft.quarkus.activitypub.hibernate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.Activity;
import org.sitenetsoft.activitystreams.Contexts;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.Tombstone;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApObject;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApSeenActivity;
import org.sitenetsoft.quarkus.activitypub.store.ObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.Page;

import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * {@link ObjectStore} on {@code ap_object} and {@code ap_seen_activity}; every method joins the caller's transaction or
 * opens one. Documents are kept as JSON ({@code body_json}); one stored with the default {@code @context} (or none)
 * reads back without it, as it was handed in: it is written again at the root when served.
 */
@ApplicationScoped
public class HibernateObjectStore implements ObjectStore {

    @Inject
    ActivityPubEntityManager entityManager;

    @Inject
    ActivityPubJson json;

    @Inject
    Clock clock;

    private EntityManager em() {
        return entityManager.get();
    }

    @Override
    public void putLocal(ASObject object, boolean isPublic) {
        put(object, true, isPublic, null);
    }

    @Override
    public void putRemote(ASObject object, URI receivedFrom) {
        put(object, false, false, receivedFrom);
    }

    private void put(ASObject object, boolean local, boolean isPublic, URI receivedFrom) {
        if (object.id() == null) {
            throw new IllegalArgumentException("Stored objects need an id");
        }
        String body = new String(json.write(object), StandardCharsets.UTF_8);
        Instant now = clock.instant();
        QuarkusTransaction.joiningExisting().run(() -> {
            ApObject row = em().find(ApObject.class, object.id().toString());
            boolean created = row == null;
            if (created) {
                row = new ApObject(object.id().toString());
            }
            row.setPrimaryType(object.primaryType());
            row.setActor(actorOf(object).map(URI::toString).orElse(null));
            row.setLocal(local);
            row.setPublic(isPublic);
            row.setActivity(object instanceof Activity);
            row.setPublished(object.published());
            row.setReceivedFrom(receivedFrom == null ? null : receivedFrom.toString());
            row.setBodyJson(body);
            row.setTombstonedAt(object instanceof Tombstone t ? (t.deleted() == null ? now : t.deleted()) : null);
            row.setCreated(now);
            if (created) {
                em().persist(row);
            }
        });
    }

    /** The {@code actor} of an activity, else the first {@code attributedTo}. */
    private static Optional<URI> actorOf(ASObject object) {
        if (object instanceof Activity activity) {
            return activity.actorUri();
        }
        return object.attributedTo().stream().findFirst().flatMap(ObjectOrLink::uri);
    }

    @Override
    public Optional<ASObject> find(URI id) {
        return QuarkusTransaction.joiningExisting()
                .call(() -> Optional.ofNullable(em().find(ApObject.class, id.toString())).map(this::read));
    }

    @Override
    public void tombstone(URI id) {
        Instant now = clock.instant();
        QuarkusTransaction.joiningExisting().run(() -> {
            ApObject row = em().find(ApObject.class, id.toString());
            if (row == null || row.getTombstonedAt() != null) {
                return;
            }
            Tombstone tombstone = new Tombstone().formerType(row.getPrimaryType()).deleted(now);
            tombstone.id(id);
            row.setPrimaryType(tombstone.primaryType());
            row.setBodyJson(new String(json.write(tombstone), StandardCharsets.UTF_8));
            row.setTombstonedAt(now);
        });
    }

    /** Removes the remote objects whose first {@code attributedTo} (an activity's {@code actor}) is {@code actorId}. */
    @Override
    public void deleteByActor(URI actorId) {
        // entity removes, not a bulk delete: rows read earlier in the caller's transaction must not stay cached
        QuarkusTransaction.joiningExisting().run(() -> {
            em().createQuery("from ApObject o where o.local = false and o.actor = :actor", ApObject.class)
                    .setParameter("actor", actorId.toString()).getResultList().forEach(em()::remove);
            em().flush();
        });
    }

    /**
     * Reads, then inserts or refreshes the row. Two concurrent first deliveries of one id can both get {@code true}
     * (one of them then fails on the primary key at commit); the inbox tolerates that.
     */
    @Override
    public boolean markSeen(URI activityId, Duration retention) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(retention);
        return QuarkusTransaction.joiningExisting().call(() -> {
            ApSeenActivity row = em().find(ApSeenActivity.class, activityId.toString());
            if (row == null) {
                em().persist(new ApSeenActivity(activityId.toString(), now));
                return true;
            }
            if (row.getSeenAt().isAfter(cutoff)) {
                return false;
            }
            row.setSeenAt(now);
            return true;
        });
    }

    @Override
    public void forgetSeen(URI activityId) {
        QuarkusTransaction.joiningExisting().run(() -> removeIfPresent(ApSeenActivity.class, activityId.toString()));
    }

    @Override
    public Page<Activity> outbox(URI actorId, int page, int size) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("page and size must be >= 1");
        }
        return QuarkusTransaction.joiningExisting().call(() -> {
            List<Activity> items = em().createQuery("from ApObject o where " + OUTBOX
                    + " order by o.published desc nulls last, o.created desc, o.id desc", ApObject.class)
                    .setParameter("actor", actorId.toString())
                    .setFirstResult((int) Math.min(Integer.MAX_VALUE, (long) (page - 1) * size)).setMaxResults(size)
                    .getResultStream().map(this::read).filter(Activity.class::isInstance).map(Activity.class::cast)
                    .toList();
            return new Page<>(items, page, size, count(actorId));
        });
    }

    @Override
    public long outboxCount(URI actorId) {
        return QuarkusTransaction.joiningExisting().call(() -> count(actorId));
    }

    private static final String OUTBOX = "o.actor = :actor and o.local = true and o.isPublic = true"
            + " and o.activity = true and o.tombstonedAt is null";

    private long count(URI actorId) {
        return em().createQuery("select count(o) from ApObject o where " + OUTBOX, Long.class)
                .setParameter("actor", actorId.toString()).getSingleResult();
    }

    private void removeIfPresent(Class<?> entity, Object id) {
        Object row = em().find(entity, id);
        if (row != null) {
            em().remove(row);
            em().flush(); // deletes run before inserts: a re-add of the same key in this transaction must follow it
        }
    }

    private ASObject read(ApObject row) {
        ASObject object = json.read(row.getBodyJson().getBytes(StandardCharsets.UTF_8));
        if (Contexts.DEFAULT.equals(object.context())) {
            object.context(null);
        }
        return object;
    }
}

package org.sitenetsoft.quarkus.activitypub.hibernate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApLocalActor;
import org.sitenetsoft.quarkus.activitypub.store.ActorStore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** {@link ActorStore} on {@code ap_local_actor}; every method joins the caller's transaction or opens one. */
@ApplicationScoped
public class HibernateActorStore implements ActorStore {

    private static final TypeReference<LinkedHashMap<String, JsonNode>> EXTRA = new TypeReference<>() {
    };

    @Inject
    ActivityPubEntityManager entityManager;

    @Inject
    ObjectMapper mapper;

    private EntityManager em() {
        return entityManager.get();
    }

    @Override
    public Optional<LocalActor> findByUsername(String username) {
        return QuarkusTransaction.joiningExisting().call(() -> em()
                .createQuery("from ApLocalActor a where a.username = :username", ApLocalActor.class)
                .setParameter("username", username).getResultStream().findFirst().map(this::toActor));
    }

    @Override
    public Optional<LocalActor> findById(URI id) {
        return QuarkusTransaction.joiningExisting()
                .call(() -> Optional.ofNullable(em().find(ApLocalActor.class, id.toString())).map(this::toActor));
    }

    @Override
    public long countLocalActors() {
        return QuarkusTransaction.joiningExisting()
                .call(() -> em().createQuery("select count(a) from ApLocalActor a", Long.class).getSingleResult());
    }

    @Override
    public List<LocalActor> list(int limit) {
        if (limit < 1) {
            return List.of();
        }
        return QuarkusTransaction.joiningExisting().call(() -> em()
                .createQuery("from ApLocalActor a order by a.username", ApLocalActor.class)
                .setMaxResults(limit).getResultList().stream().map(this::toActor).toList());
    }

    @Override
    public Optional<ActorKeyPair> keyPair(URI actorId) {
        return QuarkusTransaction.joiningExisting().call(() -> Optional
                .ofNullable(em().find(ApLocalActor.class, actorId.toString()))
                .map(a -> new ActorKeyPair(a.getPublicKeyPem(), a.getPrivateKeyPem())));
    }

    @Override
    public void save(LocalActor actor, ActorKeyPair keys) {
        String extra = writeExtra(actor.extra());
        QuarkusTransaction.joiningExisting().run(() -> {
            ApLocalActor row = em().find(ApLocalActor.class, actor.id().toString());
            boolean created = row == null;
            if (created) {
                row = new ApLocalActor(actor.id().toString());
            }
            row.setUsername(actor.username());
            row.setType(actor.type());
            row.setName(actor.name().orElse(null));
            row.setSummary(actor.summary().orElse(null));
            row.setUrl(actor.url().map(URI::toString).orElse(null));
            row.setIcon(actor.icon().map(URI::toString).orElse(null));
            row.setImage(actor.image().map(URI::toString).orElse(null));
            row.setManuallyApproves(actor.manuallyApprovesFollowers());
            row.setDiscoverable(actor.discoverable());
            row.setPublished(actor.published());
            row.setExtraJson(extra);
            row.setPublicKeyPem(keys.publicKeyPem());
            row.setPrivateKeyPem(keys.privateKeyPem());
            if (created) {
                em().persist(row);
            }
        });
    }

    @Override
    public void delete(URI actorId) {
        QuarkusTransaction.joiningExisting().run(() -> {
            ApLocalActor row = em().find(ApLocalActor.class, actorId.toString());
            if (row != null) {
                em().remove(row);
                em().flush(); // deletes run before inserts: a re-save of the id or username must follow it
            }
        });
    }

    private LocalActor toActor(ApLocalActor row) {
        LocalActor.Builder builder = LocalActor.builder(row.getUsername()).id(URI.create(row.getId()))
                .type(row.getType()).name(row.getName()).summary(row.getSummary()).url(uri(row.getUrl()))
                .icon(uri(row.getIcon())).image(uri(row.getImage())).manuallyApprovesFollowers(row.isManuallyApproves())
                .discoverable(row.isDiscoverable()).published(row.getPublished());
        readExtra(row.getExtraJson()).forEach(builder::extra);
        return builder.build();
    }

    private static URI uri(String value) {
        return value == null ? null : URI.create(value);
    }

    private String writeExtra(Map<String, JsonNode> extra) {
        try {
            return mapper.writeValueAsString(extra);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise the extra properties of the actor", e);
        }
    }

    private Map<String, JsonNode> readExtra(String json) {
        try {
            return mapper.readValue(json, EXTRA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored extra properties are not a JSON object", e);
        }
    }
}

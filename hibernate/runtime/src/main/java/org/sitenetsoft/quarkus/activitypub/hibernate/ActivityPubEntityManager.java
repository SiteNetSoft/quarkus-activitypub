package org.sitenetsoft.quarkus.activitypub.hibernate;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.quarkus.hibernate.orm.PersistenceUnit;

/**
 * The {@link EntityManager} of the persistence unit named by {@code quarkus.activitypub.hibernate.persistence-unit}
 * (the container's transaction-scoped one, safe to share).
 */
@ApplicationScoped
public class ActivityPubEntityManager {

    static final String DEFAULT_UNIT = "<default>";

    @Inject
    ActivityPubHibernateConfig config;

    @Inject
    @Any
    Instance<EntityManager> entityManagers;

    private EntityManager em;

    @PostConstruct
    void init() {
        String unit = config.persistenceUnit();
        em = DEFAULT_UNIT.equals(unit) ? entityManagers.select(Default.Literal.INSTANCE).get()
                : entityManagers.select(new PersistenceUnit.PersistenceUnitLiteral(unit)).get();
    }

    public EntityManager get() {
        return em;
    }
}

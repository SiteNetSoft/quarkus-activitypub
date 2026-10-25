package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import java.util.List;

import org.jboss.jandex.DotName;
import org.sitenetsoft.quarkus.activitypub.hibernate.ActivityPubEntityManager;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateActorStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateDeliveryStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateFollowerStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.HibernateObjectStore;
import org.sitenetsoft.quarkus.activitypub.hibernate.JtaTransactionRunner;
import org.sitenetsoft.quarkus.activitypub.hibernate.RetentionJob;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApDelivery;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApFollower;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApFollowing;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApHostHealth;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApLocalActor;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApObject;
import org.sitenetsoft.quarkus.activitypub.hibernate.entity.ApSeenActivity;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class ActivityPubHibernateProcessor {

    static final String FEATURE = "activitypub-hibernate";

    static final List<Class<?>> ENTITIES = List.of(ApLocalActor.class, ApObject.class, ApSeenActivity.class,
            ApFollower.class, ApFollowing.class, ApDelivery.class, ApHostHealth.class);

    static final List<Class<?>> BEANS = List.of(ActivityPubEntityManager.class, HibernateActorStore.class,
            HibernateObjectStore.class, HibernateFollowerStore.class, HibernateDeliveryStore.class,
            JtaTransactionRunner.class, RetentionJob.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * The entities join the application's JPA model by being indexed ({@code AdditionalJpaModelBuildItem} is
     * deprecated for removal); Hibernate ORM assigns them to the default persistence unit, or to the one whose
     * {@code packages} name their package.
     */
    @BuildStep
    AdditionalIndexedClassesBuildItem entities() {
        return new AdditionalIndexedClassesBuildItem(ENTITIES.stream().map(Class::getName).toArray(String[]::new));
    }

    @BuildStep
    void beans(BuildProducer<AdditionalBeanBuildItem> beans, BuildProducer<UnremovableBeanBuildItem> unremovable) {
        String[] names = BEANS.stream().map(Class::getName).toArray(String[]::new);
        beans.produce(AdditionalBeanBuildItem.builder().addBeanClasses(names).setUnremovable().build());
        unremovable.produce(UnremovableBeanBuildItem.beanTypes(BEANS.stream().map(DotName::createSimple)
                .toArray(DotName[]::new)));
    }
}

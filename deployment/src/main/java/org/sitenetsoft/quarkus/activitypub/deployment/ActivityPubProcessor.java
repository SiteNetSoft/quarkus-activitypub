package org.sitenetsoft.quarkus.activitypub.deployment;

import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;
import org.sitenetsoft.quarkus.activitypub.store.ActorStore;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.FollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.ObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.ReactiveActorStore;
import org.sitenetsoft.quarkus.activitypub.store.ReactiveDeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.ReactiveFollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.ReactiveObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.activitypub.store.TransactionRunner;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryActorStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryDeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryFollowerStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryObjectStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.NoTransactionRunner;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class ActivityPubProcessor {

    static final String FEATURE = "activitypub";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /** {@code ActivityPubUrls} is {@code @Startup}: its {@code @PostConstruct} validates the path templates at boot. */
    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(ActivityPubUrls.class, LocalActors.class, Stores.class, NoTransactionRunner.class,
                        InMemoryActorStore.class, InMemoryObjectStore.class, InMemoryFollowerStore.class,
                        InMemoryDeliveryStore.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    UnremovableBeanBuildItem unremovableStores() {
        return UnremovableBeanBuildItem.beanTypes(ActorStore.class, ReactiveActorStore.class, ObjectStore.class,
                ReactiveObjectStore.class, FollowerStore.class, ReactiveFollowerStore.class, DeliveryStore.class,
                ReactiveDeliveryStore.class, TransactionRunner.class);
    }
}

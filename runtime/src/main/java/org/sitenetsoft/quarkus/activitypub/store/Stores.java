package org.sitenetsoft.quarkus.activitypub.store;

import java.util.function.Function;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.InjectableInstance;

/**
 * Both views (blocking and reactive) of every store, whichever form the application provided.
 * <p>
 * Per store: an application bean wins over the in-memory {@code @DefaultBean}. If the application provides only the
 * reactive form, the blocking view awaits it (at most 30 s); if it provides only the blocking form (or nothing, so the
 * in-memory default applies), the reactive view runs the blocking store on a worker thread.
 */
@ApplicationScoped
public class Stores {

    @Inject
    InjectableInstance<ActorStore> actorStore;
    @Inject
    InjectableInstance<ReactiveActorStore> reactiveActorStore;
    @Inject
    InjectableInstance<ObjectStore> objectStore;
    @Inject
    InjectableInstance<ReactiveObjectStore> reactiveObjectStore;
    @Inject
    InjectableInstance<FollowerStore> followerStore;
    @Inject
    InjectableInstance<ReactiveFollowerStore> reactiveFollowerStore;
    @Inject
    InjectableInstance<DeliveryStore> deliveryStore;
    @Inject
    InjectableInstance<ReactiveDeliveryStore> reactiveDeliveryStore;

    private ActorStore actors;
    private ReactiveActorStore reactiveActors;
    private ObjectStore objects;
    private ReactiveObjectStore reactiveObjects;
    private FollowerStore followers;
    private ReactiveFollowerStore reactiveFollowers;
    private DeliveryStore deliveries;
    private ReactiveDeliveryStore reactiveDeliveries;

    @PostConstruct
    void init() {
        actors = blocking(actorStore, reactiveActorStore, BlockingActorStoreAdapter::new);
        reactiveActors = reactive(reactiveActorStore, actors, ReactiveActorStoreAdapter::new);
        objects = blocking(objectStore, reactiveObjectStore, BlockingObjectStoreAdapter::new);
        reactiveObjects = reactive(reactiveObjectStore, objects, ReactiveObjectStoreAdapter::new);
        followers = blocking(followerStore, reactiveFollowerStore, BlockingFollowerStoreAdapter::new);
        reactiveFollowers = reactive(reactiveFollowerStore, followers, ReactiveFollowerStoreAdapter::new);
        deliveries = blocking(deliveryStore, reactiveDeliveryStore, BlockingDeliveryStoreAdapter::new);
        reactiveDeliveries = reactive(reactiveDeliveryStore, deliveries, ReactiveDeliveryStoreAdapter::new);
    }

    /** The blocking bean if the app provided one, else the app's reactive bean adapted, else the in-memory default. */
    private static <B, R> B blocking(InjectableInstance<B> blocking, InjectableInstance<R> reactive, Function<R, B> adapt) {
        if (blocking.isResolvable() && !blocking.getHandle().getBean().isDefaultBean()) {
            return blocking.get();
        }
        if (reactive.isResolvable()) {
            return adapt.apply(reactive.get());
        }
        return blocking.get();
    }

    /** The app's reactive bean if any, else the blocking view adapted. */
    private static <B, R> R reactive(InjectableInstance<R> reactive, B blockingView, Function<B, R> adapt) {
        return reactive.isResolvable() ? reactive.get() : adapt.apply(blockingView);
    }

    public ActorStore actors() {
        return actors;
    }

    public ReactiveActorStore reactiveActors() {
        return reactiveActors;
    }

    public ObjectStore objects() {
        return objects;
    }

    public ReactiveObjectStore reactiveObjects() {
        return reactiveObjects;
    }

    public FollowerStore followers() {
        return followers;
    }

    public ReactiveFollowerStore reactiveFollowers() {
        return reactiveFollowers;
    }

    public DeliveryStore deliveries() {
        return deliveries;
    }

    public ReactiveDeliveryStore reactiveDeliveries() {
        return reactiveDeliveries;
    }
}

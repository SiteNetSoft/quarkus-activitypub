package org.sitenetsoft.quarkus.activitypub.deployment;

import java.util.Optional;

import org.jboss.logging.Logger;
import org.sitenetsoft.quarkus.activitypub.ActivityPub;
import org.sitenetsoft.quarkus.activitypub.ActivityPubBuildTimeConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.ActivityPubObjectMapperCustomizer;
import org.sitenetsoft.quarkus.activitypub.ActivityPubRecorder;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.ReactiveActivityPub;
import org.sitenetsoft.quarkus.activitypub.actor.ActorDocuments;
import org.sitenetsoft.quarkus.activitypub.actor.InstanceActor;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActors;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.client.ActivityPubClientImpl;
import org.sitenetsoft.quarkus.activitypub.client.BlockingActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.client.ReactiveActivityPubClient;
import org.sitenetsoft.quarkus.activitypub.client.RemoteCache;
import org.sitenetsoft.quarkus.activitypub.inbox.ConfigDomainPolicy;
import org.sitenetsoft.quarkus.activitypub.inbox.DefaultFollowPolicy;
import org.sitenetsoft.quarkus.activitypub.inbox.DefaultRemoteObjectPolicy;
import org.sitenetsoft.quarkus.activitypub.inbox.DomainPolicy;
import org.sitenetsoft.quarkus.activitypub.inbox.FollowPolicy;
import org.sitenetsoft.quarkus.activitypub.inbox.Inbound;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityContext;
import org.sitenetsoft.quarkus.activitypub.inbox.InboundActivityHandler;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxHandler;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxLog;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxMetrics;
import org.sitenetsoft.quarkus.activitypub.inbox.InboxProcessor;
import org.sitenetsoft.quarkus.activitypub.inbox.RemoteObjectPolicy;
import org.sitenetsoft.quarkus.activitypub.inbox.handlers.AcceptRejectHandler;
import org.sitenetsoft.quarkus.activitypub.inbox.handlers.CreateUpdateHandler;
import org.sitenetsoft.quarkus.activitypub.inbox.handlers.DeleteHandler;
import org.sitenetsoft.quarkus.activitypub.inbox.handlers.FollowHandler;
import org.sitenetsoft.quarkus.activitypub.inbox.handlers.UndoHandler;
import org.sitenetsoft.quarkus.activitypub.keys.ActorKeyResolver;
import org.sitenetsoft.quarkus.activitypub.keys.ActorSigningKeyProvider;
import org.sitenetsoft.quarkus.activitypub.health.DeliveryHealthCheck;
import org.sitenetsoft.quarkus.activitypub.nodeinfo.ActivityPubNodeInfoContributor;
import org.sitenetsoft.quarkus.activitypub.outbound.ActivityPubImpl;
import org.sitenetsoft.quarkus.activitypub.outbound.Clocks;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryExecutor;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryMetrics;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryScheduler;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliverySender;
import org.sitenetsoft.quarkus.activitypub.outbound.OutboxDeliveryExecutor;
import org.sitenetsoft.quarkus.activitypub.outbound.ReactiveActivityPubImpl;
import org.sitenetsoft.quarkus.activitypub.outbound.RecipientResolver;
import org.sitenetsoft.quarkus.activitypub.server.ActivityPubContentNegotiation;
import org.sitenetsoft.quarkus.activitypub.server.ActorHandler;
import org.sitenetsoft.quarkus.activitypub.server.CollectionHandler;
import org.sitenetsoft.quarkus.activitypub.server.FetchAuthorization;
import org.sitenetsoft.quarkus.activitypub.server.ObjectHandler;
import org.sitenetsoft.quarkus.activitypub.server.SignerIdentity;
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
import org.sitenetsoft.quarkus.activitypub.webfinger.ActivityPubWebFingerResolver;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

class ActivityPubProcessor {

    static final String FEATURE = "activitypub";

    private static final Logger LOG = Logger.getLogger("org.sitenetsoft.quarkus.activitypub");
    private static final String PATHS = "quarkus.activitypub.paths.";
    private static final String USERNAME = "username";
    private static final String ID = "id";

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

    /**
     * Routes, JSON, the instance actor, the remote client, the WebFinger, NodeInfo and HTTP signature plug-ins, when
     * {@code quarkus.activitypub.enabled}.
     * The resolver is also indexed because the WebFinger extension only registers its endpoint when the combined index
     * holds a resolver; so is the {@code @Inbound} qualifier, so ArC knows it.
     */
    @BuildStep
    void federationBeans(ActivityPubBuildTimeConfig config, BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<AdditionalIndexedClassesBuildItem> indexed) {
        if (!config.enabled()) {
            return;
        }
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(ActivityPubObjectMapperCustomizer.class, ActivityPubJson.class, ActorDocuments.class,
                        InstanceActor.class, SignerIdentity.class, FetchAuthorization.class, ActorHandler.class,
                        CollectionHandler.class, ObjectHandler.class, ActivityPubContentNegotiation.class,
                        ActivityPubWebFingerResolver.class, ActivityPubNodeInfoContributor.class,
                        ActivityPubClientImpl.class, BlockingActivityPubClient.class, RemoteCache.class,
                        ActorKeyResolver.class, ActorSigningKeyProvider.class)
                .setUnremovable()
                .build());
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(InboxHandler.class, InboxProcessor.class, InboundActivityContext.class, InboxLog.class,
                        InboxMetrics.class, FollowHandler.class, UndoHandler.class, AcceptRejectHandler.OnAccept.class,
                        AcceptRejectHandler.OnReject.class, CreateUpdateHandler.OnCreate.class,
                        CreateUpdateHandler.OnUpdate.class, CreateUpdateHandler.Keeper.class, DeleteHandler.class,
                        ConfigDomainPolicy.class, DefaultFollowPolicy.class, DefaultRemoteObjectPolicy.class)
                .setUnremovable()
                .build());
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(ActivityPubImpl.class, ReactiveActivityPubImpl.class, RecipientResolver.class,
                        OutboxDeliveryExecutor.class, DeliveryScheduler.class, DeliverySender.class,
                        DeliveryMetrics.class, Clocks.class)
                .setUnremovable()
                .build());
        indexed.produce(new AdditionalIndexedClassesBuildItem(ActivityPubWebFingerResolver.class.getName()));
        // the event qualifier must be in the bean archive index to be recognised
        beans.produce(AdditionalBeanBuildItem.unremovableOf(Inbound.class));
    }

    /**
     * Delivery runs as a Quarkus Scheduler job: the build fails without {@code quarkus-scheduler} (a dependency of this
     * extension, so a safety net only). The delivery readiness check is added when SmallRye Health is there to run it.
     */
    @BuildStep
    void deliveryCapabilities(ActivityPubBuildTimeConfig config, Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> beans) {
        if (!config.enabled()) {
            return;
        }
        if (!capabilities.isPresent(Capability.SCHEDULER)) {
            throw new IllegalStateException("quarkus-activitypub delivers activities from a Quarkus Scheduler job, "
                    + "but the io.quarkus:quarkus-scheduler extension is missing; add it to the application");
        }
        if (capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(DeliveryHealthCheck.class));
        }
    }

    /**
     * The actor, collection and object routes from the {@code paths.*} templates ({@code {username}} and {@code {id}}
     * become the Vert.x parameters {@code :username} and {@code :id}). They sit at the server root, like the ids under
     * {@code base-url}; inbox routes come with the inbox.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerRoutes(ActivityPubBuildTimeConfig config, HttpRootPathBuildItem rootPath, ActivityPubRecorder recorder,
            BuildProducer<RouteBuildItem> routes) {
        if (!config.enabled() || !config.server().enabled()) {
            LOG.debug("ActivityPub routes not registered: quarkus.activitypub.enabled or server.enabled is false");
            return;
        }
        if (!"/".equals(rootPath.getRootPath())) {
            LOG.warnf("quarkus.http.root-path is '%s'; ActivityPub still serves its routes at the server root because "
                    + "actor and object ids are built from quarkus.activitypub.base-url", rootPath.getRootPath());
        }
        ActivityPubBuildTimeConfig.Paths paths = config.paths();
        String actor = ActorHandler.class.getName();
        String collections = CollectionHandler.class.getName();
        String objects = ObjectHandler.class.getName();
        route(routes, rootPath, recorder, "actor", paths.actor(), USERNAME, actor, null);
        route(routes, rootPath, recorder, "outbox", paths.outbox(), USERNAME, collections, "OUTBOX");
        route(routes, rootPath, recorder, "followers", paths.followers(), USERNAME, collections, "FOLLOWERS");
        route(routes, rootPath, recorder, "following", paths.following(), USERNAME, collections, "FOLLOWING");
        route(routes, rootPath, recorder, "objects", paths.objects(), ID, objects, ObjectHandler.OBJECTS);
        route(routes, rootPath, recorder, "activities", paths.activities(), ID, objects, ObjectHandler.ACTIVITIES);
        String inbox = InboxHandler.class.getName();
        route(routes, rootPath, recorder, "inbox", paths.inbox(), USERNAME, inbox, InboxHandler.ACTOR);
        LOG.debugf("ActivityPub route %s -> shared-inbox", paths.sharedInbox());
        routes.produce(rootPath.routeBuilder().route(paths.sharedInbox())
                .handler(recorder.handler(inbox, InboxHandler.SHARED)).build());
    }

    private static void route(BuildProducer<RouteBuildItem> routes, HttpRootPathBuildItem rootPath,
            ActivityPubRecorder recorder, String property, String template, String param, String bean, String variant) {
        Optional<String> path = vertxPath(PATHS + property, template, param);
        if (path.isEmpty()) {
            // an invalid template: ActivityPubUrls fails startup with the property name
            return;
        }
        LOG.debugf("ActivityPub route %s -> %s", path.get(), property);
        // HttpRootPathBuildItem's builder types a leading-slash path outside the root path as an absolute route
        routes.produce(rootPath.routeBuilder().route(path.get()).handler(recorder.handler(bean, variant)).build());
    }

    /**
     * The Vert.x route path of a template: {@code {param}} (exactly once) becomes {@code :param}. Empty when the
     * placeholder is missing or repeated.
     *
     * @throws IllegalStateException when a letter, digit or {@code _} follows the placeholder (it would extend the
     *         Vert.x parameter name)
     */
    static Optional<String> vertxPath(String property, String template, String param) {
        String placeholder = "{" + param + "}";
        int first = template.indexOf(placeholder);
        if (first < 0 || template.indexOf(placeholder, first + 1) >= 0) {
            return Optional.empty();
        }
        int after = first + placeholder.length();
        if (after < template.length()) {
            char c = template.charAt(after);
            if (Character.isLetterOrDigit(c) || c == '_') {
                throw new IllegalStateException(property + ": " + placeholder + " must not be followed by a letter, "
                        + "digit or '_': '" + template + "'");
            }
        }
        return Optional.of(template.substring(0, first) + ":" + param + template.substring(after));
    }

    @BuildStep
    UnremovableBeanBuildItem unremovableStores() {
        return UnremovableBeanBuildItem.beanTypes(ActorStore.class, ReactiveActorStore.class, ObjectStore.class,
                ReactiveObjectStore.class, FollowerStore.class, ReactiveFollowerStore.class, DeliveryStore.class,
                ReactiveDeliveryStore.class, TransactionRunner.class);
    }

    @BuildStep
    UnremovableBeanBuildItem unremovableInboxSpis() {
        return UnremovableBeanBuildItem.beanTypes(InboundActivityHandler.class, DomainPolicy.class, FollowPolicy.class,
                RemoteObjectPolicy.class, ActivityPub.class, ReactiveActivityPub.class, DeliveryExecutor.class);
    }

    @BuildStep
    UnremovableBeanBuildItem unremovableClients() {
        return UnremovableBeanBuildItem.beanTypes(ActivityPubClient.class, ReactiveActivityPubClient.class);
    }
}

package org.sitenetsoft.quarkus.nodeinfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.quarkus.arc.All;
import io.quarkus.arc.InstanceHandle;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;

/**
 * Builds the merged NodeInfo (provider, then contributors in ascending {@code @Priority}), caches it for
 * {@code quarkus.nodeinfo.cache-ttl}, and serialises it as a 2.0 or 2.1 document.
 */
@ApplicationScoped
public class NodeInfoDocuments {

    public static final String V2_0 = "2.0";
    public static final String V2_1 = "2.1";
    public static final Set<String> VERSIONS = Set.of(V2_0, V2_1);
    private static final String KEY = "nodeinfo";

    private final NodeInfoProvider provider;
    private final Instance<ReactiveNodeInfoProvider> reactiveProvider;
    private final List<Step> contributors;
    private final Vertx vertx;
    private final AsyncCache<String, NodeInfo> cache;

    @Inject
    public NodeInfoDocuments(NodeInfoProvider provider, Instance<ReactiveNodeInfoProvider> reactiveProvider,
            @All List<InstanceHandle<NodeInfoContributor>> contributors,
            @All List<InstanceHandle<ReactiveNodeInfoContributor>> reactiveContributors, NodeInfoConfig config, Vertx vertx) {
        this.provider = provider;
        this.reactiveProvider = reactiveProvider;
        this.vertx = vertx;
        List<Step> steps = new ArrayList<>();
        for (InstanceHandle<NodeInfoContributor> h : contributors) {
            NodeInfoContributor c = h.get();
            steps.add(new Step(h.getBean().getPriority(), current -> blocking(() -> c.contribute(current))));
        }
        for (InstanceHandle<ReactiveNodeInfoContributor> h : reactiveContributors) {
            ReactiveNodeInfoContributor c = h.get();
            steps.add(new Step(h.getBean().getPriority(), current -> Uni.createFrom().deferred(() -> c.contribute(current))));
        }
        // ascending priority: the highest-priority contribution is merged last and wins
        steps.sort(Comparator.comparingInt(Step::priority));
        this.contributors = List.copyOf(steps);
        this.cache = Caffeine.newBuilder().expireAfterWrite(config.cacheTtl()).buildAsync();
    }

    /**
     * The merged NodeInfo, computed at most once per {@code cache-ttl}.
     */
    public Uni<NodeInfo> current() {
        return Uni.createFrom().completionStage(() -> cache.get(KEY, (k, executor) -> compute().subscribeAsCompletionStage()));
    }

    /**
     * Drops the cached NodeInfo; the next {@link #current()} recomputes it.
     */
    public void invalidate() {
        cache.synchronous().invalidateAll();
    }

    /**
     * The current NodeInfo as a document of the given version ({@code 2.0} or {@code 2.1}).
     */
    public Uni<ObjectNode> document(String version) {
        return current().map(info -> toDocument(info, version));
    }

    private Uni<NodeInfo> compute() {
        Uni<NodeInfo> result = reactiveProvider.isResolvable()
                ? Uni.createFrom().deferred(() -> reactiveProvider.get().nodeInfo())
                : blocking(provider::nodeInfo);
        for (Step step : contributors) {
            result = result.flatMap(current -> step.apply().apply(current).map(current::merge));
        }
        return result;
    }

    private Uni<NodeInfo> blocking(Callable<NodeInfo> call) {
        return Uni.createFrom().completionStage(() -> vertx.executeBlocking(call, false).toCompletionStage());
    }

    /**
     * Serialises {@code info} as a NodeInfo document of {@code version}; 2.0 omits the software repository and
     * homepage.
     */
    public static ObjectNode toDocument(NodeInfo info, String version) {
        if (!VERSIONS.contains(version)) {
            throw new IllegalArgumentException("Unsupported NodeInfo version: " + version);
        }
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode doc = f.objectNode();
        doc.put("version", version);
        ObjectNode software = doc.putObject("software");
        software.put("name", info.software().name());
        software.put("version", info.software().version());
        if (V2_1.equals(version)) {
            info.software().repository().ifPresent(u -> software.put("repository", u.toString()));
            info.software().homepage().ifPresent(u -> software.put("homepage", u.toString()));
        }
        ArrayNode protocols = doc.putArray("protocols");
        info.protocols().forEach(protocols::add);
        ObjectNode services = doc.putObject("services");
        ArrayNode inbound = services.putArray("inbound");
        info.services().inbound().forEach(inbound::add);
        ArrayNode outbound = services.putArray("outbound");
        info.services().outbound().forEach(outbound::add);
        doc.put("openRegistrations", info.openRegistrations());
        ObjectNode usage = doc.putObject("usage");
        ObjectNode users = usage.putObject("users");
        put(users, "total", info.usage().users().total());
        put(users, "activeHalfyear", info.usage().users().activeHalfyear());
        put(users, "activeMonth", info.usage().users().activeMonth());
        put(usage, "localPosts", info.usage().localPosts());
        put(usage, "localComments", info.usage().localComments());
        ObjectNode metadata = doc.putObject("metadata");
        info.metadata().forEach(metadata::set);
        return doc;
    }

    /**
     * The {@code Content-Type} of a document of {@code version}.
     */
    public static String contentType(String version) {
        return "application/json; profile=\"" + schema(version) + "#\"";
    }

    /**
     * The schema URI of {@code version}, used as the discovery {@code rel}.
     */
    public static String schema(String version) {
        return "http://nodeinfo.diaspora.software/ns/schema/" + version;
    }

    private static void put(ObjectNode node, String field, Optional<Long> value) {
        value.ifPresent(v -> node.put(field, v));
    }

    private record Step(int priority, Function<NodeInfo, Uni<NodeInfo>> apply) {
    }
}

package org.sitenetsoft.quarkus.nodeinfo;

import io.smallrye.mutiny.Uni;

/**
 * Non-blocking variant of {@link NodeInfoContributor}; ordered together with it by {@code @Priority}.
 */
public interface ReactiveNodeInfoContributor {

    Uni<NodeInfo> contribute(NodeInfo current);
}

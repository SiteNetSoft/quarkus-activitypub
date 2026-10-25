package org.sitenetsoft.quarkus.nodeinfo;

import io.smallrye.mutiny.Uni;

/**
 * Non-blocking variant of {@link NodeInfoProvider}. When such a bean exists it is used instead of any
 * {@link NodeInfoProvider}.
 */
public interface ReactiveNodeInfoProvider {

    Uni<NodeInfo> nodeInfo();
}

package org.sitenetsoft.quarkus.nodeinfo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;

/**
 * Default {@link NodeInfoProvider}: software, protocols, registrations and metadata from {@code quarkus.nodeinfo.*}.
 */
@ApplicationScoped
@DefaultBean
public class ConfigNodeInfoProvider implements NodeInfoProvider {

    private final NodeInfoConfig config;

    @Inject
    public ConfigNodeInfoProvider(NodeInfoConfig config) {
        this.config = config;
    }

    @Override
    public NodeInfo nodeInfo() {
        NodeInfoConfig.Software s = config.software();
        NodeInfo.Builder b = NodeInfo.builder(new NodeInfo.Software(s.name(), s.version(), s.repository(), s.homepage()))
                .withOpenRegistrations(config.openRegistrations());
        config.protocols().ifPresent(protocols -> protocols.forEach(b::withProtocol));
        config.metadata().forEach(b::withMetadata);
        return b.build();
    }
}

package org.sitenetsoft.quarkus.nodeinfo;

/**
 * Supplies the base NodeInfo (blocking; called on a worker thread). A CDI bean implementing it replaces the
 * config-based default {@link ConfigNodeInfoProvider}. A {@link ReactiveNodeInfoProvider} bean takes precedence.
 */
public interface NodeInfoProvider {

    NodeInfo nodeInfo();
}

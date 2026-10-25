package org.sitenetsoft.quarkus.nodeinfo;

/**
 * Adds to the provider's NodeInfo (protocols, usage counts, metadata). Blocking; called on a worker thread.
 * <p>
 * The returned value is {@linkplain NodeInfo#merge(NodeInfo) merged} into {@code current}; returning
 * {@code current.toBuilder()...build()} is the usual shape. Contributors of both kinds run in ascending
 * {@code @Priority} order, so the highest-priority contributor has the last word on usage numbers and metadata.
 */
public interface NodeInfoContributor {

    NodeInfo contribute(NodeInfo current);
}

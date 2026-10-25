# quarkus-nodeinfo — skill notes for coding agents

- Out of the box the documents come from config: `quarkus.nodeinfo.software.*`, `open-registrations`, `metadata.*`.
  `software.name` defaults to `quarkus.application.name` and must match `^[a-z0-9-]+$` (NodeInfo schema).
- To replace the base data, add a CDI bean implementing `NodeInfoProvider` (blocking) or `ReactiveNodeInfoProvider`
  (Mutiny); it overrides the `@DefaultBean` `ConfigNodeInfoProvider`.
- To add protocols, usage counts or metadata, add a `NodeInfoContributor` / `ReactiveNodeInfoContributor` and
  return `current.toBuilder()...build()`. Results are merged; higher `@Priority` runs later and wins on usage numbers.
- The NodeInfo schema requires at least one protocol (e.g. `activitypub`); without a contributor adding one the
  documents are schema-invalid.
- Pitfall: the merged result is cached for `quarkus.nodeinfo.cache-ttl` (`5m`), so counts lag. In tests that change
  counts, set `quarkus.nodeinfo.cache-ttl=0s` or call `NodeInfoDocuments.invalidate()`.
- Pitfall: `/.well-known/nodeinfo` and `{path}/2.x` stay at the server root even with `quarkus.http.root-path`.
  Discovery `href`s use the request scheme/host; behind a proxy enable `quarkus.http.proxy.proxy-address-forwarding`.
- `quarkus.nodeinfo.enabled` and `quarkus.nodeinfo.path` are build time: changing them needs a rebuild.

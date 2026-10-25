# quarkus-activitypub — skill notes for coding agents

- Patterns:
  - **Register an actor.** Inject `LocalActors` and call `register("alice", a -> a.name("Alice"))` once (it throws
    `IllegalStateException` when the username exists, so guard with `find("alice").isEmpty()`), for example from a
    `@Startup` bean or an `@Observes StartupEvent` method. The id is always `ActivityPubUrls.actor(username)` and an
    RSA 2048 key pair is generated and kept by `ActorStore`; the app never handles keys.
  - **Publish.** Inject `ActivityPub` (blocking, worker thread) or `ReactiveActivityPub` (Mutiny) and call
    `create(actor, note)`, `update`, `delete(actor, objectId)`, `follow`, `announce`, `like`, `undo`. Address public
    posts with `to = Audience.PUBLIC` and `cc = ActivityPubUrls.followers(username)`; `followers` expands to the follower
    inboxes (shared inboxes de-duplicated). The call stores the activity, adds it to the outbox and queues the
    deliveries; the scheduler job sends them, signed, with retries.
  - **Observe the inbox.** `void onCreate(@Observes @Inbound Create create)` (or `@ObservesAsync`); observe `Activity`
    to see everything. Inject the request-scoped `InboundActivityContext` for the recipient (`recipient()`, empty for
    the shared inbox) and the verified signer (`signerActor()`). Follows are answered by `FollowPolicy` (default:
    accept unless the actor sets `manuallyApprovesFollowers`, then `PENDING` for `acceptFollow`/`rejectFollow`).
  - **Replace a side effect.** Implement `InboundActivityHandler<T>` with `@Priority` above 0; remote fetches go in
    `prepare` (outside the transaction), store writes in `handle`. Other SPIs: `DomainPolicy` (event loop, no blocking),
    `RemoteObjectPolicy`, `FollowPolicy`.
  - **Replace a store.** Add `quarkus-activitypub-hibernate` for database stores (no code), or implement `ActorStore`,
    `ObjectStore`, `FollowerStore`, `DeliveryStore` (or their `Reactive*` twins; one form per store is enough) as an
    `@ApplicationScoped` bean; it wins over the in-memory `@DefaultBean`. Test it by extending `StoreContractTest`
    from `quarkus-activitypub-test`. Read stores through `Stores` (`stores.actors()`, `stores.reactiveObjects()`, ...).
  - **Fetch remote documents.** `ActivityPubClient.fetch(uri)` / `resolveActor("bob@mastodon.example")` (blocking),
    `ReactiveActivityPubClient` for Mutiny; signed by the instance actor, cached, id checked against the host.
  - **Test federation.** `FakeFediverseServer` from `quarkus-activitypub-test` plays a remote server: `fake.actor("carol")`
    signs and posts activities (`carol.post(inbox, follow, Scheme.CAVAGE)`), `fake.received()` records what arrived and
    whether its signature verified.
- Pitfalls:
  - **`quarkus.activitypub.base-url` is required** and must be the public origin remote servers reach (scheme, host,
    port). Ids are built from it, so changing it later orphans every follower. `http://localhost:8080` only works for
    loopback tests.
  - **`quarkus.http.root-path` does not move the ActivityPub or WebFinger routes**: they are registered at the server
    root (a build warning says so). Behind a proxy that serves the app under a prefix (`/app`): either the proxy
    strips the prefix and it goes only in `base-url` (`https://example.com/app`), or the proxy forwards it unchanged
    and it goes only in `paths.*` (`/app/users/{username}`, ...), never both (ids would become `/app/app/...` and
    inbound paths would not parse). Either way `/.well-known/webfinger` must reach the app at the root, or Mastodon's
    search gets 404.
  - **https-only in dev.** safe-fetch refuses plain `http` and private addresses. For a local fake or a second local
    instance set `%dev.quarkus.safe-fetch.https-only=false` and `%dev.quarkus.safe-fetch.allow-private-addresses=true`
    (and `%test.`); never in prod.
  - **The scheduler is needed.** Delivery is a Quarkus Scheduler job (`delivery.poll-interval`, default `5s`):
    `quarkus.scheduler.enabled=false` stops all delivery (and the retention jobs). Deliveries are not sent
    synchronously by `create()`; tests await them. Finished deliveries are deleted daily after
    `quarkus.activitypub.delivery.retention` (`7d`; `0` keeps them), so do not rely on old `DELIVERED` rows.
  - Inbox processing runs on virtual threads, at most `quarkus.activitypub.inbox.max-concurrency` (64) at a time:
    `@Inbound` observers may block, but a slow one delays the queue.
  - `blocked-domains` also stops outbound traffic: no deliveries (`FAILED`, `blocked domain`), no fetches
    (`ActivityPubException` `BLOCKED`), no key fetches.
  - The in-memory stores lose actors, keys and followers on restart and log a WARN in prod; use the Hibernate module
    (or your own stores) for anything that federates for real.
  - `ActivityPub` and `ActivityPubClient` block: never call them on the event loop (use the reactive twins there).
  - Dev UI (`quarkus.activitypub.devui.enabled`): Actors, Inbox log, Fetch, Deliveries (retry now). It never shows
    private keys or signatures.

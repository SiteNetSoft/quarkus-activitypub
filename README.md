# quarkus-activitypub

ActivityPub server-to-server federation for Quarkus: actors, inboxes, collections and signed delivery.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `activitystreams-model` | `activitystreams-model` | ActivityStreams 2.0 / ActivityPub vocabulary (no Quarkus) |
| `nodeinfo/runtime`, `nodeinfo/deployment` | `quarkus-nodeinfo` | NodeInfo 2.0/2.1 discovery and document |
| `test` | `quarkus-activitypub-test` | Test fixtures (fake remote fediverse server) |
| `runtime`, `deployment` | `quarkus-activitypub` | Actors, inboxes, collections, signed delivery |
| `hibernate/runtime`, `hibernate/deployment` | `quarkus-activitypub-hibernate` | Hibernate ORM stores |
| `bom` | `quarkus-activitypub-bom` | Version alignment for this repo and the Tier 1 extensions |
| `integration-tests` | | Quarkus app used for integration and native tests |

## Quick start

```xml
<dependency>
  <groupId>org.sitenetsoft.quarkus</groupId>
  <artifactId>quarkus-activitypub</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<!-- optional: database stores instead of the in-memory ones -->
<dependency>
  <groupId>org.sitenetsoft.quarkus</groupId>
  <artifactId>quarkus-activitypub-hibernate</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```properties
# the public origin of this server; every actor and object id starts with it
quarkus.activitypub.base-url=https://example.com
```

```java
@ApplicationScoped
public class Blog {

    @Inject LocalActors actors;
    @Inject ActivityPub activityPub;
    @Inject ActivityPubUrls urls;
    @Inject InboundActivityContext inbound;

    void onStart(@Observes StartupEvent event) {
        if (actors.find("alice").isEmpty()) {
            actors.register("alice", a -> a.name("Alice").summary("Writes about Quarkus"));
        }
    }

    public URI post(String text) {
        LocalActor alice = actors.find("alice").orElseThrow();
        Note note = new Note();
        note.content(text);
        note.to(ObjectOrLink.of(Audience.PUBLIC));
        note.cc(ObjectOrLink.of(urls.followers("alice")));
        return activityPub.create(alice, note).id();   // stored, then delivered to the followers
    }

    void onFollow(@Observes @Inbound Follow follow) {
        // accepted by the default FollowPolicy, the Accept is already queued; inbound.signerActor() is the follower
    }
}
```

Mastodon users can now find `@alice@example.com`, follow her and receive her posts. The `integration-tests` module is a
runnable version of this (`mvn quarkus:dev -pl integration-tests`, port 8081). Full guide: `docs/` (Antora).

## Configuration

All `quarkus.activitypub.*`. Build-time properties are fixed when the application is built.

| Property | Default | Purpose |
|---|---|---|
| `enabled` | `true` | Enable the extension (build time) |
| `server.enabled` | `true` | Register the actor, inbox, outbox, collection and object routes (build time) |
| `devui.enabled` | `true` | Show the Dev UI card (build time) |
| `paths.actor` | `/users/{username}` | Actor document, relative to `base-url` (build time) |
| `paths.inbox` | `/users/{username}/inbox` | Actor inbox (build time) |
| `paths.outbox` | `/users/{username}/outbox` | Actor outbox (build time) |
| `paths.followers` | `/users/{username}/followers` | Followers collection (build time) |
| `paths.following` | `/users/{username}/following` | Following collection (build time) |
| `paths.shared-inbox` | `/inbox` | Shared inbox (build time) |
| `paths.objects` | `/objects/{id}` | Local objects (build time) |
| `paths.activities` | `/activities/{id}` | Local activities (build time) |
| `base-url` | required | Public base URL, e.g. `https://example.com`; every id starts with it |
| `instance-actor.enabled` | `true` | Create and serve the instance actor (an `Application` that signs outbound fetches) |
| `instance-actor.username` | `instance` | Username of the instance actor |
| `authorized-fetch` | `false` | Require a valid HTTP signature on every ActivityPub GET |
| `blocked-domains` | none | Domains whose activities are dropped, matched by suffix |
| `collections.page-size` | `20` | Items per collection page |
| `collections.followers-visibility` | `public` | `public`, `count-only` or `hidden` (404) for followers and following |
| `cache-control.actor` | `max-age=300` | `Cache-Control` of actor documents |
| `fetch.timeout` | `10s` | Per-request timeout of remote fetches |
| `fetch.max-size` | `1M` | Larger remote documents are rejected |
| `fetch.cache-ttl` | `10m` | How long fetched objects are cached |
| `fetch.actor-cache-ttl` | `1h` | How long fetched actors are cached |
| `delivery.poll-interval` | `5s` | How often the delivery job polls for due deliveries |
| `delivery.batch-size` | `50` | Deliveries claimed per poll |
| `delivery.parallelism` | `8` | Concurrent sends per poll |
| `delivery.max-attempts` | `8` | Attempts before a delivery is given up |
| `delivery.signature-scheme` | `cavage` | `cavage` or `rfc9421` |
| `delivery.dead-after` | `30d` | A host failing this long gets new deliveries given up immediately |
| `health.max-backlog-age` | `1h` | Readiness is DOWN when the oldest pending delivery is older |
| `user-agent` | `quarkus-activitypub/{version} (+{base-url})` | `User-Agent` of outbound requests |
| `dedup.retention` | `7d` | How long inbound activity ids are remembered |
| `remote-objects.retention` | `90d` | How long kept remote objects live; `0` disables expiry |

Behind a proxy that serves the app under a prefix, the prefix goes either only in `base-url` (the proxy strips it) or
only in `paths.*` (the proxy forwards it unchanged), never both; `/.well-known/webfinger` must reach the app at the root.

`quarkus-activitypub-hibernate` adds `quarkus.activitypub.hibernate.persistence-unit` (see `hibernate/README.md`);
NodeInfo is configured under `quarkus.nodeinfo.*` (see `nodeinfo/README.md`).

## Dev UI

The ActivityPub card shows the base URL, the instance actor, the local actor count, the delivery queue (pending,
failed, given up) and paused hosts. Pages: **Actors** (actor document and WebFinger answer of each local actor),
**Inbox log** (the last 100 inbox outcomes), **Fetch** (a document by URL or an actor by `user@host`) and
**Deliveries** (the queue by state, with "retry now").

## Build order

This repo depends on three sibling repos, built and installed first, in this order:

1. `quarkus-safe-fetch`
2. `quarkus-webfinger`
3. `quarkus-http-signatures`
4. `quarkus-activitypub` (this repo)

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -T 8 install -DskipTests
```

Without `-DskipTests`, `install` also runs `DemoFlowIT` (`integration-tests`), which starts the packaged sample app on
port 8081: keep that port free.

## Host notes

- Use Maven `-T 8` at most.
- Run one heavy build at a time.
- Do not run native-image builds locally; native builds run in CI
  (with `-J-XX:TieredStopAtLevel=1,--parallelism=8`).

## License

Apache License 2.0

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

## Build order

This repo depends on three sibling repos, built and installed first, in this order:

1. `quarkus-safe-fetch`
2. `quarkus-webfinger`
3. `quarkus-http-signatures`
4. `quarkus-activitypub` (this repo)

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -T 8 install -DskipTests
```

## Host notes

- Use Maven `-T 8` at most.
- Run one heavy build at a time.
- Do not run native-image builds locally; native builds run in CI
  (with `-J-XX:TieredStopAtLevel=1,--parallelism=8`).

## License

Apache License 2.0

# quarkus-nodeinfo

Serve [NodeInfo](https://nodeinfo.diaspora.software/) 2.0 and 2.1 from Quarkus: the discovery document at
`/.well-known/nodeinfo` and the documents at `{path}/2.0` and `{path}/2.1`. Standalone: it does not depend on
ActivityPub. The data comes from an SPI: a `NodeInfoProvider` (default: from configuration) plus any number of
`NodeInfoContributor` beans that add protocols, usage counts and metadata.

Minimum supported Quarkus: **3.40 (LTS)**. Status: experimental, version 0.1.

```xml
<dependency>
  <groupId>org.sitenetsoft.quarkus</groupId>
  <artifactId>quarkus-nodeinfo</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Endpoints

| Route | Content-Type |
| --- | --- |
| `GET /.well-known/nodeinfo` | `application/json`; links with absolute `href` built from the request scheme and host |
| `GET {path}/2.0` | `application/json; profile="http://nodeinfo.diaspora.software/ns/schema/2.0#"` |
| `GET {path}/2.1` | `application/json; profile="http://nodeinfo.diaspora.software/ns/schema/2.1#"` |

All three are served at the server root, also when `quarkus.http.root-path` is set (a build warning says so).
Behind a reverse proxy, enable `quarkus.http.proxy.proxy-address-forwarding` (and `enable-forwarded-host`) so the
discovery links carry the public scheme and host.

## Configuration

| Property | Default | Purpose |
| --- | --- | --- |
| `quarkus.nodeinfo.enabled` | `true` | Register the routes (build time) |
| `quarkus.nodeinfo.path` | `/nodeinfo` | Base path of the documents (build time) |
| `quarkus.nodeinfo.software.name` | `${quarkus.application.name:quarkus}` | Software name; must match `^[a-z0-9-]+$` |
| `quarkus.nodeinfo.software.version` | `${quarkus.application.version:0}` | Software version |
| `quarkus.nodeinfo.software.repository` | none | Source repository URL (2.1 only) |
| `quarkus.nodeinfo.software.homepage` | none | Homepage URL (2.1 only) |
| `quarkus.nodeinfo.open-registrations` | `false` | Advertise open self-registration |
| `quarkus.nodeinfo.metadata."key"` | none | Free-form string metadata entries |
| `quarkus.nodeinfo.cache-ttl` | `5m` | How long the merged NodeInfo is cached |

## SPI

```java
@ApplicationScoped
public class UserCounts implements NodeInfoContributor {
    @Inject UserRepository users;

    @Override
    public NodeInfo contribute(NodeInfo current) {
        return current.toBuilder().withProtocol("activitypub").withUsersTotal(users.count()).build();
    }
}
```

- `NodeInfoProvider` / `ReactiveNodeInfoProvider`: the base NodeInfo. An app bean replaces the config-based default;
  a reactive provider takes precedence over a blocking one.
- `NodeInfoContributor` / `ReactiveNodeInfoContributor`: each result is merged into the running NodeInfo (protocols
  and services unioned, metadata put, usage numbers replaced when set). Contributors run in ascending `@Priority`,
  so the highest priority has the last word.

## Dev UI

The card shows the advertised software name and version; its Preview page shows both documents as served.

## Building

`JAVA_HOME=/path/to/jdk21 mvn -T 8 install -pl nodeinfo/runtime,nodeinfo/deployment` from the repo root.

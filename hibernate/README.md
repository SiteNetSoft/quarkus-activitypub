# quarkus-activitypub-hibernate

Hibernate ORM stores for `quarkus-activitypub`: local actors and their keys, objects and activities, follow
relationships, inbound de-duplication, and the delivery queue with per-host health, all in your database and in your
transactions. Add the dependency and it replaces the in-memory stores; no code is needed.

```xml
<dependency>
  <groupId>org.sitenetsoft.quarkus</groupId>
  <artifactId>quarkus-activitypub-hibernate</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
```

```properties
quarkus.datasource.db-kind=postgresql
# development only; use Flyway (below) in production
quarkus.hibernate-orm.schema-management.strategy=update
```

## What you get

| Bean | Tables | Notes |
|---|---|---|
| `HibernateActorStore` | `ap_local_actor` | key pair as PEM, `extra` as JSON |
| `HibernateObjectStore` | `ap_object`, `ap_seen_activity` | documents as JSON text |
| `HibernateFollowerStore` | `ap_follower`, `ap_following` | |
| `HibernateDeliveryStore` | `ap_delivery`, `ap_host_health` | the transactional outbox |
| `JtaTransactionRunner` | | `TransactionRunner` on JTA |
| `RetentionJob` | | daily at 03:00 |

Each store is a separate bean: an application that keeps its users in its own tables implements `ActorStore` itself,
as an `@Alternative @Priority(1)` bean so it wins over `HibernateActorStore`, and still uses the other three. The `ap_` tables belong to the extension; write them only through the stores.

**Transactions.** Every store method joins the caller's JTA transaction, or runs in its own when there is none
(`QuarkusTransaction.joiningExisting()`). So saving your post and calling `activityPub.create(...)` in one
`@Transactional` method is a transactional outbox: a rollback discards the activity and its deliveries. The inbox
handles each activity (follower write, queued `Accept`, the `@Inbound` observers) in one transaction; remote fetches
happen before it opens. When the processing fails, the activity's de-duplication marker is removed, so a redelivery is
processed again.

**Retention.** `RetentionJob` deletes, daily at 03:00, remote objects kept longer than
`quarkus.activitypub.remote-objects.retention` (default `90d`) and de-duplication rows older than
`quarkus.activitypub.dedup.retention` (default `7d`). A retention of `0` disables that part. Local objects never
expire.

**Delivery runs on one node.** The poll claims due deliveries with `SELECT … FOR UPDATE SKIP LOCKED` (where the database
supports it) and stamps `claimed_at`. A claim is honoured for 60 seconds or until the attempt is recorded. If a run
takes longer than that, for example a large batch to slow hosts, another node can claim the same delivery and send it
twice. Run the delivery poll on one node only (`quarkus.scheduler.enabled=false` elsewhere), or keep
`delivery.batch-size` × `delivery.parallelism` × `fetch.timeout` well under a minute.

## Configuration

| Property | Default | |
|---|---|---|
| `quarkus.activitypub.hibernate.persistence-unit` | `<default>` | persistence unit of the `ap_` tables (fixed at build time) |

The entities go into the default persistence unit. To put them in a named unit, assign their package to it and
point the stores at it:

```properties
quarkus.datasource."fediverse".db-kind=postgresql
quarkus.hibernate-orm."fediverse".datasource=fediverse
quarkus.hibernate-orm."fediverse".packages=org.sitenetsoft.quarkus.activitypub.hibernate.entity
quarkus.activitypub.hibernate.persistence-unit=fediverse
```

## Schema (PostgreSQL)

The DDL Hibernate expects, written by hand from the entities. Take it as the first Flyway migration.

```sql
create table ap_local_actor (
    id                varchar(2048) primary key,
    username          varchar(255)  not null unique,
    type              varchar(64)   not null,
    name              text,
    summary           text,
    url               varchar(2048),
    icon              varchar(2048),
    image             varchar(2048),
    manually_approves boolean       not null,
    discoverable      boolean       not null,
    published         timestamp(6) with time zone not null,
    extra_json        text          not null,
    public_key_pem    text          not null,
    private_key_pem   text          not null
);

create table ap_object (
    id            varchar(2048) primary key,
    primary_type  varchar(64)   not null,
    actor         varchar(2048),
    is_local      boolean       not null,
    is_public     boolean       not null,
    is_activity   boolean       not null,
    published     timestamp(6) with time zone,
    received_from varchar(2048),
    body_json     text          not null,
    tombstoned_at timestamp(6) with time zone,
    created       timestamp(6) with time zone not null
);
create index ap_object_outbox_idx on ap_object (actor, is_local, is_public, is_activity);
create index ap_object_created_idx on ap_object (is_local, created);

create table ap_seen_activity (
    id      varchar(2048) primary key,
    seen_at timestamp(6) with time zone not null
);
create index ap_seen_activity_seen_at_idx on ap_seen_activity (seen_at);

create table ap_follower (
    local              varchar(2048) not null,
    remote             varchar(2048) not null,
    remote_inbox       varchar(2048) not null,
    state              varchar(16)   not null check (state in ('PENDING', 'ACCEPTED', 'REJECTED')),
    follow_activity_id varchar(2048),
    since              timestamp(6) with time zone not null,
    primary key (local, remote)
);
create index ap_follower_list_idx on ap_follower (local, state, since);
create index ap_follower_remote_idx on ap_follower (remote);

create table ap_following (
    local              varchar(2048) not null,
    remote             varchar(2048) not null,
    state              varchar(16)   not null check (state in ('PENDING', 'ACCEPTED', 'REJECTED')),
    follow_activity_id varchar(2048),
    since              timestamp(6) with time zone not null,
    primary key (local, remote)
);
create index ap_following_list_idx on ap_following (local, state, since);
create index ap_following_remote_idx on ap_following (remote);

create table ap_delivery (
    id           varchar(64)   primary key,
    inbox        varchar(2048) not null,
    activity_id  varchar(2048) not null,
    body         bytea         not null,
    key_id       varchar(2048) not null,
    attempts     integer       not null,
    next_attempt timestamp(6) with time zone not null,
    last_error   text,
    state        varchar(16)   not null check (state in ('PENDING', 'DELIVERED', 'FAILED', 'GIVEN_UP')),
    created      timestamp(6) with time zone not null,
    claimed_at   timestamp(6) with time zone
);
create index ap_delivery_due_idx on ap_delivery (state, next_attempt);

create table ap_host_health (
    host                 varchar(255) primary key,
    consecutive_failures integer not null,
    last_failure         timestamp(6) with time zone,
    last_success         timestamp(6) with time zone,
    paused_until         timestamp(6) with time zone
);
```

`ap_local_actor.published` has nanosecond precision where the database has it (H2). PostgreSQL keeps microseconds,
so an actor saved with a nanosecond `published` reads back rounded.

## Flyway

Let Flyway own the schema in production:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-flyway</artifactId>
</dependency>
```

```properties
quarkus.flyway.migrate-at-start=true
quarkus.hibernate-orm.schema-management.strategy=validate
```

Put the DDL above in `src/main/resources/db/migration/V1__activitypub.sql` (renumber it to fit your own migrations),
and add a migration whenever an upgrade of this extension changes the entities; its release notes will say so.
`validate` makes a mismatch fail at startup instead of at the first query.

`drop-and-create` is fine for tests (H2 or Dev Services). `update` is fine in development but never removes or
retypes columns.

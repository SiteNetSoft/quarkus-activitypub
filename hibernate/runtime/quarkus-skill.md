# quarkus-activitypub-hibernate — skill notes for coding agents

- Patterns:
  - **Adding it.** Add `quarkus-activitypub-hibernate` plus a JDBC driver (`quarkus-jdbc-postgresql`) and set
    `quarkus.datasource.db-kind`. The four Hibernate stores and `JtaTransactionRunner` then replace the in-memory
    defaults, with no code. Check: `Stores.objects()` is a `HibernateObjectStore`.
  - **Schema.** Tests: `quarkus.hibernate-orm.schema-management.strategy=drop-and-create` (H2:
    `jdbc:h2:mem:ap;DB_CLOSE_DELAY=-1`). Production: Flyway with the PostgreSQL DDL from `hibernate/README.md` as a
    migration, plus `schema-management.strategy=validate`.
  - **Transactional outbox.** Call `activityPub.create/update/...` inside the application's `@Transactional` method
    (or `QuarkusTransaction`) that saves the post. A rollback then discards the activity and its deliveries. The
    remote recipient lookup runs before the stores' writes; inside an application transaction it still runs while
    that transaction is open.
  - **Your own users table.** Implement `ActorStore` as an `@Alternative @Priority(1) @ApplicationScoped` bean so it
    wins over `HibernateActorStore`; a plain bean would be ambiguous with it. The other three stores stay on Hibernate.
- Pitfalls:
  - Store methods **join** the caller's transaction (`joiningExisting`), so they commit or roll back with it. Outside a
    transaction each call commits on its own. Do not call the stores from the event loop; the reactive views
    (`Stores.reactive*()`) run them on worker threads.
  - The tables are prefixed `ap_` and owned by the extension. Do not map entities onto them or write them directly.
    Named persistence unit: set `quarkus.hibernate-orm."pu".packages=org.sitenetsoft.quarkus.activitypub.hibernate.entity`
    and `quarkus.activitypub.hibernate.persistence-unit=pu` (fixed at build time).
  - `RetentionJob` (daily 03:00) deletes remote objects after `quarkus.activitypub.remote-objects.retention` (90d)
    and de-duplication rows after `quarkus.activitypub.dedup.retention` (7d). `0` disables either. It needs the
    scheduler: `quarkus.scheduler.enabled=false` also stops retention and delivery.
  - Delivery is single-node: a claimed delivery is reserved for 60 s. Run the delivery poll on one node, or a slow
    run can send a delivery twice.
  - An inbox handler of your own (`InboundActivityHandler`) must do remote fetches in `prepare` (outside the
    transaction), not in `handle`.

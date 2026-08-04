# Generated Repository Adapter Boundary

## Target outcome

Cap4k application behavior accesses aggregate persistence only through `Mediator.repositories`. Default generation shall not expose a Spring Data `JpaRepository` or `JpaSpecificationExecutor` interface as an application repository capability. For each aggregate repository artifact, Generator shall emit one framework-owned, provider-private JPA carrier that implements the cap4k `Repository` contract through the installed JPA provider.

## Generated source contract

- The default aggregate repository artifact contains no public or application-injectable interface extending `JpaRepository` or `JpaSpecificationExecutor`.
- The generated artifact contains one provider-private carrier bound to the aggregate entity and its identifier type.
- The carrier is a framework-owned Spring component only so the cap4k repository supervisor can discover it as `Repository<ENTITY>`; application behavior does not inject it as a business port.
- No Spring Data repository interface, alias, deprecated facade, nested compatibility interface, fallback carrier, or dual generated path remains.
- Generated constructor and supertype signatures do not require an application-defined Spring Data repository bean.
- Re-running generation is idempotent with respect to this boundary: a removed public Spring Data repository is never recreated.

## Provider implementation contract

- The JPA repository adapter uses `EntityManager` or an equivalent provider-private delegate to implement the existing `JpaPredicate` query contract.
- Any Spring Data implementation class used internally remains an implementation detail and is not a generated bean, public supertype, constructor parameter, or application injection point.
- The adapter remains a cap4k `Repository<ENTITY>` contributor discovered by `DefaultRepositorySupervisor`.
- Query behavior for ID, ID collection, and Specification predicates remains the current behavior for this slice. This change does not implement the separate route-key, predicate-variant, ordering, pagination, or total-count findings.
- Repository invocation guards remain unchanged: reads require a Command or Query scope, and removals require a Command scope and an aggregate-root type.

## Persistence lifecycle contract

- A Command that reads an aggregate through `Mediator.repositories` keeps the aggregate managed in the surrounding JPA transaction.
- Ordinary aggregate changes are persisted by Hibernate dirty checking and the Command-owned Unit of Work; application code and generated carriers do not introduce an explicit public Spring Data `save` requirement.
- Repository observation continues to feed the current Unit-of-Work baseline logic.
- Aggregate deletion remains explicit: `RepositorySupervisor.remove` loads matching roots, observes them, and registers delete intent with the current persistence-intent recorder.
- Query-only reads remain read operations and do not create an alternative save boundary.

## Analyzer and round-trip contract

- The provider-private carrier carries `AggregateElementMetadata` with repository artifact identity, aggregate ownership, package, and `type = "repository"`.
- Analyzer accepts the carrier class shape and recovers the repository aggregate element from compile-time metadata without requiring a public interface.
- Physical carrier class naming and file naming may differ from the removed interface name, but normalized repository artifact identity remains stable.
- The DB-driven repository gate runs Project A generation from the DB source and compilation, the real compiler Analyzer, and Drawing Board export of the recovered repository structure. Project B regenerates the DB-driven repository from the same DB source and compiles it; the Design JSON round-trip gate is a separate gate for Design JSON elements and does not treat Repository as a Design JSON input.
- The DB-driven gate proves the carrier is recoverable from Analyzer evidence, the Drawing Board retains the repository aggregate element as structural evidence, and Project A and Project B generated source contain no public aggregate Spring Data repository interface.
- A second generation cannot infer or recreate the deleted public interface from stale Analyzer metadata.

## Verification contract

- Planner tests preserve repository artifact planning, entity/ID bindings, target module ownership, and repository metadata.
- Renderer tests assert the carrier's provider-private shape, cap4k adapter supertype, analysis metadata, and the absence of generated `JpaRepository`/`JpaSpecificationExecutor` interfaces and constructor dependencies.
- Analyzer tests compile a metadata-bearing repository carrier and assert successful repository-element recovery.
- Runtime integration tests use only `Mediator.repositories` from application behavior and prove query, managed-update persistence, and explicit removal persistence against the real JPA starter.
- The real DB/JPA generation test asserts repository carrier recovery and rejects the public Spring Data repository shape in both generations. The Design JSON round-trip test remains responsible only for Design JSON elements and must not require a Repository tag or Repository payload.
- Repository active-surface checks reject stale generated aggregate Spring Data repository templates, tests, fixtures, and machine contracts. Historical archived evidence and superseded design history are not rewritten.

## Non-goals

- Public application repository ports based on Spring Data.
- Compatibility aliases, deprecation periods, dual implementations, or fallback repository codecs/adapters.
- Changes to other Runtime audit findings.
- Replacing `Mediator.repositories`, Hibernate dirty checking, the Command-owned Unit of Work, or `RepositorySupervisor.remove`.
- Treating repository carriers as handwritten business policy or a second application extension API.

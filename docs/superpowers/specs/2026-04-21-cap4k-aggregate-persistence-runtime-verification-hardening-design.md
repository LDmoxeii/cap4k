# Cap4k Aggregate JPA Runtime Defect Reproduction Design

## Purpose

This slice replaces the earlier narrow "runtime verification hardening" idea with a more honest target:

- reproduce the JPA aggregate persistence defects exposed by `only-danmuku`
- classify whether each defect is a cap4k bug, a JPA mapping choice, or an unsupported persistence contract
- fix in place where the current JPA-backed runtime can support the desired contract safely
- defer backend replacement unless reproduction proves the current JPA path cannot support the contract without unacceptable complexity

This is not a backend comparison slice. The first priority is to make the current JPA implementation accountable with focused runtime fixtures.

## Current Context

The aggregate generator can now emit a broad set of bounded persistence annotations and relation mappings. Compile-level verification is not enough:

- generated entities can compile while Hibernate boot fails
- relation mappings can boot while aggregate save/load behavior is wrong
- application-side ID generation can compile while unit-of-work new/existing detection routes persistence incorrectly

The real defects should be reproduced inside `cap4k` before opening a larger persistence backend track.

## Real-Project Defects To Reproduce

### 1. Preassignable Application-Side IDs

The requirement is not "default generated ID or manual ID" as two separate entity modes.

The requirement is:

- an entity may declare an application-side default ID generator, such as Snowflake
- normal creation may omit the ID and let the framework assign one
- selected business flows may obtain an ID before database insert
- that preassigned ID must be used for the later insert
- the flow must not require insert-then-query just to learn the ID

This should be modeled as a preassignable application-side ID policy.

JPA distinction:

- plain assigned identifiers are standard ORM usage: application sets the ID before `persist`
- `@GeneratedValue` means the persistence provider owns generation at persist time
- combining `@GeneratedValue` with arbitrary user-preassigned IDs is not a portable JPA contract
- Hibernate can support this with a custom identifier generator or with a framework-level ID allocator, but cap4k must make the contract explicit

The runtime fixture must prove the desired cap4k contract, not rely on accidental provider behavior.

Required reproduction:

- insert aggregate with ID omitted; framework/provider assigns ID
- insert aggregate with ID already set; persisted row keeps that ID
- unit-of-work must treat the preassigned-ID aggregate as new, not route it to a failing `merge`
- no preliminary insert/query workaround is allowed

### 1.1 ID Strategy Scope

Application-side ID generation must not become a project-global single strategy.

The desired cap4k contract is a scoped ID strategy registry:

- the project may define a default ID strategy
- each aggregate root may override the default strategy
- each owned entity may override the aggregate strategy when needed
- multiple strategies may coexist in one project
- database-side strategies and application-side strategies must be distinguishable

Example strategy matrix:

| Scope | Strategy | Preassignable |
| --- | --- | --- |
| project default | `snowflake-long` | yes |
| `Category` aggregate | `snowflake-long` | yes |
| `UploadTask` aggregate | `uuid7` | yes |
| `AuditLog` aggregate | `database-identity` | no |

This means `Snowflake` and `UUID7` are both valid application-side strategies. They can coexist in the same application as long as each aggregate/entity resolves to one concrete strategy.

Preassignment is strategy-dependent:

- application-side strategies such as Snowflake and UUID7 support preassignment
- database-side strategies such as identity columns do not support preassignment
- a call site that requests early ID allocation for a database-side strategy should fail fast or be rejected by configuration validation

The implementation plan should avoid a single global `IdGenerator` abstraction that hides this distinction. It should model at least:

- strategy identity
- generated ID JVM type
- whether the strategy is application-side or database-side
- whether the strategy supports preassignment
- aggregate/entity strategy resolution order

The expected resolution order is:

1. entity-level override
2. aggregate-root-level override
3. project default
4. framework default, only if explicitly accepted by the implementation plan

### 2. Aggregate Loading Boundary and Lazy Relation Behavior

The real issue is not simply whether `FetchType.LAZY` or `FetchType.EAGER` appears in generated code.

The issue is whether cap4k's repository/unit-of-work contract can support aggregate use without forcing every aggregate relation to be eagerly loaded forever.

Required reproduction:

- load an aggregate through the repository in a normal application transaction
- access owned children inside the transaction
- verify the unit-of-work registration and transaction boundary are sufficient
- identify whether failures are caused by missing transaction scope, detached entities, generated relation mapping, or repository API shape

The fixture should not blindly encode `EAGER` as the framework answer. If eager loading is needed for a specific aggregate shape, that must be a deliberate capability decision.

### 2.1 Request Execution Policy and Command Transaction Boundary

`RequestSupervisor` is a unified dispatch entrypoint, but it must not imply one unified transaction policy for every request family.

Current cap4k shape:

- `Mediator.requests`, `Mediator.commands`, and `Mediator.queries` all point to the same `RequestSupervisor`
- `DefaultRequestSupervisor` only special-cases `SagaParam`
- non-Saga requests are all resolved to a `RequestHandler` and executed through `handler.exec(request)`
- generated or hand-written CLI requests currently have no stable framework marker and often appear only as plain `RequestParam` plus plain `RequestHandler`

This is architecturally different from NetCorePal's command-only unit-of-work behavior. Therefore, the lazy-loading fix must not be implemented by blindly wrapping the whole `RequestSupervisor.send()` path in a transaction.

The durable cap4k policy should remain family-specific:

- command requests enter the command transaction boundary by default
- query requests do not automatically share command transaction semantics
- CLI/distributed-client requests do not automatically enter a database transaction
- saga requests remain owned by `SagaSupervisor`
- saga child steps use the policy of the child request they execute
- plain `RequestHandler` remains non-transactional unless explicitly classified

However, command-boundary transaction expansion is not selected as the immediate fix for the current lazy aggregate defect. The fixture proves it can solve the lazy-access symptom, but implementing it correctly also requires reworking `JpaUnitOfWork.save()` commit/after-transaction semantics, domain-event timing, integration-event timing, and interceptor timing. That blast radius is too large if the only approved motivation is avoiding lazy-loading failure.

The immediate aggregate runtime repair direction should therefore prefer:

- safer generated relation mapping, especially removing `CascadeType.REFRESH` from default parent-child cascades
- an explicit aggregate load-plan contract through the existing mediator/supervisor APIs
- preserving command transaction-boundary expansion as a later architecture slice, only after `UnitOfWork` commit semantics are deliberately redesigned

Missing marker to record:

- CLI/distributed-client requests need their own explicit marker or typed contract in a later design
- without that marker, the runtime cannot distinguish client calls from generic request handlers safely
- until that exists, CLI requests must not be accidentally included in command transaction behavior

### 2.2 Aggregate Load Plans Through the Mediator

`FetchType.EAGER` is a global mapping policy, not a use-case loading policy. `FetchType.LAZY` avoids global over-fetching, but it leaks persistence-context requirements into application code when the repository API cannot express the intended aggregate graph.

The current mediator/supervisor API has only:

- predicate
- `persist`

That can express whether the loaded entity should be registered into `UnitOfWork`, but it cannot express how much of the aggregate graph this use case needs.

The preferred design direction is to keep the mediator/supervisor abstraction and add an explicit load-plan dimension rather than bypassing it with direct `JpaRepository`, `EntityManager`, or hand-written fetch joins.

First-slice load-plan vocabulary should stay small:

- `MINIMAL`: load only the root shape needed for read-only checks
- `WHOLE_AGGREGATE`: load the aggregate root and owned aggregate entities needed for command mutation

There is no `DEFAULT` enum value. No-plan repository reads are an API-entry convention, not a public load-plan choice. JPA and Querydsl repository implementations default no-plan reads to `WHOLE_AGGREGATE`; performance-sensitive callers must request `MINIMAL` explicitly.

Example intended shape:

```kotlin
AggregateSupervisor.instance.findOne(
    predicate = Video.byId(id),
    persist = true,
    loadPlan = AggregateLoadPlan.WHOLE_AGGREGATE
)
```

The JPA implementation may translate `WHOLE_AGGREGATE` into `EntityGraph`, fetch joins, or explicit initialization. That translation is provider-specific and should stay below the mediator/supervisor contract.

This keeps use-case loading explicit without making entity mappings globally eager and without requiring command-wide transaction expansion as the first repair.

### 3. Three-Level Aggregate Whole-Save Behavior

`only-danmuku` has aggregate structures like root -> child -> grandchild. The suspected defect is that whole-save/cascade behavior may fail or produce incorrect persistence effects.

The contract under test is aggregate-root whole-save:

- application code submits only the aggregate root to the unit of work
- owned child and grandchild entities are persisted through the aggregate relation mapping
- application code should not need child repositories
- application code should not need to call `uow.persist(child)` or `uow.persist(grandchild)` for owned entities
- the repository surface remains aggregate-root oriented

Required reproduction:

- create a root aggregate with child and grandchild entities
- persist through the cap4k unit-of-work/repository path
- update nested children and grandchildren
- remove nested children/grandchildren where orphan removal is configured
- verify database state after flush/transaction commit

This should be a runtime behavior test, not a renderer assertion.

The reproduction must separate three different concerns:

1. JPA database behavior:
   - `cascade = [CascadeType.ALL]` persists new child and grandchild rows when only the root is persisted
   - managed collection mutation updates existing child and grandchild rows
   - `orphanRemoval = true` deletes or soft-deletes removed child and grandchild rows according to the mapping
   - foreign keys from child to root and grandchild to child are valid after flush

2. Transaction and managed-state behavior:
   - loaded aggregate graphs must be modified inside the intended command transaction boundary
   - failures caused by detached entities or closed persistence contexts must be classified as transaction-boundary defects, not cascade defects
   - the test must distinguish load-modify-save behavior from create-and-save behavior

3. Unit-of-work post-processing visibility:
   - database correctness does not automatically mean cap4k interceptors see every cascaded entity
   - `postEntitiesPersisted` currently receives the explicit unit-of-work set plus any framework-tracked processing set
   - cascaded child and grandchild entities may not appear in that set unless cap4k deliberately captures them
   - whether inline persist listeners and domain-event release should include cascaded child/grandchild entities is a separate contract decision

Required test matrix:

- create root with two children and two grandchildren per child, then verify all rows and foreign keys
- load root, update a child scalar field and a grandchild scalar field, then verify database state
- remove one grandchild from a managed child collection, then verify orphan-removal behavior
- remove one child from the managed root collection, then verify child and descendant cleanup behavior
- clear and re-add a grandchild collection separately from replacing the collection instance
- if `@SQLDelete`/`@Where` are involved, verify both ORM-visible results and native SQL rows

The implementation plan must not start by re-enabling broad persistence-context scanning in `JpaUnitOfWork`.

Specifically:

- blindly adding all `persistenceContextEntities()` to `postEntitiesPersisted` can include unrelated managed entities
- it cannot accurately classify created, updated, and deleted entities without additional dirty-state analysis
- it may trigger inline listeners or domain-event release for entities that were only read
- it should be considered only after a failing test proves post-processing visibility is the actual missing contract

Likely classification outcomes:

- if create-only three-level save fails, inspect generated relation mapping first
- if create works but load-modify-save fails, inspect command transaction boundary and managed-state continuity first
- if database state is correct but listeners/events miss descendants, define a separate cascaded-entity visibility contract
- if replacing collection instances fails while mutating managed collections works, document or enforce the supported collection mutation pattern

## Fixture Strategy

Introduce a dedicated runtime fixture instead of mutating compile-only fixtures.

Recommended fixture:

- `aggregate-jpa-runtime-defect-sample`

The fixture should be intentionally small but structurally representative:

- H2-backed database
- generated aggregate entities
- a tiny runtime smoke entrypoint or Gradle task
- direct Spring/JPA or direct Hibernate boot only if that is the lowest-friction way to exercise the cap4k runtime path

The fixture should prefer cap4k's real repository/unit-of-work path when validating behavior. Direct Hibernate boot is useful only for isolating mapping validity.

## Runtime Reproduction Notes

2026-04-29 H2 fixture: `AggregateJpaRuntimeDefectReproductionTest` under `cap4k-ddd-starter` currently supports the omitted-ID Snowflake-style Hibernate generator path, but classifies the preassigned-ID path as a known defect. A repair plan must preserve this fixture and replace the preassigned-ID characterization with the desired contract assertion after the ID strategy/new-entity decision is implemented.

2026-04-30 H2 fixture: command handler repository load plus lazy child access through the current `RequestSupervisor` path is now supported because no-plan repository reads default to `WHOLE_AGGREGATE` and initialize owned collections below the repository boundary. The controlled transaction contrast test remains useful evidence, but the command path no longer requires command-wide transaction expansion for this lazy aggregate access case.

2026-04-29 H2 fixture: the fixture also verifies the exact request path under an expanded transaction scope. Wrapping `RequestSupervisor.instance.send(CountRuntimeRootChildrenRequest(...))` in a transaction allows the command handler to load the aggregate through `RepositorySupervisor`, access lazy children, and return the expected child count. This proves the lazy command defect is addressable by expanding the command/request transaction boundary around handler execution, independently of whether the implementation uses declarative or programmatic transaction mechanics.

This proof does not make command-wide transaction expansion the next implementation step. The current preferred route is to keep this fixture as evidence, then first repair generated mapping safety and add an explicit aggregate load-plan dimension through the mediator/supervisor APIs. Command-wide transaction expansion should remain deferred until a separate unit-of-work commit semantics design decides how `afterTransaction`, domain events, integration events, and interceptors bind to the real transaction commit.

2026-04-29 H2 fixture: the same fixture currently supports root-only three-level create, generated parent-id binding from A to multiple B rows and from each B to multiple C rows, managed child/grandchild scalar updates, grandchild orphan removal, child orphan removal, and clear/re-add mutation using managed collections. No repair task should be opened for this behavior unless a real-project fixture contradicts it.

2026-04-29 H2 fixture: the same fixture supports direct child-to-parent `EAGER` reverse `ManyToOne` navigation when loading the child row, but classifies the chained `C -> B -> A` navigation in an `A -> B -> C` model as a known defect. This separates pure root-to-descendant cascade support and direct reverse navigation from the nested reverse-navigation problem seen in `Video -> VideoFile -> VideoFileVariant`; a repair plan should make aggregate-internal reverse entity navigation explicit opt-in or change its default fetch/mapping policy.

The nested reverse-navigation failure is not a lazy-loading or transaction-isolation defect. The reproduced failure happens during `JpaUnitOfWork.save()`: after `entityManager.flush()`, the unit of work calls `entityManager.refresh(root)` for the newly persisted root. Since `CascadeType.ALL` includes refresh, Hibernate walks `Root -> Child -> Grandchild`; because the reverse `ManyToOne` mappings are eager, read-only navigations over the same join columns, it then re-enters `Grandchild -> Child -> Root`. The direct `Root -> Child -> Root` cycle is supported, but the deeper `Root -> Child -> Grandchild -> Child -> Root` refresh graph currently produces `FetchNotFoundException` for the root id.

The intended use case for inverse parent navigation is aggregate rehydration after repository whole-load: domain logic may need to move from a child entity back to its parent inside the already loaded aggregate graph. That use case does not require `CascadeType.REFRESH`. Therefore a repair plan should prefer removing refresh from generated parent-child cascade policy, for example by replacing `CascadeType.ALL` with the explicit persistence cascades actually required for whole-save (`PERSIST`, `MERGE`, `REMOVE`) and keeping refresh out of the default aggregate entity template.

Transaction-boundary expansion remains a separate design topic. The lazy aggregate access fixture previously proved a command path persistence-context boundary problem; that specific symptom is now addressed by default whole-aggregate repository loading. The nested reverse-navigation fixture proves a different mapping/refresh problem. A future repair plan should not conflate these two areas: changing command transaction scope may still be valid, but it should not be used as the fix for cascade-refresh inverse navigation failures.

2026-04-29 implementation result: generated parent-child aggregate cascades no longer use `CascadeType.ALL`; they render explicit `PERSIST`, `MERGE`, and `REMOVE`, excluding `REFRESH`. The runtime fixture keeps the old `CascadeType.ALL` nested inverse eager graph as a known-defect contrast and adds a safe-cascade graph that persists successfully without triggering refresh-based `FetchNotFoundException`.

2026-04-29 implementation result: repository and aggregate read APIs now carry `AggregateLoadPlan`. `WHOLE_AGGREGATE` initializes owned `@OneToMany` aggregate collections below the JPA repository boundary, allowing command handlers to request a usable aggregate graph without requiring command-wide transaction expansion or global eager mappings.

2026-04-29 implementation note: JPA `WHOLE_AGGREGATE` initialization requires the JPA repository read method itself to have a read-only transaction boundary; otherwise Spring Data's internal repository call returns a detached proxy before explicit initialization can run. This does not expand the full command/request transaction scope and does not change `JpaUnitOfWork.save()` commit semantics.

2026-04-30 implementation note: `AggregateLoadPlan.DEFAULT` was removed because it encoded migration history rather than domain intent. Public load-plan values are now only `MINIMAL` and `WHOLE_AGGREGATE`.

2026-04-30 implementation note: JPA and Querydsl repository no-plan reads now default to `WHOLE_AGGREGATE`. Querydsl repositories support the same owned `@OneToMany` initialization behavior as the JPA repository path.

## ID Contract Design Options

The implementation plan must choose one explicit ID strategy after reproducing the defect.

### Option A: Framework-Level ID Allocator

The framework exposes an application-side ID allocation path. Factories or command handlers can request an ID before constructing the aggregate, and generated entities use assigned IDs rather than relying on provider generation.

Pros:

- matches the business requirement most directly
- ID is available before persistence
- avoids ambiguous `@GeneratedValue` semantics
- is portable across JPA providers

Cons:

- changes the framework contract around generated IDs
- requires generator/runtime integration beyond annotation output

### Option B: Hibernate Assigned-Or-Generated Identifier Generator

Generated mappings keep a Hibernate-specific generator that returns the existing ID if present and otherwise generates one.

Pros:

- preserves the old annotation style more closely
- can keep normal "omit ID" creation ergonomic

Cons:

- Hibernate-specific
- easy to misunderstand as portable JPA
- still needs unit-of-work new-entity detection to treat preassigned IDs as new

### Option C: Pure JPA Assigned IDs

Generated entities do not use `@GeneratedValue` for application-side IDs. The framework always assigns IDs before persist.

Pros:

- portable and simple at the ORM layer
- no provider-specific generator behavior

Cons:

- no provider-side default generation path
- requires cap4k to supply IDs consistently before persistence

Recommended direction for the spec:

- treat Snowflake-style IDs as application-side IDs
- allow more than one application-side strategy, such as Snowflake and UUID7, in the same project
- resolve ID strategy per aggregate/entity rather than through a hidden project-global singleton
- do not model this as database identity generation
- do not rely on plain JPA `@GeneratedValue` to accept preassigned IDs
- decide between Option A and Option B only after the reproduction test exposes the current failure mode

## Boundaries

This slice is not:

- a full persistence backend comparison
- a Jimmer/MyBatis/JOOQ replacement decision
- a relation model redesign
- a query generator change
- a design-json change
- a broad real-project integration workaround

This slice may change production code only when a failing runtime reproduction proves a cap4k bug or an explicitly approved contract gap.

## Testing Strategy

The implementation should follow reproduction-first discipline:

1. create a minimal fixture that reproduces one defect
2. run the fixture and capture the failure
3. fix only the proven failure
4. keep the regression fixture as the support contract

Focused tests should live with the Gradle functional/runtime verification tests.

Expected verification shape:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*AggregateJpaRuntime*"
```

The exact class name can be chosen by the implementation plan, but the test names should mention the defect being proved:

- preassignable application-side id persists as new aggregate
- repository load keeps aggregate children usable inside transaction
- three-level aggregate cascades save and orphan removal correctly

## Success Criteria

This slice is complete when:

- the three real-project persistence defects are represented as focused runtime fixtures or explicitly classified as non-defects
- preassignable application-side ID behavior has an explicit cap4k contract
- ID strategy resolution supports project default plus aggregate/entity overrides
- application-side and database-side ID strategies are distinguishable in the contract
- the same project can use at least Snowflake-style and UUID7-style application-side strategies without forcing a global singleton
- unit-of-work behavior does not misclassify preassigned-ID new aggregates
- aggregate load behavior is explained by explicit repository load plans, mapping safety, and transaction boundaries rather than accidental eager loading
- three-level aggregate save/update/delete behavior is either supported with tests or documented as unsupported with a clear reason
- backend replacement remains deferred unless the reproduction evidence justifies it

## Residual Risk

This slice still does not prove:

- production MySQL dialect behavior
- every relation shape
- concurrency and optimistic-lock conflict behavior
- full Spring Boot application wiring
- alternative persistence backend viability

Those belong to later support-track or backend-comparison work.

# Cap4k Application Execution And Hibernate UoW Stabilization Design

Date: 2026-07-30
Status: Approved for implementation planning

## Reader Contract

This spec is the current design authority for Cap4k application execution, execution-context propagation, Command transaction ownership, Query read transactions, Capability invocation, Hibernate Unit of Work stabilization, audit enrichment, synchronous Domain Event dispatch, reliable asynchronous Command registration, and Integration Event outbox registration.

It replaces the earlier revision of this file where that revision retained a public explicit `flush()`, described Query as transaction-unspecified, omitted Query/Capability asynchronous composition, retained EXISTING persistence enrollment, or kept the runtime provider-neutral. Earlier specs remain historical evidence for delivered slices, but their conflicting future direction is superseded here.

Issue #115 is closed and is not the backlog owner for this design. Its owned-child factory work remains separate. Issue #19 remains an investigation of backend alternatives and is also not the implementation target: this design deliberately standardizes the current Spring Data JPA plus Hibernate runtime and defers multi-provider SPI work.

After reading this spec, an implementation agent should be able to answer:

- why Command, Query, Capability, and Event are independent public concepts;
- why only Command creates a write UoW;
- why Query owns one Handler-wide read-only transaction;
- why Capability is persistence-neutral even when Caller Runs executes it on a Command thread;
- what `ExecutionContext` propagates and what `InvocationScope` controls;
- why Query and Capability have one synchronous Handler shape but synchronous and asynchronous supervisor methods;
- why Query/Capability overload may run in the caller while asynchronous Command never joins the caller UoW;
- why application code has no `save()`, `persist()`, `execute()`, or `flush()` UoW surface;
- why Hibernate uses MANUAL flush for the whole Command UoW;
- why Repository load means observation rather than EXISTING persistence intent;
- how lazy owned collections, orphan removal, Strong IDs, and aggregate ownership are detected without `AggregateLoadPlan`;
- how candidate detection, audit enrichment, final detection, flush, and event frontiers form one stabilization loop;
- why sibling Domain Events and Handlers have no ordering promise;
- which failures roll back the current transaction and which belong to later reliable delivery.

## Summary

Cap4k exposes four explicit application concepts:

```text
Command      local state change, REQUIRED write transaction, automatic UoW
Query        local state observation, Handler-wide read-only transaction
Capability   context-external capability, persistence-neutral invocation
Event        immutable fact, synchronous frontier or reliable outbox ownership
```

The write path is one transaction-level Hibernate UoW. The outer Command Coordinator keeps Hibernate in MANUAL flush mode, observes aggregate roots loaded through Repository, records explicit root CREATE and DELETE intent, detects actual Entity and Collection changes, enriches audit metadata, performs final dirty detection, flushes, dispatches one non-reentrant Domain Event frontier, and repeats until stable.

Application code never saves an existing aggregate. A Factory registers a new root, Repository load keeps an existing root managed and observed, actual mutation is found by Hibernate dirty checking, and Repository removal registers root deletion.

Query and Capability share the same blocking Handler shape for synchronous and asynchronous invocation. `askAsync()` and `callAsync()` provide controlled parallel composition, not end-to-end non-blocking I/O. Their bounded executors default to Caller Runs for natural backpressure. Asynchronous Command means durable registration for later execution in a new transaction; it never falls back into the caller UoW.

`ExecutionContext` carries immutable attribution data across framework-owned asynchronous, reliable, RPC, and Integration Event boundaries. `InvocationScope` is a local semantic guard that distinguishes Command, Query, Capability, and Domain Event Handler execution. UoW, EntityManager, transaction state, event runtime state, and InvocationScope are never copied to another thread.

## Current Code Evidence

The current `master` already contains the first application-execution reset:

- Command, Query, and Capability have independent contracts and supervisors;
- Command opens or joins `JpaUnitOfWork.execute()`;
- nested Commands share one UoW Context;
- synchronous Domain Events use non-reentrant causal frontiers and fail fast;
- reliable asynchronous work belongs to Command;
- built-in Saga has been removed;
- JPA audit already has candidate detection, enrichment, final detection, and explicit provider flush phases.

The remaining code still carries older surfaces that this revision removes:

- `UnitOfWork` exposes `execute`, `persist`, `remove`, `flush`, a static `instance`, and `Mediator.uow`;
- `PersistIntent.EXISTING` and Repository `persist: Boolean` still convert every Command repository read into persistence enrollment;
- `AggregateLoadPlan.WHOLE_AGGREGATE` still expands lazy owned graphs by default;
- `AbstractJpaRepository` explicitly detaches results when `persist=false`;
- `JpaUnitOfWork` sets Hibernate MANUAL flush only after final stabilization, so Handler-time queries may trigger an early AUTO flush;
- repository observation and Strong ID completion still rely on a broad reflection baseline;
- audit context uses `Instant.now()` plus an untyped attributes map;
- Query and Capability are synchronous only and Query has no Handler-wide read transaction;
- there is no general execution-context registry or local invocation-kind guard;
- Hibernate unwrap failures may silently reduce candidate detection.

These are migration facts, not compatibility constraints. There are no external compatibility requirements for this redesign.

## Goals

- Preserve independent Command, Query, Capability, and Event public semantics.
- Keep Command as the only framework-owned write transaction boundary.
- Support REQUIRED only for public Command propagation.
- Give one physical Command transaction exactly one UoW Context and Coordinator.
- Give every Query one Handler-wide read-only transaction without a write UoW.
- Give Query and Capability native synchronous and asynchronous composition through one Handler shape.
- Propagate immutable execution attribution across all framework-owned boundaries.
- Remove application-facing UoW lifecycle operations and automatic EXISTING enrollment.
- Remove `AggregateLoadPlan` without breaking generated Strong ID timing or owned-child persistence.
- Prevent Hibernate AUTO flush from bypassing audit stabilization.
- Organize persistence changes by aggregate root with Entity-level detail.
- Keep audit enrichment deterministic, ordered, idempotent, and transaction-bound.
- Keep Domain Event dispatch non-reentrant, frontier-based, unordered among siblings, and fail-fast.
- Use current Spring Data JPA plus Hibernate capabilities directly and honestly.
- Fail fast instead of silently degrading when the required Hibernate integration is unavailable.

## Non-Goals

- Queryable Value Object persistence is not designed here.
- A public third-party Persistence Provider SPI is not designed here.
- EclipseLink, another JPA provider, or another ORM is not supported by this iteration.
- Child-only changes do not automatically advance root optimistic-lock version or root audit timestamp.
- Query does not prove that a Handler never mutates an in-memory object.
- Capability does not participate in distributed transactions.
- Query/Capability asynchronous invocation is not reactive or end-to-end non-blocking.
- Generic timeout does not promise cancellation of running SQL, RPC, or Handler code.
- Domain Event payload does not provide an occurrence-time aggregate snapshot.
- Sibling event or Handler order is not guaranteed.
- Ordinary Domain Events do not mix synchronous and `@Async` Handlers.
- Field-level audit history is not provided by the audit enricher SPI.
- Direct `EntityManager.flush()`, bulk DML, native SQL, or custom transaction synchronization is not sandboxed.
- Backward compatibility is not preserved.

## Terms

### ExecutionContext

An immutable snapshot of typed attribution elements such as actor, trace, correlation, environment, or tenant hint. It is transportable but is not authorization state and does not grant persistence access.

### InvocationScope

A local LIFO runtime scope identifying the current semantic invocation kind. It is used for policy guards and is never serialized or propagated as ambient asynchronous state.

### Outer Command

The first Command entering without an active Cap4k Command UoW. It owns the REQUIRED transaction, creates the UoW Context, and alone may stabilize and finish that UoW.

### Nested Command

A Command sent while a Command UoW is active. It executes immediately in the same physical transaction and UoW, but does not independently flush, drain events, commit, roll back, or complete the UoW.

### QueryExecution

The read-only transaction boundary covering Query validation, interceptors, Handler execution, Repository navigation, and DTO mapping. It is not a write UoW.

### Stabilization Round

One pass of persistence-intent normalization, candidate detection, audit enrichment, final detection, explicit Hibernate flush when needed, and synchronous Domain Event frontier dispatch.

### Event Frontier

An unordered snapshot of synchronous Domain Events eligible for one dispatch round. Events produced while the frontier is active enter the next frontier.

### Reliable Registration

Writing an asynchronous Command record or Integration Event outbox record into the current local transaction. Registration success is not later execution or publication success.

## Execution Context

### Typed Elements And Stable Keys

Execution attribution is not a stringly typed mutable map.

```kotlin
interface ExecutionContextElement

data class ExecutionContextKey<T : ExecutionContextElement>(
    val name: String,
    val type: Class<T>,
)
```

The registry must reject duplicate wire names and incompatible key types at startup. A snapshot is immutable. A builder rejects duplicate insertion unless the caller uses an explicit replace operation.

```kotlin
interface ExecutionContextAccessor {
    fun current(): ExecutionContextSnapshot
}

interface ExecutionContextScopeManager {
    fun install(snapshot: ExecutionContextSnapshot): AutoCloseable
}
```

The local implementation uses a strict ThreadLocal stack with LIFO close validation. It does not use `InheritableThreadLocal`.

### Codecs And Boundaries

Wire propagation is versioned and opt-in per element.

```text
RELIABLE_COMMAND
RELIABLE_DOMAIN_EVENT
INTEGRATION_EVENT
RPC
```

Each codec declares its key, version, and allowed boundaries. Local synchronous invocation serializes nothing.

Reliable persisted boundaries are strict:

- duplicate elements fail;
- a known malformed element fails;
- an unsupported known version fails;
- a known element used on a disallowed boundary fails;
- execution does not begin after decode failure.

External RPC and Integration Event ingress may ignore unknown element names for rolling compatibility, but known malformed, duplicate, unsupported-version, or disallowed elements still fail. Cap4k does not preserve unknown opaque elements for later relay.

ExecutionContext is attribution, not authorization. Authentication and authorization occur at the transport or application boundary before trusted elements are installed.

### Framework-Owned Asynchronous Propagation

`askAsync()`, `callAsync()`, reliable Command execution, reliable Domain Event handling, Integration Event adapters, and RPC adapters automatically capture and restore ExecutionContext.

Arbitrary user executors, `CompletableFuture.supplyAsync`, `thenApplyAsync`, coroutines, Reactor, SDK callbacks, and user-created threads do not receive automatic propagation. Cap4k provides explicit wrappers:

```kotlin
executionContextPropagation.decorate(executor)
executionContextPropagation.wrap(task)
```

Framework asynchronous tasks propagate ExecutionContext only. They do not propagate:

- Command UoW;
- EntityManager or Hibernate Session;
- Spring transaction state;
- InvocationScope;
- mutable Domain Event runtime state;
- arbitrary ThreadLocals.

An asynchronous result is completed only after the installed ExecutionContext and InvocationScope have been closed. This prevents `thenApply` from accidentally observing a scope merely because it happened to execute inline on the completion thread.

### Reliable And Transport Storage

Reliable Command captures an encoded snapshot when it is scheduled. The record and archive retain that snapshot unchanged across retries. Existing rows with no context decode as EMPTY.

Integration Event captures an immutable snapshot when attached and encodes it for the Integration Event boundary when the outbox record is released. Payload and context remain separate record fields. Reliable Domain Event records use their own boundary and restore scope before local Handler dispatch.

RPC uses one bounded, duplicate-rejecting envelope metadata field. The client captures before asynchronous offload. The server authenticates, decodes, installs ExecutionContext, then enters Query or Command transaction setup. Response context is not merged back into the caller.

## Invocation Scope

ExecutionContext answers "who and from where". InvocationScope answers "what semantic operation is currently running".

```kotlin
enum class InvocationKind {
    COMMAND,
    QUERY,
    CAPABILITY,
    DOMAIN_EVENT_HANDLER,
}
```

InvocationScope is a strict local LIFO stack. Supervisors install the category before validation and interceptors and restore it in `finally`.

| Current scope | Command | Query | Capability |
| --- | --- | --- | --- |
| Controller or ordinary orchestration | allowed | allowed | allowed |
| Command | synchronous nested Command, same UoW | forbidden | allowed |
| Query | forbidden | synchronous `ask()` allowed; `askAsync()` forbidden | allowed |
| Capability | forbidden | forbidden | composition allowed |
| Domain Event Handler | through nested Command | forbidden | allowed |

Repository and Factory guards consult the top InvocationScope, not only `UnitOfWork.active`. When Caller Runs executes a Capability on a Command thread, the stack temporarily becomes `COMMAND -> CAPABILITY`; Cap4k persistence entrypoints therefore remain unavailable inside the Capability even though the physical Spring transaction is still bound to that thread.

An asynchronous supervisor invocation does not copy the caller InvocationScope. It establishes a new target scope in the task.

## Public Application Concepts

### Command

```kotlin
interface Command<R : Any>

fun interface CommandHandler<C : Command<R>, R : Any> {
    fun handle(command: C): R
}
```

Command:

- enters or joins one REQUIRED write transaction and UoW;
- may load and modify aggregate roots;
- may send nested Commands;
- may call Capabilities;
- may register reliable Commands and Integration Events;
- may not call Query;
- is automatically stabilized by the outer Coordinator.

There is no public `REQUIRES_NEW`, `NESTED`, `NOT_SUPPORTED`, `SUPPORTS`, `MANDATORY`, or `NEVER` Command propagation. Work requiring a new transaction uses a reliable asynchronous Command or an Integration Event after the current transaction commits.

Asynchronous Command is durable registration:

```kotlin
val reference = Mediator.commands.enqueue(command)
val scheduled = Mediator.commands.schedule(command, executeAt)
```

It never means executor offload with Caller Runs. A worker later creates a new outer Command, REQUIRED transaction, and UoW.

### Query

```kotlin
interface Query<R : Any>

fun interface QueryHandler<Q : Query<R>, R : Any> {
    fun handle(query: Q): R
}

interface QuerySupervisor {
    fun <Q : Query<R>, R : Any> ask(query: Q): R
    fun <Q : Query<R>, R : Any> askAsync(query: Q): CompletionStage<R>
}
```

Every Query owns a Handler-wide REQUIRED read-only transaction. The boundary covers validation, Query interceptors, Handler execution, Repository lazy navigation, and DTO mapping. It creates no write UoW, performs no audit, drains no event, and never flushes.

Query may use Repository for aggregate detail reads. Criteria, projection, or a separate read component remains the recommended tool for list, report, and cross-shape queries. Query returns DTOs or Values, not Entity, Hibernate Proxy, or lazy collection graphs.

Repository-loaded entities remain managed during the Handler. Query transaction setup uses Hibernate read-only mode and MANUAL flush. Accidental in-memory mutation is discarded at the end; Cap4k does not snapshot every property merely to prove no setter was called. Direct bulk DML or manual EntityManager flush is outside the contract.

Synchronous nested `ask()` reuses the active read-only QueryExecution. Nested `askAsync()` fails because executor execution would use another transaction while Caller Runs could reuse the current transaction, making snapshot semantics load-dependent. Command-to-Query is forbidden; Command uses Repository and domain services for write decisions.

### Capability

```kotlin
interface CapabilityCall<R : Any>

fun interface CapabilityHandler<C : CapabilityCall<R>, R : Any> {
    fun call(request: C): R
}

interface CapabilitySupervisor {
    fun <C : CapabilityCall<R>, R : Any> call(request: C): R
    fun <C : CapabilityCall<R>, R : Any> callAsync(request: C): CompletionStage<R>
}
```

Capability consumes functionality owned outside the current bounded context. It may be RPC, SDK, filesystem, in-process adapter, or another external resource. It does not create a persistence transaction and may not use Repository, Factory, UnitOfWork, Command, or Query entrypoints. Capability composition is allowed.

Command and Query may call Capability. A Command that needs an asynchronous Capability result explicitly waits for its CompletionStage. Dropping the stage creates work that may outlive the caller and whose later failure cannot roll back the Command; the first implementation does not auto-join outstanding stages or provide structured concurrency.

Capability owns validation, Handler resolution, context propagation, telemetry, and diagnostics. Concrete adapters own authentication, protocol mapping, timeout, retry safety, and technical error translation.

### Event

```text
Domain Event       synchronous, current Command UoW frontier
Integration Event  reliable outbox registration, publication after commit
```

Ordinary Domain Events have no `@Async` Handler mode. Local asynchronous work is a reliable Command. Cross-context publication is an Integration Event.

## Query And Capability Asynchronous Execution

### One Handler Shape

There is no `AsyncQueryHandler` or `AsyncCapabilityHandler`. Sync and async supervisor methods invoke the same blocking Handler pipeline.

```text
ask()/call()
  -> current thread
  -> direct result or original exception

askAsync()/callAsync()
  -> bounded category executor or Caller Runs
  -> CompletionStage result
```

This is controlled concurrent scheduling, not reactive or non-blocking I/O.

### Executor Isolation And Backpressure

Query and Capability use separate bounded, replaceable executors. Slow external Capability calls must not starve database Query execution.

Default overload behavior is Caller Runs. When the queue is full and the executor is active, the caller executes the same wrapped task. This makes `askAsync()` or `callAsync()` eligible for parallel composition but does not guarantee a thread switch or immediate return.

`REJECT` remains configurable for deployments that prefer load shedding. Executor shutdown always returns a failed CompletionStage; it never silently discards or runs a task. Cap4k uses its own rejection wrapper rather than raw JDK `CallerRunsPolicy`, whose shutdown behavior can silently discard.

### Failure Contract

Synchronous methods throw original validation, resolution, Handler, or transaction failures. Asynchronous methods always express invocation failures through CompletionStage, including when Caller Runs already executed the task.

Asynchronous failure includes:

- validation failure;
- missing or conflicting Handler;
- Handler or interceptor failure;
- Query read transaction failure;
- forbidden nested `askAsync()`;
- executor shutdown;
- configured overload rejection.

Sync methods do not implement themselves by calling async and joining. This avoids unnecessary scheduling and `CompletionException` wrapping.

Generic timeout only limits the caller's wait. It does not promise cancellation of JDBC, HTTP, SDK, or Handler execution. Provider-specific timeout and cancellation remain adapter responsibilities.

## Command Transaction And UoW Ownership

### One Transaction, One Context

No active Command UoW:

```text
Command -> create REQUIRED transaction -> create UoW Context
```

Active Cap4k Command UoW:

```text
nested Command -> reuse transaction and UoW Context
```

An external Spring transaction without an active Cap4k UoW cannot be adopted as a Command transaction. Only the outer Coordinator may stabilize and finish the UoW.

### No Application UoW Surface

Application code does not call UoW lifecycle operations.

```text
Factory create       -> framework records Root CREATE
Repository load      -> framework observes managed Root
Root mutation        -> Hibernate detects actual dirty state
Repository remove    -> framework records Root DELETE
Command return       -> outer Coordinator stabilizes automatically
```

Remove public or application-facing access to:

- `UnitOfWork.instance`;
- `Mediator.uow`;
- `UnitOfWork.execute()`;
- `UnitOfWork.persist()`;
- `UnitOfWork.remove()`;
- `UnitOfWork.flush()`;
- propagation parameters.

Cross-module coordinator and intent-recorder interfaces may remain JVM-public because Kotlin `internal` cannot cross Gradle module boundaries. They are framework-internal contracts, have no static locator, and are not third-party SPI.

### MANUAL Flush For The Entire Command

The outer UoW captures the current Hibernate flush mode and sets `FlushMode.MANUAL` before Command validation, interceptors, and Handler execution. It keeps MANUAL mode through all nested Commands and event frontiers and restores the previous mode during cleanup.

Only the UoW provider-flush phase calls `EntityManager.flush()`.

This prevents a Repository or JPQL query inside a Command from triggering SQL before candidate detection and audit enrichment. A database query after an in-memory aggregate mutation is therefore not guaranteed to observe that unflushed mutation. Command business logic inspects the aggregate in memory; it does not query the database to rediscover its own pending write.

Direct EntityManager flush, bulk DML, native SQL, or user changes to Hibernate flush mode bypass Cap4k and are outside the contract.

## Repository And Aggregate Tracking

### Repository API

Remove `persist: Boolean` and `AggregateLoadPlan` from Repository and RepositorySupervisor APIs. Repository never explicitly detaches a result and never merges an existing aggregate merely because it was read.

```text
Repository load
  != persistence intent
  != dirty state
  != SQL update
```

In Command, a returned aggregate root remains Hibernate-managed and is recorded as observed. In Query, a returned Entity remains managed for the Handler-wide read transaction but is not recorded in a write UoW. Outside a framework transaction, a repository method's own transaction ends naturally and the returned object becomes detached without explicit `detach()`.

### Root Boundary

Command repository load and Repository removal accept Aggregate Root types only. Query may read roots or owned entities because it creates no write UoW.

An `AggregateRootCatalog` identifies root types. The initial implementation may derive it from registered `AggregateFactory<Payload, Root>` definitions because every generated root has a canonical creation graph. Hibernate proxy types are normalized before lookup.

```text
COMMAND Repository load  -> require root -> observe root
QUERY Repository load    -> root or child -> no write observation
Repository remove        -> require managed root in active Command
```

Capability and Domain Event Handler scopes cannot call Repository directly. A Domain Event Handler that changes state sends a nested Command.

### Observation And Ownership

The UoW tracks root object identity and known owned-entity ownership:

```text
trackedRoots
ownerByEntityInstance
```

Repository observation traverses only currently initialized owned relations. It never initializes a lazy proxy or collection merely to build a baseline. Before each stabilization round, the provider expands the currently initialized ownership graph to a fixed point.

Hibernate CollectionEntry supplies collection owner, dirty state, orphans, and queued additions/removals. A changed relation may be initialized when exact classification requires it; untouched lazy relations must remain unloaded.

If one owned Entity instance or persistent identity is associated with multiple unrelated roots, stabilization fails. A changed aggregate-owned Entity reached through supported root observation that cannot be assigned to one tracked root also fails rather than becoming an independent aggregate. Cap4k infrastructure records that share the Session, such as reliable Command or Event records, remain provider-managed and are excluded from aggregate change sets and audit enrichment. Direct EntityManager writes are an unsupported bypass and do not gain aggregate ownership semantics merely by entering the same Session.

## Hibernate Change Detection

Spring Data JPA plus Hibernate is the only supported persistence runtime for this iteration. Cap4k may use Hibernate EntityEntry, CollectionEntry, ActionQueue, persisters, and dirty checking directly.

There is no public Persistence Provider selection, capability declaration, or fallback. Internal helpers such as `JpaChangeTracker` exist only to control `JpaUnitOfWork` complexity and enable focused tests.

Failure to unwrap the required Hibernate Session or access the required change-tracking behavior is a startup error. Candidate detection must never silently skip Hibernate state.

### Aggregate-Oriented Change Set

Persistence changes are organized by root and contain Entity detail:

```kotlin
data class AggregatePersistenceChangeSet(
    val root: Any,
    val rootOperation: RootOperation,
    val entityChanges: List<EntityPersistenceChange>,
)

enum class RootOperation { NONE, CREATE, DELETE }
enum class EntityOperation { CREATE, UPDATE, DELETE }
```

Examples:

```text
existing Root scalar change
  -> rootOperation NONE
  -> Root UPDATE

existing Child-only change
  -> rootOperation NONE
  -> Child UPDATE

new aggregate
  -> rootOperation CREATE
  -> Root CREATE plus owned CREATE entries

remove aggregate
  -> rootOperation DELETE
  -> Root DELETE plus provider cascade DELETE entries
```

The provider emits net effects, not an operation log. Child CREATE then DELETE before synchronization is NONE. Existing Child UPDATE then DELETE is DELETE. Existing Child removed and re-added as the same relation is NONE when Hibernate final state matches its baseline.

Child-only change creates an aggregate change set but does not make the Root Entity dirty and does not automatically advance root version or root audit fields.

## Strong ID And Owned Entity Creation

Application-side generated Strong IDs use three layers:

```text
Factory creates Root               -> assign Root ID immediately
Factory/OwnedEntityList adds Child -> assign Child ID immediately
UoW stabilization                 -> assign missing ID as final safety net
```

Immediate generation allows domain behavior and Domain Event payload to use IDs before flush. The stabilization fallback guarantees persistence integrity but cannot repair an event payload that was already constructed with a missing ID.

Application-assigned ID being non-null is not evidence that an Entity already exists. CREATE classification uses explicit root CREATE intent, Hibernate state, and owned-relation delta rather than `EntityInformation.isNew()` alone.

For existing managed Entities, the generated ID must equal Hibernate's original identifier. Clearing or changing it fails. Attaching a detached owned child, merging owned children independently, or moving an owned child between aggregate roots is unsupported. A move is modeled as deletion from the old aggregate plus creation of a new child in the new aggregate.

## Create And Delete Semantics

`onCreate` and `onDeleted` remain optional reflective root callbacks. Missing methods are valid. `onUpdate` does not exist. Owned children never invoke aggregate lifecycle callbacks and never originate Domain Events.

Factory creation records root CREATE and invokes `onCreate`. Repository removal records managed root DELETE and invokes `onDeleted` at most once per UoW.

CREATE followed by REMOVE before the first provider synchronization folds to NONE:

- no INSERT or DELETE SQL;
- both optional callbacks may already have executed;
- all unreleased Domain Events attached to the root are discarded.

After a CREATE was already synchronized in an earlier stabilization round, a later DELETE remains an INSERT followed by DELETE inside the same transaction.

Root deletion requires a new-pending or currently managed root. Cap4k no longer merges detached roots for removal. Child deletion is expressed by changing an owned ONE or MANY relation and is classified through orphan removal and cascade state. `UnitOfWork.remove(child)` has no public or internal application path.

Normal updates never load untouched relations. Explicit aggregate-root deletion may load lazy owned relations required to complete cascade deletion and delete classification.

## Audit Lifecycle

### Audit Context

The outer Command UoW captures audit data once:

```kotlin
data class JpaPersistenceAuditContext(
    val auditTime: Instant,
    val executionContext: ExecutionContextSnapshot,
)
```

`Clock` and `ExecutionContextAccessor` are injected. `Instant` is the absolute-time semantic; formatting and database representation belong to the enricher and JPA mapping. Cap4k does not define built-in Actor, Tenant, or Trace audit fields.

### Ordered Enrichers

```kotlin
interface JpaPersistenceAuditEnricher {
    fun enrich(
        changeSet: AggregatePersistenceChangeSet,
        context: JpaPersistenceAuditContext,
    )
}
```

All enrichers run sequentially in Spring `Ordered` or `@Order` order. A later enricher sees earlier scalar changes. Cap4k does not detect two enrichers writing the same property; normal ordered last-write behavior applies. An enricher failure rolls back the Command transaction.

The outer UoW auditTime and ExecutionContext snapshot remain fixed across all stabilization rounds. The same Entity may be enriched again if a later Domain Event frontier creates a real new change. Enrichers must therefore be idempotent for one UoW and must assign context values rather than increment counters or perform external work.

### Candidate, Enrich, Final

```text
detect candidate business and persistence changes
  -> run ordered audit enrichers for candidates
  -> verify audit topology boundary
  -> detect final dirty state
  -> explicit Hibernate flush
```

Clean loaded aggregates are not candidates and receive no audit update. An Entity changed only by audit enrichment joins final dirty state but does not cause a new round after the successful flush advances Hibernate's baseline.

Audit enrichers may modify scalar properties or embedded audit values on supplied existing Entities. They may not create or delete persistent Entities, change owned relations, call persistence lifecycle operations, send Commands, or publish Domain Events. The provider records Entity/Collection topology and the Domain Event cursor before enrichment and fails if those structures change.

Without a declared audit-field registry, Cap4k cannot distinguish `updatedAt` from `status` when both are scalar properties. It enforces topology and event boundaries but treats scalar correctness as the enricher author's responsibility.

## Stabilization State Machine

### Phases

```text
HANDLER
NESTED_COMMAND
INTEGRATION_RECORDS
NORMALIZE_INTENT
CANDIDATE_DETECTION
AUDIT_ENRICHMENT
FINAL_DETECTION
PROVIDER_FLUSH
DOMAIN_EVENT_FRONTIER
STABLE
```

Only Handler, nested Command, and a nested Command entered from a Domain Event Handler may create application persistence changes. Audit, provider callbacks, and generic transaction callbacks have no mutation permission.

### Loop

```text
BEGIN REQUIRED
  -> install COMMAND InvocationScope
  -> create UoW Context and fixed audit context
  -> set Hibernate FlushMode.MANUAL
  -> invoke Command pipeline
  -> repeat
       -> release local reliable/outbox records into persistence context
       -> normalize root CREATE/DELETE intent
       -> expand initialized aggregate ownership
       -> detect aggregate candidate change sets
       -> run ordered audit enrichment
       -> verify audit did not change persistence topology or events
       -> detect final Hibernate dirty/action state
       -> explicit flush when persistent work exists
       -> snapshot one pending Domain Event frontier
       -> dispatch frontier fail-fast
     until no dirty state, action, reliable record, or pending event remains
  -> mark STABLE
  -> final late-mutation assertion
  -> COMMIT
  -> signal reliable workers after commit
  -> cleanup and restore flush mode/scopes
```

The final late-mutation assertion checks Hibernate dirty/action state, pending Domain Events, and unpersisted reliable records after stabilization. A change appearing after STABLE causes `LatePersistenceMutationException` and rollback. Cap4k does not expose an `onBeforeCommit`, `afterFlush`, or seal callback that can reopen mutation.

The UoW retains configurable limits for frontier rounds, synchronous events, nested Commands, and provider flushes. Overflow diagnostics include phase, causal Command/Event/Handler path, aggregate identity where available, pending counts, and last successful flush.

## Synchronous Domain Event Semantics

Domain Events use non-reentrant causal frontiers:

```text
Frontier N
  -> dispatch sibling events and handlers in unspecified order
  -> Handler may send nested Command
  -> new events enter Frontier N+1
```

Only the outer Coordinator drains frontiers. Sibling Event order, Handler order, derived sibling order, and global cross-transaction order are not promised. Synchronous dispatch fails fast on the first observed Handler failure; remaining siblings do not run and the transaction rolls back.

The immutable event payload is the historical fact. A Repository query would show current UoW state, not occurrence-time state, which is one reason Domain Event Handler scope cannot access Repository directly. A Handler sends a nested Command, whose Handler reloads and revalidates current aggregate state.

Event payloads contain required IDs, scalars, timestamps, immutable Values, and immutable collections. They do not contain Aggregate, Entity, Hibernate Proxy, or mutable Carrier references. "One Handler sends one Command" remains authoring guidance rather than a runtime count restriction.

Capability side effects cannot be rolled back. A synchronous Domain Event Handler should prefer reliable Command or Integration Event registration over irreversible external calls unless the application explicitly accepts the consistency model.

## Reliable Command And Integration Event

Reliable Command registration serializes the Command and execution-context envelope into the current local transaction. It executes only after commit in a new outer Command transaction. Registration failure rolls back the current transaction; later execution failure belongs to the reliable Command retry or terminal-failure policy and cannot roll back the origin transaction.

Integration Event writes payload, execution-context envelope, and delivery metadata to an outbox record in the current transaction. External publication never occurs before commit. Later publication failure leaves the committed outbox recoverable. Consumers have independent delivery and failure state; no total order or cross-consumer fail-fast is promised.

An after-commit wake-up is an optimization. Polling or recovery must discover a committed record when signaling fails.

## Failure Domains

Current transaction rollback failures include:

- Command validation, interceptor, or Handler failure;
- forbidden InvocationScope transition;
- candidate or final detection failure;
- audit enricher failure or topology violation;
- Strong ID or aggregate ownership violation;
- Hibernate flush or database constraint failure;
- synchronous Domain Event Handler failure;
- reliable Command or outbox serialization/persistence failure;
- loop limit or late-mutation failure;
- local transaction commit failure.

Later reliable execution or external publication failure does not roll back the origin transaction.

Query/Capability asynchronous invocation returns a failed CompletionStage for its own validation, Handler, transaction, or scheduling failure. Cancellation and timeout do not imply underlying work stopped.

## Removed Public Concepts And Surfaces

The following remain removed or are removed by this revision:

- generic public Request contracts and `Mediator.requests`;
- built-in Saga runtime, persistence, starter, console, generator, templates, and docs;
- public Command propagation choices other than REQUIRED;
- ordinary asynchronous Domain Event Handler mode;
- Client naming in favor of Capability;
- public UoW instance and lifecycle methods;
- Repository `persist` flags;
- `PersistIntent.EXISTING`;
- `AggregateLoadPlan`;
- automatic whole-aggregate expansion;
- Repository explicit detach and detached-root merge;
- coarse public persistence callbacks such as `onUpdate`;
- silent non-Hibernate fallback;
- speculative public Persistence Provider SPI.

## Generator And Ownership Direction

Checked-in Factory and Behavior ownership remains unchanged: first generation creates ordinary checked-in source and later runs use SKIP. This runtime redesign does not restore framework overwrite policies for checked-in code.

Generator vocabulary remains:

```text
command                 -> Command / CommandHandler
query                   -> Query / QueryHandler
capability              -> CapabilityCall / CapabilityHandler
client                  -> removed
saga                    -> removed
```

Command, Query, and Capability use one Handler shape each. Async methods belong to supervisors and do not create async Handler templates.

Generated aggregate metadata must continue supporting immediate Root and owned-child Strong ID allocation. Aggregate root identity needed by runtime may be derived from registered generated factories before introducing another catalog artifact.

## Verification Strategy

### Execution Context And Invocation Tests

- immutable snapshots and strict LIFO scopes;
- duplicate key/codec startup failure;
- strict reliable decode and tolerant unknown external decode;
- framework async propagation without UoW, transaction, InvocationScope, or event-state propagation;
- Caller Runs installs target scope and closes it before CompletionStage completion;
- forbidden invocation matrix transitions fail through the correct sync or async channel.

### Query And Capability Tests

- Query transaction spans validation, interceptors, Handler, lazy Repository navigation, and DTO mapping;
- Query never creates a write UoW or flushes accidental entity mutation;
- Command-to-Query and nested `askAsync()` fail;
- Query-to-Query synchronous reuse works;
- Query and Capability use separate bounded executors;
- Caller Runs preserves result and failure semantics;
- configured reject and executor shutdown return failed stages;
- Capability cannot use Repository even when invoked inline from Command.

### Repository And UoW Tests

- Repository reads have no persist/load-plan parameter and never explicitly detach;
- Command loads A and B unchanged, changes C, and only C produces SQL/audit;
- Query can use Repository and lazy navigation inside its read transaction;
- public UoW locator and facade are absent;
- Hibernate MANUAL mode is active before Command Handler queries;
- no query-triggered SQL bypasses audit enrichment;
- late mutation after STABLE rolls back.

### Aggregate Change Tests

- new Root and nested Strong IDs are available at creation time;
- existing Root lazy collection addition receives Child Strong ID and persists;
- untouched lazy collections remain unloaded;
- dirty queued collection additions and orphan removals are classified;
- owned ONE replacement creates/deletes the correct children;
- detached child/root merge paths fail;
- same child under two roots fails;
- child-only update does not advance root version by framework policy;
- root delete may load required cascade graph;
- CREATE then REMOVE before first flush folds to NONE and discards events.

### Audit And Event Tests

- clean loaded Entity is not enriched;
- candidate detection precedes enrichment and final detection follows it;
- one auditTime and ExecutionContext snapshot span all rounds;
- multiple enrichers follow Spring order;
- audit topology or event mutation fails;
- scalar audit fields persist in the same flush;
- same Entity may be idempotently enriched in later rounds;
- frontier dispatch is non-reentrant and fail-fast;
- sibling ordering is not asserted;
- current-state-dependent reaction goes through a nested Command.

### Reliable Boundary Tests

- reliable Command and Event records persist execution context separately from payload;
- retry and archive retain the original snapshot;
- existing null-context rows decode as EMPTY;
- decode failure prevents Handler execution;
- record registration rolls back with the origin transaction;
- external publication never precedes commit;
- wake-up failure remains recoverable.

## Fixed Decisions

- Command, Query, Capability, and Event are independent public concepts.
- Command alone owns automatic REQUIRED write transaction and UoW completion.
- one physical Command transaction has one UoW Context and outer Coordinator.
- Query owns a Handler-wide read-only transaction and no write UoW.
- Capability is persistence-neutral and may not access Repository or UoW.
- ExecutionContext propagates attribution; InvocationScope enforces local semantic policy.
- Query and Capability have one blocking Handler shape plus sync/async supervisor methods.
- Query/Capability executors are separate, bounded, and default to Caller Runs.
- asynchronous API failures always complete the stage exceptionally.
- generic timeout does not promise underlying cancellation.
- asynchronous Command is reliable later execution, never Caller Runs.
- application code has no UoW save, persist, remove, execute, or flush surface.
- Hibernate MANUAL flush covers the entire Command UoW.
- Repository load observes but does not enroll EXISTING persistence intent.
- Repository `persist` flags, explicit detach, `AggregateLoadPlan`, and `PersistIntent.EXISTING` are removed.
- Spring Data JPA plus Hibernate is the only persistence runtime in this iteration.
- no public Persistence Provider SPI is introduced.
- persistence changes are aggregate-organized with Entity-level detail.
- untouched lazy relations remain unloaded; changed or deleting relations may be initialized as required.
- Strong IDs are assigned at creation and verified/completed before persistence.
- detached owned children and detached root removal are unsupported.
- audit uses one UoW auditTime and ExecutionContext snapshot.
- audit enrichers are ordered, idempotent, scalar-only, and topology-guarded.
- `onCreate` and `onDeleted` are optional reflective Root callbacks; `onUpdate` is removed.
- Child changes do not automatically advance Root version.
- Domain Events are Root-originated immutable facts dispatched in non-reentrant causal frontiers.
- sibling events and Handlers have no ordering guarantee; synchronous failure is fail-fast.
- CREATE then REMOVE before first synchronization folds to NONE and discards unreleased Root events.
- ordinary Domain Events have no asynchronous Handler mode.
- built-in Saga remains removed.

## Open Implementation Details

Implementation may select without reopening this design:

- exact internal interface and package names;
- exact default Query/Capability executor sizes and queue capacities;
- exact custom overload-strategy property names;
- exact AggregateRootCatalog derivation implementation;
- exact Hibernate internal APIs used for queued collection operations and late-action detection;
- exact schema column names and serialized envelope format for execution context;
- exact transport header name and size limit;
- exact default loop limits, provided they remain bounded and configurable;
- exact migration ordering across core, JPA, reliable Command/Event, transports, and generators.

Any choice that restores a public generic Request, public flush/save, recursive event dispatch, mixed synchronous/async Domain Event Handlers, load-dependent Query transaction semantics, silent Hibernate degradation, or Handler-order dependency requires a design revision.

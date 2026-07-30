# Cap4k Application Execution And UoW Stabilization Design

Date: 2026-07-30
Status: Draft for implementation planning

## Reader Contract

This spec is the current design authority for Cap4k application execution, command transaction ownership, Unit of Work stabilization, synchronous domain-event dispatch, reliable asynchronous command registration, integration-event outbox registration, and external-capability invocation.

After reading it, an implementation agent should be able to answer these questions without relying on chat history:

- Why are Command, Query, Capability, and Event independent public concepts instead of variants of one Request abstraction?
- Why does only Command create a write transaction and automatically complete a Unit of Work?
- Why does one physical transaction have exactly one UoW Context and one outer Coordinator?
- What may a nested Command do, and what must it never complete independently?
- Why does stabilization use candidate change detection, audit enrichment, final change detection, flush, and event-frontier dispatch as separate phases?
- Why are domain events dispatched in non-reentrant causal frontiers rather than recursively from each nested Command?
- What ordering does Cap4k deliberately not promise for events and handlers?
- What does a Handler learn from an immutable event payload, and what does it learn from a Repository query?
- How do synchronous failure, asynchronous registration failure, and asynchronous execution failure differ?
- Why are local asynchronous work and cross-context publication represented by reliable Command and Integration Event rather than `@Async` domain-event handlers?
- What does explicit `flush()` guarantee, and what does it not guarantee?
- Why is built-in Saga removed, and why is no speculative Saga SPI defined by this design?

Earlier specs remain historical evidence for the slices they delivered. Where an earlier runtime or request-family assumption conflicts with this design, this spec governs the future application execution and UoW redesign.

## Summary

Cap4k currently exposes one generic request-dispatch family, a manually completed Unit of Work, event release from coarse post-persistence interception, reliable scheduling for arbitrary requests, and an in-core Saga runtime. Those mechanisms share implementation shape but do not share business semantics.

This design replaces that model with four explicit application concepts:

```text
Command      local state change, REQUIRED transaction, automatic UoW
Query        local state observation, no write UoW
Capability   consumption of a context-external capability, transaction-neutral
Event        immutable fact, with explicit synchronous or outbox ownership
```

The write path is coordinated by one transaction-level UoW Context. The outer Command Coordinator repeatedly detects changes, enriches audit state, performs final change detection, flushes through the Persistence Provider, snapshots the next synchronous Domain Event frontier, dispatches that frontier, and repeats until both persistent state and synchronous events are stable.

Nested Commands execute immediately and join the current UoW, but they do not independently flush, drain events, commit, roll back, or complete the UoW. Derived domain events enter the next causal frontier. The dispatcher is non-reentrant at the UoW level.

Reliable asynchronous Commands and Integration Events are registered atomically in the current transaction and execute or publish only after commit. Ordinary Domain Events do not mix synchronous and `@Async` handlers.

## Current Code Evidence

The future redesign starts from these current facts:

- `Mediator.requests`, `Mediator.commands`, and `Mediator.queries` currently resolve to the same `RequestSupervisor`.
- `RequestSupervisor` exposes synchronous send, reliable async, schedule, delay, and result APIs to every `RequestParam` family.
- Command and Query classification currently exists on Handler interfaces rather than on message contracts.
- generated client contracts currently implement `RequestParam`, and generated client handlers implement `RequestHandler`.
- `DefaultRequestSupervisor` special-cases `SagaParam`, proving that Saga already escapes the generic request abstraction internally.
- `UnitOfWork` currently exposes `persist`, `remove`, and mandatory-style `save(propagation)`.
- `JpaUnitOfWork.save()` owns transaction wrapping, flush, persistence listeners, coarse UoW interceptors, and event release.
- `DomainEventUnitOfWorkInterceptor` releases domain events from `postEntitiesPersisted`.
- `DefaultDomainEventSupervisor` publishes immediate local events synchronously and can be re-entered when a Handler sends a nested Command that saves again.
- `DefaultEventSubscriberManager` currently invokes subscribers in an implementation-defined sequence and aggregates failures before throwing.
- the Saga runtime already owns separate records, scheduling, process execution, compensation, JPA persistence, starter configuration, console support, generator output, and tests.

These facts are evidence of the current implementation, not constraints that the redesign must preserve.

## Goals

- Make Command, Query, Capability, and Event explicit and independent public contracts.
- Remove the generic public Request abstraction and its accidental cross-family policies.
- Make Command the only framework-owned write transaction boundary.
- Support only REQUIRED semantics in the public Command execution model.
- Automatically complete the Unit of Work at the outer Command boundary.
- Remove mandatory user calls to `UnitOfWork.save()`.
- Keep optional explicit `flush()` as an advanced database-synchronization capability.
- Give one physical transaction exactly one UoW Context and one stabilization Coordinator.
- Support immediate nested Command execution without recursive UoW completion or event draining.
- Stabilize audit state before every provider flush.
- Dispatch synchronous Domain Events in non-reentrant causal frontiers.
- Keep event and Handler sibling ordering deliberately unspecified.
- Fail fast on the first synchronous Handler failure.
- Register asynchronous Commands and Integration Events atomically with the local transaction.
- Rename application-facing external Client semantics to External Capability.
- Remove the built-in Saga runtime and its generator family.
- Keep the design provider-neutral while allowing JPA to remain the first Persistence Provider implementation.

## Non-Goals

- This spec does not design queryable Value Object persistence.
- This spec does not define the full public third-party Persistence Provider SPI.
- This spec does not replace JPA or select another ORM.
- This spec does not require child-entity changes to advance aggregate-root optimistic-lock versions.
- This spec does not guarantee occurrence-time aggregate snapshots to event Handlers.
- This spec does not guarantee event order or Handler order.
- This spec does not provide asynchronous Domain Event handlers.
- This spec does not provide a workflow engine, process manager, or Saga implementation.
- This spec does not define a speculative Saga Provider SPI without a real provider integration.
- This spec does not make Capability calls participate in a local distributed transaction.
- This spec does not promise that every mutable JVM object graph can be proven deeply immutable at runtime.
- This spec does not preserve backward compatibility. There are no external compatibility requirements for the affected API.

## Terms

### Outer Command

The first Command entering without an active Cap4k UoW Context. It owns the REQUIRED transaction, creates the UoW Context, invokes stabilization, and is the only Command allowed to finish the UoW and transaction.

### Nested Command

A Command sent while a UoW Context is already active. It executes immediately, uses the same physical transaction and UoW Context, and returns its Handler result. It does not independently stabilize, flush by default, drain events, commit, or roll back.

### UoW Context

The transaction-level state shared by the outer Command, nested Commands, synchronous Domain Event Handlers, Persistence Provider, audit enrichment, reliable Command registration, and Integration Event outbox registration.

### Stabilization

The repeated process of normalizing persistence intent, detecting candidate changes, enriching audit state, detecting final changes, flushing, snapshotting a Domain Event frontier, dispatching the frontier, and repeating until no persistent work or synchronous event remains.

### Event Frontier

An unordered snapshot of synchronous Domain Events eligible for one dispatch round. Events produced while a frontier is being dispatched enter the next frontier and never re-enter the active dispatcher.

### Capability

An application-facing contract for consuming a capability owned outside the current bounded context. The implementation may be remote, in-process, filesystem-backed, SDK-backed, or otherwise adapter-owned. Capability classification is about ownership, not network transport or read/write behavior.

### Reliable Registration

Writing an asynchronous Command record or Integration Event outbox record into the current local transaction. Registration success does not mean asynchronous execution or external publication has completed.

## Public Application Concepts

### Command

Command represents an intent to change local application state.

Target shape:

```kotlin
interface Command<R : Any>

fun interface CommandHandler<C : Command<R>, R : Any> {
    fun handle(command: C): R
}
```

Command behavior:

- enters or joins a REQUIRED transaction;
- enters or joins the current UoW Context;
- may load and modify aggregates;
- may send nested Commands;
- may call Capabilities;
- may register asynchronous Commands and Integration Events;
- returns a result without becoming a Query;
- is automatically stabilized by the outer Coordinator.

### Query

Query represents observation of local state.

Target shape:

```kotlin
interface Query<R : Any>

fun interface QueryHandler<Q : Query<R>, R : Any> {
    fun handle(query: Q): R
}
```

Query behavior:

- does not create or complete a write UoW;
- does not inherit Command transaction policy;
- does not expose reliable schedule or result APIs;
- may later receive provider-specific read-only policy without becoming a Command variant.

The framework does not claim that JPA can sandbox every managed entity mutation reached from a Query running inside an ambient Command transaction. The public contract is semantic and diagnostic, not an absolute memory-isolation guarantee.

### Capability

Capability replaces the public Client concept.

Target shape:

```kotlin
interface CapabilityCall<R : Any>

fun interface CapabilityHandler<C : CapabilityCall<R>, R : Any> {
    fun call(request: C): R
}

interface CapabilitySupervisor {
    fun <C : CapabilityCall<R>, R : Any> call(request: C): R
}
```

Target facade:

```kotlin
Mediator.capabilities.call(GetExchangeRate.Request(...))
```

Capability behavior:

- does not create, complete, suspend, or commit a local write transaction;
- may execute while an ambient Command transaction remains open;
- does not participate as a distributed transactional resource;
- does not expose generic async, schedule, delay, or result APIs;
- owns validation, Handler resolution, context propagation, telemetry, and diagnostics;
- leaves provider-specific protocol mapping, authentication, timeout, error translation, and safe retry policy in the adapter implementation;
- treats expected business-negative outcomes as response semantics and technical inability as technical failure.

The architecture concept is External Capability. Public code may use the shorter Capability name. Existing internal runtime-component terms such as `CapabilitySlot` should be renamed to Provider-oriented terminology so business Capability is not confused with starter availability.

### Event

Event represents a fact that already occurred.

This design distinguishes:

```text
Domain Event       synchronous, current UoW frontier
Integration Event  reliable outbox registration, publish after commit
```

The first implementation does not expose asynchronous Domain Event handlers. Local asynchronous work is represented by reliable asynchronous Commands.

## Removed Public Concepts

### Generic Request

The public `RequestParam`, `RequestHandler`, `RequestSupervisor`, `ReliableRequestSupervisor`, and request-wide interceptor family are removed or split into the explicit public concepts above.

Shared Handler resolution, validation, invocation, diagnostics, and observation may remain in an internal invocation kernel. Shared mechanics do not justify a shared public semantic contract.

Target facade:

```text
Mediator.commands.send(...)
Mediator.queries.ask(...)
Mediator.capabilities.call(...)
Mediator.events.publish(...)
```

There is no public `Mediator.requests` escape hatch.

### Built-In Saga

The in-core Saga runtime, JPA implementation, starter, console support, generator family, templates, public docs, and tests are removed.

Reason:

- reliable Saga support requires durable process state, lease and concurrency control, crash recovery, at-least-once semantics, step idempotency, retry and backoff, compensation, definition versioning, observability, and operational intervention;
- the current runtime offers useful pieces but creates a support promise larger than the mainline can safely maintain;
- retaining a partial Saga runtime creates false confidence;
- defining a provider SPI now would freeze assumptions from the partial runtime without evidence from a real orchestration provider.

A future orchestration integration should begin from a real provider and expose a narrow adapter around stable Command, Capability, and Event boundaries. It should not revive the removed generic Request abstraction.

## Command Transaction Policy

### REQUIRED Only

The public Command model supports REQUIRED semantics only.

```text
No active transaction
  -> outer Command creates REQUIRED transaction and UoW Context

Active Cap4k Command transaction
  -> nested Command joins current transaction and UoW Context
```

Public `REQUIRES_NEW`, `NESTED`, `NOT_SUPPORTED`, `SUPPORTS`, `MANDATORY`, and `NEVER` choices are removed from Command and UoW APIs.

If a new transaction is required, the preferred boundary is an after-commit Integration Event or a reliable asynchronous Command. The design does not retain unsupported propagation names as speculative API.

Here, "after commit" means delivery on a new thread/new outer transaction through the reliable Command provider, or publication through the Integration Event boundary. Cap4k does not promise that a synchronous `TransactionSynchronization.afterCommit` callback or `@TransactionalEventListener(AFTER_COMMIT)` listener can call `Mediator.commands.send(...)` to start a new REQUIRED Command: Spring invokes those callbacks before transaction resources are unbound, so the old physical transaction is no longer writable but is still visible to REQUIRED propagation.

### One Physical Transaction, One UoW Context

The UoW Context is created once at the outer Command boundary and cleared once after commit or rollback.

```text
Outer Command
  ├─ Nested Command
  │    ├─ Nested Command
  │    └─ Capability
  ├─ Domain Event Frontier
  │    └─ Handler -> Nested Command
  └─ final stabilization and commit
```

No nested call may create another logical UoW for the same physical transaction.

### Automatic Completion

Application code no longer calls mandatory `UnitOfWork.save()`.

Repository loads enroll aggregate roots for write tracking according to the Command path. Aggregate factories register CREATE intent. Remove operations register REMOVE intent. Managed aggregate changes are detected during stabilization.

The outer Coordinator completes the UoW automatically after the outer Handler returns.

## UoW Context Model

The exact implementation may differ, but the context must be able to represent at least:

```text
UnitOfWorkContext
├── execution phase
├── outer Command identity
├── nested Command depth and causal identity
├── tracked aggregate roots
├── repository/provider observation baseline
├── CREATE / EXISTING / REMOVE intent
├── provider dirty/change state
├── pending Domain Event attachments
├── current Event Frontier
├── next Event Frontier
├── pending asynchronous Command records
├── pending Integration Event outbox records
├── audit context
├── flush count
├── frontier round count
├── synchronous event count
├── nested Command count
└── Command -> Event -> Handler -> Command causal graph
```

Suggested phases:

```text
COMMAND_EXECUTION
CHANGE_DETECTION
AUDIT_ENRICHMENT
FLUSHING
EVENT_DISPATCH
COMMITTING
COMMITTED
ROLLED_BACK
```

The phase model is internal but diagnostics should identify illegal operations in a way that reflects the active phase.

## Aggregate Lifecycle Decisions

### Root-Oriented Enrollment

Public persistence enrollment remains aggregate-root oriented. Owned child entities participate through the root graph and Provider lifecycle classification. Application code does not persist owned children independently.

### Root-Only Domain Events

All Domain Events are attached to and released from aggregate roots. Owned child entities do not publish Domain Events independently.

### `onCreate` And `onDeleted`

Optional aggregate-root callbacks remain useful because creation and deletion do not automatically imply one universal domain fact.

- factory creation may invoke optional `onCreate` reflectively;
- remove may invoke optional `onDeleted` reflectively;
- absence of either method is valid and must not fail compilation or runtime;
- callbacks may attach zero or more Domain Events;
- callback naming remains framework convention and reflective discovery rather than generated adapter hard coupling.

### `onUpdate`

`onUpdate` is removed.

Reason:

- update is not one unambiguous domain fact;
- one transaction may mutate the same aggregate repeatedly across Commands and event frontiers;
- dirty detection and flush do not define a useful domain callback boundary;
- emitting business facts remains explicit aggregate behavior responsibility.

### Child Changes And Root Version

The first implementation does not force every owned-child change to advance aggregate-root optimistic-lock version or audit timestamp.

JPA/provider-native entity dirty tracking and optimistic locking remain sufficient for the current support level. Aggregate-wide version advancement may be revisited only with concrete concurrency evidence.

## Persistence Intent Normalization

Before change detection, the Coordinator normalizes unresolved intent for the same aggregate instance.

```text
NONE     + CREATE  -> CREATE
EXISTING + REMOVE  -> REMOVE
CREATE   + REMOVE  -> NONE, before first synchronization
REMOVE   + CREATE  -> invalid unless a future explicit recreate operation exists
```

### CREATE Then REMOVE

When a CREATE-pending aggregate is removed before its first synchronization:

- no aggregate INSERT or DELETE SQL is emitted;
- the net persistence effect is NONE;
- pending unreleased Domain Events attached to that root are discarded;
- optional `onCreate` and `onDeleted` may already have executed, but their unreleased events do not escape;
- no external observer receives a fact about an aggregate that never existed outside the transaction.

After explicit or stabilization flush has already synchronized the CREATE, a later REMOVE cannot eliminate the already executed INSERT SQL. It remains inside the same transaction and may later be followed by DELETE.

## Audit Lifecycle

Audit must not depend on a generic listener after flush. The required lifecycle is:

```text
candidate change detection
  -> audit enrichment
  -> final change detection
  -> provider flush
```

### Candidate Change Detection

The Persistence Provider identifies business changes before audit fields are modified.

Candidate changes may include:

- created roots and owned entities;
- dirty existing roots and owned entities;
- removed roots and owned entities;
- pending reliable Command records;
- pending Integration Event outbox records.

Merely loading or enrolling an existing aggregate does not make it an update candidate.

### Audit Enrichment

Audit enrichment applies only to candidates.

Typical enrichment:

```text
CREATE  -> createdAt, createdBy, tenant, initial updated fields
UPDATE  -> updatedAt, updatedBy
DELETE  -> deletedAt, deletedBy, soft-delete metadata
```

The outer UoW captures one stable audit context containing timestamp, actor, tenant, and required environment context. Repeated frontier rounds use the same audit context so audit fields do not create meaningless timestamp churn.

Audit enrichment is framework/provider lifecycle, not an unrestricted public UoW callback surface.

### Final Change Detection

After audit fields are enriched, the Provider computes the actual final change set.

Final detection excludes:

- mutations restored to the original baseline before stabilization;
- normalized NONE effects;
- clean loaded objects;
- objects already flushed in an earlier round and not modified again.

Only the final change set enters persistence planning and flush.

## Stabilization State Machine

### High-Level Flow

```text
BEGIN REQUIRED
  -> create UoW Context
  -> invoke outer Command
  -> invoke any nested Commands
  -> stabilize
       -> normalize intent
       -> detect candidates
       -> audit enrich
       -> detect final changes
       -> flush local persistence and reliable records
       -> snapshot current Domain Event frontier
       -> dispatch frontier fail-fast
       -> repeat while dirty state or pending event remains
  -> final stability check
  -> COMMIT
  -> signal reliable workers after commit
  -> clear UoW Context
```

### Stabilization Loop

Illustrative pseudocode:

```kotlin
fun executeOuterCommand(command: Command<*>): Any = requiredTransaction {
    val context = UnitOfWorkContext.create(command)

    try {
        val response = invokeCommand(command, context)

        while (true) {
            context.normalizeIntents()

            val candidates = persistence.detectCandidateChanges(context)
            audit.enrich(candidates, context.auditContext)
            val finalChanges = persistence.detectFinalChanges(context)

            if (finalChanges.isNotEmpty() || context.hasUnflushedReliableRecords()) {
                persistence.flush(finalChanges, context)
                context.advanceProviderBaseline()
            }

            val frontier = context.snapshotPendingDomainEvents()
            if (frontier.isEmpty()) {
                check(!context.hasUnflushedPersistentWork())
                break
            }

            domainEvents.dispatchFailFast(frontier, context)
            context.advanceFrontierRound()
        }

        context.verifyStable()
        commitTransaction()
        reliableWork.signalAfterCommit()
        response
    } catch (ex: Exception) {
        rollbackTransaction()
        throw ex
    } finally {
        context.clear()
    }
}
```

### Stability Condition

The UoW is stable only when all of these are true:

- no dirty persistent state remains;
- no unresolved CREATE, EXISTING, or REMOVE work remains;
- no pending Domain Event remains;
- no unflushed asynchronous Command record remains;
- no unflushed Integration Event outbox record remains;
- no active frontier remains;
- the Provider baseline matches the last successful flush.

Asynchronous work does not need to be executed or externally delivered for the local UoW to be stable. It only needs to be durably registered in the current transaction.

## Synchronous Domain Event Semantics

### Causal Frontiers

Domain Events are dispatched by causal generation rather than one global total order.

```text
Frontier 0 = events pending after current flush
  -> dispatch all events and handlers in unspecified sibling order
  -> nested Commands may create more events

Frontier 1 = events created while Frontier 0 was handled
  -> dispatch after Frontier 0 finishes
```

The only ordering guarantee is the frontier partial order:

```text
parent frontier < derived frontier
```

Cap4k does not guarantee:

- event order within one UoW flush;
- Handler order for one event;
- order among derived sibling events;
- global order across independent transactions.

An implementation may be deterministic for operational reasons, but application correctness must not rely on that incidental order.

### Non-Reentrant Dispatch

While a frontier is active, nested Commands may execute and attach new events, but those events enter the next frontier. They never recursively invoke the active dispatcher.

Only the outer Coordinator drains frontiers. Nested Command, explicit flush, audit enrichment, and Provider callbacks cannot start an independent event-drain loop.

### Historical Fact Versus Current State

Domain Event payload is the immutable historical fact. Repository access inside a Handler returns the current UoW state at the time of the query.

Cap4k does not promise that a Handler can reconstruct occurrence-time aggregate state by reloading the aggregate. The aggregate may have changed after the event was attached and before the frontier was dispatched, or an earlier unspecified Handler may have executed a nested Command.

Therefore:

- payload contains the IDs, scalars, timestamps, old/new values, and Value Objects required to understand the fact;
- payload does not contain Aggregate, Entity, persistence proxy, or mutable Carrier references;
- a `val` property pointing to a mutable entity is not deeply immutable;
- Repository queries are deliberate current-state queries;
- Commands sent by event Handlers revalidate against current state and support idempotent, reject, or no-op outcomes.

### Handler Independence

Sibling Handlers must not depend on execution order.

If reaction B must happen after reaction A, the dependency is represented explicitly:

```text
E1
  -> Handler A
      -> Command A
          -> E2
              -> Handler B
```

It is not represented by relying on Handler A being registered before Handler B.

"One Handler sends one Command" may be useful authoring guidance, but it is not a runtime restriction and does not solve occurrence-state ambiguity. A Handler may send multiple Commands when one reaction intentionally owns explicit orchestration. Artificially splitting ordered work across unordered Handlers is worse than keeping the order explicit.

### Fail-Fast

Synchronous frontier dispatch fails fast.

```text
E1
  -> H1 succeeds
  -> H2 fails
  -> H3 does not run
  -> active frontier stops
  -> next frontier is discarded
  -> local transaction rolls back
```

The framework does not aggregate and continue sibling Handler failures after the transaction is already doomed.

Because Handler order is unspecified, the subset that executed before failure is unspecified. Database effects roll back. External Capability side effects cannot be rolled back, so synchronous Domain Event Handlers should not perform irreversible external side effects unless the application explicitly accepts that consistency model.

## Asynchronous Commands

Local reliable asynchronous work uses Command records rather than asynchronous Domain Event handlers.

Target API direction:

```kotlin
val ref = Mediator.commands.enqueue(command)
val scheduled = Mediator.commands.schedule(command, executeAt)
```

`enqueue` is preferred to an ambiguous `async` name because it means durable registration, not merely another thread.

Registration behavior:

- serialize and write a reliable Command record in the current local transaction;
- capture required tenant, actor, trace, correlation, and environment context in an explicit envelope;
- do not execute before commit;
- roll back the record when the current transaction rolls back;
- signal a worker only after commit.

Execution behavior:

- worker execution creates a new outer Command;
- the Command receives its own REQUIRED transaction and UoW Context;
- its synchronous Domain Events use the same frontier and fail-fast semantics;
- later failure does not roll back the original transaction;
- retry, terminal failure, result retention, and dead-letter behavior belong to the reliable Command provider.

The built-in recovery scheduler is named `retry`, not `compense` or `compensation`: it resumes a failed reliable record and does not infer or execute a reverse business action. The same naming applies to reliable Domain Event delivery retries. Business compensation remains an explicit orchestration concern.

An asynchronous Command returns a Command reference, not an immediate business result. Result lookup may remain a reliable Command capability but is not shared with Query or Capability.

## Integration Events

Integration Event represents a fact published outside the current bounded context.

Registration behavior:

- write the event and delivery metadata into an outbox record in the current transaction;
- do not call MQ, HTTP, or another external transport before commit;
- roll back the outbox record with the local transaction;
- signal publishers after commit;
- retain polling or recovery so a failed after-commit signal does not lose the outbox record.

Delivery behavior:

- external publication failure does not roll back the already committed local transaction;
- retry and terminal delivery state belong to the Integration Event transport/provider;
- multiple asynchronous consumers have independent delivery and failure state;
- no cross-consumer fail-fast or total order is promised.

The first implementation does not support one ordinary event type with a mixture of synchronous and `@Async` Handlers. If one business fact needs both local synchronous reaction and external publication, the aggregate attaches a synchronous Domain Event and the local reaction registers a distinct Integration Event, or the aggregate/application explicitly registers both fact contracts.

## Explicit Flush

Mandatory `save()` is removed. An optional advanced `flush()` remains.

Target semantics:

```text
candidate change detection
  -> audit enrichment
  -> final change detection
  -> Provider SQL synchronization
  -> Provider baseline advancement
```

`flush()` guarantees:

- current eligible persistent changes are synchronized to the database;
- database constraints may fail at the call site;
- provider-generated values may be available;
- later database queries in the same transaction can observe synchronized state according to Provider behavior.

`flush()` does not guarantee:

- Domain Event frontier drain;
- nested Command completion beyond its Handler result;
- transaction commit;
- after-commit publication;
- asynchronous Command execution;
- Integration Event delivery.

Events created before or during explicit flush remain pending for the outer Coordinator. Explicit flush cannot be used to restore reentrant event dispatch.

## Failure Domains

### Synchronous Execution Failure

Examples:

- Command Handler failure;
- candidate/final detection failure;
- audit enrichment failure;
- Provider flush or database constraint failure;
- synchronous Domain Event Handler failure;
- reliable record serialization or local insert failure.

Result:

- stop immediately;
- roll back the current transaction;
- discard current and next frontiers;
- discard uncommitted reliable records;
- clear event attachments and UoW Context;
- return failure from the outer Command.

### Asynchronous Registration Failure

If an asynchronous Command record or Integration Event outbox record cannot be serialized or persisted in the local transaction, registration fails synchronously and the local transaction rolls back.

### Asynchronous Execution Or Delivery Failure

If a committed asynchronous Command later fails, its own transaction rolls back and its provider applies retry or terminal-failure policy. The original transaction remains committed.

If a committed Integration Event later fails to publish, the outbox record remains recoverable and the transport/provider applies retry or terminal-failure policy. The original transaction remains committed.

### Commit Failure

No asynchronous worker or external publisher may treat work as available before successful local commit. Commit failure discards the local transaction and its reliable registrations.

### After-Commit Signal Failure

After-commit signaling is an optimization. Failure to wake a worker or publisher after commit does not make the already committed Command fail. Durable polling or recovery must eventually find the record.

## Diagnostics And Loop Protection

Protection belongs to the transaction-level UoW Context, not a recursive function argument.

The implementation must track configurable limits for at least:

- maximum frontier rounds;
- maximum synchronous Domain Events;
- maximum nested Commands;
- maximum Provider flushes.

On overflow, diagnostics include the causal path:

```text
CreateOrderCommand
  -> OrderCreated
  -> ReserveInventoryHandler
  -> ReserveInventoryCommand
  -> InventoryReserved
  -> ConfirmOrderHandler
  -> ConfirmOrderCommand
  -> OrderConfirmed
```

Diagnostics should also report:

- active UoW phase;
- frontier number;
- source aggregate identity when available;
- Handler identity;
- pending event and reliable-record counts;
- last successful flush;
- whether the transaction is rollback-only.

## Internal Invocation Reuse

Command, Query, and Capability may reuse internal infrastructure:

```text
resolve Handler
validate input
create invocation context
run category-specific interceptors
invoke
observe diagnostics
```

The internal kernel must preserve the category discriminator. It must not expose a public common Request marker merely to simplify generics.

Category-specific policy remains isolated:

```text
Command     transaction, UoW, audit, synchronous event stabilization
Query       read policy, query diagnostics, possible cache/projection support
Capability  context propagation, telemetry, protocol/resilience adapters
```

## Persistence Provider Boundary

This design intentionally describes runtime phases in provider-neutral terms:

- observe/enroll aggregate roots;
- detect candidate changes;
- enrich provider/audit state;
- detect final changes;
- plan persistence operations;
- flush;
- advance provider baseline;
- expose diagnostics.

JPA remains the first implementation. The redesign must not leak Hibernate-specific state into Command, Query, Capability, event, or audit public contracts.

This spec does not define the complete public third-party Persistence Provider SPI. That SPI should be designed from the stabilized runtime phases and at least one real alternate provider or integration need, rather than by wrapping the current `JpaUnitOfWork` class.

## Generator And Ownership Direction

Checked-in generator ownership remains unchanged in principle: first generation creates ordinary repository source and later runs use SKIP.

Future generator/runtime alignment:

```text
command                 -> Command / CommandHandler
query                   -> Query / QueryHandler
client                  -> removed
client-handler          -> removed
capability              -> CapabilityCall
capability-handler      -> CapabilityHandler
saga                    -> removed
```

Recommended paths:

```text
application/commands
application/queries
application/capabilities
adapter/application/capabilities
```

The application Capability contract uses internal business language. The adapter Capability Handler owns protocol conversion, authentication, provider DTO mapping, timeout, and technical error translation.

Canonical generator models may share private field or type-rendering structures, but Command, Query, and Capability are independent semantic models rather than values of one public Request kind.

## Breaking API Direction

Expected breaking changes include:

- remove public generic `RequestParam` and `RequestHandler` usage from generated Command, Query, and Capability code;
- replace Handler-category interfaces named `Command` and `Query` with message markers plus `CommandHandler` and `QueryHandler`;
- replace `Mediator.requests` aliases with real category-specific supervisors;
- move reliable async/schedule/result APIs to Command-only ownership;
- remove `UnitOfWork.save(propagation)`;
- add optional `UnitOfWork.flush()` or equivalent advanced facade;
- remove public propagation parameters;
- replace coarse public `UnitOfWorkInterceptor` lifecycle with explicit internal/provider phases;
- replace immediate nested event release with UoW-owned frontier queues;
- change synchronous subscriber failure from aggregate-and-continue to fail-fast;
- rename Client generator/runtime concepts to Capability;
- rename internal starter-availability `CapabilitySlot` terminology to Provider terminology;
- remove Saga core, persistence, starter, console, generator, templates, tests, and public docs.

Exact package migration and class names belong to the implementation plan, but no compatibility layer should preserve the semantic problems this design removes.

## Verification Strategy

### Command Boundary Tests

- outer Command creates one REQUIRED transaction and one UoW Context;
- nested Command reuses both;
- nested Command does not independently commit or drain events;
- outer Command returns only after stabilization and commit;
- Query and Capability do not create a write UoW.

### Audit Tests

- clean loaded aggregate does not receive update audit fields;
- business dirty state becomes a candidate before audit enrichment;
- audit enrichment participates in final change detection;
- repeated frontiers reuse one audit context;
- child-entity audit can occur without forced root version advancement.

### Frontier Tests

- events created in one flush form one frontier snapshot;
- events created by a Handler enter the next frontier;
- nested Command cannot re-enter the active dispatcher;
- no test relies on sibling event or Handler order;
- first Handler failure stops the frontier and rolls back;
- causal limits detect Event -> Command -> Event loops across frontiers.

### Historical Fact Tests

- event payload rejects or diagnoses Aggregate/Entity references;
- immutable IDs, scalars, timestamps, Value Objects, and immutable collections are accepted;
- a Handler query observes current UoW state rather than a promised historical snapshot;
- an order-dependent reaction is represented through a derived event rather than Handler registration order.

### Reliable Work Tests

- async Command registration rolls back with the originating transaction;
- committed async Command starts a new outer REQUIRED transaction;
- async Command failure does not roll back the originating transaction;
- Integration Event outbox registration rolls back with the local transaction;
- external publish never happens before commit;
- after-commit signal failure remains recoverable through durable polling.

### Explicit Flush Tests

- flush performs candidate detection, audit enrichment, final detection, and SQL synchronization;
- flush advances Provider baseline;
- flush does not drain Domain Events;
- flush does not commit;
- CREATE then REMOVE before first synchronization folds to NONE;
- CREATE then explicit flush then REMOVE performs synchronized INSERT and later DELETE within the transaction.

### Removal Tests

- generic Request APIs no longer exist in production code;
- Saga modules and runtime APIs are absent;
- client and `*Cli` generator terminology is absent from active generator/runtime contracts;
- archived historical evidence may continue to mention removed paths without becoming active runtime documentation.

## Implementation Slices

Recommended sequencing:

1. Introduce explicit Command, Query, and Capability message and Handler contracts plus category-specific supervisors.
2. Introduce the outer Command Coordinator and transaction-level UoW Context while keeping current persistence behavior behind an adapter.
3. Move candidate/final detection and audit enrichment into explicit stabilization phases.
4. Replace recursive domain-event release with current/next frontier queues and fail-fast dispatch.
5. Move reliable request persistence to Command-only enqueue/schedule ownership.
6. Enforce Integration Event outbox commit boundary.
7. Remove generic Request APIs and compatibility aliases.
8. Rename Client generator/runtime concepts to Capability.
9. Remove Saga runtime, modules, generator, templates, tests, and active docs.
10. Replace coarse UoW interceptors and remove obsolete transaction propagation API.

Each slice must preserve focused runtime fixtures. Do not combine all removals and lifecycle changes into one unverified mechanical rewrite.

## Fixed Decisions

- Command, Query, Capability, and Event are independent public concepts.
- There is no public generic Request abstraction.
- Command alone owns automatic REQUIRED transaction and UoW completion.
- Public propagation supports REQUIRED only.
- One physical transaction has one UoW Context and one outer Coordinator.
- Nested Commands execute immediately but do not independently stabilize or drain events.
- Mandatory user `save()` is removed.
- Optional explicit flush synchronizes persistence but never drains events or commits.
- Audit uses candidate detection, enrichment, and final detection before flush.
- Domain Events are root-originated immutable facts.
- Domain Events use non-reentrant causal frontiers.
- Sibling events and Handlers have no ordering guarantee.
- Derived events enter the next frontier.
- Synchronous Handler failure is fail-fast.
- Repository queries in Handlers return current UoW state, not event-time snapshots.
- Event payloads do not contain Aggregate or Entity references.
- One Handler/one Command is guidance, not a hard limit.
- Local async work uses reliable asynchronous Commands.
- Integration Events use transactional outbox registration and publish after commit.
- ordinary Domain Events do not mix synchronous and `@Async` Handlers.
- the first implementation has no separate Async Domain Event category.
- CREATE then REMOVE before first synchronization folds to NONE and discards unreleased root events.
- `onCreate` and `onDeleted` remain optional reflective root callbacks; `onUpdate` is removed.
- child changes do not automatically advance root version in the first implementation.
- Client is renamed to External Capability, shortened to Capability in code.
- built-in Saga is removed.
- no speculative Saga Provider SPI is defined.
- JPA remains the first Persistence Provider implementation, but public execution semantics remain provider-neutral.

## Open Implementation Details

These details may be selected during implementation without reopening the architecture:

- exact public package names;
- exact Command reference and reliable result types;
- exact UoW Context internal classes;
- concrete default limits for rounds, events, Commands, and flushes;
- best-effort runtime validation strategy for deep event immutability;
- exact JPA dirty-inspection and baseline implementation;
- exact after-commit worker wake-up mechanism;
- exact migration sequencing across starters and generator modules.

Any implementation choice that reintroduces nested UoW completion, recursive event draining, mixed Request policies, pre-commit external publication, or Handler-order dependency is not an implementation detail and requires revision of this spec.

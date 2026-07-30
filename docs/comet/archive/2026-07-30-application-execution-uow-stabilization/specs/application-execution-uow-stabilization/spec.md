# Application Execution And UoW Stabilization

## Public application categories

Cap4k shall expose Command, Query, Capability, and Event as independent public application concepts. It shall not expose a generic public Request marker, Handler, supervisor, interceptor contract, or `Mediator.requests` escape hatch that makes the categories inherit one policy family.

Shared Handler discovery, validation, invocation, diagnostics, and observation may remain internal, but every invocation shall retain its category and apply only category-appropriate policy.

### Command

A Command represents an intent to change local application state. Sending an outer Command shall create a REQUIRED transaction and a transaction-level UoW Context when none is active. Sending a nested Command shall immediately invoke it in the current transaction and UoW Context.

Only the outer Command Coordinator may stabilize the UoW, finish the physical transaction, and clear the Context. A nested Command shall not independently commit, roll back, or drain Domain Events. It shall return its Handler result to its caller.

Public Command and UoW APIs shall not expose alternative transaction propagation choices. A caller that requires a separate transaction shall use a reliable asynchronous Command or an after-commit boundary.

### Query

A Query represents observation of local state. Query execution shall not create or automatically complete a write UoW and shall not inherit Command transaction, reliable scheduling, or result-retention APIs.

When a Query runs inside an ambient Command transaction, the framework does not promise memory isolation from the current managed persistence context. This limitation shall not redefine Query as a Command variant.

### Capability

Capability represents consumption of behavior owned outside the current bounded context. Public code may use the name Capability while architecture documentation may say External Capability.

A Capability call shall not create, suspend, commit, or enlist a local distributed transaction. It may execute while an ambient Command transaction remains open. Capability infrastructure may own validation, Handler resolution, context propagation, telemetry, and diagnostics; protocol mapping, authentication, timeout, technical error translation, and safe retry remain adapter/provider concerns.

Existing generated Client and client-handler semantics shall migrate to Capability contracts and handlers. Internal starter-availability terminology such as `CapabilitySlot` shall migrate to Provider-oriented terminology to avoid ambiguity.

### Event

A Domain Event represents a local immutable fact dispatched synchronously inside the current UoW. An Integration Event represents a fact durably registered in the local outbox and published outside the bounded context after commit.

The first implementation shall not support one ordinary Domain Event type with a mixture of synchronous and `@Async` Handlers. Local asynchronous work shall use reliable asynchronous Commands.

## Unit of Work ownership

One physical transaction shall have exactly one Cap4k UoW Context and one outer Coordinator. The Context shall be shared by the outer Command, nested Commands, synchronous Domain Event Handlers, repositories, Persistence Provider, audit enrichment, asynchronous Command registration, and Integration Event outbox registration.

Application code shall not be required to call `UnitOfWork.save()` to complete persistence. Repository loads and aggregate factories shall enroll roots and persistence intent; remove operations shall register REMOVE intent; the outer Coordinator shall automatically stabilize after the outer Command Handler returns.

The UoW Context shall retain enough state to diagnose its phase, outer and nested Commands, tracked roots, persistence intent, provider baselines, dirty state, current and next event frontiers, reliable registrations, audit context, round counts, and Command/Event/Handler causality.

Only the outer Coordinator may transition the UoW to commit or rollback. Provider callbacks, audit enrichment, nested Commands, and explicit flush shall not begin an independent UoW-completion loop.

## Aggregate lifecycle

Public persistence enrollment shall remain aggregate-root oriented. Owned child entities participate through the aggregate graph and Provider lifecycle classification and shall not be persisted independently by application code.

Domain Events shall be attached to aggregate roots. Owned child entities shall not independently publish Domain Events.

Aggregate roots may define convention-named `onCreate` and `onDeleted` callbacks. Factory creation and remove operations may discover and invoke them reflectively. Absence of either callback shall be valid. These callbacks may attach zero or more Domain Events.

The framework shall not require a generated adapter that makes empty lifecycle methods a compilation requirement. It shall remove `onUpdate`, because persistence dirty detection and repeated flushes do not define one unambiguous domain-fact boundary.

The first implementation shall not force aggregate-root optimistic-lock version or audit timestamp advancement solely because an owned child changed. Provider-native entity dirty tracking and optimistic locking remain the initial support level.

## Persistence intent and audit lifecycle

Before change detection, the Coordinator shall normalize persistence intent for the same aggregate instance. CREATE followed by REMOVE before first synchronization shall become a NONE persistence effect. It shall emit no aggregate INSERT or DELETE, and unreleased Domain Events attached to that root shall be discarded.

If CREATE has already been synchronized by explicit or stabilization flush, a later REMOVE shall remain a real DELETE operation inside the same transaction; the already executed INSERT cannot be retroactively eliminated.

Every provider synchronization round shall use the following phases:

1. normalize unresolved persistence intent;
2. detect candidate business changes;
3. enrich audit state for candidates using one stable transaction-level audit context;
4. detect the actual final changes after enrichment;
5. plan and flush final persistence operations and reliable records;
6. advance the provider baseline.

Loading or enrolling a clean aggregate shall not by itself make it an update candidate. Final detection shall exclude state restored to its baseline, normalized NONE effects, clean loaded objects, and objects already flushed and not modified again.

Audit enrichment shall be framework/provider lifecycle, not an unrestricted public callback surface. Repeated stabilization rounds shall reuse the same timestamp, actor, tenant, and environment audit context so the framework does not create meaningless audit churn.

## Stabilization and event frontiers

After the outer Command Handler returns, the Coordinator shall repeat persistence synchronization and synchronous event dispatch until the UoW is stable.

A stabilization round shall normalize intent, perform candidate detection, audit enrichment and final detection, flush eligible work, snapshot pending Domain Events as one current frontier, dispatch that frontier, and then repeat if persistent state or events remain.

Domain Event dispatch shall be non-reentrant. While one frontier is active, nested Commands and Handlers may attach new events, but those events shall enter the next frontier. They shall not recursively invoke the active dispatcher. Only the outer Coordinator shall drain frontiers.

Cap4k shall guarantee only causal frontier order: a parent frontier finishes before its derived frontier. It shall not guarantee event order inside a frontier, Handler order for one event, order among derived sibling events, or global order across transactions. An implementation may be deterministic, but application correctness shall not depend on that incidental order.

If one reaction must happen after another, the dependency shall be represented through an explicit derived fact or explicit orchestration, not registration order. A Handler may send more than one Command; one Handler/one Command is guidance rather than a runtime restriction.

Synchronous dispatch shall fail fast. The first Handler failure shall stop the active frontier, discard later frontiers, and roll back the local transaction. Because Handler order is unspecified, the subset invoked before failure is unspecified. Database effects shall roll back; irreversible external Capability effects cannot be rolled back and require explicit application acceptance.

The UoW shall be stable only when no dirty persistent state, unresolved persistence intent, pending Domain Event, unflushed asynchronous Command record, unflushed Integration Event outbox record, or active frontier remains and the provider baseline matches the last successful flush.

The Context shall enforce configurable transaction-level limits for frontier rounds, synchronous event count, nested Command count, and provider flush count. Overflow diagnostics shall include the active phase and causal Command/Event/Handler path.

## Historical facts and current state

A Domain Event payload shall be the immutable historical fact. It shall carry the identifiers, scalars, timestamps, old/new values, Value Objects, and immutable collections required to understand the occurrence.

A Domain Event payload shall not carry Aggregate, Entity, persistence proxy, or mutable persistence Carrier references. A read-only property that points to a mutable entity does not satisfy this constraint.

A Repository query inside a Handler shall return current UoW state at query time. Cap4k shall not promise that reloading an aggregate reconstructs occurrence-time state. Commands sent by Handlers shall revalidate current state and support explicit idempotent, reject, or no-op outcomes.

## Reliable asynchronous Commands

Command-only APIs may durably enqueue or schedule asynchronous Commands. Registration shall serialize the Command and its required tenant, actor, trace, correlation, and environment envelope into the current local transaction.

The record shall not execute before commit and shall roll back with the originating transaction. Workers shall be signaled only after commit, and durable polling or recovery shall handle failed wake-up signals.

Worker execution shall create a new outer Command with its own REQUIRED transaction and UoW Context. Later failure shall roll back that new transaction without rolling back the originating transaction. Retry, terminal failure, retained result, and dead-letter behavior belong to the reliable Command provider.

Reliable Command and reliable Domain Event recovery schedulers shall use retry terminology. They shall not expose `compense` or `compensation` names for merely resuming a failed record, because record retry does not imply a reverse business action.

An enqueue API shall return a Command reference rather than an immediate business result. Reliable result lookup, if retained, shall belong to Command rather than Query or Capability.

## Integration Events

Integration Event publication shall use an outbox record written in the current local transaction. The framework shall not call an external MQ, HTTP endpoint, or transport before commit. Rollback shall remove the registration.

After commit, a publisher may deliver the record. Failed delivery shall not roll back the already committed local transaction. The transport/provider shall own retry and terminal delivery state, and durable recovery shall find records when after-commit signaling fails.

Asynchronous consumers shall have independent delivery and failure state. Cap4k shall not promise cross-consumer fail-fast or total ordering.

## Explicit flush

The mandatory completion-oriented `save()` API shall be removed. An optional advanced `flush()` may remain for database synchronization.

Explicit flush shall perform candidate detection, audit enrichment, final detection, Provider synchronization, and provider-baseline advancement. It may expose constraint failures and provider-generated values at the call site.

Explicit flush shall not drain Domain Events, complete nested Commands beyond their Handler result, commit the transaction, run asynchronous Commands, publish Integration Events, or perform after-commit signaling. Events created before or during flush shall remain pending for the outer Coordinator.

## Removed Saga support

The built-in Saga core/runtime, JPA persistence, starter, console support, generator family, templates, tests, and active documentation shall be removed.

Cap4k shall not define a speculative Saga Provider SPI as part of this redesign. A future orchestration integration shall begin from a real provider and adapt around stable Command, Capability, and Event boundaries rather than restoring generic Request semantics.

## Persistence Provider boundary

Public execution semantics shall use provider-neutral phases: enroll/observe roots, detect candidate changes, enrich audit/provider state, detect final changes, plan persistence, flush, advance baseline, and expose diagnostics.

JPA shall remain the first implementation. Hibernate-specific state shall not enter public Command, Query, Capability, Event, or audit contracts.

This capability shall not define the complete third-party Persistence Provider SPI. That SPI shall be designed only after the runtime phases are stable and a real alternate-provider or integration requirement supplies evidence.

## Generator ownership

Checked-in generated source shall retain first-generation ownership followed by SKIP. This capability shall not introduce overwrite, merge, patch, or regeneration promises for checked-in factories, behaviors, Commands, Queries, Capabilities, or other user-owned source.

Generator semantics shall migrate Command, Query, and Capability to their independent contracts, remove Client and Saga families, and avoid recreating a generic public Request model merely because renderers share internal field/type structures.

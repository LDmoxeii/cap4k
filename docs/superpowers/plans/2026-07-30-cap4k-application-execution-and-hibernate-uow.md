# Cap4k Application Execution And Hibernate UoW Implementation Plan

Date: 2026-07-30

Status: Implementation in progress

Design authority: [2026-07-30-cap4k-application-execution-and-uow-stabilization-design.md](../specs/2026-07-30-cap4k-application-execution-and-uow-stabilization-design.md)

## Purpose

This plan turns the approved application-execution and Hibernate UoW design into reviewable implementation slices. It starts from current `origin/master` after the first execution-model reset and covers only the remaining delta.

Issue #115 is closed and remains scoped to owned-child factory creation. Issue #19 remains an investigation of backend alternatives. Neither issue owns this implementation, and this implementation does not create or reopen an issue.

## Principles

- No compatibility layer is required. Delete obsolete surfaces instead of retaining deprecated aliases.
- Keep Command, Query, Capability, and Event independent; do not reintroduce generic Request.
- Land semantic scopes before persistence code begins relying on them.
- Remove application UoW control before deepening Hibernate internals.
- Use Spring Data JPA plus Hibernate directly and fail when required Hibernate integration is unavailable.
- Do not introduce provider capabilities, a public Persistence Provider SPI, or a speculative module split.
- Each slice must compile and pass focused owner-module tests.

## Current Delta

Current `master` already has independent application contracts, REQUIRED Command UoW execution, nested-Command UoW reuse, non-reentrant synchronous event frontiers, fail-fast dispatch, reliable asynchronous Command ownership, audit phases, and no built-in Saga.

The remaining work is:

- typed `ExecutionContext` and local `InvocationScope`;
- Query/Capability async supervisor methods and bounded executors;
- Handler-wide read-only Query transactions;
- removal of public UoW lifecycle control;
- removal of Repository persistence flags, `PersistIntent.EXISTING`, and `AggregateLoadPlan`;
- Command-wide Hibernate MANUAL flush;
- aggregate-oriented Hibernate change and ownership tracking;
- revised Strong ID, delete, audit, and late-mutation semantics;
- durable execution-context propagation;
- final generator, sample, and documentation cleanup.

## Implementation Sequence

| Phase | Scope | Outcome |
| --- | --- | --- |
| 1 | ExecutionContext and InvocationScope | typed attribution and local semantic guards |
| 2 | Query/Capability execution | read-only Query transaction and bounded async composition |
| 3 | Public UoW and Repository cleanup | no application save/flush or EXISTING enrollment |
| 4 | Hibernate aggregate stabilization and audit | aggregate changes, IDs, delete, audit, final seal |
| 5 | Reliable and transport propagation | versioned context across durable boundaries |
| 6 | Generator and documentation cleanup | one final vocabulary without stale examples |

The phases are implementation and verification boundaries inside one working branch. They may land together, but each owner module still needs focused evidence before the final repository-wide verification.

## Phase 0: Baseline Fixtures

### Tasks

1. Record focused test baselines for `ddd-core`, `ddd-domain-repo-jpa`, `cap4k-ddd-core-starter`, `cap4k-ddd-jpa-starter`, and the current reliable Command/Event and Integration Event owner modules.
2. Establish small fixtures for unchanged root observation, root scalar update, child-only update, owned ONE replacement, owned MANY addition/orphan removal, lazy collection mutation, root CREATE then REMOVE, nested Command from an event, and late mutation.
3. Keep fixtures in capability-owner modules; do not restore the removed monolithic starter fixture.
4. Confirm tests run with the supported Hibernate version and make version/runtime mismatch diagnostic.

### Exit Criteria

- Existing focused tests pass before production edits.
- Each high-risk Hibernate semantic has a named fixture owner.

## Phase 1: ExecutionContext And InvocationScope

### Contracts

Add focused `ddd-core` contracts for typed keys/elements, immutable snapshots, access/scope management, versioned codecs, context propagation, and:

```kotlin
enum class InvocationKind {
    COMMAND,
    QUERY,
    CAPABILITY,
    DOMAIN_EVENT_HANDLER,
}
```

The snapshot builder rejects duplicate insertion unless explicit replacement is requested. Codec registration rejects duplicate wire names and incompatible key types at startup.

### Runtime

1. Implement ExecutionContext and InvocationScope as separate strict ThreadLocal stacks with LIFO close validation; do not use `InheritableThreadLocal`.
2. Provide explicit wrappers for `Runnable`, `Callable`, and `Executor`.
3. Propagate only `ExecutionContextSnapshot`, never UoW, transaction, EntityManager, InvocationScope, event state, or arbitrary ThreadLocals.
4. Close installed scopes before completing an asynchronous stage.
5. Support codec boundaries `RELIABLE_COMMAND`, `RELIABLE_DOMAIN_EVENT`, `INTEGRATION_EVENT`, and `RPC`.
6. Use strict reliable decode. External ingress may ignore unknown names but must reject malformed known, duplicate, unsupported-version, and disallowed elements.
7. Do not relay unknown opaque elements.

### Invocation Matrix

| Current scope | Command | Query | Capability |
| --- | --- | --- | --- |
| none | allowed | allowed | allowed |
| Command | nested synchronous allowed | forbidden | allowed |
| Query | forbidden | synchronous nested allowed | allowed |
| Capability | forbidden | forbidden | composition allowed |
| Domain Event Handler | nested Command allowed | forbidden | allowed |

Install scope around validation, interceptors, and Handler invocation. Add one policy service instead of duplicating rules in supervisors. Repository/Factory enforcement follows in Phase 3.

### Tests And Exit

- Test immutable snapshots, duplicate registration, strict LIFO behavior, transition matrix, restoration after failure, external/reliable decode differences, and propagation without unrelated state.
- Verify continuations cannot observe a scope after CompletionStage completion.
- Every policy failure must identify current and requested invocation kinds.

## Phase 2: Query And Capability Execution

### Public APIs

Extend the existing supervisors with `askAsync()` and `callAsync()` returning `CompletionStage`. Retain one blocking `QueryHandler` and one blocking `CapabilityHandler`; do not add async Handler types.

### QueryExecution

1. Add a JPA-owned REQUIRED read-only boundary spanning validation, interceptors, Handler execution, Repository lazy navigation, and DTO mapping.
2. Use Hibernate read-only mode and MANUAL flush.
3. Create no write UoW, audit, event drain, or provider flush.
4. Reuse active QueryExecution for synchronous nested `ask()`.
5. Reject Command-to-Query and nested Query `askAsync()`.
6. Document DTO/Value results; do not add runtime graph copying.

### Bounded Async Dispatcher

Use one internal implementation with separate Query and Capability executors:

- bounded workers and queue;
- category-specific replaceable configuration;
- default `CALLER_RUNS`, optional `REJECT`;
- custom overload logic rather than raw JDK CallerRunsPolicy, so shutdown never silently drops work;
- overload, shutdown, scheduling, validation, transaction, and Handler failures complete the stage exceptionally;
- sync APIs keep throwing original exceptions directly;
- timeouts stop waiting but do not promise cancellation of SQL/RPC/Handler work.

Caller Runs executes the same wrapped task. It installs the target scope above the caller scope, so Capability remains persistence-neutral even on a Command thread. Query remains forbidden from Command.

### Capability Rules

Capability starts no persistence transaction and may not use Repository, Factory, UoW, Command, or Query. Composition with another Capability is allowed. Command and Query may call Capability. The first version does not track or auto-join dropped stages.

### Tests And Exit

- Prove the Query transaction covers lazy navigation and mapping, while accidental mutation is not flushed.
- Test sync Query nesting, forbidden transitions, category isolation, saturation, Caller Runs, configured rejection, executor shutdown, and context restoration.
- Prove Capability cannot reach persistence even when executed inline on a Command thread.
- Async API outcome must be semantically identical whether a worker or caller executes it.

## Phase 3: Public UoW And Repository Cleanup

### UoW Surface

Remove `UnitOfWork.instance`, `Mediator.uow`, and application-facing `execute`, `persist`, `remove`, and `flush`. Replace cross-module use with narrow internal coordinator/intent interfaces. Bytecode-public internal types must have no static locator, must not be exposed by `Mediator`, and are not a third-party SPI.

### Repository Surface

1. Remove `persist: Boolean`, `PersistIntent.EXISTING`, `AggregateLoadPlan`, and JPA load-plan support.
2. Remove explicit detach and read-triggered merge behavior.
3. Command reads keep roots managed and record observation only.
4. Query reads remain managed only for the read transaction and do not join a write UoW.
5. Outside framework transaction boundaries, normal JPA detachment applies when the method transaction closes.
6. Write/remove operations are aggregate-root-only; child changes occur through owned relations.
7. Derive the initial `AggregateRootCatalog` from registered generated factories.
8. Enforce top InvocationScope: read Repository from Command/Query, Factory and write Repository from Command, and no Repository from Capability or Domain Event Handler.

Update production calls, starter configuration, Java/Kotlin tests, templates, examples, public docs, and skills. Delete aliases and obsolete overloads.

### Tests And Exit

- Public UoW locator/lifecycle methods and Repository persistence/load-plan parameters are absent.
- Reading A and B then changing C produces SQL/audit only for C.
- Query can lazily navigate only inside Handler transaction.
- Capability/Event Handler Repository access and detached root/child persistence fail.
- Existing immediate root/child Strong ID behavior survives `AggregateLoadPlan` deletion.

## Phase 4: Hibernate Aggregate Stabilization And Audit

### Flush Ownership

1. Unwrap Hibernate Session at outer Command entry and fail clearly if required integration is unavailable.
2. Save the previous flush mode, set MANUAL before Handler execution, keep it through nested Commands and event frontiers, and restore it during cleanup.
3. Only the provider phase calls `EntityManager.flush()`.
4. Document that Handler queries need not see unflushed in-memory changes.
5. Treat direct EntityManager flush, bulk DML, native SQL, and custom transaction synchronization as unsupported bypasses.

### Change Tracker And Ownership

Extract a focused internal JPA/Hibernate helper rather than exposing a provider abstraction. It inspects persistence-context `EntityEntry`, loaded/dirty state, `CollectionEntry`, queued collection operations, orphan/cascade state, and `ActionQueue`. Never silently downgrade after an unwrap or Hibernate integration failure.

Track per UoW:

- observed roots and explicit root CREATE/DELETE intent;
- Entity instance to root ownership;
- lifecycle callback state;
- unreleased root event cursors.

Traverse initialized owned relations only during ordinary detection. Dirty or queued collection state must still be inspected. Initialize more graph only when a changed relation or root delete requires it. Fail shared ownership and any changed aggregate-owned Entity reached through supported root observation that cannot be assigned to one tracked root. Exclude Cap4k infrastructure records sharing the Session from aggregate audit; direct EntityManager writes remain an unsupported bypass.

### Aggregate Net Changes

Produce an outer aggregate change set with root operation `NONE`, `CREATE`, or `DELETE`, plus Entity detail `CREATE`, `UPDATE`, or `DELETE`.

Normalize net effects:

- child CREATE then DELETE before synchronization becomes NONE;
- existing child UPDATE then DELETE becomes DELETE;
- remove/re-add becomes NONE when final provider state matches baseline;
- child-only mutation creates an aggregate change set but does not dirty/version the root by framework policy.

### Strong IDs And Delete

1. Preserve immediate root ID assignment in Factory and immediate child assignment in Factory/`OwnedEntityList`.
2. Complete missing supported IDs at stabilization only as a safety net.
3. Do not treat non-null application ID or `EntityInformation.isNew()` alone as existing-state proof.
4. Fail managed ID clearing/change, detached child attachment, independent child merge, and detached root removal.
5. Model child movement as old-root deletion plus new child creation.
6. Keep optional reflective root `onCreate`/`onDeleted`; missing methods are valid, `onDeleted` runs at most once, and `onUpdate` stays removed.
7. Infer child deletion from owned relations/orphans, never from a child UoW remove call.
8. Fold CREATE then REMOVE before first synchronization to NONE and discard unreleased root events. After an earlier flush, preserve INSERT then DELETE.

### Audit

Replace direct time lookup and an untyped attributes map with one outer-UoW context:

```kotlin
data class JpaPersistenceAuditContext(
    val auditTime: Instant,
    val executionContext: ExecutionContextSnapshot,
)
```

Inject `Clock` and `ExecutionContextAccessor`, capture once, and reuse across all rounds. `Instant` is the framework semantic; storage/format remains application mapping.

Pass aggregate change sets to all enrichers sequentially by Spring order. Enrichers must be idempotent and may change supplied scalar or embedded audit values only. Snapshot persistent topology and event cursors before enrichment, then fail Entity/relation topology changes, Command/event production, or persistence lifecycle calls. Without an audit-field registry, scalar-field correctness remains the enricher author's responsibility.

Keep the exact order:

```text
candidate detection
  -> ordered audit enrichment
  -> topology guard
  -> final Hibernate detection
  -> explicit flush
```

Clean loaded aggregates are not candidates and receive no audit update.

### Stabilization And Events

Run bounded rounds:

```text
release reliable/outbox records
  -> normalize root intent and ownership
  -> detect candidates
  -> enrich audit and verify topology
  -> final detection and flush
  -> dispatch one non-reentrant event frontier
  -> repeat until stable
```

Only the outer Coordinator drains frontiers and finishes the transaction. After STABLE, assert no Hibernate dirty/action state, pending Domain Event, or unpersisted reliable record remains. Late mutation fails and rolls back. Do not expose seal, before-commit, after-flush, or generic UoW callbacks.

### Tests And Exit

- Prove MANUAL flush begins before Handler queries and prevents early SQL.
- Cover clean/dirty root, child-only change, lazy queued add/orphan, owned ONE replacement, application/provider IDs, ID mutation, ownership ambiguity, and CREATE/DELETE net effects.
- Verify one fixed audit time/context, ordered enrichers, no audit on clean roots, scalar persistence, topology violations, and later-round idempotence.
- Verify frontiers can create later rounds and that loop limits/late mutations include causal diagnostics.
- Acceptance: every changed owned Entity maps to one root, untouched lazy relations remain unloaded, and supported persistent SQL cannot bypass audit stabilization.

## Phase 5: Reliable And Transport Context

### Reliable Boundaries

1. Capture encoded context when a reliable Command or Domain Event is registered.
2. Store payload and context separately.
3. Preserve the original context unchanged across retry and archive.
4. Treat existing null-context records as EMPTY.
5. Decode/install context before worker transaction and Handler scope setup; strict decode failure prevents execution.

### Integration Event

Persist payload, context, and delivery metadata separately in the outbox. Capture at attachment time, encode only allowed `INTEGRATION_EVENT` elements, and publish only after commit. Unknown external elements may be ignored on ingress; malformed known elements fail. Polling/recovery must still find records when after-commit wake-up fails.

### RPC

Add mapping only to concrete RPC integrations present when this phase begins; do not invent a generic transport framework. Client capture occurs before offload. Server authentication precedes trusted context installation, which precedes Command/Query transaction setup. Response context is not merged into the caller.

### Schema And Tests

- Add nullable versioned context storage using each owner module's existing migration pattern.
- Apply envelope size limits and ensure archive/copy logic retains context without recapturing worker attribution.
- Test separate storage, null compatibility, strict known-element failures, origin-transaction rollback, new worker Command transaction, post-commit publication, retry/archive identity, and context cleanup.

Acceptance: every supported durable boundary either propagates through an explicit codec boundary or explicitly documents no context support; no retry replaces origin attribution with worker context.

## Phase 6: Generator And Documentation Cleanup

1. Keep one Handler shape for Command, Query, and Capability. Async belongs only to supervisor/Mediator methods.
2. Remove active references to generic Request, Client, Saga, public save/flush, Repository persistence flags, `AggregateLoadPlan`, and ordinary async Domain Event Handlers.
3. Preserve checked-in Factory/Behavior first-generation SKIP ownership.
4. Preserve generated immediate root/child Strong ID assignment.
5. Update samples for parallel Query composition, Capability async with explicit waiting, Query DTO mapping inside its transaction, event-to-nested-Command reaction, framework context propagation, and explicit wrapping of user executors.
6. Search production, starters, tests, templates, fixtures, README, and repo-local skills. Historical specs/plans may retain old vocabulary when clearly marked historical.

Acceptance: generated projects compile against final APIs and no active example teaches direct UoW, Command-to-Query, Capability Repository use, or event-order dependence.

## Cross-Cutting Diagnostics

Provide stable errors for forbidden scope transitions, executor rejection/shutdown, missing Hibernate integration, ambiguous ownership, observed aggregate-owned Entity without root, ID mutation, detached persistence, audit topology/event mutation, loop overflow, late mutation, and context codec/envelope failures.

Include current/requested invocation kind, phase/round, aggregate/Entity identity, causal Command/Event/Handler path, and pending counts where available. Do not log context element values indiscriminately.

## Verification

Run focused owners after each slice:

```powershell
.\gradlew.bat :ddd-core:test
.\gradlew.bat :ddd-domain-repo-jpa:test
.\gradlew.bat :cap4k-ddd-core-starter:test
.\gradlew.bat :cap4k-ddd-jpa-starter:test
```

Then run the current reliable Command, Domain Event, Integration Event, and generator owner modules discovered from the Gradle project list. Do not copy historical removed-starter names into automation without checking `gradlew projects`.

Before review:

```powershell
.\gradlew.bat check
git diff --check
```

## Final Acceptance Checklist

- [x] Command alone owns automatic REQUIRED write UoW completion.
- [x] Query has a Handler-wide read-only transaction and no write UoW.
- [x] Capability stays persistence-neutral in executor and Caller Runs paths.
- [x] Query/Capability use one Handler shape plus sync/async supervisor APIs.
- [x] ExecutionContext propagates across supported framework boundaries.
- [x] InvocationScope enforces the approved local call matrix.
- [x] Public UoW lifecycle/locator APIs are gone.
- [x] Repository persistence flags, EXISTING intent, detach behavior, and `AggregateLoadPlan` are gone.
- [x] Hibernate MANUAL flush covers the complete Command UoW.
- [x] Changes are aggregate-organized with Entity-level detail and demand-driven lazy traversal.
- [x] Strong IDs, root/child deletion, and audit follow the approved rules.
- [x] Domain Events remain root-originated, frontier-based, sibling-unordered, and fail-fast.
- [x] Reliable retry/archive retains origin execution context.
- [x] No public Persistence Provider SPI was added.
- [x] Generators, samples, docs, and tests expose only final vocabulary.

## Deferred

- queryable Value Object persistence, recursive ONE/MANY, and root-level persistent Value Objects;
- automatic root-version advancement for child-only changes;
- public Persistence Provider SPI or another ORM;
- framework-owned database tenant isolation;
- structured concurrency and automatic joining of Capability stages;
- hard cancellation of running Query/Capability work;
- generic RPC transport architecture;
- a read-model framework;
- field-level audit history or audit-enricher scalar conflict detection.

Do not pull these into this implementation merely because the new internal structure may host them later.

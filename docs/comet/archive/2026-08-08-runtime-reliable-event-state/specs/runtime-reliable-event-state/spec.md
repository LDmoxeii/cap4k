# Reliable Event State Machine

## Depends on

`runtime-handler-contract` and `runtime-reliable-jpa-substrate`.

## Requirements

### Requirement: One authoritative reliable Event state machine

Persisted Domain Events and outbound Integration Events MUST use one private semantic state machine for ownership, attempt, lease, acknowledgement, and failure transitions. They MAY keep separate provider/carrier classes and provider-specific metadata, but no legacy mutable EventRecord path or provider-local retry loop may independently decide whether an event is owned, delivered, or retryable.

#### Scenario: Same state meaning for both Event kinds

- **WHEN** a worker claims a persisted Domain Event or an outbound Integration Event
- **THEN** the record enters its existing in-flight state, receives an opaque token, records the current attempt and lease expiry, and is completed through the same token-bound rules

#### Scenario: Legacy path cannot bypass ownership

- **WHEN** an after-commit callback, scheduler, provider callback, or diagnostic repository tries to publish or transition a reliable Event
- **THEN** it MUST route through the private coordinator/substrate and MUST NOT mutate state or dispatch a payload without a current claim token

### Requirement: Claim before every reliable delivery attempt

The Event runtime MUST atomically claim an eligible record before loading its payload and invoking local handlers or handing it to an outbound provider. Claim eligibility MUST evaluate service/consumer identity, non-terminal/non-cancelled state, due retry time, record expiry, and an absent or expired lease. An expired in-flight lease MUST be immediately reclaimable for worker-loss recovery.

#### Scenario: Due persisted Domain Event

- **WHEN** a due Domain Event is released after commit or discovered by the due-record coordinator
- **THEN** exactly one worker claims it, receives the current opaque token, and only that worker may execute the attempt

#### Scenario: Due outbound Integration Event

- **WHEN** a due outbound Integration Event is released after commit or discovered by the due-record coordinator
- **THEN** exactly one worker claims it before provider handoff; a provider must never receive a reliable record that has not been claimed

#### Scenario: First attempt numbering

- **WHEN** a newly persisted reliable Event is claimed for the first time
- **THEN** its durable attempt number is `1`; persistence-time initialization MUST NOT consume an attempt

#### Scenario: Lease expiry recovery

- **WHEN** an owned Event lease expires before acknowledgement
- **THEN** a later worker MAY claim it with a new token and attempt metadata, and the old owner MUST lose all transition rights

### Requirement: Token-bound lease and completion transitions

Renewal, acknowledgement, and failure/retry transitions MUST verify the current opaque token and live lease using a conditional update or equivalent database guard. A token mismatch, expired lease, terminal state, or cancelled state MUST have zero durable write effect.

#### Scenario: Active owner renews

- **WHEN** the current owner renews before lease expiry
- **THEN** only the current lease expiry is extended and the token remains unchanged

#### Scenario: Stale owner cannot finish

- **WHEN** an old owner attempts to renew, acknowledge, or record failure after lease expiry or after another worker has reclaimed the row
- **THEN** the operation is rejected and cannot overwrite the new owner's state, token, lease, attempt, or failure facts

#### Scenario: Successful local delivery

- **WHEN** all matching synchronous local Domain Event handlers return successfully and the handler contract has joined all scoped `queries.askAsync*` and `capabilities.callAsync*` operations
- **THEN** the coordinator acknowledges the Event with the current token and enters the existing terminal delivered state

#### Scenario: Successful outbound handoff

- **WHEN** the configured outbound provider reports successful acceptance of the claimed Integration Event
- **THEN** the coordinator acknowledges the Event with the current token and enters the existing terminal delivered state

### Requirement: Synchronous handler scope is the delivery boundary

Reliable Event handlers MUST remain synchronous methods. The delivery coordinator MUST consider the handler scope complete only after all matching handlers return and the handler contract has completed its scoped asynchronous query/capability work. `send` executes a command immediately; `enqueue`, `schedule`, and `delay` only create/schedule work through the Mediator and MUST NOT detach the current Event delivery scope.

#### Scenario: Scoped async query/capability work

- **WHEN** a listener starts several `queries.askAsync*` and `capabilities.callAsync*` operations and then returns
- **THEN** the Event delivery remains in-flight until all scoped operations succeed

#### Scenario: Scoped child failure

- **WHEN** any scoped asynchronous query/capability operation fails
- **THEN** the handler scope fails and the Event is transitioned through token-bound failure/retry rules

#### Scenario: Detached work is not part of acknowledgement

- **WHEN** a handler schedules a command through `enqueue`/`schedule`/`delay`
- **THEN** that command is a separate reliable record and its later execution does not extend or retroactively alter the already completed Event handler scope

### Requirement: After-commit is only a wake-up signal

Persisted Domain Events and outbound Integration Events MUST become durable within the owning transaction. An after-commit callback MAY wake the coordinator for low latency, but it MUST NOT directly publish, dispatch, mark delivered, or mutate retry state. If the wake-up is lost, due-record discovery MUST still recover the Event.

#### Scenario: Commit then wake

- **WHEN** the transaction containing an Event record commits
- **THEN** the after-commit path only requests immediate coordination; normal claim/lease/dispatch/ack or handoff/fail semantics still apply

#### Scenario: Lost wake-up

- **WHEN** the after-commit notification is not delivered
- **THEN** the due-record coordinator eventually discovers and claims the durable Event without requiring a second state machine

### Requirement: Local Domain Event dispatch and outbound handoff share delivery facts

The runtime MUST expose a `ReliableEventDeliveryContext` scoped to one delivery attempt. The context MUST carry stable event identity, origin/provider facts, attempt number, and redelivery information required by handlers and diagnostics. It MUST be cleared after success or failure and MUST NOT persist entities, persistence-bound objects, internal row ids, lease timestamps, or ownership tokens as user payload.

#### Scenario: Local context ownership

- **WHEN** a local Domain Event listener executes
- **THEN** it observes the current event id, origin, attempt, and redelivery facts from the active context, and the context is unavailable after the scope completes

#### Scenario: Outbound context ownership

- **WHEN** an outbound provider hands off a claimed Integration Event
- **THEN** the same core context semantics identify the delivery attempt without conflating provider confirmation with local listener dispatch

#### Scenario: Persistence-bound payload rejection

- **WHEN** a reliable Event payload or derived outbound payload contains a persisted Aggregate, Entity, or other persistence-bound instance
- **THEN** record creation or handoff is rejected before the payload becomes a durable reliable delivery record

### Requirement: Derived outbound Events release only after successful parent scope

Outbound Integration Events derived while handling a local Domain Event MUST remain inside the current unit-of-work/delivery scope until the parent scope succeeds. A failed parent handler scope MUST NOT release those derived outbound Events. A successful scope MUST release them at its durable handoff boundary using the same reliable Event state machine.

#### Scenario: Derived Event after local success

- **WHEN** a local Domain Event handler derives an outbound Integration Event and completes successfully
- **THEN** the derived event is durably recorded/released after the enclosing transaction and becomes independently claimable

#### Scenario: Derived Event after local failure

- **WHEN** the parent local Domain Event handler fails
- **THEN** derived outbound Events from that failed scope are discarded or rolled back with the enclosing unit of work and are not handed to a transport

### Requirement: Failure and retry use the persisted snapshot

An Event failure MUST be recorded through the token-bound substrate using only safe structured failure facts. Retry timing, retry budget, and terminal behavior MUST use the immutable retry-policy snapshot captured when the Event was persisted. Raw business payloads and exception stack traces MUST NOT be stored as failure data.

#### Scenario: Retryable local failure

- **WHEN** a claimed local delivery fails and retry budget and record expiry remain
- **THEN** the Event enters the existing retryable exception state, stores safe failure facts, calculates the next due time from its persisted snapshot, and becomes claimable only when due

#### Scenario: Retryable outbound failure

- **WHEN** a claimed outbound handoff fails before provider acceptance and retry budget and record expiry remain
- **THEN** the Event enters the same retryable state and follows the same snapshot-based retry calculation

#### Scenario: Terminal failure

- **WHEN** retry budget or record expiry is exhausted
- **THEN** the Event enters the existing terminal failure state with safe terminal failure facts and cannot be claimed again

### Requirement: Public Integration Event scheduling surface is explicit

`Mediator.events.enqueue`, `Mediator.events.schedule`, and `Mediator.events.delay` MUST be the only public operations for creating a reliable outbound Integration Event from application code. They MUST create the same durable event envelope/state entry with due-time semantics; they MUST NOT expose provider-specific routing or a generic scheduler abstraction.

#### Scenario: Immediate scheduling

- **WHEN** application code calls `Mediator.events.enqueue(payload)`
- **THEN** one durable outbound Event is created as immediately due and later processed through the reliable state machine

#### Scenario: Delayed scheduling

- **WHEN** application code calls `schedule(payload, dueTime)` or `delay(payload, duration)`
- **THEN** one durable outbound Event is created with the requested due time and is not claimable before it is due

### Requirement: At-least-once and duplicate delivery are explicit

The reliable Event state machine MUST provide sender-side at-least-once execution/handoff semantics when persistence and the configured provider are available. It MUST NOT claim exactly-once delivery, global consumer ordering, or tracking of every downstream recipient. A duplicate local dispatch or provider handoff is an allowed recovery outcome and remains the consumer/provider's idempotency responsibility.

#### Scenario: Crash after side effect before acknowledgement

- **WHEN** a worker performs the handler or provider side effect and loses its lease before acknowledgement
- **THEN** another worker may redeliver the Event, and the runtime records no exactly-once guarantee

### Requirement: Real JPA composition evidence

The implementation MUST include real JPA integration tests proving that the production Event path composes the atomic substrate with event persistence and delivery. Mock-only tests are insufficient.

#### Scenario: Required verification matrix

- **WHEN** the focused Event verification suite runs
- **THEN** it covers concurrent claims, first-attempt numbering, token mismatch, renewal before/after expiry, worker-loss re-claim, success acknowledgement, local and outbound failure/retry/terminal transitions, retry snapshot stability, safe failure facts, after-commit wake-up recovery, context cleanup, derived-event release, duplicate delivery, terminal/cancelled rejection, and entity-payload rejection

### Requirement: Scope boundaries

This change MUST remain private Runtime infrastructure. It MUST NOT add public generic scheduling APIs, broker-specific routes, inbound Integration Event subscriber contracts, manual redrive, retention/cleanup, or retired runtime surfaces.

#### Scenario: Downstream transport work remains separate

- **WHEN** this change is complete
- **THEN** later Integration Event core/transport changes can add envelopes, routes, publisher confirmation, subscription identity, and inbound acknowledgement without introducing a competing Event ownership state machine

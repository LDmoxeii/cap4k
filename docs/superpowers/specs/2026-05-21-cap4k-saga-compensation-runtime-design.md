# Cap4k Saga Compensation Runtime Design

Date: 2026-05-21

Status: Proposed

Scope: add first-class compensation-oriented Saga runtime support within the existing request/process replay model, covering:

- `ddd-core` Saga authoring and runtime contracts
- `ddd-distributed-saga-jpa` Saga persistence, archive, and scheduler/resume semantics
- public authoring docs under `docs/public/authoring/**`
- internal runtime analysis docs under `docs/superpowers/analysis/**`
- agent skills under `skills/**`

## Backlog Source

This design is the implementation slice for:

- `#58` saga: enhance compensation-oriented runtime support

## Goal

Turn cap4k Saga into a reviewable compensation/recovery surface without turning it into a general workflow engine.

The design narrows the first-class use case on purpose:

1. Forward steps still run through the current Saga handler and process replay model.
2. Compensable forward steps explicitly declare their compensation step.
3. Business code explicitly requests compensation when it knows the forward flow must stop.
4. Runtime persists compensation intent, reverse-compensates completed steps, and exposes auditable terminal states.
5. Retry exhaustion does not silently decide to compensate on behalf of business logic.

## Problem Statement

The current Saga runtime persists replayable forward steps in `__saga` and `__saga_process`, but it is still fundamentally a whole-handler replay coordinator.

That creates four practical gaps for compensation-oriented flows such as paid publication:

1. compensation is authored with ad hoc handler-level `try/catch`, not with an explicit runtime contract
2. retry failure and compensation intent are conflated, so runtime cannot clearly tell whether it should retry forward progress or reverse completed work
3. compensation progress, compensation failure, and manual repair are not first-class persisted states
4. public docs, internal analysis, and skills still talk about Saga more broadly than the runtime can honestly support today

The result is that cap4k can persist a long-running process, but it cannot yet present a stable compensation authoring shape.

## Current Findings

Current runtime semantics, confirmed from `DefaultSagaSupervisor`, `Saga`, and `SagaProcess`, are:

- `SagaHandler.execProcess(processCode, request)` executes a child request through `RequestSupervisor`.
- successful process codes are sticky and skipped on replay
- failed process execution records `EXCEPTION` and rethrows
- `retry(uuid)` re-enters the whole handler with cached successful process results
- scheduled resume advances timing first and only re-enters when the Saga remains executable
- Saga states are currently `INIT`, `EXECUTING`, `CANCEL`, `EXPIRED`, `EXHAUSTED`, `EXCEPTION`, `EXECUTED`
- Saga process states are currently `INIT`, `EXECUTING`, `EXCEPTION`, `EXECUTED`

These semantics are useful and should be preserved where possible. The design should extend them for compensation rather than replace them with a separate workflow engine model.

## Scope

Runtime scope:

- add explicit compensable-step authoring APIs
- add explicit internal and operator-driven compensation request APIs
- add compensation-aware Saga and Saga-process state models
- persist compensation request data and compensation execution data
- support compensation resume from the failed compensation step
- expose manual repair as a stable terminal state

Documentation and skill scope:

- update public authoring docs to teach the compensation-oriented slice, not an abstract future Saga
- update runtime analysis docs so they reflect actual compensation semantics
- update modeling, implementation, verification, and shared skill rules so generated guidance stops recommending hand-written Saga compensation patterns

Dogfood scope:

- validate the runtime and authoring shape against `cap4k-reference-content-studio` paid publication Saga

## Non-Goals

- do not redesign Saga into a full waiting/callback workflow engine
- do not add a generic callback-step resume API
- do not make retry exhaustion automatically trigger compensation
- do not require users to throw framework business exceptions such as `CompensableFailure`
- do not redesign transport adapters, controller surfaces, or console UX in this slice
- do not promise generator-first Saga skeleton generation in this slice
- do not replace the current process replay model with a separate step-pointer engine

## Options Considered

### Option 1: Minimal intrusion

Keep `execProcess(...)` as the only forward-step API and bolt compensation on through additional SagaRecord metadata plus `requestCompensation(...)`.

Pros:

- smallest public API addition
- lower immediate implementation cost

Cons:

- compensation declaration remains implicit
- authoring can easily slide back into handler-level `try/catch`
- runtime records still need to infer too much from incomplete process metadata

### Option 2: Explicit compensable step contract

Add `execCompensableProcess(...)` for forward steps that want runtime-managed compensation, plus explicit compensation request APIs.

Pros:

- compensation intent is visible at authoring time
- runtime can persist completed forward-step compensation data at the correct moment
- fits the existing replay model without pretending to be a different engine

Cons:

- requires schema and state model expansion
- public docs and skills must be narrowed to the new shape

### Option 3: Unified workflow state machine

Generalize Saga into a broader workflow-node system that treats forward, wait, callback, compensation, and manual actions as one engine.

Pros:

- conceptually uniform
- could later support waiting/callback flows more naturally

Cons:

- exceeds `#58`
- would effectively replace the current request/process replay surface
- introduces a much larger contract than the current framework can safely support

## Recommended Design

Adopt Option 2.

The framework should add an explicit compensation-oriented layer on top of the existing Saga replay model:

- authors explicitly choose which forward steps are compensable
- runtime persists the final compensation request at forward-step success time
- compensation is triggered by explicit Saga control signals, not by exception taxonomy guessing
- compensation reuses the current persisted whole-Saga model while adding process-level reverse execution state

## Design Decisions

### Compensation Is Entered By Signal, Not Retry Exhaustion

Ordinary forward exceptions continue to mean forward execution failed and may be retried.

Compensation begins only when one of these explicit signals occurs:

- business code calls `requestCompensation(code, reason)`
- an operator calls `SagaManager.requestCompensation(sagaId, code, reason)`

Retry exhaustion remains a runtime exhaustion outcome. It does not imply that the business wants compensation.

### First-Class API Surface

Public Saga authoring should expose three main entry points:

```kotlin
interface SagaHandler<REQUEST : SagaParam<RESPONSE>, RESPONSE : Any> : RequestHandler<REQUEST, RESPONSE> {

    fun <SUB_REQUEST : RequestParam<SUB_RESPONSE>, SUB_RESPONSE : Any> execProcess(
        processCode: String,
        request: SUB_REQUEST
    ): SUB_RESPONSE

    fun <SUB_REQUEST : RequestParam<SUB_RESPONSE>, SUB_RESPONSE : Any> execCompensableProcess(
        processCode: String,
        request: SUB_REQUEST,
        compensationCode: String,
        compensationRequest: (SUB_RESPONSE) -> RequestParam<*>
    ): SUB_RESPONSE

    fun requestCompensation(
        code: String,
        reason: String
    ): Nothing
}
```

Manager-level operator support should expose:

```kotlin
interface SagaManager {
    fun requestCompensation(
        sagaId: String,
        code: String,
        reason: String
    )
}
```

API intent:

- `execProcess(...)` remains the default forward-step API
- `execCompensableProcess(...)` is used only when a completed forward step should become eligible for reverse compensation
- `requestCompensation(...)` is the only business-facing compensation trigger
- `requestCompensation(...)` should terminate the current forward path and therefore returns `Nothing`

The public contract should not expose a framework exception type as the normal authoring trigger.

### Final Compensation Request Is Persisted At Forward Success Time

The compensation lambda may depend on the forward result:

```kotlin
execCompensableProcess(
    processCode = "create-entitlement-plan",
    request = CreateAccessEntitlementPlanCmd.Request(taskId),
    compensationCode = "cancel-entitlement-plan",
    compensationRequest = { result ->
        CancelEntitlementPlanCmd.Request(result.planId)
    }
)
```

Runtime must evaluate and persist the final compensation request immediately after the forward step succeeds.

Do not defer compensation-request construction to compensation time, because:

- forward result objects may not be recomputable later
- compensation should not depend on re-running forward inference
- persisted diagnostics must show the actual reverse action that would be attempted

### Compensation Starts Immediately In The Current Execution

When `requestCompensation(...)` is called inside the Saga handler:

1. persist compensation request metadata
2. stop forward execution
3. move Saga into compensation mode
4. immediately attempt reverse compensation in the current Saga execution thread
5. if compensation later fails, hand continuation to the scheduler/resume path

This gives business code a direct and reviewable stop signal while still preserving retry/resume for failed reverse steps.

### Compensation-Type Termination Is Externally A Failure

If compensation is requested, the Saga did not complete its forward business goal.

Therefore:

- forward success returns the normal Saga `RESPONSE`
- compensation-driven termination does not return normal forward success
- even when compensation completes successfully, the outward result is still a failed or rejected Saga outcome rather than a normal `RESPONSE`

This keeps `EXECUTED` and `COMPENSATED` semantically distinct.

Future support for a separate "complete with compensation result" contract is outside this slice.

### External Compensation Trigger Belongs In The Same State Machine

Operator-driven compensation should be part of the same design, not deferred to a separate future model.

First implementation can stay narrow:

- add manager/runtime API support
- reuse scheduler/resume semantics
- do not require a new console UI in this slice

This keeps the runtime state model coherent and avoids inventing a second compensation path later.

## Runtime State Model

### Saga States

Replace the current forward-only model with these persisted Saga states:

- `INIT`
- `EXECUTING_FORWARD`
- `COMPENSATION_REQUESTED`
- `COMPENSATING`
- `COMPENSATED`
- `EXCEPTION`
- `EXHAUSTED`
- `MANUAL_REPAIR_REQUIRED`
- `CANCELLED`
- `EXPIRED`
- `EXECUTED`

State meaning:

- `EXECUTED`: forward business goal completed successfully
- `COMPENSATED`: compensation completed successfully after a compensation request
- `EXHAUSTED`: forward retries are exhausted; no automatic compensation was chosen
- `MANUAL_REPAIR_REQUIRED`: runtime cannot automatically finish a compensation-required situation

`EXECUTED` and `COMPENSATED` must remain separate terminal meanings.

### Saga Process Record Model

First implementation should not introduce a separate compensation-process entity. Instead, each `SagaProcess` represents:

- one forward step execution record
- optional compensation definition
- optional compensation execution record

This keeps schema shape close to the existing replay model and avoids forcing a second process table family into runtime and archive flows.

### Saga Root Metadata

`Saga` and `ArchivedSaga` should add compensation request metadata:

- `compensationRequestCode`
- `compensationRequestReason`
- `compensationRequestedAt`
- `compensationRequestedBy`
  - `INTERNAL`
  - `OPERATOR`
- `compensationSourceProcessCode`

Existing `exception` remains useful for forward failure diagnostics, but it is not enough to represent compensation intent or reverse failure state by itself.

### Saga Process Forward Data

Keep existing forward-step data and extend it with explicit forward completion timing:

- `processCode`
- `param`
- `paramType`
- `result`
- `resultType`
- `exception`
- `processState`
- `createAt`
- `lastTryTime`
- `triedTimes`
- `executedAt`

`executedAt` should not be inferred from `lastTryTime`, because reverse compensation ordering needs a stable semantic timestamp for successful forward completion.

### Saga Process Compensation Definition And Execution Data

`SagaProcess` and `ArchivedSagaProcess` should add:

- `compensationCode`
- `compensationParam`
- `compensationParamType`
- `compensationState`
- `compensationResult`
- `compensationResultType`
- `compensationException`
- `compensationLastTryTime`
- `compensationTriedTimes`
- `compensatedAt`

### Compensation States

`compensationState` should be persisted as its own enum:

- `NONE`
- `READY`
- `COMPENSATING`
- `COMPENSATED`
- `FAILED`
- `MANUAL_REPAIR_REQUIRED`

Meaning:

- `NONE`: no compensation was declared for this forward step
- `READY`: forward step completed and final compensation request is persisted
- `COMPENSATING`: reverse step is being attempted
- `COMPENSATED`: reverse step finished successfully
- `FAILED`: reverse step failed and should resume from here
- `MANUAL_REPAIR_REQUIRED`: this step or its surrounding context cannot be automatically closed

## Failure And Resume Semantics

### Forward Exception

When a forward step throws a normal exception:

- the forward process records `EXCEPTION`
- the Saga records `EXCEPTION`
- existing retry/backoff semantics continue to apply
- runtime does not convert this into compensation unless compensation is explicitly requested later

### Explicit Compensation Request

When compensation is explicitly requested:

- Saga records compensation request metadata
- Saga enters `COMPENSATION_REQUESTED`
- current execution immediately transitions into `COMPENSATING`

### Compensation Exception

When a compensation step throws:

- Saga remains in `COMPENSATING`
- that process records `compensationState = FAILED`
- the failure is persisted separately from the forward exception record

### Retry Exhaustion

When forward retries are exhausted:

- Saga enters `EXHAUSTED`
- no automatic compensation is triggered

When compensation retries are exhausted:

- Saga enters `MANUAL_REPAIR_REQUIRED`

### Non-Compensable Completed Step

If compensation is requested after a forward step completed without compensation support:

- that forward step remains non-compensable
- other completed compensable steps should still be reversed
- Saga terminal state becomes `MANUAL_REPAIR_REQUIRED`

This is better than refusing all compensation work just because one completed step requires manual repair.

### Compensation Resume Granularity

Compensation resume should continue from the failed compensation step, not restart the whole reverse chain.

Resume algorithm:

1. consider only forward steps with `processState == EXECUTED`
2. order them by `executedAt desc`
3. skip already `COMPENSATED` steps
4. stop at the first `FAILED` or `READY` compensation step
5. continue reverse compensation from there

Compensation commands must still be idempotent, but runtime should not force unnecessary compensation replays when the persisted process model can already identify progress.

### Scheduler And Resume Path

`resume(...)` semantics split by Saga state:

- `EXCEPTION`: continue forward replay semantics
- `COMPENSATING`: continue compensation resume semantics
- `COMPENSATION_REQUESTED`: transition into compensation and continue
- `EXHAUSTED`, `MANUAL_REPAIR_REQUIRED`, `COMPENSATED`, `EXECUTED`, `CANCELLED`, `EXPIRED`: no normal forward replay

Operator-triggered compensation should reuse the same persisted state machine rather than inventing an alternate resume path.

## Authoring Shape

First public authoring shape should stay deliberately small.

Normal forward step:

```kotlin
val publish = execProcess(
    "publish-content",
    PublishContentCmd.Request(taskId)
)
```

Compensable forward step:

```kotlin
val plan = execCompensableProcess(
    processCode = "create-entitlement-plan",
    request = CreateAccessEntitlementPlanCmd.Request(taskId),
    compensationCode = "cancel-entitlement-plan",
    compensationRequest = { result ->
        CancelEntitlementPlanCmd.Request(result.planId)
    }
)
```

Business stop signal:

```kotlin
if (!publish.accepted) {
    requestCompensation("PUBLISH_REJECTED", publish.reason)
}
```

Authoring rules:

- keep `execProcess(...)` as the default
- use `execCompensableProcess(...)` only where reverse compensation is needed
- use `requestCompensation(...)` as the single explicit compensation trigger
- for multiple trigger points, authors may wrap it in Saga-local helpers such as `stopAndCompensate(reason)`
- do not re-teach handler-level compensation `try/catch` as the normal cap4k shape

## Testing Contract

Implementation must add tests for four levels.

### Runtime Unit Tests

- `execCompensableProcess(...)` persists compensation metadata after forward success
- compensation request can depend on forward result
- `requestCompensation(...)` stops forward execution and enters compensation mode

### Persistence And Archive Tests

- new Saga compensation metadata persists in `Saga` and `ArchivedSaga`
- new process compensation fields persist in `SagaProcess` and `ArchivedSagaProcess`
- compensation records survive reload and replay/resume

### Scheduler And Resume Tests

- forward exception remains forward retry
- forward exhaustion does not auto-trigger compensation
- compensation failure resumes from the failed compensation step
- operator-triggered compensation enters the same runtime path
- non-compensable completed steps still allow other reverse steps to run and end in `MANUAL_REPAIR_REQUIRED`

### Dogfood Tests

Use the paid publication flow to validate:

- forward success
- business rejection leading to compensation success
- compensation partial failure and scheduler recovery
- manual repair requirement when some completed step is not compensable

## Documentation, Analysis, And Skill Sync

This design requires synchronized updates across three surfaces. These are not optional cleanup tasks; they are part of the contract change.

### `docs/public/authoring`

Update public authoring pages that promise Saga usage:

- `docs/public/authoring/advanced/saga.md`
- `docs/public/authoring/examples/content-publication-saga.md`

Update only adjacent higher-level pages if their current Saga wording would become false after runtime changes, especially:

- `docs/public/authoring/advanced/index.md`
- `docs/public/authoring/framework-positioning.md`

Public doc changes must:

- teach compensation-oriented Saga as the supported slice
- show `execCompensableProcess(...)` and `requestCompensation(...)` as the authoring surface
- keep waiting/callback resume out of the supported runtime promise
- keep callback main-path and polling-fallback guidance intact

### `docs/superpowers/analysis`

Update runtime-truth analysis docs, especially:

- `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md`

Analysis changes must:

- record the new API surface
- describe compensation state transitions and resume semantics
- explicitly state that forward exhaustion does not auto-trigger compensation
- document operator-triggered compensation in the same runtime model

### `skills`

Update any skill guidance that routes or shapes Saga authoring, especially:

- `skills/cap4k-modeling/**`
- `skills/cap4k-implementation/**`
- `skills/cap4k-verification/**`
- `skills/shared/**`
- `skills/cap4k-authoring/**` where Saga routing or gap wording would drift

Skill changes must:

- narrow Saga guidance to the supported compensation-oriented slice
- stop recommending handler-level compensation `try/catch` as the normal pattern
- add verification checks for explicit compensation request, compensation resume, and manual repair

## Compatibility And Migration

This design expands runtime contract and schema. First implementation should assume:

- JPA Saga schema migration is required for active and archived tables
- archive copy logic must be updated together with active persistence
- existing plain `execProcess(...)` Sagas remain valid and non-compensable unless explicitly migrated

Backward compatibility target for first implementation:

- existing forward-only Saga flows should continue to work
- new compensation-oriented behavior should activate only when the new APIs and fields are used

## Acceptance Mapping To `#58`

This design satisfies the intended direction for `#58` by defining:

- a compensation/recovery spec within the current replay model
- waiting/callback resume as out of scope
- a compensable-step contract
- explicit compensation request APIs
- forward exception versus compensation request versus exhaustion semantics
- reverse-order compensation
- separate forward and compensation failure records
- explicit compensation and manual repair states
- required tests
- documentation and skill sync requirements
- paid publication dogfood validation

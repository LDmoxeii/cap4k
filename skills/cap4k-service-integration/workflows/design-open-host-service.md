# Design Open Host Service

Use this workflow when external consumers need synchronous access to an internal capability.

## Input Signals

- User asks for Open Host Service, public API, synchronous external access, RPC/gRPC/HTTP entry, partner integration, or frontend-facing capability.
- The task names request/response/status/error wording that external readers must depend on.
- A write entry might otherwise bypass Command or an entry might read Aggregates directly.

## Technical Design Fields To Update

- `businessIntent`: external consumer and business reason.
- `ubiquitousLanguage`: Published Language terms for request, response, status, and errors.
- `cleanArchitecturePlacement`: entry protocol mapping in Adapter, use-case routing in Application, invariants in Domain.
- `cap4kCarriers`: Command for writes, Query/read API for reads, API payload or controller skeleton only as protocol surface.
- `generatorInputPlan`: expected payload, controller, command/query, and handler skeleton inputs where cap4k supports them.
- `handwrittenLogicSlots`: mapping, validation translation, authorization policy hook, command/query delegation.
- `verificationEvidence`: path review, routing review, no direct repository or Aggregate write from the entry.
- `rollbackTriggers`: unclear consumer contract, direct state mutation from entry, or leaked internal model fields.

## Generator Input Implications

- Prefer generator-supported API payload, controller, Command, Query, and handler skeletons when available.
- Do not handwrite a missing generated skeleton just to make the design compile; return to generator inputs or record a technical design exception.
- Keep Published Language field names stable before generating payload skeletons.

## Handwritten Slots

- Adapter entry maps protocol input/output to the Published Language and delegates.
- Application Command or Query handler orchestrates the use case.
- Domain behavior enforces business invariants.
- UoW and Mediator are framework capabilities used from the approved application surface, not project-owned implementations.

## Verification Evidence

- Technical design records consumer, Published Language, read/write split, generator inputs, and rollback target.
- Diff shows entry code only maps protocol shape and delegates.
- No entry implementation writes Repository, UoW, or Aggregate state directly.
- Boundary fields do not mirror private Aggregate or persistence structure by default.

## Rollback Target

Return to technical design when the external contract, read/write split, generated skeleton expectation, or Published Language compatibility is unclear.

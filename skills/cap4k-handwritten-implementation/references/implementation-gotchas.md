# Implementation Gotchas

Use `../../shared/workflows/skeleton-generation-gate.md` for skeleton existence,
ownership, and structural-change decisions. Do not duplicate that gate here.

- Query handlers live in adapter packages by default.
- Direct handler injection bypasses the category-specific Command, Query, or Capability supervisor.
- Internal triggers should not become write repositories.
- Generated subscriber shells need semantic methods after implementation.
- Value objects owned by aggregates are saved through aggregate persistence.
- Do not call external Capabilities from entry implementations before routing state changes through a Command.
- Inspect `build/cap4k/plan.json` before editing generated Command, Query, Capability, Handler, or subscriber surfaces.
- Missing generator output belongs to generator inputs or technical design, not handwritten parallel structure.

## Hidden Listener Dispatch

Strongly discourage one public `on(event)` listener method that manually
dispatches to several private business reaction methods. Use multiple
independent `@EventListener` methods with business-semantic names. Cap4k does
not guarantee ordering between multiple listeners, so commands triggered by
listeners must be idempotent and zero-trust.

## Listener Filter Overreach

A listener-side condition is only a cheap routing filter. If it decides final
write eligibility, the flow has moved the trust boundary out of the command.
Commands must reload state, validate release policy, readiness, existing tasks,
already-applied state, ownership, and invariants as applicable, then return
explicit no-op reasons for normal retreat paths.

## Zero-Trust Command Boundary

- Load the aggregate root or write target inside the command.
- Validate target existence, ownership, aggregate status, child membership, and business invariants before mutating.
- Treat query results, listener filters, job checks, provider orchestration state, another Command, and external entry validation as insufficient for writes.
- Return an explicit no-op result for expected non-ready or already-applied states.
- Throw a domain or application error for missing targets, invalid identities, wrong ownership, invalid child keys, and invariant violations.
- Read multiple aggregates or facts only for validation or fact observation.
- Load existing aggregate roots through Repository so the active Command can observe managed changes automatically.
- Create roots through Factory and delete roots through Repository; do not locate Unit of Work or call save/persist/flush.
- Change owned children only through their managed root relations; do not persist or remove owned children independently.

## Reliable Record Registration Must Not Flush The Provider

- A reliable Command record or reliable Domain Event record is registered in the current local transaction, but its repository must not call `saveAndFlush()` or otherwise force a provider-wide flush.
- In JPA, `saveAndFlush()` flushes the shared persistence context, not only the reliable record. That can execute aggregate SQL before the outer Command Coordinator finishes candidate detection, audit enrichment, final detection, and event-frontier stabilization.
- Let the outer Coordinator own the final provider flush. After commit, worker wake-up remains only an optimization over the durable record.
- Call the recovery job `retry`; retrying a failed record is not a reverse business compensation action.

## Command-To-Command Calls

- Default stance: commands are write boundaries, not process coordinators.
- A command may call another command only as local reuse inside the same synchronous write use case.
- A Command that reads state, branches, and sends multiple follow-up Commands is explicit orchestration. Keep it only when that order belongs to one reaction; otherwise prefer Domain Event fan-out, Integration Event entry, or a scheduled reaction.
- If command-to-command remains, document why the called command is local synchronous reuse and why event-driven continuation would be worse.

## Durable Orchestration Guidance

- Cap4k has no built-in Saga runtime or generated Saga skeleton.
- Prefer reliable Commands and Integration Events for durable local work and cross-context facts.
- If persisted progress or reverse compensation still requires an orchestration engine, return to technical design and select an explicit provider-owned boundary.
- Keep provider records and compensation mechanics outside Domain objects; each local state change still enters through an idempotent Command.

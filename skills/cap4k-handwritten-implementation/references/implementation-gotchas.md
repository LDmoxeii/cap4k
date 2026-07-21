# Implementation Gotchas

Use `../../shared/workflows/skeleton-generation-gate.md` for skeleton existence,
ownership, and structural-change decisions. Do not duplicate that gate here.

- Query handlers live in adapter packages by default.
- Direct handler injection bypasses cap4k request supervision.
- Internal triggers should not become write repositories.
- Generated subscriber shells need semantic methods after implementation.
- Value objects owned by aggregates are saved through aggregate persistence.
- Do not call external clients from entry implementations before routing state changes through a command.
- Inspect `build/cap4k/plan.json` before editing generated request, handler, or subscriber surfaces such as `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, and generated subscriber shells.
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
- Treat query results, listener filters, job checks, Saga state, another command, and external entry validation as insufficient for writes.
- Return an explicit no-op result for expected non-ready or already-applied states.
- Throw a domain or application error for missing targets, invalid identities, wrong ownership, invalid child keys, and invariant violations.
- Read multiple aggregates or facts only for validation or fact observation.
- Persist exactly one aggregate root through Unit of Work.

## Command-To-Command Calls

- Default stance: commands are write boundaries, not process coordinators.
- A command may call another command only as local reuse inside the same synchronous write use case.
- A command that reads state, branches, and sends multiple follow-up commands is suspicious; prefer domain event fan-out, external fact entry, job, or Saga.
- If command-to-command remains, document why the called command is local synchronous reuse and why event-driven continuation would be worse.

## Saga Compensation Guidance

- If the flow needs persisted reverse compensation, prefer Saga over handler-local rollback code.
- Use `execCompensableProcess(...)` for compensable forward steps and persist reverse-compensation request metadata on forward success.
- Use explicit `requestCompensation(code, reason)` when business intent decides the flow must enter compensation.
- Keep operator-triggered compensation on the manager or control-plane entrypoint.
- Do not treat forward retry exhaustion as a compensation trigger; compensation must be explicitly requested.

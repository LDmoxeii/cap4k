# Implement Command Slice

1. Confirm the command intent and aggregate root.
2. Add or update a focused behavior/application test when feasible.
3. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Cmd.kt`.
4. If the write use case depends on an external capability result, call the client inside the command handler.

## Zero-Trust Command Boundary

Every command must treat all callers as untrusted routing hints:

- [ ] Load the aggregate root or write target inside the command.
- [ ] Validate target existence, ownership, aggregate status, child membership, and business invariants before mutating.
- [ ] Treat query results, listener filters, job checks, Saga state, another command, and external entry validation as insufficient for writes.
- [ ] Return an explicit no-op result for expected non-ready or already-applied states.
- [ ] Throw a domain/application error for missing targets, invalid identities, wrong ownership, invalid child keys, and invariant violations.
- [ ] Persist only aggregate roots through `Mediator.uow.save(...)`.

5. Call aggregate behavior to record the internal state change.
6. Save the aggregate root through `Mediator.uow.save()`.
7. Keep external protocol mapping in adapter code or client handlers.

## Command-To-Command Calls

- Default stance: commands are write boundaries, not process coordinators.
- Allowed exception: a command may call another command only as local reuse inside the same synchronous write use case.
- Suspicious shape: a command reads state, branches, and sends multiple follow-up commands. Prefer domain event fan-out, external fact entry, job, or Saga for that flow.
- If the follow-up is driven by a business fact, record a domain event and let independent listeners react.
- If command-to-command remains, document why the called command is local synchronous reuse and why event-driven continuation would be worse.

8. Run affected compile/tests and report evidence.

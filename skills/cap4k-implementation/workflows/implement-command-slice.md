# Implement Command Slice

1. Run the skeleton gate before writing code.
2. Stop and return to `cap4k-generation` when the write use case needs a missing `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, client, payload, domain event, integration event, or subscriber skeleton that current generation supports.
3. Stop and return to `cap4k-generation` when DDL, enum, or type-registry facts already exist but the aggregate, repository, factory, specification, enum, or unique-helper skeleton is still missing. If relation or field-mapping behavior seems missing after those facts exist, that is still aggregate/entity generation drift, not a standalone skeleton plan item, and must return to `cap4k-generation`.
4. Stop and return to `cap4k-modeling` when the missing piece is a design entry, DDL annotation, enum manifest entry, or `types.registryFile` entry.
5. Stop and return to `cap4k-generation` when generation is blocked by missing KSP metadata output/config/setup.
6. Confirm the command intent and aggregate root.
7. Add or update a focused behavior/application test when feasible.
8. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Cmd.kt`.
9. If the write use case depends on an external capability result, call the client inside the command handler.

## Saga Compensation Guidance

- If the flow needs persisted reverse compensation, prefer Saga over handler-local rollback code.
- Use `execCompensableProcess(...)` to run compensable forward steps and persist the reverse-compensation request metadata on forward success.
- Inside the handler or Saga flow, use explicit `requestCompensation(code, reason)` when business intent decides the flow must enter compensation.
- Keep operator-triggered compensation on the manager/control-plane entrypoint rather than modeling it as the same command-slice call surface.
- Do not present handler-level `try/catch` reverse command replay as the recommended shape for persisted compensation.
- Do not treat forward retry exhaustion as a compensation trigger; the current runtime moves exhausted forward execution to `EXHAUSTED` unless compensation is explicitly requested.

## Zero-Trust Command Boundary

Every command must treat all callers as untrusted routing hints:

- [ ] Load the aggregate root or write target inside the command.
- [ ] Validate target existence, ownership, aggregate status, child membership, and business invariants before mutating.
- [ ] Treat query results, listener filters, job checks, Saga state, another command, and external entry validation as insufficient for writes.
- [ ] Return an explicit no-op result for expected non-ready or already-applied states.
- [ ] Throw a domain/application error for missing targets, invalid identities, wrong ownership, invalid child keys, and invariant violations.
- [ ] Read multiple aggregates or facts only for validation or fact observation.
- [ ] Persist exactly one aggregate root through `Mediator.uow.save(...)`.

10. Call aggregate behavior to record the internal state change.
11. Save the aggregate root through `Mediator.uow.save()`.
12. Keep external protocol mapping in adapter code or client handlers.

## Command-To-Command Calls

- Default stance: commands are write boundaries, not process coordinators.
- Allowed exception: a command may call another command only as local reuse inside the same synchronous write use case.
- Suspicious shape: a command reads state, branches, and sends multiple follow-up commands. Prefer domain event fan-out, external fact entry, job, or Saga for that flow.
- If the follow-up is driven by a business fact, record a domain event and let independent listeners react.
- If command-to-command remains, document why the called command is local synchronous reuse and why event-driven continuation would be worse.

13. Run affected compile/tests and report evidence.

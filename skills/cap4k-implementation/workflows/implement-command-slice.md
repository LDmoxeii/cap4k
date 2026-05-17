# Implement Command Slice

1. Confirm the command intent and aggregate root.
2. Add or update a focused behavior/application test when feasible.
3. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Cmd.kt`.
4. If the write use case depends on an external capability result, call the client inside the command handler.
5. Call aggregate behavior to record the internal state change.
6. Save the aggregate root through `Mediator.uow.save()`.
7. Keep external protocol mapping in adapter code or client handlers.
8. Run affected compile/tests and report evidence.

# Implement Command Slice

1. Confirm the command intent and aggregate root.
2. Add or update a focused behavior/application test when feasible.
3. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Cmd.kt`.
4. Load aggregate roots through `Mediator.repositories` or create them through `Mediator.factories`.
5. Apply domain behavior on the aggregate or call a domain service for cross-aggregate decisions.
6. Attach domain or integration events only after the state transition is meaningful.
7. Persist through `Mediator.uow.save()`.
8. Keep external protocol mapping in adapter code or client/cli handlers.
9. Run affected compile/tests and report evidence.

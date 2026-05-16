# Implement Subscriber Or Job

1. Identify the triggering event or schedule.
2. Use subscribers or jobs as orchestration points, not as hidden aggregate persistence layers.
3. Inspect `build/cap4k/plan.json` before editing generated subscriber shells.
4. Translate follow-up work into `Mediator.cmd`, `Mediator.qry`, or `Mediator.requests`.
5. Use semantic method names once business logic exists.
6. Keep external callback protocol details outside domain events.
7. Run focused tests or compile checks.

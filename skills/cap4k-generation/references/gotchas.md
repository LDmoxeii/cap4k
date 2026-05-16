# Generation Gotchas

- `src/main/kotlin` does not automatically mean handwritten; check `plan.json`.
- `src-generated/main/kotlin` is a snapshot, not active generation output.
- `@VO` is not the default value-object path.
- `integration_event` without payload fields is invalid.
- `designIntegrationEventSubscriber` depends on `designIntegrationEvent`.
- Do not use stale enum translation core DSL; use an addon path.

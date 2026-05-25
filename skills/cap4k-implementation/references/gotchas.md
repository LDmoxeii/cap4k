# Implementation Gotchas

- Query handlers live in adapter packages by default.
- Direct handler injection bypasses cap4k request supervision.
- Internal triggers should not become write repositories.
- Generated subscriber shells need semantic methods after implementation.
- Value objects owned by aggregates are saved through aggregate persistence.
- Do not call external clients from entry implementations before routing state changes through command.
- Inspect `build/cap4k/plan.json` before editing generated request, handler, or subscriber surfaces such as `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, and generated subscriber shells.
- Do not handwrite generator-capable skeletons just because `plan.json` inspection showed a family you expected; missing generator output belongs to generation, and missing source facts belong to modeling.

## Hidden Listener Dispatch

Strongly discouraged: one public `on(event)` listener method manually dispatches to several private business reaction methods. Use multiple independent `@EventListener` methods with business-semantic names. Cap4k does not guarantee ordering between multiple listeners, so commands triggered by listeners must be idempotent and zero-trust.

## Listener Filter Overreach

A listener-side condition is only a cheap routing filter. If it decides final write eligibility, the flow has moved the trust boundary out of the command. Commands must reload state, validate release policy/readiness/existing tasks/already-applied state/ownership/invariants as applicable, and return explicit no-op reasons for normal retreat paths.

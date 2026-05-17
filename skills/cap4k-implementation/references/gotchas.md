# Implementation Gotchas

- Query handlers live in adapter packages by default.
- Direct handler injection bypasses cap4k request supervision.
- Internal triggers should not become write repositories.
- Generated subscriber shells need semantic methods after implementation.
- Value objects owned by aggregates are saved through aggregate persistence.
- Do not call external clients from entry implementations before routing state changes through command.
- Inspect `build/cap4k/plan.json` before editing generated request, handler, or subscriber surfaces such as `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, and generated subscriber shells.

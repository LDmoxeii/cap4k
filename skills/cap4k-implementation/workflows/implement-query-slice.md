# Implement Query Slice

1. Confirm the read contract and caller.
2. Keep query handlers read-oriented.
3. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Qry.kt`, `*QryHandler.kt`, and `*CliHandler.kt`.
4. Use repository/JPA/query infrastructure behind the handler.
5. Do not repair aggregate state from query paths.
6. Map persistence/read shapes to response DTOs without leaking transport concerns into domain code.
7. Run focused tests or compile checks.

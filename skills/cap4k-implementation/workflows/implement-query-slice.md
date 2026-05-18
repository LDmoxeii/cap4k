# Implement Query Slice

1. Run the skeleton gate before writing code.
2. Stop and return to `cap4k-generation` when the read use case needs a missing `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, payload, client, or validator skeleton that current generation supports.
3. Stop and return to `cap4k-modeling` when the missing piece is a design entry, type-registry entry, enum manifest entry, or KSP metadata contract.
4. Confirm the read contract and caller.
5. Keep query handlers read-oriented.
6. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Qry.kt`, `*QryHandler.kt`, and `*CliHandler.kt`.
7. Use repository/JPA/query infrastructure behind the handler.
8. Do not repair aggregate state from query paths.
9. Map persistence/read shapes to response DTOs without leaking transport concerns into domain code.
10. Run focused tests or compile checks.

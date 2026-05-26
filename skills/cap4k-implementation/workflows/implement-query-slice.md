# Implement Query Slice

1. Run the skeleton gate before writing code.
2. Stop and return to `cap4k-generation` when the read use case needs a missing `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, payload, or client skeleton that current generation supports, including generated handler surfaces.
3. Stop and return to `cap4k-generation` when DDL, enum, value-object, or type-registry facts already exist but the aggregate, repository, factory, specification, enum, value-object, or unique-helper skeleton is still missing. If relation or field-mapping behavior seems missing after those facts exist, that is still aggregate/entity generation drift, not a standalone skeleton plan item, and must return to `cap4k-generation`.
4. Stop and return to `cap4k-modeling` when the missing piece is a design entry, DDL contract, `types.registryFile`, `types.enumManifest`, or `types.valueObjectManifest` entry.
5. Stop and return to `cap4k-generation` when generation is blocked by missing KSP metadata output/config/setup.
6. Confirm the read contract and caller.
7. Keep query handlers read-oriented.
8. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Qry.kt`, `*QryHandler.kt`, and `*CliHandler.kt`.
9. Use repository/JPA/query infrastructure behind the handler.
10. Do not repair aggregate state from query paths.
11. Map persistence/read shapes to response DTOs without leaking transport concerns into domain code.
12. Run focused tests or compile checks.

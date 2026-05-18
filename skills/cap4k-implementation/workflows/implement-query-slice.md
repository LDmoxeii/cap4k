# Implement Query Slice

1. Run the skeleton gate before writing code.
2. Stop and return to `cap4k-generation` when the read use case needs a missing `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, payload, client, or validator skeleton that current generation supports, including generated handler surfaces.
3. Stop and return to `cap4k-generation` when DDL, enum, or type-registry facts already exist but the aggregate, repository, factory, specification, enum, relation, field-mapping, or unique-helper skeleton is still missing.
4. Stop and return to `cap4k-modeling` when the missing piece is a design entry, DDL contract, `types.registryFile` entry, or enum manifest entry.
5. Stop and return to `cap4k-generation` when generation is blocked by missing KSP metadata output/config/setup.
6. Confirm the read contract and caller.
7. Keep query handlers read-oriented.
8. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Qry.kt`, `*QryHandler.kt`, and `*CliHandler.kt`.
9. Use repository/JPA/query infrastructure behind the handler.
10. Do not repair aggregate state from query paths.
11. Map persistence/read shapes to response DTOs without leaking transport concerns into domain code.
12. Run focused tests or compile checks.

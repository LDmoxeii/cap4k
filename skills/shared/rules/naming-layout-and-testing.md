# Naming Layout And Testing

## Always True

- Make role inferable from file name plus directory.
- Separate domain behavior tests from application orchestration tests.
- Separate adapter mapping tests from runtime wiring tests.
- Separate generation evidence checks from behavior tests.
- Keep transport DTOs, external protocol details, query projections, and domain behavior in their proper layers.
- Keep runtime guidance self-contained.

## Drift Checks

- Prevent "place files by convenience or physical proximity."
- Prevent "one test type verifies every layer."
- Prevent "generation evidence replaces behavior tests."
- Prevent "opaque helper DSLs can hide business semantics."

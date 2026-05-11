# Implement Cap4k Project Slice

1. Confirm the active branch/worktree and read the latest approved spec/plan.
2. Inspect existing generated and handwritten ownership before editing.
3. Add or update focused tests before behavior implementation when behavior changes.
4. Apply generation or template changes before handwritten code that depends on them.
5. Keep command handlers responsible for write-side behavior and use `Mediator.uow` for persistence.
6. Keep process orchestration in application subscribers or orchestration surfaces.
7. Keep adapter code as transport/input/output glue.
8. Run focused tests and compile.
9. Run generation or analysis commands if their inputs or outputs changed.
10. Record exact evidence and residual risks.

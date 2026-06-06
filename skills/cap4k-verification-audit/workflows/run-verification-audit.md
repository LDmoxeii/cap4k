# Run Verification Audit

declare verification mode
list commands allowed by user/environment
run checks
map findings to rollback target
state skipped checks
limit final claim to evidence produced

## Coverage And Residual Risk Audit

- Classify coverage shape: domain behavior, application command boundary, adapter/integration smoke, generation/design contract, analysis/flow, static diff/path review.
- State critical behavior covered only indirectly through smoke coverage.
- Record ambiguous no-op outcomes where the evidence cannot prove why a command or flow retreated.
- Record heavy fixture coupling such as direct SQL fixtures, enum ordinal assertions, test ordering, or hand-rolled polling.
- Report residual behavior risk separately from command exit status.

Compile, test, analysis, HTTP, and generation evidence may be used only when
both user instruction and environment policy allow it. Otherwise report those
checks as skipped and limit the final claim to the evidence actually produced.

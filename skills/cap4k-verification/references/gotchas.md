# Verification Gotchas

- `cap4kAnalysisGenerate` does not replace `cap4kGenerate`.
- Analysis output proves observed relationships, not business correctness.
- Passing plan generation does not prove generated source compiles.
- Docs-only changes still need scans and `git diff --check`.

## Coordinator Command Smell

A command that reads state, branches, and sends multiple follow-up commands may be hiding process coordination. Review whether the flow should instead be driven by a domain event, external fact entry, job, or Saga. Command-to-command calls should remain local synchronous write-use-case reuse.

## Manual Reverse Replay In Handler

If Saga code still uses handler-level `try/catch` to replay reverse commands manually, review whether the flow should migrate to `execCompensableProcess(...)` plus explicit `requestCompensation(...)`. The current runtime's first-class slice is persisted compensation-oriented coordination, and compensable forward steps should persist reverse metadata instead of rebuilding reverse requests ad hoc during failure handling.

## Split Flow Output

If analysis flow output splits one business process across command and subscriber diagrams, do not automatically treat it as a code bug. Record whether the split is an expected projection of event-driven flow. If the output hides causality, reference cap4k issue #55 for investigation.

## Multi-Listener Failure Diagnostics

When multiple listeners react to one event, cap4k does not guarantee listener order. Review idempotency, zero-trust command validation, and error messages. Reference cap4k issue #56 when diagnostics make failures hard to identify.

Also review observability. A normal multi-listener retreat should show which listener woke up, which command was sent, whether the command applied work or no-oped, and why. Boolean-only no-op responses hide expected paths such as `NotPaidContent`, `NotPublicationReady`, `AlreadyStarted`, and `AlreadyPublished`.

## Smoke Test Coverage Masking

Spring, HTTP, callback, and integration smoke tests are useful runtime proof, but
they can hide missing command-boundary or aggregate-invariant tests. When a rule
is only proven by a full runtime path, report it as indirect coverage and decide
whether a focused domain or application test is needed.

## Heavy Fixture Coupling

Direct SQL fixtures, enum ordinal assertions, `@TestMethodOrder`, and
hand-rolled polling loops can be acceptable in smoke tests, but they carry
residual risk. Report that risk explicitly instead of treating the smoke test as
the only proof of command-level policy.

# Test Strategy

- Treat test execution and test-shape adequacy as separate audit questions.
  A green test command proves only that the current suite ran successfully; it
  does not prove that critical behavior is covered at the right layer.
- Classify verification evidence by shape:
  - domain behavior tests;
  - application orchestration or command-boundary tests;
  - adapter, HTTP, callback, message, or integration smoke tests;
  - generation or design contract tests;
  - analysis and flow evidence.
- Prefer domain behavior tests and application orchestration tests first.
- Use adapter or integration smoke tests when the project claims runnable HTTP, callback, message, or external event behavior.
- Smoke tests prove runtime wiring and cross-layer paths. They do not replace
  focused domain or application tests for critical policy.
- Domain behavior tests should cover positive state progression and negative
  aggregate lifecycle invariants.
- State-changing command handlers should have focused coverage for zero-trust
  validation, no-op, already-applied, and invariant-rejection paths.
- Event-driven continuation with multiple listeners should have focused coverage
  for independent listeners, idempotency, no ordering assumptions, and
  command-side retreat when a listener is not applicable.
- No-op outcomes should be typed or otherwise inspectable enough for tests to
  assert why a command retreated. If only a boolean result is available, report
  the residual precision risk.
- When critical behavior is covered only indirectly through a smoke test, report
  that as residual test risk instead of treating the suite as fully shaped.
- Keep test helpers thin; helpers should expose business semantics instead of hiding them behind opaque DSLs.
- Use generated analysis output to review relationships and flows, not to replace compile/tests.
- Avoid brittle line-by-line snapshots of generated analysis output.
- For docs-only or skill-only changes, use targeted scans, validation scripts, and `git diff --check`.
- For event-authoring changes, scan active skills and public authoring docs for removed automatic routing terms, direct outbound event delivery APIs, and wording that presents attachment and direct delivery as alternatives.

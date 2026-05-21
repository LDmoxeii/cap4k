# Test Strategy

- Prefer domain behavior tests and application orchestration tests first.
- Use adapter or integration smoke tests when the project claims runnable HTTP, callback, message, or external event behavior.
- Keep test helpers thin; helpers should expose business semantics instead of hiding them behind opaque DSLs.
- Use generated analysis output to review relationships and flows, not to replace compile/tests.
- Avoid brittle line-by-line snapshots of generated analysis output.
- For docs-only or skill-only changes, use targeted scans, validation scripts, and `git diff --check`.
- For event-authoring changes, scan active skills and public authoring docs for removed automatic routing terms, direct outbound event delivery APIs, and wording that presents attachment and direct delivery as alternatives.

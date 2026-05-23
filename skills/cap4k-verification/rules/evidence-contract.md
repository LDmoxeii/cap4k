# Evidence Contract

- Report exact commands, scope, exit status, and meaningful result.
- Do not claim completion without fresh verification evidence.
- For docs-only or skill-only changes, use targeted scans and `git diff --check`.
- When verifying event authoring guidance, include active skills and public authoring docs in the scan scope; historical specs and plans may be reported separately but must not drive authoring rules.
- When verifying Strong ID 1.0 guidance, scan public authoring docs, cap4k skills, and analysis maps for old default ID guidance. Any legacy compatibility mention must explicitly say it is not the default path.
- Disclose skipped compile, tests, generation, or analysis checks with reasons.
- Human audit remains required for domain decisions.

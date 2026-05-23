# Evidence Contract

- Report exact commands, scope, exit status, and meaningful result.
- Do not claim completion without fresh verification evidence.
- For docs-only or skill-only changes, use targeted scans and `git diff --check`.
- When verifying event authoring guidance, include active skills and public authoring docs in the scan scope; historical specs and plans may be reported separately but must not drive authoring rules.
- When verifying Strong ID 1.0 guidance, scan public authoring docs, cap4k skills, and analysis maps for old default ID guidance. Any legacy compatibility mention must explicitly say it is not the default path.
- Strong ID 1.0 verification must also confirm: generated aggregate-root IDs default to Strong ID types; same-context aggregate references use `@RefAggregate`; current-context identities for external concepts use `@RefId`; UoW save-time paths do not ask users to generate aggregate IDs; local language such as `AuthorId` is not replaced by cross-context `UserId` inside `Content`.
- Disclose skipped compile, tests, generation, or analysis checks with reasons.
- Human audit remains required for domain decisions.

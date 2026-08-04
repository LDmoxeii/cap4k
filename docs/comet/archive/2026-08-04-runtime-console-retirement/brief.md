# Outcome

Cap4k mainline no longer ships or documents `cap4k-ddd-console`. The framework exposes no Console-owned administrative HTTP or direct-SQL operations surface, while the existing programmatic reliable Command/Event recovery API remains available through its Runtime owners.

# Scope

- Delete the complete `cap4k-ddd-console` module, including its production sources, resources, auto-configuration metadata, tests, and module-local build definition.
- Remove every active repository integration for that module: Gradle settings/build wiring, dependency declarations, aggregate build/test references, and release/publication participation.
- Remove Console-owned Command/Event search and retry operations, Locker unlock operations, Snowflake search operations, direct SQL services, and unauthenticated HTTP administration endpoints.
- Remove active public documentation and Agent fact/manifest references that present Console or its administrative operations as an available capability.
- Keep the change bounded to the Console-retirement finding from `runtime-capability-reset`.

# Non-goals

- Do not modify the `docs/framework-capability-audit` branch or its worktree.
- Do not change the reliable Command/Event state machines, eligibility rules, retry/redrive semantics, persistence model, or programmatic recovery API.
- Do not remove or redesign Locker Runtime or Snowflake Runtime modules, starters, schemas, APIs, or behavior.
- Do not implement any other Runtime finding from the broader capability audit, including codec, transport, repository, identifier, or handler changes.
- Do not retain aliases, deprecated APIs, dual implementations, fallback codecs, compatibility modules, or migration bridges for Console.
- Do not rewrite historical design records merely to erase history; active public guidance and generated/machine-readable Agent facts must describe the post-retirement surface.

# Acceptance examples

- Given a fresh Gradle configuration of the repository, there is no `:cap4k-ddd-console` project, build, publication, dependency, or test task to resolve.
- Given Spring Boot auto-configuration discovery, no Console auto-configuration, Console service, or Console HTTP handler is discoverable.
- Given a consuming application, cap4k exposes no unauthenticated HTTP endpoint for Command/Event search or retry, Locker unlock, or Snowflake search, and no Console-owned direct SQL service implements those actions.
- Given Runtime code that uses the existing programmatic reliable Command/Event recovery API, the API remains present and its focused tests continue to pass without a Console compatibility layer.
- Given Locker and Snowflake Runtime consumers, their existing non-Console modules and contracts remain present and are not behaviorally changed by this slice.
- Given active public docs and Agent facts, Console is not listed as a supported module or operation; repository stale-surface checks find no active Console build/code/test/documentation/fact references.

# Constraints and invariants

- The implementation branch is `feature/runtime-console-retirement`, created in an isolated worktree from the latest `origin/master`.
- This repository has no external users, so deletion is intentionally breaking and complete.
- Ownership remains with existing Runtime modules: reliable recovery stays programmatic; Locker and Snowflake remain Runtime capabilities but lose only Console views/actions.
- Repository history and unrelated user work remain untouched.

# Decisions

- Retire Console as a module and capability rather than securing, replacing, or deprecating its HTTP surface.
- Remove all Console-specific operations in one slice; do not leave service-only, endpoint-only, or build-only remnants.
- Preserve the existing programmatic reliable Command/Event recovery API as the only retained recovery surface in this slice.
- Preserve Locker and Snowflake Runtime modules; only their Console search/unlock exposure is removed.
- Validate removal through focused owner tests, repository stale-surface checks, and the repository required check before delivery.
- The user explicitly confirmed this complete Shape contract on 2026-08-04 before implementation began.

# Open questions


# Verification expectations

- Run focused tests for the Runtime owners whose programmatic recovery contracts must remain available and for any Agent fact generation/contract surfaces changed by the removal.
- Run a repository-wide stale-surface check covering module names, Console packages/types, auto-configuration metadata, settings/build/publication wiring, active public docs, and Agent facts.
- Run the repository's required `check` contract using the same supported entrypoint expected for a non-docs-only pull request.
- Record exact commands, exit results, and any intentionally excluded historical records in `verification.md`; never report an unrun check as passed.

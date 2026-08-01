# Outcome

Re-audit the implementation delivered by PR #149 against the current application-execution and Hibernate UoW design authority, including the later PR #150 changes that touch the same runtime surfaces. Produce evidence that the merged mainline satisfies the complete target specification. If the audit identifies a confirmed contract violation and the user authorizes it, repair it in this change and verify the repair.

# Scope

- PR #149 implementation commit `c09ea808eb6450fabbfe3480d153a6c15c7bb2dc` and merge commit `cc63f566c4f632ca477e4bc634d979b053f3c5b6`, compared with the current `origin/master` implementation.
- ExecutionContext and InvocationScope; Query and Capability synchronous/asynchronous execution; Command transaction and UoW ownership; Repository and aggregate tracking; Hibernate stabilization, Strong IDs, delete, audit, Domain Event frontiers; reliable Command/Event and Integration Event context propagation; generator, starter, sample, documentation, and test surfaces affected by the contract.
- The canonical capability `application-execution-uow-stabilization` and this change's verification evidence.
- Focused source inspection, static searches, focused owner-module tests, and the repository-wide check required by the risk of this cross-cutting change.

# Non-goals

- No new application-execution architecture, provider SPI, ORM backend, compatibility layer, Saga runtime, or generic Request surface.
- No redesign of PR #150 managed-field or pipeline behavior; its archived Native contract is authoritative wherever the two changes overlap.
- No reliance on historical issue #115 or investigation issue #19 as implementation authority.
- No claim that unsupported direct EntityManager flush, bulk DML, native SQL, custom transaction synchronization, or arbitrary user executors gain framework guarantees.
- No silent weakening of an acceptance check when a focused reproduction fails.

# Acceptance examples

- Command owns one REQUIRED write transaction and one UoW Context; nested Commands reuse it and only the outer Coordinator stabilizes, commits, rolls back, and drains frontiers.
- Query has one Handler-wide read-only transaction covering validation, interceptors, Handler execution, lazy navigation, and DTO mapping; it creates no write UoW, audit, event drain, or flush.
- Capability is persistence-neutral, including when Caller Runs executes it on a Command thread; Query and Capability use separate bounded executors and one blocking Handler shape with sync/async supervisor APIs.
- Async Query/Capability failures complete exceptionally, while synchronous APIs preserve original exceptions; generic timeout does not promise cancellation. Async Command is durable later execution in a new transaction, never Caller Runs.
- ExecutionContext is immutable, typed, versioned, boundary-restricted attribution; InvocationScope is local strict-LIFO policy state. Neither propagates UoW, EntityManager, transaction, event state, or arbitrary ThreadLocals.
- Reliable and Integration Event records preserve origin context separately from payload, decode before worker scope/transaction setup, publish only after commit, and retain null-context compatibility as EMPTY.
- Application code has no public UoW lifecycle or save/persist/remove/flush surface, Repository persistence flags, `PersistIntent.EXISTING`, or `AggregateLoadPlan`; Command Repository reads observe managed roots without enrolling clean roots as writes.
- Hibernate MANUAL flush begins before Command validation/interceptors/Handler, remains through nested Commands and event frontiers, and is restored during cleanup; required Hibernate integration fails clearly rather than degrading.
- Persistence changes are aggregate-root organized with Entity detail; unchanged lazy relations remain unloaded, changed/deleting relations may be expanded, and ambiguous ownership, detached attachment, ID mutation, or detached root removal fails.
- Strong IDs are available at root/owned-child creation; CREATE then REMOVE before first synchronization folds to NONE and discards unreleased root events.
- Managed-field admission initializes and validates supported IDs before root `onCreate` or owned-child attachment; UoW validation is side-effect-free and never performs late allocation or repair.
- Audit uses one outer-UoW time and ExecutionContext snapshot; PR #150 qualifier-owned enrichers receive exact handles, enforce per-enricher dirty footprints, and run between candidate and final detection. Separate qualifiers have no semantic order; clean loaded aggregates receive no audit update.
- Domain Event dispatch is non-reentrant, causal-frontier based, sibling/Handler order independent, fail-fast, and limited to synchronous handlers; current-state reactions use nested Commands.
- Generated projects, starters, templates, docs, and active repo-local skills use the final Command/Query/Capability vocabulary and do not teach removed UoW, Repository, Client, Saga, or ordering-dependent APIs.

# Constraints and invariants

- `docs/superpowers/specs/2026-07-30-cap4k-application-execution-and-uow-stabilization-design.md` is the published design authority; the proposed target spec is a complete tracked restatement, not an incremental diff.
- `docs/comet/archive/2026-07-31-managed-field-policy-and-pipeline-extension/` is the archived Native evidence for PR #150 and overrides conflicting managed-field, admission, enrichment, and pipeline behavior.
- The current canonical spec is replaced only through Native Runtime-controlled progression; never edit Runtime-managed hashes or phase fields.
- Work remains on `fix/pr-149-comet-verification`, based on `origin/master`; do not mutate protected `master`.
- Evidence must use real commands and project-relative artifact references. Unrun checks are skipped honestly, and a failed check returns to Build.
- If implementation changes are authorized, keep them narrowly scoped to demonstrated violations and add a regression test in the owning module.

# Decisions

- The audit target is the merged PR #149 behavior as it exists in current mainline, with PR #150 overlap explicitly inspected.
- The current design's removed surfaces and Query/Capability/ExecutionContext semantics supersede conflicting older canonical/spec text.
- Acceptance is behavior-first: PR description checkboxes, historical plan checkmarks, and successful CI are evidence inputs but do not replace focused reproduction.
- Confirmed by the user: when a confirmed target-spec violation is found, implement the narrow fix in this change, add a regression test in the owning module, and rerun the affected verification.
- Confirmed by the user: PR #150's archived Native design and verification take precedence over PR #149's design wherever their managed-field, admission, enrichment, or pipeline contracts overlap.
- Confirmed by the user after the precedence update: proceed with this complete outcome, scope, acceptance, non-goals, and repair policy.

# Open questions

None.

# Verification expectations

- Inspect the complete diff and shared-file history for PR #149 and PR #150, then search active code, starters, tests, templates, docs, and repo-local skills for removed surfaces and stale semantics.
- Read the archived PR #150 Native brief/spec/verification and treat its managed-field admission, exact policy, qualifier/slot, mutation-footprint, pipeline-extension, and migration rules as higher-priority overlap evidence.
- Run focused owner-module tests for `ddd-core`, `ddd-domain-repo-jpa`, core/JPA starters, reliable Command/Domain Event, all Integration Event transports, code-analysis, pipeline API, and generated Bootstrap compilation as applicable to the final changed scope.
- Run `./gradlew.bat check --rerun-tasks --no-build-cache` when the implementation scope requires repository-wide verification; record any environment failure and immediate retry separately.
- Run `git diff --check`, stale-term checks, and removed-public-API searches; use `comet native check` for bounded text hygiene after Build seals the scope.
- Verify every Runtime-provided acceptance ID with project-relative evidence refs or an honest skipped reason.

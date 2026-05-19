# Cap4k Skill Shared Core Design

Date: 2026-05-19

Status: Proposed

Scope: internalize stable public authoring constraints into the repo-local cap4k skills without making AI runtime skills depend on `docs/public/authoring`.

## Background

The current cap4k skill architecture correctly separates human-facing authoring docs from AI runtime skills. The validation script already rejects wording that tells skills to read `docs/public/authoring` during normal operation.

That separation should stay.

The gap is that several stable authoring constraints are still only present in public docs or are scattered across focused skill files. The highest-impact example is the write boundary rule: one command path may read multiple facts for zero-trust validation, but only one aggregate root may enter the persistence write boundary.

This rule exists in focused implementation material, but it is not elevated as a shared first-screen constraint for modeling, generation, implementation, service integration, and verification.

## Problem

Focused cap4k skills currently optimize for local task execution:

- `cap4k-modeling` covers tactical modeling and event facts.
- `cap4k-generation` covers input contracts and output ownership.
- `cap4k-implementation` covers skeleton gates, layering, Mediator, and UoW.
- `cap4k-service-integration` covers external interaction boundaries.
- `cap4k-verification` covers evidence and tests.

This split is useful, but it leaves top-level project discipline under-activated:

- framework positioning and over-claim prevention are not on every normal route;
- single aggregate write boundary is too local to implementation;
- naming/layout responsibility rules are thin;
- default testing contract is too small;
- advanced concept gates are scattered;
- route validation checks stale wording, but does not require shared constraints to be present.

This is a skill architecture problem, not a public-doc problem. The fix is not to make skills read public authoring docs. The fix is to internalize stable constraints into a shared skill core.

## Source Policy

This slice treats the following as input material, not runtime dependencies:

1. current `cap4k/skills/**`;
2. stable constraints from `docs/public/authoring/**`;
3. current validation behavior in `skills/scripts/validate-cap4k-skills.ps1`;
4. `skill-based-architecture` rules for thin `SKILL.md` files, activation over storage, rules/workflows separation, and token efficiency.

The resulting skills must remain self-contained. They must not tell agents to read `docs/public/authoring` during normal operation.

## Design Goals

1. Add a shared cap4k skill core for stable constraints that all focused skills should activate.
2. Keep every `SKILL.md` concise and routing-oriented.
3. Keep Always Read small enough to be usable while still surfacing high-cost rules.
4. Preserve focused skill ownership: detailed generation facts stay in generation, detailed service-boundary facts stay in service integration, and implementation workflow detail stays in implementation.
5. Avoid copying stale examples into runtime skills.
6. Extend validation so shared core usage is checked, not just implied.

## Non-Goals

- no generator/runtime code changes;
- no public authoring rewrite;
- no direct `docs/public/authoring` runtime dependency;
- no example-chain hardcoding from `docs/public/authoring/examples`;
- no `.agents/skills` installation update in this repository slice;
- no replacement of existing focused skill directories.

## Shared Core Shape

Add:

```text
skills/shared/
└── rules/
    ├── core-positioning.md
    ├── default-path-and-write-boundaries.md
    ├── ownership-and-generation-flow.md
    ├── naming-layout-and-testing.md
    └── advanced-mode-gates.md
```

### `core-positioning.md`

Stable constraints:

- default path first, advanced concepts only after a clear boundary reason;
- AI must not present one project-specific shape as a framework default;
- human audit remains required for domain and architecture decisions;
- unsupported capability must be recorded as a gap instead of implied as working framework behavior.

### `default-path-and-write-boundaries.md`

Stable constraints:

- one command path may persist only one aggregate root;
- commands may read multiple aggregates or facts for zero-trust validation;
- other reads must not become shared write ownership;
- state-changing external entries, jobs, subscribers, and controllers route to commands;
- aggregate roots own write invariants and emit meaningful domain facts;
- domain events must not be modeled as technical continuation steps;
- callback and polling entries converge to the same internal command semantics.

### `ownership-and-generation-flow.md`

Stable constraints:

- default flow is `cap4kPlan` -> review `plan.json` -> `cap4kGenerate` -> handwritten completion -> verification -> review;
- missing generator-capable skeleton returns to generation;
- missing generation input contract returns to modeling;
- `src/main/kotlin` is not automatically handwritten;
- copied generated snapshots are evidence only, not active authoring surface;
- `outputKind`, `resolvedOutputRoot`, `templateId`, and `conflictPolicy` must be inspected before editing generated or checked-in skeletons.

Use "copied generated snapshots" wording to avoid the existing stale-text validator rule against spelling out the old snapshot path.

### `naming-layout-and-testing.md`

Stable constraints:

- files live in responsibility directories;
- file name plus directory should make the role inferable;
- do not place transport DTOs, query projections, or external protocol details in the wrong layer for convenience;
- default verification starts with domain behavior and application orchestration tests;
- helpers must stay thin and must not hide business semantics;
- analysis output assists review but does not replace compile/tests.

### `advanced-mode-gates.md`

Stable constraints:

- Domain Service, Saga, Strong ID, Read-only Weak Reference, and Value Object are not default shortcuts;
- each advanced concept requires a reason why default aggregate/command/event/query modeling is insufficient;
- Saga is for persisted long-running coordination, retry, recovery, compensation, or cross-time waiting;
- Strong ID is engineering reinforcement, not a substitute for aggregate and command boundaries;
- Value Object modeling should choose the business value first, then the persistence carrier.

## Focused Skill Activation

Update Always Read lists to activate the shared core on normal routes while respecting token efficiency.

Target:

- `cap4k-modeling`: `core-positioning`, `default-path-and-write-boundaries`, `advanced-mode-gates`, then local tactical modeling.
- `cap4k-generation`: `core-positioning`, `ownership-and-generation-flow`, then local input/output rules.
- `cap4k-implementation`: `default-path-and-write-boundaries`, `ownership-and-generation-flow`, then local skeleton/layering/UoW rules.
- `cap4k-service-integration`: `default-path-and-write-boundaries`, `naming-layout-and-testing`, then local service rules.
- `cap4k-verification`: `default-path-and-write-boundaries`, `ownership-and-generation-flow`, `naming-layout-and-testing`, then local evidence/test rules.
- `cap4k-authoring`: keep as router, but add a short boundary note that focused skills carry shared constraints.

This deliberately allows some skills to have more than three Always Read entries where the risk justifies it. The line budget remains under 100 lines per `SKILL.md`.

## Focused Rule Updates

Update existing rules so the shared core is not contradicted:

- `cap4k-implementation/rules/mediator-and-uow.md` must explicitly allow multi-aggregate reads for zero-trust validation while forbidding multiple aggregate roots in the write boundary.
- `cap4k-implementation/workflows/implement-command-slice.md` must make "exactly one aggregate root enters persistence" a checklist item.
- `cap4k-verification/rules/test-strategy.md` must include the default testing contract and thin helper boundary.
- `cap4k-modeling/rules/tactical-modeling.md` may keep advanced concept details local, but should point back to advanced-mode gates.

## Validation

Extend `skills/scripts/validate-cap4k-skills.ps1`:

- require the five shared core files to exist;
- require focused skill `SKILL.md` files to reference their expected shared rules;
- keep the existing ban on direct public authoring runtime dependency;
- add required shared-core phrase checks for:
  - one command path / one aggregate write boundary;
  - zero-trust validation reads;
  - plan before generate;
  - copied generated snapshots as evidence only;
  - default before advanced;
  - role-inferable naming/layout;
  - domain/application default tests.

## Acceptance Criteria

1. `skills/shared/rules/**` contains the five shared core files.
2. Focused skills activate shared core rules from their normal `Always Read` path.
3. `SKILL.md` files remain under 100 lines.
4. No skill tells agents to read `docs/public/authoring` during normal operation.
5. The one-command-one-persistent-aggregate rule is present in shared core and implementation workflow checks.
6. Multi-aggregate reads for zero-trust validation are explicitly allowed without weakening the single write boundary.
7. Skill validation enforces shared core existence and focused skill references.
8. `powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1` passes.
9. `git diff --check` passes.

## Risks

1. Always Read lists can grow too large. Mitigation: keep shared rules short and put detailed concept material in focused rules or references.
2. Shared core can duplicate focused rules. Mitigation: shared core states high-level constraints; focused skills keep task-specific detail.
3. Old examples can leak into runtime skill rules. Mitigation: avoid example-chain specifics and phrase rules as general cap4k authoring discipline.

# Cap4k Skill And Public Authoring Boundary Sync Second-Pass Delta Design

Date: 2026-05-18

Status: Proposed

Depends on:

- `docs/superpowers/specs/2026-05-18-cap4k-skill-authoring-boundary-sync-design.md`

## Why This Delta Exists

The first-pass PR fixed the main boundary failure: `cap4k-implementation` no longer treats missing generator-capable skeletons as a cue to handwrite them.

Review after that pass exposed a second problem class: several surfaces are now directionally correct, but they still contain fact drift, concept drift, or self-containment gaps.

This delta does not replace the first design. It narrows the remaining gaps before the PR merges.

## Confirmed Remaining Gaps

### 1. DB annotation facts are not fully flattened across touched surfaces

Code-backed parser behavior is stricter than several skill/public/analysis pages currently imply.

Confirmed from `cap4k-plugin-pipeline-source-db`:

- `@Parent` / `@P` require an explicit table value.
- `@AggregateRoot=true|false` / `@Root=true|false` / `@R=true|false` are the accepted aggregate-root forms; marker form without an explicit boolean is invalid.
- `@ValueObject` / `@VO` are marker-only and reject explicit values.
- `@Ignore` / `@I` are marker-only and reject explicit values.
- `@Type` / `@T` only become meaningful with an explicit type name; blank or marker-only forms are ignored.
- `@Enum` / `@E` only become meaningful with an explicit payload; explicit enum payload still requires `@T`, while blank or marker-only forms are ignored.
- `@Reference` / `@Ref`, `@Relation` / `@Rel`, `@Lazy` / `@L`, and `@Count` / `@C` all require explicit values when present.
- `@Relation`, `@Lazy`, and `@Count` require `@Reference`.
- `@Deleted`, `@Version`, `@Managed`, and `@Exposed` are marker-only and reject explicit values.
- `@GeneratedValue` may be a marker or one of `uuid7`, `snowflake-long`, `identity`, or `database-identity`.
- Legacy `@IdGenerator`, `@IG`, and `@SoftDeleteColumn` are rejected.

Some current pages still describe looser or incomplete forms and need correction.

### 2. `domain_event` generation output is still understated outside the skill

Code-backed planner behavior is:

- `DesignDomainEventHandlerArtifactPlanner` generates checked-in subscriber/handler shells for `domain_event`.
- `DesignIntegrationEventSubscriberArtifactPlanner` generates inbound subscriber shells for `integration_event`.

The first pass aligned the skill reference, but some public-doc and analysis wording still treats `domain_event` as payload-only or fails to state the generated shell explicitly.

### 3. `relation` and `field-mapping` are still described like standalone skeleton outputs

This is concept drift, not just wording drift.

The actual model is:

- relation facts and field-mapping facts belong to aggregate/entity generation inputs;
- they are not standalone `plan.json` output families that implementation should expect to appear by themselves.

The implementation skill and any mirrored public/analysis wording must stop naming them as if they were standalone generated skeletons.

### 4. Source references are still too syntax-heavy

The first-pass `skills/cap4k-generation/references/sources/*.md` files reduced guessing, but several of them still behave more like parser cheat sheets than self-contained source contracts.

The main gaps are:

- `db-schema.md` lists annotation shapes but does not explain each annotation's role, non-role, typical use, or common misuse.
- `design-json.md` lists supported tags but under-documents validator constraints, reserved fields, and manifest-file failure cases already enforced by code.
- `types-registry.md` gives the minimum shape but not the loader rejection rules that matter during authoring.

The second pass should make these references self-contained enough that an installed skill does not need adjacent public docs for the touched topics.

## Second-Pass Scope

### A. Implementation skill wording cleanup

Update:

- `skills/cap4k-implementation/rules/source-of-truth-and-skeletons.md`
- `skills/cap4k-implementation/workflows/implement-command-slice.md`
- `skills/cap4k-implementation/workflows/implement-query-slice.md`
- `skills/cap4k-implementation/workflows/implement-subscriber-or-job.md`

Required change:

- stop describing `relation` and `field-mapping` as standalone missing skeletons;
- describe them as aggregate/entity input facts whose resulting aggregate-family outputs belong to generation.

### B. Generation reference hardening

Update:

- `skills/cap4k-generation/references/sources/source-map.md`
- `skills/cap4k-generation/references/sources/db-schema.md`
- `skills/cap4k-generation/references/sources/design-json.md`
- `skills/cap4k-generation/references/sources/types-registry.md`

Required change:

- flatten the DB annotation fact contract to match parser behavior;
- explicitly state `domain_event` subscriber/handler shell output;
- add semantic guidance per source, not only syntax;
- add validator/manifest/type-registry rejection rules already enforced by code.

### C. Public authoring and analysis resync

Update touched pages so they match the same code-backed facts:

- `docs/public/authoring/project-authoring-workflow.md`
- `docs/public/authoring/generation-boundaries.md`
- `docs/public/authoring/generator/input-sources.md`
- `docs/public/authoring/generator/code-generation.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-generator-input-output-and-verification-map.md`
- optionally `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md` only if wording touched in this pass needs the same sync

Required change:

- public and analysis wording must reflect the same parser/planner truth as the skill;
- touched analysis pages should keep derived-fact wording and avoid inventing new normative guidance where code evidence is enough.

## Non-Goals

- no generator feature development;
- no change to the already accepted first-pass boundary direction;
- no change to `.agents/skills`;
- no broad analysis-system redesign outside the touched pages.

## Acceptance

This second pass is complete only when all of the following are true:

1. Touched skill/public/analysis surfaces use DB annotation wording that matches current parser behavior.
2. Touched skill/public/analysis surfaces state that `domain_event` can generate subscriber/handler shells.
3. Touched implementation/public/analysis surfaces no longer describe `relation` or `field-mapping` as standalone generated skeleton outputs.
4. `db-schema.md`, `design-json.md`, and `types-registry.md` each explain enough semantics that they are more than syntax cards.
5. `powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1` still passes.
6. `git diff --check` stays clean.

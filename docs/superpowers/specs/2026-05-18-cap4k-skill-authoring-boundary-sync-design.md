# Cap4k Skill And Public Authoring Boundary Sync Design

Date: 2026-05-18

Status: Proposed

Scope: harden the generation vs implementation boundary across cap4k skills and Chinese public authoring docs, primarily in:

- `skills/cap4k-implementation`
- `skills/cap4k-generation`
- `docs/public/authoring`
- `docs/superpowers/analysis`

This slice also deletes the current English mirror pages under `docs/public/authoring/*.en.md`.

Out of scope:

- editing `only-workspace/.agents/skills`
- generator feature development
- new design tags such as first-class `value_object` or `domain_service`
- broad `cap4k-authoring` skill redesign
- internationalization replacement for deleted English pages

## Problem

During paid publication saga work in `cap4k-reference-content-studio`, the current `cap4k-implementation` skill proved too soft about generator-capable skeleton boundaries.

Current failure mode:

- the skill reminds the agent to inspect `design.json` and `plan.json`
- but when a needed skeleton is missing, the agent can still handwrite a generator-capable surface
- that violates the intended boundary of design-driven generation plus handwritten implementation

This is not just one bad example. The current skill split is asymmetric:

- `cap4k-generation` already says generator-supported surfaces should come from source inputs plus regeneration
- `cap4k-implementation` does not yet hard-stop on missing skeletons or missing generation inputs

The result is predictable drift:

- implementation can invent command, query, subscriber, handler, or aggregate-adjacent skeletons that should have come from generation
- generation input gaps can be silently bypassed by handwritten files instead of being sent back to modeling
- public authoring docs and installed skills can diverge on where the hard boundary actually is

## Source Of Truth Policy

This slice must treat repository code as the only source of truth.

Fact priority:

1. source code, templates, DSL, tests, and current skill files
2. `docs/superpowers/analysis`
3. `docs/public/authoring`

Implications:

- analysis files are derived evidence, not authority
- if analysis and code disagree, code wins
- every new rule added to a skill or public authoring page must be reconfirmed from code
- if code review during this slice reveals stable facts missing from analysis, analysis should be updated in the same PR
- if an analysis page currently says something softer or wrong, it should be corrected in the same PR

This policy must be reflected in both the work itself and the resulting wording.

## Goals

1. Make `cap4k-implementation` stop before handwritten creation of generator-capable skeletons.
2. Make `cap4k-generation` self-contained enough that installed skills do not depend on nearby public docs to understand input contracts.
3. Align Chinese public authoring docs with the hardened boundary language.
4. Remove the current English mirror pages instead of maintaining a softer duplicate contract.
5. Keep analysis material as a derived, code-backed evidence layer for future doc and skill work.

## Core Boundary Contract

The boundary to enforce is:

- implementation does not create generator-capable skeletons
- implementation only fills already existing, ownership-clear generated or project-owned skeletons
- if facts already exist but the skeleton is missing, return to generation
- if the missing piece is the fact that generation should consume, return to modeling
- handwritten work is allowed only on surfaces the current generator does not support, and that exception must be stated explicitly in review notes or final notes

More specifically:

- missing `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `subscriber`, `validator`, `*QryHandler.kt`, or `*CliHandler.kt` surfaces should route back to `cap4k-generation`
- missing aggregate, repository, factory, specification, enum, field-mapping, relation, or unique-helper skeletons should route back to `cap4k-generation`
- missing design entries, DDL table or column annotations, enum manifest definitions, `types.registryFile`, or KSP metadata should route back to `cap4k-modeling`

The key distinction is:

- if the question is "what business fact should the generator consume?" the answer belongs to modeling
- if the question is "the fact exists, why did the generator not produce the needed skeleton?" the answer belongs to generation

## Current Code Facts To Preserve

The design wording must match current repository reality.

Relevant confirmed facts include:

- `types.registryFile` belongs to `types {}` rather than `sources {}` and still participates in generation input contracts
- `sources.designJson`, `sources.db`, `sources.enumManifest`, `sources.kspMetadata`, and `sources.irAnalysis` are distinct provider families with different responsibilities
- `sources.irAnalysis` is for flow and drawing-board style observation after compile, not for main business source generation
- `GENERATED_SOURCE` is force-resolved to `OVERWRITE` by the shared pipeline runner and exporter
- aggregate behavior scaffolds are planned with fixed `SKIP`
- aggregate factory and specification scaffolds are checked-in artifacts whose effective conflict policy can depend on template-level policy resolution
- inbound `integration_event` generation can produce subscriber skeletons; outbound integration events do not subscribe to themselves
- addon artifacts are planned through the same pipeline and appear in `plan.json`, but addon artifacts are not business modeling inputs

## Detailed Design

### 1. `cap4k-implementation` hardening

Add:

- `skills/cap4k-implementation/rules/source-of-truth-and-skeletons.md`

Update:

- `skills/cap4k-implementation/SKILL.md`
- `skills/cap4k-implementation/workflows/implement-command-slice.md`
- `skills/cap4k-implementation/workflows/implement-query-slice.md`
- `skills/cap4k-implementation/workflows/implement-subscriber-or-job.md`
- optionally `skills/cap4k-implementation/references/gotchas.md` when a pitfall needs to be surfaced directly

Required behavior:

- `Always Read` must include the new boundary rule file
- each implementation workflow must begin with a skeleton gate, not with "confirm intent" alone
- the skeleton gate must explicitly classify the work as:
  - safe to implement now
  - missing generator-capable skeleton, return to generation
  - missing generation input contract, return to modeling

The new rule file should define the source-of-truth map for implementation decisions:

- SQL schema / DDL is the source for aggregate, repository, factory, specification, enum, field mapping, relation, uniqueness, and value-object carrier generation
- `design.json` is the source for use-case and interface surfaces such as command, query, client, payload, domain event, integration event, subscriber, and validator families
- `types.registryFile`, enum manifest, and KSP metadata are generation input contracts even when they are configured outside one unified `sources {}` block
- implementation is not allowed to handwrite around missing contracts

### 2. `cap4k-generation` self-contained source references

Add:

- `skills/cap4k-generation/references/sources/source-map.md`
- `skills/cap4k-generation/references/sources/design-json.md`
- `skills/cap4k-generation/references/sources/db-schema.md`
- `skills/cap4k-generation/references/sources/enum-manifest.md`
- `skills/cap4k-generation/references/sources/types-registry.md`
- `skills/cap4k-generation/references/sources/ksp-and-analysis.md`
- `skills/cap4k-generation/references/sources/addons.md`

Update:

- `skills/cap4k-generation/SKILL.md`
- `skills/cap4k-generation/rules/input-contracts.md`

Intent:

- `rules/input-contracts.md` stays short and directive
- the new `references/sources/` folder becomes the self-contained source contract reference
- installed skills should no longer require adjacent public authoring docs to understand these input boundaries

Content contract for each new reference:

- `source-map.md`
  - one routing table that says what each source or adjacent contract is responsible for
  - what it does not cover
  - when to return to modeling
  - when to stay in generation
- `design-json.md`
  - supported tags only
  - common fields
  - `integration_event` constraints
  - manifest-file mode
  - unsupported tags such as `value_object` and `domain_service`
- `db-schema.md`
  - table annotations
  - column annotations
  - relation annotations
  - unique constraints
  - value-object carrier rules such as `@T` and `@VO`
- `enum-manifest.md`
  - JSON shape
  - relationship to DB `@T`
  - duplicate type rejection
  - enum translation is not core generation
- `types-registry.md`
  - `types.registryFile`
  - FQN mapping
  - converter kinds
  - JSON-backed and inline value mapping boundaries
- `ksp-and-analysis.md`
  - KSP metadata supports design-driven generation
  - IR analysis remains post-compile observation, not business source generation input
- `addons.md`
  - addons contribute artifacts and plan items
  - addons are not business modeling sources
  - addon outputs still participate in normal ownership, template override, and `plan.json` review

`SKILL.md` route changes:

- generation from DB should route to `db-schema.md`
- generation from design should route to `design-json.md`
- enum and custom type questions should route to `enum-manifest.md` and `types-registry.md`
- KSP / IR / addon confusion should route to the dedicated references
- source-of-truth uncertainty should route first to `source-map.md`

### 3. Chinese public authoring sync

Update Chinese pages only:

- `docs/public/authoring/project-authoring-workflow.md`
- `docs/public/authoring/generation-boundaries.md`
- `docs/public/authoring/generator/input-sources.md`
- `docs/public/authoring/generator/code-generation.md`
- `docs/public/authoring/index.md` or `docs/public/authoring/default-happy-path.md`

Delete:

- `docs/public/authoring/*.en.md`

Required wording changes:

- before implementation starts, the workflow must state that missing generator-capable skeletons stop implementation and route back to generation
- missing generation input contracts must route back to modeling
- `cap4kGenerate` must be described as materializing already-modeled inputs into skeletons, not as a late-stage substitute for handwritten missing files
- `types.registryFile` must be classified correctly as an input contract outside `sources {}`
- the top-level authoring entry should contain a harder summary sentence for the design-driven generation plus handwritten implementation boundary

The docs should stay user-facing, but they must stop being softer than the skill contract.

### 4. Analysis updates

Update only where code-verified facts require it.

This slice should not create a broad new analysis system.
It should:

- correct any analysis wording that conflicts with code
- add code-backed notes that support the new skill and doc wording
- keep analysis reusable for future work without pretending it is authoritative

The existing uncommitted file:

- `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md`

should be reviewed and included in the same PR instead of being left behind on `master`.

## Delivery Plan

This work should be done in one branch and one PR inside:

- `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`

Constraints:

- do not modify `only-workspace/.agents/skills`
- do not continue working on `master`
- after merge, reinstall the updated skills from `cap4k/skills` into the workspace copy

## Verification

This slice should verify consistency, not claim unrelated build coverage.

Minimum checks:

1. `git diff --check`
2. route and reference existence checks for updated skills
3. link and reference scans for changed public authoring pages after English-page deletion
4. skill validation if the existing repository script is applicable:
   - `skills/scripts/validate-cap4k-skills.ps1`

Targeted review expectations:

- the new implementation rule must appear in `Always Read`
- each implementation workflow must show the skeleton gate before normal implementation steps
- `cap4k-generation` routes must point at the new source references
- public docs must consistently distinguish modeling vs generation vs implementation fallback
- no remaining English mirror pages should be linked from the surviving Chinese entry points

## Residual Risks

1. Deleting English mirrors is a deliberate scope cut. Readers who relied on them lose that entry point until a future internationalization pass.
2. Some existing analysis wording may be broader or looser than the code-backed contract. This slice should fix what it touches, but it will not fully rewrite every historical analysis page.
3. Public docs will still be human-facing summaries. The self-contained AI execution contract should live in skills, not be pushed back into authoring pages as a runtime dependency.

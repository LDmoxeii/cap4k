# cap4k Skills System Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `skills/` as a self-contained, route-driven cap4k agent execution system for business authoring.

**Architecture:** Use a clean-cut migration from the old focused-skill tree to the detailed design in `docs/superpowers/specs/2026-06-04-cap4k-skills-system-redesign.md`. `cap4k-authoring/routing.yaml` becomes the only routing source, `SKILL.md` files stay thin, shared rules/workflows/references carry runtime knowledge, and validation is split into static checks under `skills/scripts/checks/`.

**Tech Stack:** Markdown Agent Skills, YAML routing manifest, PowerShell validation scripts, static `rg`/Git verification. Do not run Gradle, build, compile, application, HTTP, install, or runtime tasks unless the user explicitly overrides this plan.

---

## Required Context For Every Worker

Every worker or subagent must read these before editing:

1. `C:\Users\LD_moxeii\.codex\skills\skill-based-architecture\SKILL.md`
2. `C:\Users\LD_moxeii\.codex\skills\skill-based-architecture\references\progressive-rigor.md`
3. `docs/superpowers/specs/2026-06-04-cap4k-skills-system-redesign.md`

Every worker must state its write scope before editing. Do not edit outside that scope.

## File Structure Map

Create this final tree:

```text
skills/
  cap4k-authoring/
    SKILL.md
    routing.yaml
  cap4k-business-discovery/
    SKILL.md
    workflows/discover-business-intent.md
    references/business-signals.md
  cap4k-tactical-modeling/
    SKILL.md
    workflows/map-tactical-carriers.md
    references/modeling-gotchas.md
  cap4k-technical-design/
    SKILL.md
    workflows/write-technical-design-contract.md
    references/technical-design-contract.md
  cap4k-generator-inputs/
    SKILL.md
    workflows/project-generator-inputs.md
    references/input-surfaces.md
  cap4k-generation-review/
    SKILL.md
    rules/generation-stop-policy.md
    workflows/review-plan-and-generate.md
    references/plan-review-gotchas.md
  cap4k-handwritten-implementation/
    SKILL.md
    rules/implementation-entry-gates.md
    workflows/implement-inside-generated-skeletons.md
    references/implementation-gotchas.md
  cap4k-verification-audit/
    SKILL.md
    rules/verification-claim-policy.md
    workflows/run-verification-audit.md
    references/evidence-modes.md
  cap4k-service-integration/
    SKILL.md
    rules/published-language.md
    rules/integration-event-boundaries.md
    workflows/design-open-host-service.md
    workflows/consume-external-capability.md
    workflows/handle-inbound-integration-event.md
    references/gotchas.md
  shared/
    rules/cap4k-positioning.md
    rules/generated-skeleton-ownership.md
    rules/layer-and-runtime-boundaries.md
    rules/generator-input-source-of-truth.md
    rules/naming-layout-and-testing.md
    rules/verification-claim-policy.md
    workflows/skeleton-generation-gate.md
    workflows/forced-rollback.md
    references/tactical-affordance-map.md
    references/generator-supported-skeletons.md
    references/output-ownership-taxonomy.md
    references/runtime-capability-map.md
    references/drift-gotchas.md
  scripts/
    validate-cap4k-skills.ps1
    checks/structure.ps1
    checks/routing.ps1
    checks/progressive-loading.ps1
    checks/self-contained-runtime.ps1
    checks/skeleton-gate-refs.ps1
    checks/stale-terms.ps1
    checks/link-check.ps1
```

Delete these old directories after their useful content has been migrated:

```text
skills/cap4k-modeling
skills/cap4k-generation
skills/cap4k-generated-output-review
skills/cap4k-implementation
skills/cap4k-verification
```

Rebuild these in place:

```text
skills/cap4k-authoring
skills/cap4k-service-integration
skills/shared
skills/scripts
```

## Verification Commands Used Throughout

Run these static commands after relevant tasks:

```powershell
git status --short -uall
git diff --check -- skills docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
rg -n "T[B]D|T[O]DO|PLACEH[O]LDER|F[I]XME|historical[-]decision|historical[ ]decision|Continue[ ]Brainstorming|Open[ ]Decisions|implement[ ]later|fill[ ]in[ ]details|similar[ ]to" skills docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
```

Expected:

```text
git status shows only files expected for the current task
git diff --check exits 0
rg exits 1 with no matches
```

After Task 7, also run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected:

```text
cap4k skill validation passed
```

---

### Task 1: Create Clean Routing Source And Thin Authoring Entry

**Files:**
- Modify: `skills/cap4k-authoring/SKILL.md`
- Modify: `skills/cap4k-authoring/routing.yaml`
- Delete: `skills/cap4k-authoring/references/route-map.md`

- [ ] **Step 1: Read required context and inspect current router**

Run:

```powershell
Get-Content -Path "C:\Users\LD_moxeii\.codex\skills\skill-based-architecture\SKILL.md" -Raw
Get-Content -Path "C:\Users\LD_moxeii\.codex\skills\skill-based-architecture\references\progressive-rigor.md" -Raw
Get-Content -Path docs/superpowers/specs/2026-06-04-cap4k-skills-system-redesign.md -Raw
Get-Content -Path skills/cap4k-authoring/SKILL.md -Raw
Get-Content -Path skills/cap4k-authoring/routing.yaml -Raw
```

Expected:

```text
Worker can state that routing.yaml is the only routing source and route-map.md must be removed.
```

- [ ] **Step 2: Replace `cap4k-authoring/SKILL.md` with thin router content**

Write this exact runtime shape, preserving frontmatter:

```markdown
---
name: cap4k-authoring
description: >
  Route cap4k business-authoring work through the self-contained cap4k skill
  system. Use when the user asks to discover a business slice, model DDD
  boundaries, write cap4k technical design, author generator inputs, review
  plan or generated output, implement handwritten business logic, design service
  integration, or verify cap4k work.
---

# Cap4k Authoring Router

This is the entry router for cap4k business-authoring agents. It is not the rulebook.

## Always Read

1. `routing.yaml`
2. `../shared/workflows/forced-rollback.md`

## Session Discipline

- Re-read this file and `routing.yaml` for every new user task.
- Do not use public docs, analysis maps, issues, Context7, or historical specs as runtime instructions.
- Route to the current phase skill before acting.
- For structural creation or modification, make sure the routed workflow loads `../shared/workflows/skeleton-generation-gate.md`.

## Routing Source

`routing.yaml` is the only routing source of truth. Do not maintain a second route table in Markdown.

## Priority

1. Current user instruction and explicit project scope.
2. `routing.yaml` phase and specialist route.
3. Routed skill rules and workflows.
4. Existing business project conventions.
5. Human audit remains required for domain decisions.
```

- [ ] **Step 3: Replace `routing.yaml` with canonical route manifest**

Use these route ids and route targets:

```yaml
version: 1
default_entry: cap4k-authoring
routes:
  - id: full-authoring-flow
    trigger_examples:
      - "build a cap4k project from scratch"
      - "take this business idea through cap4k authoring"
      - "model, generate, implement, and verify this cap4k slice"
    positive_signals:
      - "from scratch"
      - "end-to-end"
      - "full authoring"
      - "new cap4k project"
    negative_signals:
      - "only verify this work"
      - "review plan.json before generation"
      - "fill this generated handler"
    route_first: cap4k-business-discovery
    then_chain:
      - cap4k-tactical-modeling
      - cap4k-technical-design
      - cap4k-generator-inputs
      - cap4k-generation-review
      - cap4k-handwritten-implementation
      - cap4k-verification-audit
    required_reads:
      - ../shared/workflows/forced-rollback.md
    workflow: ../cap4k-business-discovery/workflows/discover-business-intent.md
    rollback_targets:
      - business-discovery
      - tactical-modeling
      - technical-design
      - generator-inputs
      - generation-review
      - handwritten-implementation
      - verification-audit

  - id: business-discovery
    trigger_examples:
      - "clarify this business idea before modeling"
      - "discover the business slice"
      - "what are the actors, states, and external facts?"
    route_first: cap4k-business-discovery
    required_reads:
      - ../shared/workflows/forced-rollback.md
    workflow: ../cap4k-business-discovery/workflows/discover-business-intent.md
    rollback_targets:
      - business-discovery

  - id: tactical-modeling
    trigger_examples:
      - "model the aggregate boundaries"
      - "map this business process to cap4k DDD carriers"
      - "decide Command, Query, Event, Saga, or Value Object"
    route_first: cap4k-tactical-modeling
    required_reads:
      - ../shared/references/tactical-affordance-map.md
      - ../shared/workflows/forced-rollback.md
    workflow: ../cap4k-tactical-modeling/workflows/map-tactical-carriers.md
    rollback_targets:
      - business-discovery
      - tactical-modeling

  - id: technical-design
    trigger_examples:
      - "write the cap4k technical design"
      - "place this model into layers and modules"
      - "decide skeleton expectations and handwritten slots"
    route_first: cap4k-technical-design
    required_reads:
      - ../shared/rules/layer-and-runtime-boundaries.md
      - ../shared/references/generator-supported-skeletons.md
      - ../shared/workflows/skeleton-generation-gate.md
    workflow: ../cap4k-technical-design/workflows/write-technical-design-contract.md
    rollback_targets:
      - tactical-modeling
      - technical-design

  - id: generator-inputs
    trigger_examples:
      - "write design.json"
      - "project this design into generator inputs"
      - "author DB schema, manifests, Gradle extension, or addons"
    route_first: cap4k-generator-inputs
    required_reads:
      - ../shared/rules/generator-input-source-of-truth.md
      - ../shared/references/generator-supported-skeletons.md
      - ../shared/workflows/skeleton-generation-gate.md
    workflow: ../cap4k-generator-inputs/workflows/project-generator-inputs.md
    rollback_targets:
      - technical-design
      - generator-inputs

  - id: generation-review
    trigger_examples:
      - "review cap4kPlan"
      - "review generated output ownership"
      - "run generation after plan review"
    positive_signals:
      - "plan.json"
      - "bootstrap-plan.json"
      - "conflictPolicy"
      - "templateId"
      - "generated output"
    negative_signals:
      - "implement business logic before generation review"
    route_first: cap4k-generation-review
    required_reads:
      - ../shared/references/output-ownership-taxonomy.md
      - ../shared/references/drift-gotchas.md
      - ../shared/workflows/skeleton-generation-gate.md
    workflow: ../cap4k-generation-review/workflows/review-plan-and-generate.md
    rollback_targets:
      - technical-design
      - generator-inputs
      - generation-review

  - id: handwritten-implementation
    trigger_examples:
      - "fill the generated command handler"
      - "implement handwritten logic inside generated skeletons"
      - "continue implementation after generated diff review"
    positive_signals:
      - "human approved generated output"
      - "inside generated skeleton"
      - "handwritten slot"
    negative_signals:
      - "missing generated skeleton"
      - "create handler to fix compile"
    route_first: cap4k-handwritten-implementation
    required_reads:
      - ../shared/rules/generated-skeleton-ownership.md
      - ../shared/references/generator-supported-skeletons.md
      - ../shared/references/runtime-capability-map.md
      - ../shared/workflows/skeleton-generation-gate.md
    workflow: ../cap4k-handwritten-implementation/workflows/implement-inside-generated-skeletons.md
    rollback_targets:
      - technical-design
      - generator-inputs
      - generation-review

  - id: verification-audit
    trigger_examples:
      - "verify this cap4k work"
      - "audit generated vs handwritten ownership"
      - "produce final cap4k evidence"
    positive_signals:
      - "final evidence"
      - "verification claim"
      - "skipped checks"
      - "static-only"
      - "focused-local"
      - "full-evidence"
    negative_signals:
      - "review plan.json before generation"
      - "inspect generated output before implementation"
      - "is this generated output safe to edit?"
    route_first: cap4k-verification-audit
    required_reads:
      - ../shared/rules/verification-claim-policy.md
      - ../shared/references/runtime-capability-map.md
      - ../shared/references/drift-gotchas.md
      - ../shared/workflows/forced-rollback.md
    workflow: ../cap4k-verification-audit/workflows/run-verification-audit.md
    verification_mode: static-only
    rollback_targets:
      - business-discovery
      - tactical-modeling
      - technical-design
      - generator-inputs
      - generation-review
      - handwritten-implementation

  - id: service-integration
    trigger_examples:
      - "design an Open Host Service"
      - "handle an external callback"
      - "consume an external capability"
      - "model an inbound integration event"
    positive_signals:
      - "Open Host Service"
      - "Published Language"
      - "external fact"
      - "callback"
      - "message"
      - "integration event"
    route_first: cap4k-service-integration
    required_reads:
      - ../shared/rules/layer-and-runtime-boundaries.md
      - ../shared/references/runtime-capability-map.md
      - ../cap4k-service-integration/rules/integration-event-boundaries.md
    specialist_handoffs:
      - cap4k-technical-design
      - cap4k-generator-inputs
      - cap4k-handwritten-implementation
      - cap4k-verification-audit
    rollback_targets:
      - business-discovery
      - tactical-modeling
      - technical-design
```

- [ ] **Step 4: Remove duplicate Markdown route map**

Delete:

```text
skills/cap4k-authoring/references/route-map.md
```

Expected:

```text
No route table remains outside routing.yaml.
```

- [ ] **Step 5: Verify and commit Task 1**

Run:

```powershell
git diff --check -- skills/cap4k-authoring
rg -n "route-map|Route Map|\\| If the user asks" skills/cap4k-authoring
```

Expected:

```text
git diff --check exits 0
rg exits 1 with no duplicate route map
```

Commit:

```powershell
git add -- skills/cap4k-authoring
git commit --no-verify -m "docs: rebuild cap4k authoring router"
```

---

### Task 2: Rebuild Shared Rules And Core Workflows

**Files:**
- Delete and recreate: `skills/shared/rules/*.md`
- Create: `skills/shared/workflows/skeleton-generation-gate.md`
- Create: `skills/shared/workflows/forced-rollback.md`
- Transitional quality fix: update still-present old skill consumers and `skills/scripts/validate-cap4k-skills.ps1` so this intermediate state does not point at deleted shared rules before later deletion/rebuild tasks.

- [ ] **Step 1: Read required context and current shared rules**

Run:

```powershell
Get-Content -Path "C:\Users\LD_moxeii\.codex\skills\skill-based-architecture\SKILL.md" -Raw
Get-Content -Path docs/superpowers/specs/2026-06-04-cap4k-skills-system-redesign.md -Raw
Get-ChildItem -Path skills/shared -Recurse -File | Select-Object FullName
```

Expected:

```text
Worker can state that shared/ is aggressively rebuilt as rules/workflows/references.
```

- [ ] **Step 1A: Repair intermediate consumers before commit**

Until Tasks 4-8 remove or rebuild the old focused skills and validator, update their `Always Read` lists and current validation expectations to reference the new shared rules/workflows. Do not re-add deleted shared rule files and do not create compatibility aliases.

Required transitional `Always Read` mapping:

```text
skills/cap4k-modeling/SKILL.md:
../shared/rules/cap4k-positioning.md
../shared/rules/layer-and-runtime-boundaries.md
../shared/workflows/forced-rollback.md

skills/cap4k-generation/SKILL.md:
../shared/rules/generator-input-source-of-truth.md
../shared/rules/generated-skeleton-ownership.md
../shared/workflows/skeleton-generation-gate.md

skills/cap4k-implementation/SKILL.md:
../shared/rules/layer-and-runtime-boundaries.md
../shared/rules/generated-skeleton-ownership.md
../shared/workflows/skeleton-generation-gate.md

skills/cap4k-verification/SKILL.md:
../shared/rules/verification-claim-policy.md
../shared/rules/generated-skeleton-ownership.md
../shared/rules/naming-layout-and-testing.md

skills/cap4k-service-integration/SKILL.md:
../shared/rules/layer-and-runtime-boundaries.md
../shared/rules/naming-layout-and-testing.md
../shared/workflows/forced-rollback.md
```

Also update the temporary current-state validator so it requires the six new shared rule files, the two shared workflow files, the transitional skill references above, and wording from the rebuilt shared files.

- [ ] **Step 2: Replace shared rules**

Create these files with the responsibilities below:

```text
skills/shared/rules/cap4k-positioning.md
skills/shared/rules/generated-skeleton-ownership.md
skills/shared/rules/layer-and-runtime-boundaries.md
skills/shared/rules/generator-input-source-of-truth.md
skills/shared/rules/naming-layout-and-testing.md
skills/shared/rules/verification-claim-policy.md
```

Required content contract:

```markdown
# <Rule Name>

## Always True

- Use short imperative rules.
- Do not link to docs/public, analysis maps, issues, Context7, or historical specs as runtime prerequisites.

## Drift Checks

- List exact phrases or misconceptions this rule prevents.
```

Each file must include these facts:

```text
cap4k-positioning.md:
- cap4k is a DDD tactical framework plus generator-backed authoring system.
- It is not generic CRUD.
- Generator stabilizes structure; humans decide business semantics.

generated-skeleton-ownership.md:
- Skeletons are generated by cap4k.
- Complex business logic belongs inside generated skeleton surfaces.
- Parallel handwritten structure requires explicit technical design exception.

layer-and-runtime-boundaries.md:
- Domain owns business invariants.
- Application owns use-case orchestration.
- Adapter owns protocol shape mapping.
- Start owns runtime assembly.
- Repository handles aggregate read/access/load.
- Unit of Work owns persistence intent, delete intent, commit, and save.
- Mediator is a framework facade.
- Framework/runtime integration-event transport handles consume/parse/register/dispatch.
- Business inbound subscriber handles typed external fact interpretation, idempotency, semantic translation, and delegation.

generator-input-source-of-truth.md:
- DB/schema, design JSON, manifests, Gradle extension, addons/options are generator inputs.
- plan.json is generated evidence.
- analysis outputs are observation evidence.

naming-layout-and-testing.md:
- File name plus directory should make role inferable.
- Tests must separate domain behavior, application orchestration, adapter mapping, runtime wiring, generation evidence.

verification-claim-policy.md:
- Claims must match evidence mode.
- Static-only evidence is not full verification.
- Disclose skipped checks.
```

- [ ] **Step 3: Create `skeleton-generation-gate.md`**

Required sections:

```markdown
# Skeleton Generation Gate

## Trigger Before
## Gate Questions
## Allowed Pass States
## Failure Action
## Evidence To Record
```

Copy the trigger list, seven gate questions, allowed pass states, and failure action from `docs/superpowers/specs/2026-06-04-cap4k-skills-system-redesign.md`.

Add this evidence block:

```markdown
## Evidence To Record

- Technical design section that expects this skeleton.
- Generator input file or source that should produce it.
- Plan item proving output path, outputKind, templateId, and conflictPolicy.
- Explicit exception if handwritten.
- Verification command or review that checked ownership.
```

- [ ] **Step 4: Create `forced-rollback.md`**

Required content:

```markdown
# Forced Rollback

Later phases test earlier assumptions. Do not continue forward when evidence invalidates an earlier phase.

| Symptom | Roll Back To |
|---|---|
| concept mismatch | cap4k-tactical-modeling |
| unclear carrier | cap4k-technical-design |
| missing input | cap4k-generator-inputs |
| plan mismatch | cap4k-generator-inputs or cap4k-technical-design |
| generation ownership conflict | cap4k-generation-review |
| implementation bypass | cap4k-technical-design |
| structure drift | earliest phase that introduced the wrong assumption |
| verification drift | earliest phase that introduced the wrong assumption |
```

- [ ] **Step 5: Remove old shared rule files**

Delete:

```text
skills/shared/rules/advanced-mode-gates.md
skills/shared/rules/core-positioning.md
skills/shared/rules/default-path-and-write-boundaries.md
skills/shared/rules/ownership-and-generation-flow.md
```

Keep no compatibility alias files.

- [ ] **Step 6: Verify and commit Task 2**

Run:

```powershell
git diff --check -- skills/shared
rg -n "docs/public|docs/superpowers/analysis|Context7|historical specs|route-map" skills/shared
```

Expected:

```text
git diff --check exits 0
rg exits 1 unless the only matches are explicit forbidden-runtime-dependency warnings in drift sections
```

Commit:

```powershell
git add -- skills/shared
git commit --no-verify -m "docs: rebuild shared cap4k skill rules"
```

---

### Task 3: Create Shared References

**Files:**
- Create: `skills/shared/references/tactical-affordance-map.md`
- Create: `skills/shared/references/generator-supported-skeletons.md`
- Create: `skills/shared/references/output-ownership-taxonomy.md`
- Create: `skills/shared/references/runtime-capability-map.md`
- Create: `skills/shared/references/drift-gotchas.md`

- [ ] **Step 1: Read source pages for extracted facts**

Run:

```powershell
Get-Content -Path docs/public/authoring/index.md -Raw
Get-Content -Path docs/public/authoring/technical-design.md -Raw
Get-Content -Path docs/public/generator/planning-and-ownership-review.md -Raw
Get-Content -Path docs/public/reference/plan-json.md -Raw
Get-Content -Path docs/public/concepts/modeling-building-blocks/integration-event.md -Raw
Get-Content -Path docs/superpowers/analysis/runtime-and-integration-map.md -Raw
Get-Content -Path docs/superpowers/analysis/source-and-generator-contract-map.md -Raw
```

Expected:

```text
Worker has current public and analysis source facts without making these runtime dependencies.
```

- [ ] **Step 2: Create `tactical-affordance-map.md`**

Use this table header:

```markdown
# Tactical Affordance Map

| Business Signal | Cap4k Carrier | Use When | Do Not Use When | Generator Input Surface | Expected Plan Evidence | Handwritten Logic Location | Verification Evidence | Rollback Trigger |
|---|---|---|---|---|---|---|---|---|
```

Include rows for:

```text
state-changing intent -> Command
read-only view -> Query
business lifecycle owner -> Aggregate
identity/value semantics -> Value Object or Strong ID
internal fact -> Domain Event
external published fact -> Integration Event
reaction after event -> Subscriber
long-running/recoverable process -> Saga
scheduled or polling trigger -> Scheduled Reaction
cross-service call -> External Capability
stable external contract -> Open Host Service / Published Language
pre-save constraint -> Specification
domain collaboration not owned by one aggregate -> Domain Service
```

Each row must fill every column. No empty cells.

- [ ] **Step 3: Create ownership and runtime references**

Create:

```text
generator-supported-skeletons.md
output-ownership-taxonomy.md
runtime-capability-map.md
drift-gotchas.md
```

Required content:

```text
generator-supported-skeletons.md:
- list command/query/client/api payload/domain event/integration event/subscriber/domain service/saga/aggregate/entity/factory/specification/repository/controller/job/projection families
- say missing supported skeleton returns to generator inputs or technical design

output-ownership-taxonomy.md:
- checked-in skeleton
- build-owned generated source
- generated snapshot/evidence
- template override
- handwritten logic
- conflictPolicy review rule

runtime-capability-map.md:
- Repository read/access/load only
- Unit of Work persistence/delete intent and commit/save
- Mediator framework facade
- Specification pre-save through UoW interception
- Integration Event transport split
- Saga runtime scope
- analysis evidence as observation

drift-gotchas.md:
- stale KSP wording
- old analysis enabled switches
- design validator wording
- integration event transport assigned to business subscriber
- Repository save ownership
- UoW/Mediator implementation wording
- src-generated/main/kotlin
- client/cli stale boundary
- spaced analysis output paths
```

- [ ] **Step 3A: Activate high-risk shared references from route paths**

After creating the shared references, update route or workflow paths so high-risk references are reachable when relevant and are not only stored background. At minimum, `routing.yaml` route `required_reads` must include:

```text
technical-design -> ../shared/references/generator-supported-skeletons.md
generator-inputs -> ../shared/references/generator-supported-skeletons.md
generation-review -> ../shared/references/drift-gotchas.md
handwritten-implementation -> ../shared/references/generator-supported-skeletons.md, ../shared/references/runtime-capability-map.md
verification-audit -> ../shared/references/runtime-capability-map.md, ../shared/references/drift-gotchas.md
service-integration -> ../shared/references/runtime-capability-map.md
```

Expected:

```text
High-risk shared references are activated through routed task paths without adding them to `cap4k-authoring/SKILL.md` Always Read.
```

- [ ] **Step 4: Verify and commit Task 3**

Run:

```powershell
rg -n "\\|\\s*\\|" skills/shared/references/tactical-affordance-map.md
rg -n "docs/public|docs/superpowers/analysis|Context7" skills/shared/references
git diff --check -- skills/shared/references
```

Expected:

```text
affordance table exists
runtime dependency scan has no required-read wording
git diff --check exits 0
```

Commit:

```powershell
git add -- skills/shared/references
git commit --no-verify -m "docs: add shared cap4k skill references"
```

---

### Task 4: Create Discovery, Modeling, And Technical Design Phase Skills

**Files:**
- Create: `skills/cap4k-business-discovery/**`
- Create: `skills/cap4k-tactical-modeling/**`
- Create: `skills/cap4k-technical-design/**`
- Delete: `skills/cap4k-modeling/**`
- Modify: `skills/scripts/validate-cap4k-skills.ps1` as a temporary current-state validator update
- Modify: `docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md`
- Narrow stale-reference cleanup only:
  - `skills/cap4k-implementation/rules/source-of-truth-and-skeletons.md`
  - `skills/cap4k-implementation/workflows/implement-command-slice.md`
  - `skills/cap4k-implementation/workflows/implement-query-slice.md`
  - `skills/cap4k-implementation/workflows/implement-subscriber-or-job.md`
  - `skills/cap4k-generation/rules/input-contracts.md`
  - `skills/cap4k-generation/references/sources/source-map.md`

Task 4 sequencing fixes:

- Create `skills/cap4k-business-discovery/references/business-signals.md` and activate it from the business discovery `Always Read` list. The file is part of the global file structure map and must not be omitted.
- After deleting `skills/cap4k-modeling`, update the temporary validator so the current intermediate state validates the three new phase skills and no longer requires `cap4k-modeling`.
- Clear stale `cap4k-modeling` route references only in the listed old generation/implementation files. Those old files remain until later tasks, but after Task 4 they must not point to a deleted runtime skill.
- Validation must hard-fail if `skills/cap4k-modeling` exists, even if it contains a valid `SKILL.md`; no compatibility alias may remain.
- Code-spanned route names must be real skill IDs. Do not write combined route phrases such as one code span containing two skill names joined with `or`.

- [ ] **Step 1: Read context and old modeling content**

Run:

```powershell
Get-Content -Path "C:\Users\LD_moxeii\.codex\skills\skill-based-architecture\SKILL.md" -Raw
Get-Content -Path "C:\Users\LD_moxeii\.codex\skills\skill-based-architecture\references\progressive-rigor.md" -Raw
Get-Content -Path docs/superpowers/specs/2026-06-04-cap4k-skills-system-redesign.md -Raw
Get-ChildItem -Path skills/cap4k-modeling -Recurse -File | ForEach-Object { $_.FullName }
Get-Content -Path skills/cap4k-modeling/SKILL.md -Raw
```

Expected:

```text
Worker can identify useful modeling workflows to migrate and old directory to delete.
```

- [ ] **Step 2: Create `cap4k-business-discovery`**

Create `SKILL.md` with:

```markdown
---
name: cap4k-business-discovery
description: >
  Discover business intent for a cap4k business slice before tactical modeling.
  Use when the user describes a business idea, workflow, policy, external fact,
  or desired behavior and needs the authoring process to start.
---

# Cap4k Business Discovery

Capture business intent before choosing cap4k tactical carriers.

## Always Read

1. `../shared/workflows/forced-rollback.md`
2. `workflows/discover-business-intent.md`
3. `references/business-signals.md`

## Rules

- Do not choose Aggregate boundaries in this phase.
- Mark high-risk words for tactical modeling.
- Ask for missing business facts before projecting generator inputs.
- Route to `cap4k-tactical-modeling` when the business brief is coherent.
```

Create `workflows/discover-business-intent.md` with sections:

```markdown
# Discover Business Intent

## Inputs
## Questions To Ask
## Business Brief Output
## Risk Words To Mark
## Exit Criteria
```

Business Brief Output must include the fields from the spec: goal, actors, vocabulary, state changes, read needs, external facts, policies, open decisions.

Create `references/business-signals.md` with concrete examples of business intent signals, risk words, and missing facts to ask about. Keep it self-contained and do not require public docs or analysis maps at runtime.

- [ ] **Step 3: Create `cap4k-tactical-modeling`**

Create `SKILL.md` with always-read:

```text
../shared/references/tactical-affordance-map.md
../shared/workflows/forced-rollback.md
workflows/map-tactical-carriers.md
```

Create `workflows/map-tactical-carriers.md` requiring:

```text
business brief input
carrier selection table
Command/Query split
Domain Event vs Integration Event
Subscriber/Saga/Scheduled Reaction decision
Domain Service/Specification decision
rollback notes
```

Create `references/modeling-gotchas.md` with the high-risk words from the detailed spec and the rule that generic DDD concepts must map to cap4k carriers.

- [ ] **Step 4: Create `cap4k-technical-design`**

Create `SKILL.md` with always-read:

```text
../shared/rules/layer-and-runtime-boundaries.md
../shared/workflows/skeleton-generation-gate.md
workflows/write-technical-design-contract.md
```

Create `references/technical-design-contract.md` containing the required headings:

```text
businessIntent
ubiquitousLanguage
aggregateBoundaries
cap4kCarriers
cleanArchitecturePlacement
generatorInputPlan
skeletonExpectations
handwrittenLogicSlots
ownershipExceptions
verificationEvidence
rollbackTriggers
openDecisions
```

Create `workflows/write-technical-design-contract.md` requiring the worker to fill every heading before generator input authoring starts.

- [ ] **Step 5: Delete old modeling directory**

Delete:

```text
skills/cap4k-modeling
```

Expected:

```text
No cap4k-modeling compatibility wrapper remains.
```

- [ ] **Step 6: Update current temporary validator and stale references**

Update `skills/scripts/validate-cap4k-skills.ps1` so it no longer requires `cap4k-modeling` and instead validates:

```text
cap4k-business-discovery:
../shared/workflows/forced-rollback.md
workflows/discover-business-intent.md
references/business-signals.md

cap4k-tactical-modeling:
../shared/references/tactical-affordance-map.md
../shared/workflows/forced-rollback.md
workflows/map-tactical-carriers.md

cap4k-technical-design:
../shared/rules/layer-and-runtime-boundaries.md
../shared/workflows/skeleton-generation-gate.md
workflows/write-technical-design-contract.md
```

Keep validation for still-present old generation, implementation, service-integration, and verification skills until later tasks replace them.

Replace stale `cap4k-modeling` references only in the narrow cleanup files listed in this task. Use the precise new phase target:

```text
business clarification or concept discovery -> cap4k-business-discovery
tactical carrier, boundary, or modeling decision -> cap4k-tactical-modeling
placement, skeleton expectation, generator input prerequisite, or design contract -> cap4k-technical-design
mixed old sentence -> split into separate ownership statements; each code-spanned route name must be one real skill ID
```

Also update the temporary validator to hard-fail when `skills/cap4k-modeling` exists and to forbid stale `cap4k-modeling` references under `skills`.

- [ ] **Step 7: Verify and commit Task 4**

Run:

```powershell
git diff --check -- skills/cap4k-business-discovery skills/cap4k-tactical-modeling skills/cap4k-technical-design skills/cap4k-modeling skills/scripts/validate-cap4k-skills.ps1 docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md skills/cap4k-implementation/rules/source-of-truth-and-skeletons.md skills/cap4k-implementation/workflows/implement-command-slice.md skills/cap4k-implementation/workflows/implement-query-slice.md skills/cap4k-implementation/workflows/implement-subscriber-or-job.md skills/cap4k-generation/rules/input-contracts.md skills/cap4k-generation/references/sources/source-map.md
Test-Path skills/cap4k-modeling
rg -n "cap4k-modeling" skills
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected:

```text
git diff --check exits 0
Test-Path returns False
rg exits 1 with no cap4k-modeling references under skills
validation exits 0 with pass message
```

Commit:

```powershell
git add -- skills/cap4k-business-discovery skills/cap4k-tactical-modeling skills/cap4k-technical-design skills/cap4k-modeling skills/scripts/validate-cap4k-skills.ps1 docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md skills/cap4k-implementation/rules/source-of-truth-and-skeletons.md skills/cap4k-implementation/workflows/implement-command-slice.md skills/cap4k-implementation/workflows/implement-query-slice.md skills/cap4k-implementation/workflows/implement-subscriber-or-job.md skills/cap4k-generation/rules/input-contracts.md skills/cap4k-generation/references/sources/source-map.md
git commit --no-verify -m "docs: add cap4k discovery modeling design skills"
```

---

### Task 5: Create Generator Inputs And Generation Review Skills

**Files:**
- Create: `skills/cap4k-generator-inputs/**`
- Create: `skills/cap4k-generation-review/**`
- Delete: `skills/cap4k-generation/**`
- Delete: `skills/cap4k-generated-output-review/**`

- [ ] **Step 1: Read old generation and generated-output review content**

Run:

```powershell
Get-ChildItem -Path skills/cap4k-generation -Recurse -File | ForEach-Object { $_.FullName }
Get-ChildItem -Path skills/cap4k-generated-output-review -Recurse -File | ForEach-Object { $_.FullName }
Get-Content -Path skills/cap4k-generation/SKILL.md -Raw
Get-Content -Path skills/cap4k-generated-output-review/SKILL.md -Raw
```

Expected:

```text
Worker identifies reusable source references and review gotchas before deleting old directories.
```

- [ ] **Step 2: Create `cap4k-generator-inputs`**

Create `SKILL.md` with always-read:

```text
../shared/rules/generator-input-source-of-truth.md
../shared/workflows/skeleton-generation-gate.md
workflows/project-generator-inputs.md
```

Create `references/input-surfaces.md` listing:

```text
DB/schema
design/design.json
value-object manifest
enum manifest
Gradle extension
addons/options
template override decisions
```

Quality fix: keep `references/input-surfaces.md` compact but include high-risk identity and reference contracts: Strong ID defaults, same-context aggregate references, local external-reference IDs, legacy generated-value compatibility, and rollback to `cap4k-technical-design` before plan review when those facts are unclear.

Create `workflows/project-generator-inputs.md` requiring:

```text
read technical design contract
read ../references/input-surfaces.md before editing generator inputs
identify required generator input surface
update input only when design supports it
return to technical design if a carrier is unclear
return to cap4k-tactical-modeling if the business concept no longer maps cleanly
run or request cap4kPlan only after inputs are coherent
```

- [ ] **Step 3: Create `cap4k-generation-review`**

Create `SKILL.md` with always-read:

```text
rules/generation-stop-policy.md
../shared/references/output-ownership-taxonomy.md
workflows/review-plan-and-generate.md
```

Sequencing fix: `rules/generation-stop-policy.md` must be activated directly by this skill. Route-level `routing.yaml` already activates the shared skeleton gate and shared drift gotchas for generation review, so do not duplicate them in `SKILL.md`.

Create `rules/generation-stop-policy.md` with:

```markdown
# Generation Stop Policy

- Generation may run only after plan review gate allows it.
- After generation, stop for human review of generated diff, ownership, and plan-output alignment.
- Do not continue into handwritten implementation after generation.
- Resume implementation only after the user explicitly authorizes it.
```

Create `workflows/review-plan-and-generate.md` with steps:

```text
read technical design and generator inputs
read ../references/plan-review-gotchas.md before reviewing plan or output
run cap4kPlan only when the user instruction and environment policy permit runtime/generation commands
request permission or report the exact cap4kPlan command instead of executing when not permitted
review plan.json/bootstrap-plan.json
classify output ownership
check conflictPolicy/templateId/module placement/outputKind
run cap4kGenerate only when the user instruction and environment policy permit runtime/generation commands and plan review gate allows it
request permission or report the exact cap4kGenerate command instead of executing when not permitted
after cap4kGenerate, stop for human review and do not continue to handwritten implementation
```

Create `references/plan-review-gotchas.md` with gotchas from old `cap4k-generated-output-review` and `cap4k-generation`.

- [ ] **Step 4: Delete old generation directories**

Delete:

```text
skills/cap4k-generation
skills/cap4k-generated-output-review
```

- [ ] **Step 5: Update current temporary validator and stale references**

Update `skills/scripts/validate-cap4k-skills.ps1` so:

- it hard-fails if the deleted generation or generated-output review directories are reintroduced;
- it scans root runtime shell files such as `AGENTS.md` for removed skill references, not only `skills/**`;
- it validates current references for `cap4k-generator-inputs` and `cap4k-generation-review`;
- it no longer requires the old generation or generated-output review skill names;
- it still validates all current skills in the intermediate tree.

Clean stale references under `AGENTS.md` and `skills/**` so no runtime shell or skill text points to the removed generation or generated-output review route names. References to `cap4k-generation-review` remain valid.

- [ ] **Step 6: Verify and commit Task 5**

Run:

```powershell
git diff --check -- AGENTS.md skills/cap4k-generator-inputs skills/cap4k-generation-review skills/cap4k-generation skills/cap4k-generated-output-review skills/scripts/validate-cap4k-skills.ps1 docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
Test-Path skills/cap4k-generation
Test-Path skills/cap4k-generated-output-review
rg -n "cap4k-generated-output-review|generated-output-review" AGENTS.md skills
rg -n "cap4k-generation($|[^-])" AGENTS.md skills
rg -n "references/input-surfaces.md|plan-review-gotchas.md" skills/cap4k-generator-inputs skills/cap4k-generation-review
rg -n "when allowed" skills/cap4k-generation-review/workflows/review-plan-and-generate.md
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected:

```text
git diff --check exits 0
both Test-Path commands return False
both rg scans exit 1 with no old skill references
activation scan exits 0
ambiguous permission scan exits 1
validation exits 0 with pass message
```

Commit:

```powershell
git add -- skills/cap4k-generator-inputs skills/cap4k-generation-review skills/cap4k-generation skills/cap4k-generated-output-review skills/scripts/validate-cap4k-skills.ps1 docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
git commit --no-verify -m "docs: add cap4k generator input review skills"
```

---

### Task 6: Create Implementation And Verification Phase Skills

**Files:**
- Create: `skills/cap4k-handwritten-implementation/**`
- Create: `skills/cap4k-verification-audit/**`
- Delete: `skills/cap4k-implementation/**`
- Delete: `skills/cap4k-verification/**`

Sequencing / activation fix:

- `rules/implementation-entry-gates.md` must be in the handwritten implementation Always Read set so implementation authorization is not stored but inactive.
- `references/evidence-modes.md` must be in the verification audit Always Read set so verification claim limits are loaded before final claims.
- Route-level `routing.yaml` already activates shared Skeleton Generation Gate, generator-supported skeletons, forced rollback, drift gotchas, and runtime capability map for these routes; do not duplicate those into the phase `SKILL.md` files.
- `AGENTS.md` and the temporary validator must switch to the new focused skill IDs before deleting old directories to avoid broken intermediate references.

- [ ] **Step 1: Read old implementation and verification content**

Run:

```powershell
Get-ChildItem -Path skills/cap4k-implementation -Recurse -File | ForEach-Object { $_.FullName }
Get-ChildItem -Path skills/cap4k-verification -Recurse -File | ForEach-Object { $_.FullName }
Get-Content -Path skills/cap4k-implementation/SKILL.md -Raw
Get-Content -Path skills/cap4k-verification/SKILL.md -Raw
```

- [ ] **Step 2: Create `cap4k-handwritten-implementation`**

Create `SKILL.md` with always-read:

```text
rules/implementation-entry-gates.md
../shared/rules/generated-skeleton-ownership.md
workflows/implement-inside-generated-skeletons.md
```

Create `rules/implementation-entry-gates.md`:

```markdown
# Implementation Entry Gates

- Human has reviewed generated output.
- User explicitly authorized implementation.
- Technical design identifies handwritten slots.
- Skeleton Generation Gate passes for structural changes.
- Missing generator-supported skeleton returns to generator inputs or technical design.
```

Create `workflows/implement-inside-generated-skeletons.md`:

```text
confirm human review authorization
identify generated skeleton and handwritten slot
avoid creating parallel structure
use Repository only for aggregate access
use Unit of Work for persistence/delete intent and commit
use Mediator as framework facade when routing internal command/query
return to earlier phase when skeleton or ownership is wrong
```

Also activate `references/implementation-gotchas.md` from this workflow with a
clear relative path, such as `Read ../references/implementation-gotchas.md
before editing handwritten logic.`

Create `references/implementation-gotchas.md` from old command/query/subscriber gotchas, but remove repeated skeleton-gate copies and replace them with a link to shared gate.

- [ ] **Step 3: Create `cap4k-verification-audit`**

Create `SKILL.md` with always-read:

```text
../shared/rules/verification-claim-policy.md
references/evidence-modes.md
workflows/run-verification-audit.md
```

Create `references/evidence-modes.md`:

```markdown
# Evidence Modes

| Mode | Allowed Evidence | Claim Limit |
|---|---|---|
| static-only | text review, diff review, path review, plan/output review, drift scan | structural/static audit only |
| focused-local | targeted generation, analysis, compile, or focused tests when user permits | focused verified surface |
| full-evidence | broader compile/test/analysis/HTTP evidence when explicitly authorized | full evidence for the checked scope |
```

Create `workflows/run-verification-audit.md`:

```text
declare verification mode
list commands allowed by user/environment
run checks
map findings to rollback target
state skipped checks
limit final claim to evidence produced
```

Make command permission explicit: compile/test/analysis/HTTP/generation evidence may be used only when user instruction and environment policy allow it; otherwise report skipped checks and limit claims.

- [ ] **Step 4: Delete old implementation and verification directories**

Delete:

```text
skills/cap4k-implementation
skills/cap4k-verification
```

- [ ] **Step 5: Update root shell, validator, and stale refs**

Update:

```text
AGENTS.md
skills/scripts/validate-cap4k-skills.ps1
```

Ensure root routing and runtime skill text no longer contain exact old implementation or verification skill references. The validator should fail if either old directory is reintroduced, require current refs for the new implementation and verification-audit skills, scan `AGENTS.md` and `skills/**` for removed skill references, and continue validating all current skills.

- [ ] **Step 6: Verify and commit Task 6**

Run:

```powershell
git diff --check -- AGENTS.md skills/cap4k-handwritten-implementation skills/cap4k-verification-audit skills/cap4k-implementation skills/cap4k-verification skills/scripts/validate-cap4k-skills.ps1 docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
Test-Path skills/cap4k-implementation
Test-Path skills/cap4k-verification
rg -n "cap4k-implementation($|[^-])|cap4k-verification($|[^-])" AGENTS.md skills
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected:

```text
git diff --check exits 0
both Test-Path commands return False
rg exits 1 with no old skill references
validation exits 0 with pass message
```

Commit:

```powershell
git add -- AGENTS.md skills/cap4k-handwritten-implementation skills/cap4k-verification-audit skills/cap4k-implementation skills/cap4k-verification skills/scripts/validate-cap4k-skills.ps1 docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
git commit --no-verify -m "docs: add cap4k implementation verification skills"
```

---

### Task 7: Rebuild Service Integration Specialist

**Files:**
- Modify: `skills/cap4k-service-integration/SKILL.md`
- Modify/Create: `skills/cap4k-service-integration/rules/*.md`
- Modify/Create: `skills/cap4k-service-integration/workflows/*.md`
- Modify: `skills/cap4k-service-integration/references/gotchas.md`

- [x] **Step 1: Read old service integration content and current public boundary**

Run:

```powershell
Get-ChildItem -Path skills/cap4k-service-integration -Recurse -File | ForEach-Object { $_.FullName }
Get-Content -Path skills/cap4k-service-integration/SKILL.md -Raw
Get-Content -Path docs/public/concepts/modeling-building-blocks/integration-event.md -Raw
Get-Content -Path docs/public/architecture/dependency-rules.md -Raw
```

- [x] **Step 2: Replace specialist `SKILL.md`**

Create thin content with always-read:

```text
rules/integration-event-boundaries.md
../shared/rules/layer-and-runtime-boundaries.md
```

Routes in this skill:

```text
Open Host Service -> workflows/design-open-host-service.md
external capability consumption -> workflows/consume-external-capability.md
inbound integration event -> workflows/handle-inbound-integration-event.md
```

- [x] **Step 3: Create service rules**

Create `rules/published-language.md`:

```text
Published Language is a stable boundary language for external readers.
Do not expose internal Aggregate shape as the external contract.
Version and compatibility decisions belong in technical design.
```

Create `rules/integration-event-boundaries.md`:

```text
Framework/runtime transport handles HTTP/message consume, parse, register, dispatch.
Application inbound subscriber handles typed external fact interpretation, idempotency, semantic translation, delegation.
Domain does not receive raw callback payload or protocol fields.
Outbound Integration Event expresses confirmed internal facts in published language.
```

- [x] **Step 4: Create service workflows**

Create:

```text
workflows/design-open-host-service.md
workflows/consume-external-capability.md
workflows/handle-inbound-integration-event.md
```

Each workflow must include:

```text
input signals
technical design fields to update
generator input implications
handwritten slots
verification evidence
rollback target
```

`handle-inbound-integration-event.md` must explicitly reject:

```text
business subscriber manually implementing framework transport consume/register
raw callback payload entering domain
callback/message transport assigned to domain/application business logic
```

- [x] **Step 5: Verify and commit Task 7**

Run:

```powershell
git diff --check -- skills/cap4k-service-integration skills/scripts/validate-cap4k-skills.ps1 docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md skills/cap4k-authoring/routing.yaml
powershell -NoProfile -ExecutionPolicy Bypass -File skills\scripts\validate-cap4k-skills.ps1
rg -n "consume/register.*business|raw callback.*domain|adapter/application" skills/cap4k-service-integration
```

Expected:

```text
git diff --check exits 0
validation script exits 0
rg exits 1 with no boundary drift
```

Commit:

```powershell
git add -- skills/cap4k-service-integration skills/scripts/validate-cap4k-skills.ps1 docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
git commit --no-verify -m "docs: rebuild cap4k service integration skill"
```

Completion notes:

- Rebuilt the service-integration specialist as the final thin specialist with two Always Read entries and three routed workflows.
- Replaced the old service-integration structure with final Published Language, Integration Event boundary, Open Host Service, external capability, and inbound Integration Event guidance.
- Updated the validator to require the new specialist references and reject stale legacy service-integration files without creating static scan self-matches.
- `skills/cap4k-authoring/routing.yaml` was updated with `negative_signals` so the high-risk service-integration route has positive signals, negative signals, and rollback targets.

---

### Task 8: Replace Validation Script With Modular Static Checks

**Files:**
- Modify: `skills/scripts/validate-cap4k-skills.ps1`
- Create: `skills/scripts/checks/structure.ps1`
- Create: `skills/scripts/checks/routing.ps1`
- Create: `skills/scripts/checks/progressive-loading.ps1`
- Create: `skills/scripts/checks/self-contained-runtime.ps1`
- Create: `skills/scripts/checks/skeleton-gate-refs.ps1`
- Create: `skills/scripts/checks/stale-terms.ps1`
- Create: `skills/scripts/checks/link-check.ps1`

- [x] **Step 1: Read current validation script**

Run:

```powershell
Get-Content -Path skills/scripts/validate-cap4k-skills.ps1 -Raw
```

- [x] **Step 2: Replace entrypoint with check dispatcher**

`validate-cap4k-skills.ps1` must:

```powershell
$ErrorActionPreference = 'Stop'

$checkDir = Join-Path $PSScriptRoot 'checks'
$checks = @(
  'structure.ps1',
  'routing.ps1',
  'progressive-loading.ps1',
  'self-contained-runtime.ps1',
  'skeleton-gate-refs.ps1',
  'stale-terms.ps1',
  'link-check.ps1'
)

foreach ($check in $checks) {
  $path = Join-Path $checkDir $check
  if (-not (Test-Path -LiteralPath $path)) {
    throw "Missing validation check: $path"
  }
  & $path
}

Write-Host "cap4k skill validation passed."
```

- [x] **Step 3: Implement `structure.ps1`**

Check required directories and absence of old directories:

```text
required: cap4k-authoring, cap4k-business-discovery, cap4k-tactical-modeling, cap4k-technical-design, cap4k-generator-inputs, cap4k-generation-review, cap4k-handwritten-implementation, cap4k-verification-audit, cap4k-service-integration, shared, scripts
forbidden: cap4k-modeling, cap4k-generation, cap4k-generated-output-review, cap4k-implementation, cap4k-verification
```

Hard fail if missing/forbidden conditions are violated, even when a forbidden directory contains valid frontmatter. Do not allow compatibility aliases.

- [x] **Step 4: Implement `routing.ps1`**

Check:

```text
skills/cap4k-authoring/routing.yaml exists
skills/cap4k-authoring/references/route-map.md does not exist
no markdown file in cap4k-authoring contains a route table
routing.yaml contains route ids from Task 1
full-authoring-flow route exists and has then_chain
high-risk verification route has positive_signals and negative_signals
no `workflow:\s+workflows/` shorthand remains in `routing.yaml`
no route-local `- rules/integration-event-boundaries.md` shorthand remains in `routing.yaml`
```

- [x] **Step 5: Implement `progressive-loading.ps1`**

Check:

```text
every SKILL.md has frontmatter with name and description
every SKILL.md has <= 100 lines
each SKILL.md Always Read section has <= 3 numbered entries
no SKILL.md contains the full tactical affordance table
```

- [x] **Step 6: Implement `self-contained-runtime.ps1`**

Hard fail if runtime skill files require external runtime dependencies. Patterns:

```text
read docs/public
read docs/superpowers/analysis
read GitHub issue
read Context7
read historical spec
read cap4k source checkout
```

Allow mentions only when phrased as "do not require" or in validation/source-extraction context.

- [x] **Step 7: Implement `skeleton-gate-refs.ps1`**

Hard fail unless these skills reference `../shared/workflows/skeleton-generation-gate.md`:

```text
cap4k-technical-design
cap4k-generator-inputs
cap4k-generation-review
cap4k-handwritten-implementation
cap4k-service-integration
```

Hard fail unless these skills reference `../shared/workflows/forced-rollback.md` or route to a workflow that does:

```text
cap4k-business-discovery
cap4k-tactical-modeling
cap4k-verification-audit
```
Hard fail unless high-risk shared references have at least one inbound `routing.yaml` or workflow reference. Check these references specifically:

```text
../shared/references/generator-supported-skeletons.md
../shared/references/runtime-capability-map.md
../shared/references/drift-gotchas.md
```

- [x] **Step 8: Implement `stale-terms.ps1`**

Hard fail for these patterns in `skills/**/*.md`, `skills/**/*.yaml`, and `skills/**/*.yml`:

```text
sources\.irAnalysis\.enabled
generators\.flow\.enabled
generators\.drawingBoard\.enabled
kspKotlin
design validator
unsupported validator tag
Repository save
Repository.*保存
business.*implement.*Unit of Work
business.*implement.*Mediator
src-generated/main/kotlin
client/cli
build/cap4k code analysis
build/cap4k/analysis plan\.json
business subscriber.*consume
raw callback.*domain
cap4k-tactical-modeling or cap4k-technical-design
```

Route code spans must contain one real skill ID, not a combined phrase.

- [x] **Step 9: Implement `link-check.ps1`**

Preserve the current local Markdown link check behavior:

```text
scan Markdown links ending in .md
ignore http links
resolve relative to the current file directory
hard fail on missing target
```

- [x] **Step 10: Run validation and commit Task 8**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected:

```text
cap4k skill validation passed.
```

Commit:

```powershell
git add -- skills docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
git commit --no-verify -m "chore: split cap4k skill validation checks"
```

Completion notes:

- `validate-cap4k-skills.ps1` is now a dispatcher. It derives the repo root from `$PSScriptRoot`, uses `Push-Location` while checks run, and invokes checks in the required order.
- The modules preserve the previous monolithic guardrails for removed skill directories, focused skill references, shared core wording, stale service-integration refs, removed event guidance, public authoring stale text, optional runtime-source scans, and local Markdown link validation.
- `routing.yaml` now activates `../shared/workflows/skeleton-generation-gate.md` for the service-integration route so the new gate-reference check can hard fail honestly.
- Runtime stale-term examples were reworded to avoid carrying literal removed terms in installed skill text while preserving the drift guidance.
---

### Task 9: Add Dry Run And Final Migration Audit

**Files:**
- Create: `skills/cap4k-authoring/references/content-studio-dry-run.md`
- Modify: `skills/cap4k-authoring/SKILL.md`
- Modify: `skills/cap4k-authoring/routing.yaml`

- [ ] **Step 1: Create Content Studio dry run reference**

Create `skills/cap4k-authoring/references/content-studio-dry-run.md` with sections:

```markdown
# Content Studio Dry Run

## Scenario
## Business Discovery Output
## Tactical Modeling Output
## Technical Design Contract Excerpt
## Generator Input Expectations
## Plan Review Expectations
## Generation Stop Point
## Human Review Gate
## Handwritten Implementation Surfaces
## Verification Mode
## Rollback Examples
```

The scenario must follow:

```text
draft content
submit review
approve content
media processing external fact
media ready
publication ready
publish
```

The dry run must not instruct the worker to run Gradle tasks. It is a spec-level route and workflow validation reference.

- [ ] **Step 2: Add dry-run route**

Add route id to `routing.yaml`:

```yaml
  - id: content-studio-dry-run
    trigger_examples:
      - "dry run the cap4k skill workflow"
      - "show the end-to-end Content Studio authoring path"
    route_first: cap4k-authoring
    required_reads:
      - references/content-studio-dry-run.md
      - ../shared/workflows/forced-rollback.md
    rollback_targets:
      - business-discovery
      - tactical-modeling
      - technical-design
      - generator-inputs
      - generation-review
      - handwritten-implementation
      - verification-audit
```

- [ ] **Step 3: Mention dry run in authoring router without duplicating content**

Add one bullet in `cap4k-authoring/SKILL.md`:

```text
- For a spec-level end-to-end example, use the `content-studio-dry-run` route in `routing.yaml`.
```

- [ ] **Step 4: Run full static audit**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
git status --short -uall
git diff --check -- skills
rg -n "T[B]D|T[O]DO|PLACEH[O]LDER|F[I]XME|historical[-]decision|historical[ ]decision|Continue[ ]Brainstorming|Open[ ]Decisions|implement[ ]later|fill[ ]in[ ]details|similar[ ]to" skills
```

Expected:

```text
validation passes
git diff --check exits 0
placeholder scan exits 1
git status shows only expected dry-run files before commit
```

- [ ] **Step 5: Commit Task 9**

Commit:

```powershell
git add -- skills
git commit --no-verify -m "docs: add cap4k skill workflow dry run"
```

---

### Task 10: Final Review, Spec Coverage, And PR Update

**Files:**
- Verify: `skills/**`
- Verify: `docs/superpowers/specs/2026-06-04-cap4k-skills-system-redesign.md`
- Verify: `docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md`

- [ ] **Step 1: Run final validation**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
git diff --check -- skills docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
rg -n "T[B]D|T[O]DO|PLACEH[O]LDER|F[I]XME|historical[-]decision|historical[ ]decision|Continue[ ]Brainstorming|Open[ ]Decisions|implement[ ]later|fill[ ]in[ ]details|similar[ ]to" skills docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
```

Expected:

```text
validation passes
git diff --check exits 0
placeholder scan exits 1
```

- [ ] **Step 2: Check spec coverage**

Run:

```powershell
rg -n "cap4k-business-discovery|cap4k-tactical-modeling|cap4k-technical-design|cap4k-generator-inputs|cap4k-generation-review|cap4k-handwritten-implementation|cap4k-verification-audit|cap4k-service-integration|routing.yaml|skeleton-generation-gate|forced-rollback|full-authoring-flow|content-studio-dry-run" skills docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md
```

Expected:

```text
Each required skill and shared gate appears in implementation output or plan.
```

- [ ] **Step 3: Confirm old directories are gone**

Run:

```powershell
Test-Path skills/cap4k-modeling
Test-Path skills/cap4k-generation
Test-Path skills/cap4k-generated-output-review
Test-Path skills/cap4k-implementation
Test-Path skills/cap4k-verification
```

Expected:

```text
All five commands return False
```

- [ ] **Step 4: Summarize final migration**

Prepare final report with:

```text
new skill tree summary
old directories removed
validation commands and outputs
checks not run and why
any warning-level validation findings
commit list
```

- [ ] **Step 5: Commit plan-only changes if needed**

If this task only verifies implementation and edits no files, do not create an empty commit.

If final review fixes this plan or documentation, commit:

```powershell
git add -- docs/superpowers/plans/2026-06-04-cap4k-skills-system-redesign.md skills
git commit --no-verify -m "docs: finalize cap4k skills system rewrite"
```

# Cap4k Skill Rearchitecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current broad `cap4k-authoring` skill with a routed multi-skill architecture that gives AI agents detailed, task-specific cap4k authoring instructions without loading unrelated workflows.

**Architecture:** Keep `skills/cap4k-authoring` as a thin router only, and split real behavior into focused skills for modeling, generation, implementation, runtime integration, verification, and generated-output review. Add a repo-local validation script so future changes fail fast when routing, line budgets, stale capability statements, or link targets drift.

**Tech Stack:** Markdown Agent Skills, repo-local `AGENTS.md` routing, PowerShell validation script, Git worktrees, Gradle only for optional implementation-adjacent verification.

---

## Design Constraints

- Break compatibility with the old single-skill shape when it improves routing quality.
- Do not make AI skills read `docs/public/**` or example projects during normal operation.
- Preserve the distinction between human authoring docs and AI runtime skills.
- Keep every `SKILL.md` under 100 lines and make it route, not teach everything.
- Put stable constraints in `rules/`, procedures in `workflows/`, background and gotchas in `references/`.
- Surface gotchas on the workflow path; never bury high-cost pitfalls only in references.
- Treat `integration_event` as supported for design generation: `role`, `eventName`, at least one `requestFields` entry, and empty `responseFields`.
- Keep unsupported design tags explicit: first-class `value_object` and `domain_service`.

## Target File Structure

Delete most of the current broad content under `skills/cap4k-authoring/**` and rebuild these formal skill folders:

```text
skills/
├── cap4k-authoring/
│   ├── SKILL.md
│   ├── routing.yaml
│   └── references/
│       └── route-map.md
├── cap4k-modeling/
│   ├── SKILL.md
│   ├── rules/domain-language.md
│   ├── rules/tactical-modeling.md
│   ├── workflows/clarify-business-intent.md
│   ├── workflows/derive-ddd-model.md
│   ├── workflows/define-cross-boundary-events.md
│   └── references/gotchas.md
├── cap4k-generation/
│   ├── SKILL.md
│   ├── rules/input-contracts.md
│   ├── rules/output-ownership.md
│   ├── rules/template-and-addon-boundary.md
│   ├── workflows/bootstrap-project.md
│   ├── workflows/generate-from-db.md
│   ├── workflows/generate-from-design.md
│   ├── workflows/review-plan-json.md
│   └── references/gotchas.md
├── cap4k-implementation/
│   ├── SKILL.md
│   ├── rules/layering.md
│   ├── rules/mediator-and-uow.md
│   ├── rules/value-types.md
│   ├── workflows/implement-command-slice.md
│   ├── workflows/implement-query-slice.md
│   ├── workflows/implement-subscriber-or-job.md
│   └── references/gotchas.md
├── cap4k-runtime-integration/
│   ├── SKILL.md
│   ├── rules/request-and-event-runtime.md
│   ├── rules/integration-event-adapters.md
│   ├── workflows/handle-external-callback.md
│   ├── workflows/configure-http-integration-events.md
│   └── references/framework-tables.md
├── cap4k-verification/
│   ├── SKILL.md
│   ├── rules/evidence-contract.md
│   ├── rules/test-strategy.md
│   ├── workflows/run-focused-tests.md
│   ├── workflows/run-analysis-and-flow-review.md
│   ├── workflows/final-audit-report.md
│   └── references/gotchas.md
└── cap4k-generated-output-review/
    ├── SKILL.md
    ├── rules/review-priorities.md
    ├── workflows/review-generated-output.md
    └── references/gotchas.md
```

Add validation:

```text
skills/scripts/validate-cap4k-skills.ps1
```

Update routing shell:

```text
AGENTS.md
```

Optional discovery shells should be added only if the repository wants editor-specific discovery committed:

```text
.cursor/skills/<skill-name>/SKILL.md
.agents/skills/<skill-name>/SKILL.md
```

For this plan, commit only `skills/**`, `AGENTS.md`, and this plan unless the user explicitly asks for editor shell materialization.

---

### Task 1: Replace The Broad Authoring Skill With A Router

**Files:**
- Modify: `skills/cap4k-authoring/SKILL.md`
- Create: `skills/cap4k-authoring/routing.yaml`
- Create: `skills/cap4k-authoring/references/route-map.md`
- Delete or archive obsolete broad files under `skills/cap4k-authoring/rules/`, `skills/cap4k-authoring/workflows/`, and `skills/cap4k-authoring/references/`

- [ ] **Step 1: Remove old broad skill content**

Run:

```powershell
Remove-Item -LiteralPath `
  'skills/cap4k-authoring/rules', `
  'skills/cap4k-authoring/workflows', `
  'skills/cap4k-authoring/references' `
  -Recurse -Force
New-Item -ItemType Directory -Force 'skills/cap4k-authoring/references'
```

Expected: old broad content is removed from the new branch only.

- [ ] **Step 2: Write router `SKILL.md`**

Replace `skills/cap4k-authoring/SKILL.md` with:

```markdown
---
name: cap4k-authoring
description: >
  Route cap4k business-project AI authoring tasks to focused skills. Use when
  the user asks to model a cap4k business project, generate cap4k code from DB
  or design JSON, implement a cap4k project slice, handle cap4k runtime
  integration, verify cap4k work, or review generated cap4k output.
---

# Cap4k Authoring Router

This is a router, not the rulebook. Pick the focused skill that matches the current task and read that skill before acting.

If a task matches multiple rows, read `references/route-map.md` before choosing or chaining focused skills.

## Boundaries

- Use these skills for business projects built with cap4k.
- Do not load public docs as runtime instructions unless the user asks for public documentation work.

## Routes

| Task | Use Skill |
|---|---|
| Clarify business intent, aggregate boundaries, DDD concepts, events | `cap4k-modeling` |
| Bootstrap or generate from DB/design/enum/KSP/addon inputs | `cap4k-generation` |
| Implement command/query/subscriber/job/controller project code | `cap4k-implementation` |
| Handle integration events, callbacks, request runtime, adapter wiring | `cap4k-runtime-integration` |
| Run tests, compile, analysis, flow/drawing-board, final evidence | `cap4k-verification` |
| Review generated output, plan output, or ownership | `cap4k-generated-output-review` |

## Priority

1. Current user instruction and explicit project scope.
2. Focused skill rules for the routed task.
3. Existing project conventions.
4. Human audit remains required for domain decisions.
```

- [ ] **Step 3: Write `routing.yaml`**

Create `skills/cap4k-authoring/routing.yaml`:

```yaml
routes:
  - task: modeling
    skill: cap4k-modeling
    triggers:
      - "derive cap4k DDD design"
      - "clarify aggregate boundaries"
      - "model value objects or domain events"
      - "设计 cap4k 业务模型"
  - task: generation
    skill: cap4k-generation
    triggers:
      - "generate cap4k code from DB"
      - "generate cap4k code from design JSON"
      - "cap4kPlan"
      - "cap4kGenerate"
  - task: implementation
    skill: cap4k-implementation
    triggers:
      - "implement a cap4k project slice"
      - "write command handler"
      - "write query handler"
      - "实现 cap4k 业务切片"
  - task: runtime-integration
    skill: cap4k-runtime-integration
    triggers:
      - "integration event"
      - "external callback"
      - "HTTP integration event"
      - "集成事件"
  - task: verification
    skill: cap4k-verification
    triggers:
      - "run cap4k analysis"
      - "verify cap4k project"
      - "flow review"
      - "drawing-board"
  - task: generated-output-review
    skill: cap4k-generated-output-review
    triggers:
      - "review generated output"
      - "inspect plan.json"
      - "generated vs handwritten ownership"
      - "审查生成物"
```

- [ ] **Step 4: Write route-map reference**

Create `skills/cap4k-authoring/references/route-map.md`:

```markdown
# Cap4k Skill Route Map

Use this file when a task appears to match more than one focused skill.

| If the user asks for... | Route first to... | Then chain to... |
|---|---|---|
| "build a project from scratch" | `cap4k-modeling` | `cap4k-generation`, `cap4k-implementation`, `cap4k-verification` |
| "generate from DB/design" | `cap4k-generation` | `cap4k-generated-output-review`, `cap4k-verification` |
| "fill generated handlers" | `cap4k-implementation` | `cap4k-verification` |
| "callback/integration event flow" | `cap4k-runtime-integration` | `cap4k-implementation`, `cap4k-verification` |
| "is this generated output safe to edit?" | `cap4k-generated-output-review` | `cap4k-generation` if regeneration is needed |
```

- [ ] **Step 5: Validate router line budget**

Run:

```powershell
(Get-Content 'skills/cap4k-authoring/SKILL.md').Count
```

Expected: line count is less than or equal to 100.

---

### Task 2: Create The Modeling Skill

**Files:**
- Create: `skills/cap4k-modeling/SKILL.md`
- Create: `skills/cap4k-modeling/rules/domain-language.md`
- Create: `skills/cap4k-modeling/rules/tactical-modeling.md`
- Create: `skills/cap4k-modeling/workflows/clarify-business-intent.md`
- Create: `skills/cap4k-modeling/workflows/derive-ddd-model.md`
- Create: `skills/cap4k-modeling/workflows/define-cross-boundary-events.md`
- Create: `skills/cap4k-modeling/references/gotchas.md`

- [ ] **Step 1: Create directories**

Run:

```powershell
New-Item -ItemType Directory -Force `
  'skills/cap4k-modeling/rules', `
  'skills/cap4k-modeling/workflows', `
  'skills/cap4k-modeling/references'
```

- [ ] **Step 2: Write modeling `SKILL.md`**

Create `skills/cap4k-modeling/SKILL.md`:

```markdown
---
name: cap4k-modeling
description: >
  Use for cap4k business modeling before code: business intent, DDD boundaries,
  aggregates, entities, value concepts, domain events, integration events,
  domain services, specifications, and technical方案 before generation or implementation.
---

# Cap4k Modeling

Use this before generating or implementing when domain boundaries, events, invariants, or use-case shape are not settled.

## Always Read

1. `rules/domain-language.md`
2. `rules/tactical-modeling.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Clarify unclear requirements | `references/gotchas.md` | `workflows/clarify-business-intent.md` |
| Derive aggregates and tactical objects | `rules/tactical-modeling.md` | `workflows/derive-ddd-model.md` |
| Model external facts and integration events | `rules/tactical-modeling.md`, `references/gotchas.md` | `workflows/define-cross-boundary-events.md` |

## Hard Boundary

Do not invent cap4k generator support for first-class `value_object` or `domain_service`. Treat them as handwritten modeling choices unless a later generator capability exists in the codebase.
```

- [ ] **Step 3: Write modeling rules**

Create `skills/cap4k-modeling/rules/domain-language.md`:

```markdown
# Domain Language Rules

- Ask for business vocabulary before naming commands, aggregates, events, or fields.
- Separate facts the business already knows from implementation guesses.
- Treat aggregate boundaries as business consistency boundaries, not table grouping.
- Name commands as user or process intentions, not CRUD operations.
- Name domain events as completed business facts.
- Name integration events as cross-boundary message contracts, not internal domain facts.
- Record unresolved domain choices as human decisions, not agent assumptions.
```

Create `skills/cap4k-modeling/rules/tactical-modeling.md`:

```markdown
# Tactical Modeling Rules

- Aggregates own write invariants and state transitions.
- Entities belong inside aggregate consistency boundaries.
- Value concepts may be primitive wrappers, inline values, JSON-backed values, or table-backed `@VO`; choose the domain concept before choosing the persistence carrier.
- Domain services are handwritten when a domain decision crosses aggregate boundaries or does not naturally belong to one aggregate.
- Specifications are handwritten validation policies used only when the project intentionally demonstrates or enforces that concept.
- Domain events are emitted by domain behavior and handled by application subscribers.
- Integration events represent external or cross-service facts. Design JSON supports `integration_event` with `role`, `eventName`, at least one request field, and no response fields.
- Do not model external callbacks as domain events. Translate external input into commands or process steps at the application boundary.
```

- [ ] **Step 4: Write modeling workflows**

Create `skills/cap4k-modeling/workflows/clarify-business-intent.md`:

````markdown
# Clarify Business Intent

## Inputs

- User story, requirement text, DDL draft, design draft, or verbal business goal.

## Steps

1. Identify actors, business outcome, write commands, read needs, external systems, and failure paths.
2. List decisions that are missing or ambiguous.
3. Ask the human only for decisions that cannot be safely inferred from supplied project evidence.
4. Separate domain decisions from generation decisions.
5. Produce a short modeling brief with commands, queries, aggregates, events, external contracts, and unresolved choices.

## Output

Use this format:

```markdown
## Modeling Brief

- Business outcome:
- Actors:
- Commands:
- Queries:
- Aggregates:
- Value concepts:
- Domain events:
- Integration events:
- External systems:
- Human decisions still needed:
```
````

Create `skills/cap4k-modeling/workflows/derive-ddd-model.md`:

```markdown
# Derive DDD Model

1. Start from agreed business vocabulary.
2. Propose aggregate roots and explain each consistency boundary.
3. Classify child entities and value concepts.
4. Identify invariants and the command that enforces each invariant.
5. Identify domain events emitted after state transitions.
6. Identify domain services only for decisions that cross aggregate boundaries.
7. Mark first-class `value_object` and `domain_service` generation as unsupported; keep implementation handwritten.
8. Produce DDL/design JSON recommendations only after the tactical model is coherent.
```

Create `skills/cap4k-modeling/workflows/define-cross-boundary-events.md`:

```markdown
# Define Cross-Boundary Events

1. Identify whether the fact originates inside the domain or outside the service boundary.
2. Use domain events for internal domain facts emitted by aggregate behavior.
3. Use `integration_event` for cross-boundary contracts.
4. For every `integration_event`, define `role`, `eventName`, payload fields, producer, consumer, and serialization expectations.
5. For inbound events, define the application command or process step that receives the translated fact.
6. For outbound events, define which domain fact or command result releases the message.
7. Keep MQ binding, HTTP callback shape, and external protocol mapping out of the domain model.
```

- [ ] **Step 5: Write modeling gotchas**

Create `skills/cap4k-modeling/references/gotchas.md`:

```markdown
# Modeling Gotchas

- Do not let table shape define aggregate boundaries without business consistency reasoning.
- Do not create a domain service just because code feels procedural.
- Do not call every small type a generated value object. First-class `value_object` design generation is not supported.
- Do not pretend external callbacks are domain events.
- Do not define integration events without event name, role, and payload fields.
```

- [ ] **Step 6: Validate modeling skill**

Run:

```powershell
(Get-Content 'skills/cap4k-modeling/SKILL.md').Count
rg -n "value_object|domain_service|integration_event|external callbacks" skills/cap4k-modeling
```

Expected: `SKILL.md` is under 100 lines and the capability boundaries are visible.

---

### Task 3: Create The Generation Skill

**Files:**
- Create: `skills/cap4k-generation/SKILL.md`
- Create: `skills/cap4k-generation/rules/input-contracts.md`
- Create: `skills/cap4k-generation/rules/output-ownership.md`
- Create: `skills/cap4k-generation/rules/template-and-addon-boundary.md`
- Create: `skills/cap4k-generation/workflows/bootstrap-project.md`
- Create: `skills/cap4k-generation/workflows/generate-from-db.md`
- Create: `skills/cap4k-generation/workflows/generate-from-design.md`
- Create: `skills/cap4k-generation/workflows/review-plan-json.md`
- Create: `skills/cap4k-generation/references/gotchas.md`

- [ ] **Step 1: Create directories**

Run:

```powershell
New-Item -ItemType Directory -Force `
  'skills/cap4k-generation/rules', `
  'skills/cap4k-generation/workflows', `
  'skills/cap4k-generation/references'
```

- [ ] **Step 2: Write generation `SKILL.md`**

Create `skills/cap4k-generation/SKILL.md`:

```markdown
---
name: cap4k-generation
description: >
  Use when generating or regenerating cap4k business-project code,
  bootstrapping a cap4k project, generating from database schema or design JSON,
  reviewing generation ownership, or configuring template overrides and addons.
---

# Cap4k Generation

Use this before writing or regenerating cap4k generated output.

## Always Read

1. `rules/input-contracts.md`
2. `rules/output-ownership.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Bootstrap a project | `rules/template-and-addon-boundary.md` | `workflows/bootstrap-project.md` |
| Generate from DB | `references/gotchas.md` | `workflows/generate-from-db.md` |
| Generate from design JSON | `references/gotchas.md` | `workflows/generate-from-design.md` |
| Inspect ownership before writing | `rules/output-ownership.md` | `workflows/review-plan-json.md` |

## Stop Conditions

Stop before generation when `plan.json` ownership is unclear, a target conflicts with handwritten code, or a requested design tag is unsupported.
```

- [ ] **Step 3: Write generation rules**

Create `skills/cap4k-generation/rules/input-contracts.md`:

```markdown
# Input Contracts

- DB source defines aggregate model, relations, enum bindings, generated IDs, soft delete, versions, managed/exposed fields, provider controls, repositories, factories, specifications, and unique helpers.
- Design JSON defines command, query, client, api_payload, domain_event, integration_event, and validator contracts.
- `integration_event` requires `role`, `eventName`, at least one request field, and empty response fields.
- Enum manifest supplies shared enums referenced by DB `@Type` / `@T`.
- KSP metadata supplies aggregate metadata for design-driven artifacts.
- IR analysis is post-code observation for flow and drawing-board output, not normal business source generation.
- Unsupported design tags are first-class `value_object` and `domain_service`.
```

Create `skills/cap4k-generation/rules/output-ownership.md`:

```markdown
# Output Ownership

- Build-owned generated source lives under module `build/generated/cap4k/main/kotlin` and can be overwritten.
- Checked-in skeletons are project author surfaces only when their conflict policy protects handwritten logic.
- Copied snapshots such as `src-generated/main/kotlin` are audit or learning snapshots, not active generator output.
- Handwritten source must not be overwritten by regeneration.
- Inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot` before writing files.
- Use `SKIP` for handlers, behavior, factory, specification, and subscriber skeletons that receive project logic.
- Use `OVERWRITE` only for build-owned generated source or intentional regenerated artifacts.
```

Create `skills/cap4k-generation/rules/template-and-addon-boundary.md`:

```markdown
# Template And Addon Boundary

- Template override dirs are checked before addon and built-in resources.
- Addon artifacts behave like built-in artifacts for plan, render, conflict policy, and generation.
- Addon template conflict policies use the exact `templateId` from `cap4kPlan`.
- Runtime dependencies and generation-time `cap4kAddon` dependencies are separate.
- Enum translation is addon-owned, not a core aggregate DSL toggle.
- Do not override templates before proving the default output is the wrong abstraction.
```

- [ ] **Step 4: Write generation workflows**

Create `skills/cap4k-generation/workflows/review-plan-json.md`:

```markdown
# Review Plan Json

1. Run the relevant plan task before generation.
2. Open `build/cap4k/plan.json` or `build/cap4k/bootstrap-plan.json`.
3. For each item, inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
4. Classify every target as build-owned, checked-in skeleton, snapshot, template override, or handwritten source.
5. Stop if a handwritten target would be overwritten or if ownership is unclear.
6. Report the target classification before running generation.
```

Create `skills/cap4k-generation/workflows/bootstrap-project.md`:

```markdown
# Bootstrap Project

1. Confirm bootstrap scope: project skeleton only, not business source generation.
2. Run `cap4kBootstrapPlan`.
3. Inspect root files, module files, package slots, resource slots, and conflict policy.
4. Confirm whether bootstrap runs in-place or preview subtree mode.
5. Apply template overrides or slots only after plan review.
6. Run `cap4kBootstrap` only after target ownership is clear.
7. Compile or run the minimal project verification command if the generated project is expected to build.
```

Create `skills/cap4k-generation/workflows/generate-from-db.md`:

```markdown
# Generate From DB

1. Confirm DDL is the source of truth for aggregate generation.
2. Review table annotations: `@Parent`, `@AggregateRoot`, `@ValueObject`, `@Ignore`, dynamic insert/update.
3. Review column annotations: `@Type`, `@Enum`, `@GeneratedValue`, `@Deleted`, `@Version`, `@Managed`, `@Exposed`, insertable/updatable.
4. Classify custom value fields as `@T` plus type registry, inline field, JSON-backed value, or table-backed `@VO`.
5. Review relation annotations: `@Reference`, `@Relation`, `@Lazy`, `@Count`; reject many-to-many assumptions.
6. Review unique constraint names and decide whether unique query, handler, and validator helpers are useful.
7. Run `cap4kPlan`.
8. Review `plan.json` using `workflows/review-plan-json.md`.
9. Run `cap4kGenerate` or `cap4kGenerateSources` only after ownership classification.
10. Run affected compile/tests.
```

Create `skills/cap4k-generation/workflows/generate-from-design.md`:

```markdown
# Generate From Design

1. Confirm design JSON is the source of truth for use-case and interface contracts.
2. Allow only supported tags: `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, and `validator`.
3. Reject first-class `value_object` and `domain_service` as unsupported design generation.
4. Check common fields: package, name, desc, aggregates, requestFields, and responseFields.
5. For `integration_event`, require role, eventName, at least one request field, and empty response fields.
6. Check tag-specific rules: page traits, persisted domain events, validator fields, and manifest path safety.
7. Run `cap4kPlan`.
8. Review `plan.json` using `workflows/review-plan-json.md`.
9. Generate only after deciding which handlers and subscribers are project-owned.
10. Run affected compile/tests.
```

- [ ] **Step 5: Write generation gotchas**

Create `skills/cap4k-generation/references/gotchas.md`:

```markdown
# Generation Gotchas

- `src/main/kotlin` does not automatically mean handwritten; check `plan.json`.
- `src-generated/main/kotlin` is a snapshot, not active generation output.
- `@VO` is not the default value-object path.
- `integration_event` without payload fields is invalid.
- `designIntegrationEventSubscriber` depends on `designIntegrationEvent`.
- Do not use stale enum translation core DSL; use an addon path.
```

- [ ] **Step 6: Validate generation skill**

Run:

```powershell
(Get-Content 'skills/cap4k-generation/SKILL.md').Count
rg -n "plan.json|outputKind|integration_event|cap4kAddon|enum translation" skills/cap4k-generation
```

Expected: `SKILL.md` is under 100 lines and generation ownership rules are discoverable.

---

### Task 4: Create Implementation And Runtime Skills

**Files:**
- Create all files under `skills/cap4k-implementation/**`
- Create all files under `skills/cap4k-runtime-integration/**`

- [ ] **Step 1: Create implementation directories**

Run:

```powershell
New-Item -ItemType Directory -Force `
  'skills/cap4k-implementation/rules', `
  'skills/cap4k-implementation/workflows', `
  'skills/cap4k-implementation/references'
```

- [ ] **Step 2: Write implementation `SKILL.md`**

Create `skills/cap4k-implementation/SKILL.md`:

```markdown
---
name: cap4k-implementation
description: >
  Use when implementing cap4k business project code: command handlers, query
  handlers, subscribers, jobs, controllers, factories, specifications, domain
  services, Mediator usage, repository access, value types, and UoW persistence.
---

# Cap4k Implementation

Use this after modeling and generation boundaries are clear.

## Always Read

1. `rules/layering.md`
2. `rules/mediator-and-uow.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Implement write use case | `rules/value-types.md`, `references/gotchas.md` | `workflows/implement-command-slice.md` |
| Implement read use case | `references/gotchas.md` | `workflows/implement-query-slice.md` |
| Implement subscriber or job | `rules/layering.md`, `rules/mediator-and-uow.md` | `workflows/implement-subscriber-or-job.md` |

## Stop Conditions

Stop when aggregate boundaries are unclear, generated ownership is unclear, or a write path bypasses command/UoW without explicit human approval.
```

- [ ] **Step 3: Write implementation rules and workflows**

Create `skills/cap4k-implementation/rules/layering.md`:

```markdown
# Layering Rules

- Domain owns aggregates, entities, value concepts, invariants, domain events, domain services, factories, and specifications.
- Application owns commands, command handlers, validators, subscribers, jobs, and process orchestration.
- Adapter owns HTTP controllers, persistence adapters, query handlers, client/cli handlers, and external protocol mapping.
- Query handlers and client/cli handlers are adapter-side physical handlers by default.
- Controllers and jobs must not become direct write-persistence surfaces.
```

Create `skills/cap4k-implementation/rules/mediator-and-uow.md`:

```markdown
# Mediator And UoW Rules

- Use static `Mediator.*` surfaces in business code.
- Use `Mediator.repositories` for aggregate loading in write flows.
- Use `Mediator.factories` for aggregate-root creation.
- Use `Mediator.services` for domain services.
- Use `Mediator.uow.save()` as the normal write persistence boundary.
- Use `Mediator.cmd`, `Mediator.qry`, and `Mediator.requests` for orchestration across request boundaries.
- Do not inject handlers directly to bypass the request supervisor path.
```

Create `skills/cap4k-implementation/rules/value-types.md`:

```markdown
# Value Type Rules

- Save aggregate-owned values through the aggregate root.
- Do not call `Mediator.uow.persist(valueObject)` for JSON-backed or inline aggregate-owned values.
- Use handwritten Kotlin types and converters for JSON-backed or inline values.
- Use table-backed `@VO` only when the model intentionally chooses separate persistence.
```

Create `skills/cap4k-implementation/workflows/implement-command-slice.md`:

```markdown
# Implement Command Slice

1. Confirm the command intent and aggregate root.
2. Add or update a focused behavior/application test when feasible.
3. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Cmd.kt`.
4. Load aggregate roots through `Mediator.repositories` or create them through `Mediator.factories`.
5. Apply domain behavior on the aggregate or call a domain service for cross-aggregate decisions.
6. Attach domain or integration events only after the state transition is meaningful.
7. Persist through `Mediator.uow.save()`.
8. Keep external protocol mapping in adapter code or client/cli handlers.
9. Run affected compile/tests and report evidence.
```

Create `skills/cap4k-implementation/workflows/implement-query-slice.md`:

```markdown
# Implement Query Slice

1. Confirm the read contract and caller.
2. Keep query handlers read-oriented.
3. Inspect `build/cap4k/plan.json` before editing generated request or handler surfaces such as `*Qry.kt`, `*QryHandler.kt`, and `*CliHandler.kt`.
4. Use repository/JPA/query infrastructure behind the handler.
5. Do not repair aggregate state from query paths.
6. Map persistence/read shapes to response DTOs without leaking transport concerns into domain code.
7. Run focused tests or compile checks.
```

Create `skills/cap4k-implementation/workflows/implement-subscriber-or-job.md`:

```markdown
# Implement Subscriber Or Job

1. Identify the triggering event or schedule.
2. Use subscribers or jobs as orchestration points, not as hidden aggregate persistence layers.
3. Inspect `build/cap4k/plan.json` before editing generated subscriber shells.
4. Translate follow-up work into `Mediator.cmd`, `Mediator.qry`, or `Mediator.requests`.
5. Use semantic method names once business logic exists.
6. Keep external callback protocol details outside domain events.
7. Run focused tests or compile checks.
```

Create `skills/cap4k-implementation/references/gotchas.md`:

```markdown
# Implementation Gotchas

- Query handlers live in adapter packages by default.
- Direct handler injection bypasses cap4k request supervision.
- Jobs should not become write repositories.
- Generated subscriber shells need semantic methods after implementation.
- Value objects owned by aggregates are saved through aggregate persistence.
- Inspect `build/cap4k/plan.json` before editing generated request, handler, or subscriber surfaces such as `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, and generated subscriber shells.
```

- [ ] **Step 4: Create runtime integration directories**

Run:

```powershell
New-Item -ItemType Directory -Force `
  'skills/cap4k-runtime-integration/rules', `
  'skills/cap4k-runtime-integration/workflows', `
  'skills/cap4k-runtime-integration/references'
```

- [ ] **Step 5: Write runtime integration skill files**

Create `skills/cap4k-runtime-integration/SKILL.md`:

```markdown
---
name: cap4k-runtime-integration
description: >
  Use for cap4k runtime integration work: integration events, external callbacks,
  HTTP/RabbitMQ/RocketMQ event adapters, request runtime, framework persistence
  tables, event subscriber bridge, and cross-service event contracts.
---

# Cap4k Runtime Integration

Use this when external systems, integration event transport, callbacks, or runtime framework tables are involved.

## Always Read

1. `rules/request-and-event-runtime.md`
2. `rules/integration-event-adapters.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| External callback enters project | `references/framework-tables.md` | `workflows/handle-external-callback.md` |
| Configure HTTP integration event path | `references/framework-tables.md` | `workflows/configure-http-integration-events.md` |

## Stop Conditions

Stop when an external callback is being modeled as a domain event or required framework tables are missing.
```

Create `skills/cap4k-runtime-integration/rules/request-and-event-runtime.md`:

```markdown
# Request And Event Runtime

- Commands, queries, and client/cli requests use request params and handlers.
- `Mediator.requests`, `Mediator.cmd`, and `Mediator.qry` send through request supervision.
- Domain events are internal domain facts.
- Integration events are cross-boundary messages.
- `EventSubscriberManager` bridges scanned integration event payloads to Spring `ApplicationEventPublisher`, so generated inbound `@EventListener` subscribers can receive them.
```

Create `skills/cap4k-runtime-integration/rules/integration-event-adapters.md`:

```markdown
# Integration Event Adapters

- HTTP, RabbitMQ, and RocketMQ adapters transport integration events.
- Inbound integration events should translate into commands, queries, or process steps.
- Outbound integration events should be released from meaningful domain facts or command results.
- MQ binding and external protocol mapping are project-owned integration code, not design JSON generation.
- Cross-service contract stability depends on event name, schema, and serialization behavior.
```

Create `skills/cap4k-runtime-integration/workflows/handle-external-callback.md`:

```markdown
# Handle External Callback

1. Treat the callback as adapter input or inbound integration event.
2. Parse and validate external protocol payload in adapter-facing code.
3. Translate the external fact into an application command or process step.
4. Keep third-party status codes out of aggregate behavior unless normalized.
5. Persist internal state changes through command handlers and UoW.
6. Emit domain events only after internal domain state changes.
7. Run an adapter smoke test or focused integration test when the project claims runnable callback behavior.
```

Create `skills/cap4k-runtime-integration/workflows/configure-http-integration-events.md`:

```markdown
# Configure HTTP Integration Events

1. Confirm the project uses the HTTP integration event adapter.
2. Confirm whether HTTP-JPA subscriber registry persistence is present.
3. Add only the required framework table DDL for the selected database.
4. Verify event scan package includes generated integration event classes.
5. Confirm inbound event classes do not use `[none]` / `IntegrationEvent.NONE_SUBSCRIBER` and resolve to the actual consuming service subscriber value.
6. Test the consume path or document the manual HTTP smoke path.
```

Create `skills/cap4k-runtime-integration/references/framework-tables.md`:

```markdown
# Framework Tables

Runtime families with JPA persistence may require tables for:

- request records: `__request`, `__archived_request`
- domain event records: `__event`, `__archived_event`
- integration event HTTP subscriber registry: `__event_http_subscriber`
- saga records: `__saga`, `__saga_process`, `__archived_saga`, `__archived_saga_process`
- distributed locker records: `__locker`

Local examples should include only the required subset and translate SQL to the selected local database.
```

- [ ] **Step 6: Validate implementation and runtime skills**

Run:

```powershell
(Get-Content 'skills/cap4k-implementation/SKILL.md').Count
(Get-Content 'skills/cap4k-runtime-integration/SKILL.md').Count
rg -n "Mediator|uow|EventSubscriberManager|ApplicationEventPublisher|framework tables" skills/cap4k-implementation skills/cap4k-runtime-integration
rg -n "plan.json|NONE_SUBSCRIBER|\[none\]|__request|__event_http_subscriber|__saga_process|__locker" skills/cap4k-implementation skills/cap4k-runtime-integration
```

Expected: both `SKILL.md` files are under 100 lines and runtime bridge rules are visible.

---

### Task 5: Create Verification And Review Skills

**Files:**
- Create all files under `skills/cap4k-verification/**`
- Create all files under `skills/cap4k-generated-output-review/**`

- [ ] **Step 1: Create directories**

Run:

```powershell
New-Item -ItemType Directory -Force `
  'skills/cap4k-verification/rules', `
  'skills/cap4k-verification/workflows', `
  'skills/cap4k-verification/references', `
  'skills/cap4k-generated-output-review/rules', `
  'skills/cap4k-generated-output-review/workflows', `
  'skills/cap4k-generated-output-review/references'
```

- [ ] **Step 2: Write verification skill**

Create `skills/cap4k-verification/SKILL.md`:

```markdown
---
name: cap4k-verification
description: >
  Use for cap4k verification: focused tests, compile checks, generation evidence,
  cap4kAnalysisPlan, cap4kAnalysisGenerate, flow output, drawing-board output,
  final audit reports, and skipped-check disclosure.
---

# Cap4k Verification

Use before claiming cap4k authoring work is complete.

## Always Read

1. `rules/evidence-contract.md`
2. `rules/test-strategy.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Run focused verification | `references/gotchas.md` | `workflows/run-focused-tests.md` |
| Review analysis or flow output | `references/gotchas.md` | `workflows/run-analysis-and-flow-review.md` |
| Prepare final human audit summary | `rules/evidence-contract.md` | `workflows/final-audit-report.md` |
```

Create `skills/cap4k-verification/rules/evidence-contract.md`:

```markdown
# Evidence Contract

- Report exact commands, scope, exit status, and meaningful result.
- Do not claim completion without fresh verification evidence.
- For docs-only or skill-only changes, use targeted scans and `git diff --check`.
- Disclose skipped compile, tests, generation, or analysis checks with reasons.
- Human audit remains required for domain decisions.
```

Create `skills/cap4k-verification/rules/test-strategy.md`:

```markdown
# Test Strategy

- Prefer domain and application behavior tests first.
- Use adapter or integration smoke tests when the project claims runnable HTTP or external event behavior.
- Avoid brittle line-by-line snapshots of generated analysis output.
- Use analysis outputs to review relationships and flows, not to replace compile/tests.
```

Create `skills/cap4k-verification/workflows/run-focused-tests.md`:

```markdown
# Run Focused Tests

1. Identify affected modules and behavior.
2. Choose the narrowest compile/test command that proves the change.
3. Run the command fresh.
4. Read the exit code and meaningful failures.
5. If failures are unrelated, state the evidence and residual risk.
6. Report command, result, and skipped checks.
```

Create `skills/cap4k-verification/workflows/run-analysis-and-flow-review.md`:

```markdown
# Run Analysis And Flow Review

1. Compile relevant modules when IR analysis depends on code relationships.
2. Confirm `sources.irAnalysis.inputDirs` points at module `build/cap4k-code-analysis` directories.
3. Run `cap4kAnalysisPlan`.
4. Inspect `build/cap4k/analysis-plan.json`.
5. Run `cap4kAnalysisGenerate`.
6. Review flow and drawing-board output for expected entries and relationship gaps.
7. Avoid claiming full behavior correctness from analysis output alone.
```

Create `skills/cap4k-verification/workflows/final-audit-report.md`:

````markdown
# Final Audit Report

Use this output format:

```markdown
## Changes

- 

## Verification

- Command:
- Result:

## Human Audit Points

- 

## Skipped Checks

- 
```
````

Create `skills/cap4k-verification/references/gotchas.md`:

```markdown
# Verification Gotchas

- `cap4kAnalysisGenerate` does not replace `cap4kGenerate`.
- Analysis output proves observed relationships, not business correctness.
- Passing plan generation does not prove generated source compiles.
- Docs-only changes still need scans and `git diff --check`.
```

- [ ] **Step 3: Write generated-output review skill**

Create `skills/cap4k-generated-output-review/SKILL.md`:

```markdown
---
name: cap4k-generated-output-review
description: >
  Use to review cap4k generated output, plan.json items, generated-vs-handwritten
  ownership, conflict policies, template IDs, addon artifacts, and documentation
  drift before generation or PR review.
---

# Cap4k Generated Output Review

Use with a code review mindset. Findings come first.

## Always Read

1. `rules/review-priorities.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Review generated output or plan | `references/gotchas.md` | `workflows/review-generated-output.md` |
```

Create `skills/cap4k-generated-output-review/rules/review-priorities.md`:

```markdown
# Review Priorities

1. Behavioral bugs or generation output that will not compile.
2. Ownership bugs that overwrite handwritten project logic.
3. Layering violations that put business decisions in adapter or transport code.
4. Missing tests or verification for changed behavior.
5. Documentation drift that will mislead future agents.
```

Create `skills/cap4k-generated-output-review/workflows/review-generated-output.md`:

```markdown
# Review Generated Output

1. Read the plan output before judging files.
2. Compare `generatorId`, `templateId`, `outputKind`, `conflictPolicy`, `outputPath`, and `resolvedOutputRoot`.
3. Check generated source compiles or has an explicit compile verification plan.
4. Check checked-in skeletons protect handwritten logic.
5. Check handlers, subscribers, factories, and specifications have correct ownership.
6. Report findings first with file and line references.
```

Create `skills/cap4k-generated-output-review/references/gotchas.md`:

```markdown
# Review Gotchas

- `src/main/kotlin` does not automatically mean handwritten.
- Plan output must be reviewed before judging generated files.
- Addon artifacts need the same ownership review as built-in artifacts.
- Public docs and AI skills can intentionally differ in audience, but must agree on facts.
```

- [ ] **Step 4: Validate verification and review skills**

Run:

```powershell
(Get-Content 'skills/cap4k-verification/SKILL.md').Count
(Get-Content 'skills/cap4k-generated-output-review/SKILL.md').Count
rg -n "cap4kAnalysisPlan|findings|outputKind|conflictPolicy|human audit" skills/cap4k-verification skills/cap4k-generated-output-review
```

Expected: both `SKILL.md` files are under 100 lines and verification/review paths are discoverable.

---

### Task 6: Add Skill Validation Script

**Files:**
- Create: `skills/scripts/validate-cap4k-skills.ps1`

- [ ] **Step 1: Create scripts directory**

Run:

```powershell
New-Item -ItemType Directory -Force 'skills/scripts'
```

- [ ] **Step 2: Write validation script**

Create `skills/scripts/validate-cap4k-skills.ps1`:

```powershell
$ErrorActionPreference = 'Stop'

$skillDirs = Get-ChildItem -LiteralPath 'skills' -Directory |
  Where-Object { $_.Name -ne 'scripts' }

if ($skillDirs.Count -lt 7) {
  throw "Expected at least 7 cap4k skill directories, found $($skillDirs.Count)."
}

$badLineCounts = @()
$missingFrontmatter = @()
$brokenLinks = @()

foreach ($dir in $skillDirs) {
  $skillFile = Join-Path $dir.FullName 'SKILL.md'
  if (-not (Test-Path -LiteralPath $skillFile)) {
    throw "Missing SKILL.md in $($dir.FullName)"
  }

  $lines = Get-Content -LiteralPath $skillFile
  if ($lines.Count -gt 100) {
    $badLineCounts += "$($dir.Name): $($lines.Count)"
  }

  $text = $lines -join "`n"
  if ($text -notmatch '(?s)^---\s*.*name:\s*.+description:\s*.+---') {
    $missingFrontmatter += $dir.Name
  }

  $matches = [regex]::Matches($text, '\(([^)]+?\.md)(?:[#?][^)\s]*)?(?:\s+"[^"]*")?\)')
  foreach ($match in $matches) {
    $target = $match.Groups[1].Value
    if ($target.StartsWith('http')) { continue }
    $targetPath = Join-Path $dir.FullName $target
    if (-not (Test-Path -LiteralPath $targetPath)) {
      $brokenLinks += "$($dir.Name): $target"
    }
  }
}

if ($badLineCounts.Count -gt 0) {
  throw "SKILL.md files over 100 lines: $($badLineCounts -join ', ')"
}

if ($missingFrontmatter.Count -gt 0) {
  throw "Missing required frontmatter: $($missingFrontmatter -join ', ')"
}

if ($brokenLinks.Count -gt 0) {
  throw "Broken local markdown links: $($brokenLinks -join ', ')"
}

$skillTextFiles = Get-ChildItem -LiteralPath 'skills' -Recurse -File |
  Where-Object { $_.Extension -in '.md', '.yaml', '.yml' }

$allText = $skillTextFiles |
  ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }

$combined = $allText -join "`n"

$forbiddenPatterns = @(
  ('No design support for `integration_' + 'event`'),
  ('integration-event design support that does not exist ' + 'today'),
  ('unsupported design tags.*integration_' + 'event'),
  ('enumTranslation' + '\.set'),
  ('read docs/public/authoring during normal ' + 'operation')
)

foreach ($pattern in $forbiddenPatterns) {
  if ($combined -match $pattern) {
    throw "Forbidden stale skill text matched: $pattern"
  }
}

Write-Host "cap4k skill validation passed for $($skillDirs.Count) skills."
```

- [ ] **Step 3: Run validation script before it passes**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected: script passes after all skill directories from previous tasks exist.

---

### Task 7: Update AGENTS Routing Shell

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Replace old authoring route table**

Replace the current `## AI Authoring Skill` section in `AGENTS.md` with:

```markdown
## Cap4k Skill Routing

When a task involves cap4k business-project authoring, use the repo-local skill router first:

- [skills/cap4k-authoring/SKILL.md](skills/cap4k-authoring/SKILL.md)

Then route to the focused skill:

| Task | Focused skill |
|---|---|
| Business modeling, aggregate boundaries, events, value concepts | [skills/cap4k-modeling/SKILL.md](skills/cap4k-modeling/SKILL.md) |
| Bootstrap, DB/design generation, plan review, templates, addons | [skills/cap4k-generation/SKILL.md](skills/cap4k-generation/SKILL.md) |
| Command/query/subscriber/job/controller implementation | [skills/cap4k-implementation/SKILL.md](skills/cap4k-implementation/SKILL.md) |
| Integration events, callbacks, request/event runtime, framework tables | [skills/cap4k-runtime-integration/SKILL.md](skills/cap4k-runtime-integration/SKILL.md) |
| Tests, compile, analysis, flow/drawing-board, evidence | [skills/cap4k-verification/SKILL.md](skills/cap4k-verification/SKILL.md) |
| Generated output, ownership, plan.json review | [skills/cap4k-generated-output-review/SKILL.md](skills/cap4k-generated-output-review/SKILL.md) |

Keep this file as a routing shell. Do not duplicate focused skill rules here.
```

- [ ] **Step 2: Validate AGENTS links**

Run:

```powershell
rg -n "cap4k-modeling|cap4k-generation|cap4k-implementation|cap4k-runtime-integration|cap4k-verification|cap4k-generated-output-review" AGENTS.md
```

Expected: all focused skills are referenced.

---

### Task 8: Full Validation And Commit

**Files:**
- All changed skill files
- `AGENTS.md`
- `docs/superpowers/plans/2026-05-16-cap4k-skill-rearchitecture.md`

- [ ] **Step 1: Run validation script**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File skills/scripts/validate-cap4k-skills.ps1
```

Expected: `cap4k skill validation passed for 7 skills.`

- [ ] **Step 2: Run targeted stale-text scan**

Run:

```powershell
rg -n "No design support for `integration_event`|integration-event design support that does not exist today|unsupported design tags.*integration_event|enumTranslation\.set|read docs/public/authoring during normal operation" skills AGENTS.md
```

Expected: no matches and exit code 1.

- [ ] **Step 3: Run markdown/link shape scan**

Run:

```powershell
$patterns = @('TO' + 'DO', 'TB' + 'D', 'FI' + 'LL:', 'Similar' + ' to Task', 'implement' + ' later')
Get-ChildItem -Path skills -Recurse -File | Select-String -Pattern $patterns
Select-String -Path AGENTS.md -Pattern $patterns
```

Expected: no matches and exit code 1.

- [ ] **Step 4: Run diff whitespace validation**

Run:

```powershell
git diff --check
```

Expected: exit code 0.

- [ ] **Step 5: Inspect changed paths**

Run:

```powershell
git diff --name-only
```

Expected: changed files are limited to `skills/**`, `AGENTS.md`, and `docs/superpowers/plans/2026-05-16-cap4k-skill-rearchitecture.md`.

- [ ] **Step 6: Commit**

Run:

```powershell
git add AGENTS.md skills docs/superpowers/plans/2026-05-16-cap4k-skill-rearchitecture.md
git commit -m "docs: rebuild cap4k authoring skills"
```

Expected: commit succeeds on branch `skills/rebuild-cap4k-authoring`.

---

## Self-Review

- Spec coverage: The plan covers the requested no-compatibility skill rewrite by replacing one broad skill with a routed multi-skill architecture, adding detailed workflows, adding validation, and updating routing.
- Red-flag scan: The implementation steps avoid deferred-work markers and copy-forward instructions.
- Type/path consistency: All referenced focused skill names match target directories and `AGENTS.md` route names.
- Known risk: The plan intentionally does not materialize `.cursor/skills/**` or `.agents/skills/**` discovery shells unless requested. This keeps the first PR focused on repo-local formal skills and AGENTS routing.

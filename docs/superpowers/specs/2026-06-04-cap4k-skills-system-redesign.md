# cap4k Skills System Redesign

Date: 2026-06-04

Status: Detailed Design

Scope: detailed design for issue #100, rebuilding `skills/` as a self-contained, drift-resistant agent execution system for authoring business systems with cap4k. This spec designs the repo-local `skills/` source tree only. Optional harness adapters such as Cursor registration entries or root thin shells can be designed later.

## Source Inputs

This design is based on:

- Shared documentation-system design: `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
- Completed analysis maps: `docs/superpowers/analysis/**`
- Completed public docs: `README.md` and `docs/public/**`
- Coarse skills outline: `docs/superpowers/specs/2026-06-01-cap4k-skills-workflow-outline.md`
- Issue #100: `skills: rebuild cap4k authoring skills as an agent execution system`
- Current `skills/` tree and `skills/scripts/validate-cap4k-skills.ps1`
- `skill-based-architecture` guidance

## Authority Chain

Code facts win.

`analysis` is a maintenance fact index. It is useful while maintaining this repository, but it is not a runtime source for installed skills.

`public` is the human mental model. It teaches cap4k users concepts, architecture, authoring, examples, generator mechanics, and reference fields. It is not an agent execution manual.

`skills` is the installed agent runtime. It may be maintained from code, analysis, public docs, and specs, but installed skills must be self-contained.

Installed skills must not require an agent to read:

- `docs/public/**`
- `docs/superpowers/analysis/**`
- GitHub issues
- historical specs or plans
- Context7
- a cap4k source checkout

If a code fact is required for agent execution, transform it into bundle-local operational guidance.

## Design Goals

- Rebuild skills as an end-to-end cap4k authoring execution system.
- Keep `SKILL.md` entries thin and route-driven.
- Use `routing.yaml` as the only routing source of truth.
- Encode cap4k-specific gates, not generic DDD advice.
- Centralize Skeleton Generation Gate and forced rollback.
- Keep runtime skills self-contained and AI-friendly.
- Merge generated-output review into generation review.
- Preserve service integration as a high-risk specialist skill.
- Add validation that catches structural drift and high-risk semantic drift.
- Include one end-to-end dry run based on Reference Content Studio's default publication flow.

## Non-Goals

- Do not implement cap4k framework features.
- Do not rewrite public docs.
- Do not design optional harness adapters in this issue.
- Do not keep old skill directories as compatibility aliases.
- Do not use skills as a first-time human tutorial.
- Do not require Context7 at skill runtime.
- Do not copy historical specs or plans into the skill bundle.

## Final Directory Tree

The final tree is:

```text
skills/
  cap4k-authoring/
    SKILL.md
    routing.yaml
  cap4k-business-discovery/
    SKILL.md
    workflows/
    references/
  cap4k-tactical-modeling/
    SKILL.md
    workflows/
    references/
  cap4k-technical-design/
    SKILL.md
    workflows/
    references/
  cap4k-generator-inputs/
    SKILL.md
    workflows/
    references/
  cap4k-generation-review/
    SKILL.md
    rules/
    workflows/
    references/
  cap4k-handwritten-implementation/
    SKILL.md
    rules/
    workflows/
    references/
  cap4k-verification-audit/
    SKILL.md
    rules/
    workflows/
    references/
  cap4k-service-integration/
    SKILL.md
    rules/
    workflows/
    references/
  shared/
    rules/
    workflows/
    references/
  scripts/
    validate-cap4k-skills.ps1
    checks/
```

All runtime skill content uses English. Exact cap4k identifiers remain unchanged.

## Routing Model

`cap4k-authoring` is the only recommended entry skill. It is a thin router. It must not teach deep rules and must not duplicate a full route table.

`cap4k-authoring/SKILL.md` does only this:

1. Direct the agent to read `routing.yaml`.
2. Identify the current authoring phase.
3. Load the routed phase or specialist skill.
4. Load required shared gates and rules.

`routing.yaml` is the only routing source of truth.

Each route uses this shape:

```yaml
id:
trigger_examples:
positive_signals:
negative_signals:
route_first:
then_chain:
required_reads:
workflow:
specialist_handoffs:
rollback_targets:
verification_mode:
```

Simple routes may omit advanced fields. High-risk routes must include `positive_signals`, `negative_signals`, and `rollback_targets`.

High-risk route families include:

- generation
- handwritten implementation
- service integration
- verification claims
- structural skeleton creation or modification
- ownership conflict
- generated output review

## Phase Skills

### cap4k-business-discovery

Purpose: capture business intent before tactical design.

Output:

- business goal
- actors and external parties
- business vocabulary
- state changes
- read needs
- external facts, callbacks, or messages
- policy, eligibility, compensation, and workflow words
- open decisions

This skill does not choose Aggregate boundaries or generator inputs. It marks risk words and routes to tactical modeling.

### cap4k-tactical-modeling

Purpose: map business language to cap4k tactical carriers.

Required shared read:

- `shared/references/tactical-affordance-map.md`
- `shared/workflows/forced-rollback.md`

Output:

- Aggregate boundary candidates
- Value Object and Strong ID candidates
- Command and Query split
- Domain Event versus Integration Event decisions
- Subscriber, Saga, and Scheduled Reaction decisions
- Domain Service and Specification decisions
- External Capability and Published Language decisions
- rollback notes

### cap4k-technical-design

Purpose: turn approved tactical model into a fixed technical design contract.

Required output headings:

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

No generator input authoring starts before this contract exists.

### cap4k-generator-inputs

Purpose: project the technical design into supported cap4k generator inputs.

Allowed input surfaces:

- DB/schema
- `design/design.json`
- value-object manifest
- enum manifest
- Gradle extension
- addons/options
- template override decisions

Missing generator-supported skeletons must return to generator inputs or technical design. Do not handwrite missing skeletons to fix compilation.

### cap4k-generation-review

Purpose: review plan evidence, generation output, ownership, and conflict policy.

This skill replaces the old generated-output-review boundary.

Workflow:

```text
run or guide cap4kPlan / bootstrap plan when allowed
review plan.json / bootstrap-plan.json
classify output ownership
check conflictPolicy / templateId / module placement / outputKind
run cap4kGenerate only after gate allows
stop after generation for human review
```

Generation may be run when the environment and user permit it. After generation, the agent must stop for human review of generated diff, ownership, and plan-output alignment. It must not continue into handwritten implementation.

### cap4k-handwritten-implementation

Purpose: implement business logic inside approved generated skeleton surfaces.

Entry conditions:

- generation output has been reviewed by a human;
- user explicitly authorizes implementation;
- Skeleton Generation Gate passes for any structural creation or modification.

This skill must not create generator-supported skeletons outside cap4k generation unless an explicit technical design exception exists.

### cap4k-verification-audit

Purpose: verify structure, ownership, behavior, and evidence without overstating what was checked.

Verification modes:

```text
static-only
focused-local
full-evidence
```

`static-only` covers text review, diff review, plan/output ownership review, path review, and drift scans.

`focused-local` may run targeted local generation, analysis, compile, or tests only when the user and environment allow it.

`full-evidence` may run broader compile/test/analysis/HTTP evidence only when explicitly authorized.

Final claims must match the evidence actually produced.

## Specialist Skill

### cap4k-service-integration

Purpose: handle service-boundary design and implementation risks.

Internal structure:

```text
cap4k-service-integration/
  SKILL.md
  workflows/
    design-open-host-service.md
    consume-external-capability.md
    handle-inbound-integration-event.md
  rules/
    published-language.md
    integration-event-boundaries.md
  references/
    gotchas.md
```

The routing manifest inserts this specialist when a task contains Open Host Service, Published Language, external capability, external fact, callback, message, inbound Integration Event, outbound Integration Event, or cross-service boundary decisions.

Critical runtime boundary:

- cap4k framework/runtime transport handles HTTP/message consume, parse, register, and dispatch.
- business application inbound subscriber handles typed external fact interpretation, idempotency, semantic translation, and Command/application delegation.
- business projects use UoW/Mediator framework capabilities; they do not implement UoW/Mediator.

## Shared Content

### shared/rules

`cap4k-positioning.md`

- cap4k is a DDD tactical framework plus generator-backed authoring system.
- It is not a generic CRUD scaffold.
- It is not a business decision automaton.

`generated-skeleton-ownership.md`

- Skeletons are generated by cap4k.
- Complex business logic is implemented inside generated skeletons.
- Do not create parallel structure unless technical design explicitly records an exception.

`layer-and-runtime-boundaries.md`

- Domain owns business invariants.
- Application owns use-case orchestration.
- Adapter owns protocol shape mapping.
- Start owns runtime assembly.
- Repository handles aggregate read/access/load.
- Unit of Work owns persistence intent, delete intent, commit, and save.
- Mediator is a framework facade, not a business engine.
- Integration event transport boundary follows the service-integration rule above.

`generator-input-source-of-truth.md`

- DB/schema, `design/design.json`, type manifests, Gradle extension, addons/options are generation inputs.
- `plan.json` is generated evidence, not business source truth.
- analysis outputs are observation evidence, not source skeletons.

`naming-layout-and-testing.md`

- File name plus directory must make the role inferable.
- Layer, package, and test placement must match cap4k conventions.
- Testing must distinguish domain behavior, application orchestration, adapter mapping, runtime wiring, and generation evidence.

`verification-claim-policy.md`

- No verification claim without evidence.
- Static-only evidence cannot be reported as full verification.
- Skipped checks must be disclosed.

### shared/workflows

`skeleton-generation-gate.md`

Trigger before creating or editing:

- command, query, client, api payload
- command handler, query handler, client handler
- domain event, integration event, subscriber skeleton
- domain service, saga, scheduled reaction
- aggregate, entity, relation, projection
- factory, specification, unique helper
- repository, controller, adapter, start skeleton
- package or directory skeleton
- any class/interface added only to fix compilation

Gate questions:

1. Is this generator-supported?
2. Can current cap4k express it through DB/design/types/addon/options?
3. Does the project already have corresponding input?
4. Has plan evidence been reviewed?
5. Will generation create or update it?
6. If handwritten, is the exception in technical design?
7. Does the handwritten path preserve generated-vs-handwritten ownership?

Allowed pass states:

- not generator-supported;
- generated from inputs and reviewed in plan;
- explicitly documented technical design exception;
- author-owned logic inside generated skeleton;
- explicit user override with risk and verification recorded.

Failure action:

- stop implementation;
- return to technical design or generator inputs;
- update inputs or exception decision;
- review plan again.

`forced-rollback.md`

Rollback table:

```text
concept mismatch -> tactical modeling
unclear carrier -> technical design
missing input -> generator inputs
plan mismatch -> generator inputs or technical design
generation ownership conflict -> generation review
implementation bypass -> technical design
structure drift -> earliest phase that introduced wrong assumption
verification drift -> earliest phase that introduced wrong assumption
```

### shared/references

`tactical-affordance-map.md`

Matrix columns:

```text
business signal
cap4k carrier
when to use
when not to use
generator input surface
expected skeleton / plan evidence
handwritten logic location
verification evidence
rollback trigger
```

`generator-supported-skeletons.md`

Lists generator-supported skeleton families and where agents must return when expected skeletons are missing.

`output-ownership-taxonomy.md`

Distinguishes checked-in skeletons, build-owned generated source, generated snapshots/evidence, template overrides, and handwritten logic.

`runtime-capability-map.md`

Captures UoW, Mediator, Repository, Specification, Integration Event transport, Saga, and analysis evidence boundaries needed at skill runtime.

`drift-gotchas.md`

Stores costly pitfalls and must be activated from task paths, not only stored as background.

## Progressive Loading

The system follows `skill-based-architecture`.

Hard rules:

- `SKILL.md` is at most 100 lines.
- Always-read files are at most 2-3.
- `SKILL.md` links one level deep.
- `routing.yaml` is the routing source.
- `rules/` stores stable constraints.
- `workflows/` stores procedures.
- `references/` stores architecture, maps, gotchas, and source-independent facts.
- Gotchas must be reachable from task routes.

Line count for rules, workflows, and references is a review signal, not an automatic failure. Split files when topics are independently navigable or when a reader needs only one part.

## Source-To-Skill Extraction Map

| Source | Extract into | Notes |
| --- | --- | --- |
| Shared documentation-system design | authority chain, reader boundaries, self-contained runtime rule, skills workflow philosophy | Do not copy public/analysis dependency into runtime. |
| Analysis maps | runtime facts, generator/source contracts, analysis evidence boundaries, stale-term families | Transform code facts into bundle-local rules. |
| Public concepts | tactical affordance map, layer boundaries, command/query/event/repository/UoW/Mediator meanings | Public prose becomes operational agent constraints. |
| Public architecture | layer-and-runtime-boundaries, testing policy, adapter/application/start responsibilities | Keep Clean Architecture and framework runtime boundary precise. |
| Public authoring | end-to-end authoring workflow, technical design contract, generation stop point, verification feedback | Convert human guidance to executable phase gates. |
| Public generator/reference | generator input surfaces, plan/output fields, ownership taxonomy, analysis evidence contract | Do not require public docs at runtime. |
| Coarse skills outline | Skeleton Generation Gate, forced rollback, bundle shape, acceptance criteria | This design resolves the coarse outline decisions. |
| Current skills | useful workflows, gotchas, current validation behavior | Migrate useful content into new phase/specialist files, delete old directories. |
| Issue #100 | problem statement, acceptance criteria, related evidence | Use as scope confirmation, not runtime content. |

## Migration Strategy

Clean cut. No alias wrappers.

| Current path | Final handling |
| --- | --- |
| `skills/cap4k-authoring` | Rebuild as thin router and `routing.yaml`. |
| `skills/cap4k-modeling` | Split into `cap4k-business-discovery` and `cap4k-tactical-modeling`. |
| `skills/cap4k-generation` | Split into `cap4k-generator-inputs` and `cap4k-generation-review`. |
| `skills/cap4k-generated-output-review` | Merge into `cap4k-generation-review`. |
| `skills/cap4k-implementation` | Rebuild as `cap4k-handwritten-implementation`. |
| `skills/cap4k-verification` | Rebuild as `cap4k-verification-audit`. |
| `skills/cap4k-service-integration` | Rebuild in place as specialist. |
| `skills/shared` | Rebuild aggressively as rules/workflows/references. |
| `skills/scripts/validate-cap4k-skills.ps1` | Keep as entrypoint, delegate to checks. |

## Validation Design

Validation tree:

```text
skills/scripts/
  validate-cap4k-skills.ps1
  checks/
    structure.ps1
    routing.ps1
    progressive-loading.ps1
    self-contained-runtime.ps1
    skeleton-gate-refs.ps1
    stale-terms.ps1
    link-check.ps1
```

Hard fail checks:

- missing `SKILL.md` or frontmatter;
- `SKILL.md` over 100 lines;
- always-read over 3 files;
- missing `routing.yaml`;
- duplicated independent route table;
- missing required Skeleton Generation Gate references;
- missing required forced rollback references;
- runtime skills requiring `docs/public`, `docs/superpowers/analysis`, issues, Context7, or cap4k source checkout;
- broken local links;
- old skill directories still present;
- forbidden stale terms.

Warning or review-signal checks:

- large rules/workflows/references file;
- gotcha not activated from any task route;
- route lacks trigger examples;
- advanced route lacks positive or negative signals;
- affordance map row lacks verification or rollback column.

Stale term checks include:

- `sources.irAnalysis.enabled`
- `generators.flow.enabled`
- `generators.drawingBoard.enabled`
- old KSP plan/generate wording
- design validator / unsupported validator tag wording
- wrong integration-event design support
- wrong Repository save ownership
- wrong UoW/Mediator implementation wording
- callback/message transport assigned to business subscriber
- generated-output / handwritten ownership blur
- `src-generated/main/kotlin`
- `client/cli`
- spaced output paths such as `build/cap4k code analysis` and `build/cap4k/analysis plan.json`

## End-To-End Dry Run

Use Reference Content Studio's default publication flow:

```text
draft content
submit review
approve content
media processing external fact
media ready
publication ready
publish
```

The dry run must show:

- business discovery output;
- tactical carrier choices;
- technical design contract;
- generator input expectations;
- plan review expectations;
- generation stop point;
- human review gate;
- handwritten implementation surfaces;
- verification mode choice;
- rollback examples.

This is a spec-level dry run. It does not require running Gradle tasks.

## Implementation Handoff

Implementation planning should use subagent-driven slices where write scopes are independent.

Every implementation subagent must:

1. Read `skill-based-architecture` before editing.
2. Own a disjoint write scope.
3. Avoid preserving old skill names unless the migration table says so.
4. Keep `SKILL.md` thin.
5. Treat `routing.yaml` as source of routing truth.
6. Keep always-read files under budget.
7. Run only allowed static checks unless the user explicitly permits broader execution.

Suggested independent slices:

- routing and `cap4k-authoring`;
- shared rules/workflows/references;
- discovery/modeling/design phase skills;
- generator-inputs/generation-review phase skills;
- implementation/verification phase skills;
- service-integration specialist;
- validation script checks;
- migration/delete audit and dry run.

## Acceptance Criteria

- Final skill bundle directory tree is defined.
- Final skill names and route responsibilities are defined.
- `routing.yaml` is the only route source of truth.
- Bundle self-contained content inventory is defined.
- Tactical affordance map shape is defined.
- Complete spiral workflow is defined.
- Forced rollback table is defined.
- Skeleton Generation Gate workflow is defined.
- Technical Design Output Contract is defined.
- Generator input and plan review workflows are defined.
- Generation is allowed only through gate-based automation and stops after generation for human review.
- Handwritten implementation requires explicit user authorization after generation review.
- Verification audit workflow supports static-only, focused-local, and full-evidence modes.
- Old skill deletion and migration strategy is defined.
- Validation script redesign is defined.
- Source-to-skill extraction map is defined.
- One end-to-end dry-run scenario is defined.
- Subagent-driven implementation handoff requires each subagent to read `skill-based-architecture`.

## Handoff

After this spec is approved, write an implementation plan. Do not implement skills from memory or from the coarse outline alone.

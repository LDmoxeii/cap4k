# Cap4k Authoring and AI Skill Guide Design

Date: 2026-05-11

Status: Proposed

Scope: close the first version of `#17` by consolidating human-facing authoring guidance under `docs/public/authoring`, and by introducing a repo-local AI authoring skill that is self-contained at runtime.

Out of scope: framework runtime changes, generator feature implementation, runnable reference project changes, full English parity, and using the AI skill as a link collection to public documentation.

## Background

`#17` originally tracked a broad "DDD plus cap4k plus AI collaboration guide" requirement.

The prerequisite slices have now provided enough evidence for a first version:

- `#21` established practical advanced-concept examples.
- `#18` established the testing-contract direction.
- `#27-v1` provides an accepted reference-project shape, even though the larger total-goal issue can continue.
- `#33` clarified addon and external generator extension direction.

The remaining problem is not one more long document. The project now needs two parallel guidance surfaces for two different users:

- human authors, who make decisions and perform final audit
- AI authors, who assist decisions, implement most changes, and run pre-audit verification

These two surfaces may share conclusions, but they must not depend on each other at runtime.

## Problem

The current public documentation still exposes multiple entry surfaces:

- `docs/public/getting-started.zh-CN.md`
- `docs/public/framework-positioning.zh-CN.md`
- `docs/public/authoring/index.zh-CN.md`

This makes the human authoring path harder to reason about because project positioning, quick start, and authoring rules are split across peer-level public entries.

The AI-authoring side has a different problem. If the AI guide is written as another public documentation page, agents will either:

- load too much context by repeatedly reading `docs/public/authoring` and the reference project, or
- miss key cap4k-specific tactical rules because the guide only links to external material.

For `#17-v1`, the human and AI surfaces must therefore be separated by audience and optimized for their respective use.

## Goals

1. Make `docs/public/authoring` the single public human-authoring system.
2. Move the public quick-start and framework-positioning entry points into the authoring tree.
3. Update README navigation so it points into the authoring system instead of root-level duplicate pages.
4. Create a repo-local AI authoring skill that is self-contained when invoked.
5. Structure the AI skill with progressive disclosure, following the `skill-based-architecture` pattern:
   - thin `SKILL.md`
   - stable constraints in `rules/`
   - procedures in `workflows/`
   - background, gaps, and pitfalls in `references/`
6. Mark known missing capabilities and follow-up extension points explicitly instead of pretending they are already supported.
7. Keep `#17-v1` small enough to implement without touching framework runtime or generator behavior.

## Non-Goals

This slice does not:

- implement value-object, saga, domain-service, or integration-event generator support
- change the cap4k runtime
- change the reference project
- require the AI skill to read `docs/public/authoring` during normal operation
- require the AI skill to read `cap4k-reference-content-studio` during normal operation
- create a generic DDD guide detached from cap4k
- make humans responsible for reading AI-specific skill internals
- complete English parity for all moved or expanded authoring content

## Design Principles

### 1. One human authoring surface

Human-facing guidance belongs under:

```text
docs/public/authoring/
```

Root-level `getting-started` and `framework-positioning` pages should not remain peer public entry points. Their content should become part of the authoring system.

### 2. AI skill is not a public-doc link farm

The AI authoring skill can absorb already-agreed project conclusions while it is being written, but after creation it must be usable without requiring agents to load external documentation or example repositories.

The skill should carry the cap4k-specific operating rules it needs.

### 3. Separate roles, shared project discipline

Human authors:

- decide the domain and architectural direction
- audit final behavior and code shape
- decide when a gap becomes a new issue

AI authors:

- help clarify decisions
- produce specs and plans when needed
- implement the main changes
- run TDD, compile, generation, analysis, and link checks before final audit
- report evidence and remaining gaps

### 4. Progressive disclosure for AI guidance

The AI skill should not load every rule for every task.

`SKILL.md` should route common tasks to the smallest relevant rule and workflow files. Expensive background and known gaps should be available, but only loaded when the task path needs them.

### 5. Known gaps remain visible

Current missing or incomplete surfaces must be recorded as extension points:

- value object authoring and generator support
- saga authoring and generator support
- domain service authoring and generator support
- integration event design and generator support
- public tactical model definition
- layered model definition
- design support for command, query, cli, domain event, integration event, value object, and domain service
- `drawing_board.json` usage for external integration-event communication
- addon and SPI guidance beyond the first stable surface

## Human Authoring Documentation Design

The target public structure is:

```text
docs/public/authoring/
  index.zh-CN.md
  index.md

  getting-started.zh-CN.md
  getting-started.md
  framework-positioning.zh-CN.md
  framework-positioning.md

  default-happy-path.zh-CN.md
  default-happy-path.md
  domain.zh-CN.md
  application.zh-CN.md
  adapter.zh-CN.md
  naming-and-layout.zh-CN.md
  generation-boundaries.zh-CN.md
  testing-contract.zh-CN.md
  example-contract.zh-CN.md
  generator/
  advanced/
  examples/
```

The root-level files should be removed after their content is moved:

```text
docs/public/getting-started.zh-CN.md
docs/public/getting-started.md
docs/public/framework-positioning.zh-CN.md
docs/public/framework-positioning.md
```

`README.zh-CN.md` and `README.md` should point to the authoring tree, not to the removed root-level documents.

The authoring index should act as the public decision hub:

- framework positioning
- quick start
- default happy path
- generator and analysis
- domain, application, and adapter authoring
- testing contract
- advanced concepts and practical examples
- known gaps and extension points

The index should remain navigational. It should not absorb every detail from quick start and framework positioning.

## AI Authoring Skill Design

The AI guidance should be delivered as a repo-local skill:

```text
.agents/skills/cap4k-authoring/
  SKILL.md
  rules/
    role-boundary.md
    layering-and-tactical-model.md
    generator-ownership.md
    verification-contract.md
  workflows/
    design-before-code.md
    implement-cap4k-project-slice.md
    review-generated-output.md
    close-task-with-evidence.md
  references/
    gotchas.md
    public-tactical-model.md
    known-gaps.md
    issue-lifecycle.md
```

### SKILL.md responsibilities

`SKILL.md` should stay short. It should define:

- trigger conditions
- when not to use the skill
- role split between human and AI
- common task routing
- priority rules
- session discipline

It should not contain the full rulebook.

### Rules

`rules/` contains stable constraints:

- AI does not replace human final decisions.
- Commands own write-side behavior.
- Repositories, factories, domain services, and unit of work are command-handler-side tools.
- Process orchestration may use command, query, and cli boundaries.
- Generated artifacts, handwritten artifacts, copied generated snapshots, and template overrides must remain distinguishable.
- Missing framework support must be recorded as a gap, not implemented as an implied contract.

### Workflows

`workflows/` contains procedures:

- clarify domain flow before code
- write a spec or concise design before substantial changes
- identify generated vs handwritten ownership before editing
- inspect generator output and conflict policies
- use TDD where behavior is being changed
- run compile, generation, analysis, and relevant tests before claiming completion
- update issues with evidence and remaining gaps

### References

`references/` contains background and lower-frequency material:

- high-cost gotchas
- public tactical model vocabulary
- known generator and design gaps
- issue lifecycle guidance

Known gotchas must also be reachable from normal task routing. They must not be stored only as passive background.

## README and Link Design

`README.zh-CN.md` should point readers to:

- `docs/public/authoring/index.zh-CN.md`
- `docs/public/authoring/getting-started.zh-CN.md`
- `docs/public/authoring/framework-positioning.zh-CN.md`

`README.md` should point readers to:

- `docs/public/authoring/index.md`
- `docs/public/authoring/getting-started.md`
- `docs/public/authoring/framework-positioning.md`

Internal links from moved documents must be updated relative to their new location.

Any remaining links to removed root-level files should be treated as implementation defects.

## Validation

The implementation should verify:

1. Git diff has no whitespace errors.
2. Root-level `getting-started*` and `framework-positioning*` files no longer exist.
3. README links point into `docs/public/authoring`.
4. Moved documents have correct relative links.
5. The AI skill has a concise `SKILL.md` and separates rules, workflows, and references.
6. The skill does not instruct agents to load `docs/public/authoring` or `cap4k-reference-content-studio` as normal runtime context.
7. Known gaps are explicitly documented in the skill.
8. No framework runtime or generator implementation files are changed.

## Risks

### Link breakage

Moving root public docs can break README and internal links.

Mitigation: explicitly scan for removed paths after edits.

### Skill bloat

The AI skill can become another oversized guide.

Mitigation: keep `SKILL.md` as a router and place detailed content in task-routed files.

### Hidden dependency on human docs

The AI skill may accidentally tell agents to read public docs or the reference project during normal use.

Mitigation: state the self-contained constraint in both the spec and the skill; scan for those references during validation.

### Premature completeness claims

The guide may imply unsupported generator capabilities are already available.

Mitigation: list missing capabilities as known gaps and future extension points.

## Acceptance Criteria

- Human-facing authoring guidance has one public tree under `docs/public/authoring`.
- Root-level quick-start and framework-positioning docs are removed after their content is moved.
- READMEs no longer reference removed root-level docs.
- AI authoring guidance is delivered as a repo-local skill with progressive disclosure.
- The AI skill is self-contained at runtime and does not depend on loading human authoring docs or the reference project.
- Known missing capabilities are visible as extension points.
- The work remains documentation and skill guidance only.

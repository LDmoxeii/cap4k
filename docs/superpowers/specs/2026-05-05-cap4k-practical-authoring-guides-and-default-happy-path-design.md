# Cap4k Practical Authoring Guides and Default Happy Path Design

Date: 2026-05-05

Status: Proposed

Scope: define a complete authoring-guide documentation system for `cap4k`, centered on Default Happy Path, practical project authoring, layered responsibilities, generation-vs-handwritten boundaries, and audit-oriented writing rules that later AI-collaboration guidance can rely on.

Out of scope: AI-collaboration guide writing, framework runtime refactoring, advanced-concept runtime implementation, second-development guide writing, complete English parity for all deep guides in phase one, and broad repository cleanup outside documentation movement required by this slice.

## Background

The public documentation rewrite fixed the top-level entry problem, but it did not solve the deeper authoring problem.

`cap4k` now has:

- a framework capability baseline from `#15`
- a public entry and positioning surface from `#16`
- advanced-concept example follow-up work tracked in `#21`
- AI-collaboration guidance reserved for `#17`

What is still missing is the practical writing model for real projects:

- how domain code should be written
- how application code should be written
- how adapter code should be written
- how generation should be used
- how default rules should constrain all of the above
- how humans should review and audit AI-assisted code instead of acting as black-box testers

Without this authoring layer, later AI-collaboration guidance will drift because there is no stable human-facing writing model underneath it.

## Problem

The current documentation is still too high-level for day-to-day authoring work.

The main gap is not one missing page. It is the lack of a complete authoring-guide system that can answer:

- what the default writing path is
- which rules are mandatory and which are only default recommendations
- what belongs in domain, application, adapter, and generated surfaces
- what must be generated, what must be handwritten, and what can be mixed
- what examples should look like
- how a human reviewer should audit whether the project still respects cap4k's tactical boundaries

If this layer remains missing, the project will tend to collapse into:

- concept drift across layers
- inconsistent generation and hand-writing boundaries
- README-level understanding without project-level discipline
- AI output that cannot be meaningfully reviewed by humans

## Goals

1. Define a complete authoring-guide system for cap4k-based projects.
2. Make Default Happy Path the center of the writing model rather than a side note.
3. Provide full first-phase authoring coverage for:
   - generator usage
   - domain authoring
   - application authoring
   - adapter authoring
   - advanced concepts
4. Establish horizontal contracts for:
   - naming and layout
   - generation vs handwritten boundaries
   - example structure
   - minimal authoring workflow
5. Make the guides practical enough that a project author can directly start a cap4k project from them.
6. Make the guides strong enough that a human reviewer can audit AI-assisted code against them.
7. Prepare a stable upstream writing model for `#17` instead of forcing AI-collaboration guidance to invent rules on its own.

## Non-Goals

This slice does not:

- collapse into a light README wording pass
- write the AI-collaboration guide itself
- replace code and architecture reading for deep framework contributors
- provide a second-development guide for extending the framework internals
- force all deep authoring guides to English in phase one
- fully populate every possible example category in phase one

## Design Principles

The authoring-guide system must follow these principles.

### 1. Default Happy Path is the center

The framework is not taught as a bag of capabilities.

It is taught as:

- one default writing path
- some bounded advanced deviations
- some runtime and infrastructure realities

Every guide must be anchored back to Default Happy Path.

### 2. Humans remain reviewers and auditors

The target is not to let humans become opaque acceptance testers for AI-generated code.

The target is:

- humans understand the framework deeply enough to review code shape
- AI has explicit writing rules it can follow
- both sides operate from one shared project model

### 3. Current reality first

The guides must reflect the current real cap4k landing shape:

- current Spring + JPA reality
- current repository and event runtime reality
- current generation and analysis task families

This is not the place to teach a future idealized architecture instead of the real one.

### 4. Complete phase-one authoring coverage

This slice does not stop at a minimal skeleton.

Phase one must deliver a full authoring system across the six major guide areas, even if deeper example expansion continues later.

### 5. One consistent reference project

All guides must speak through one shared teaching project and one shared end-to-end chain, instead of each guide inventing independent examples.

## Delivery Scope

Phase one delivers a complete authoring-guide system, not only an outline.

The required output is:

1. one authoring overview document
2. one complete Default Happy Path guide
3. one complete Generator guide family
4. one complete Domain authoring guide
5. one complete Application authoring guide
6. one complete Adapter authoring guide
7. one complete Advanced Concepts guide family
8. horizontal rule documents for naming, boundaries, and example structure
9. a minimal but real example appendix set
10. a generator DSL reference document

Phase one does not require:

- deep second-development extension docs
- complete English parity for every deep guide
- full example saturation for every advanced topic

## Documentation System

The documentation system is organized in three layers.

### Layer 1: Entry documents

Existing public entry documents remain:

- `README.md`
- `README.zh-CN.md`
- `docs/public/getting-started.md`
- `docs/public/getting-started.zh-CN.md`
- `docs/public/framework-positioning.md`
- `docs/public/framework-positioning.zh-CN.md`

These documents remain entry and navigation surfaces only.
They must not absorb the full authoring-guide responsibility.

### Layer 2: Authoring guide tree

The main authoring work lives under:

```text
docs/public/authoring/
```

Phase one must introduce:

```text
docs/public/authoring/
  index.zh-CN.md
  index.md

  default-happy-path.zh-CN.md
  default-happy-path.md

  domain.zh-CN.md
  application.zh-CN.md
  adapter.zh-CN.md

  naming-and-layout.zh-CN.md
  generation-boundaries.zh-CN.md
  example-contract.zh-CN.md

  generator/
    index.zh-CN.md
    bootstrap.zh-CN.md
    code-generation.zh-CN.md
    code-analysis.zh-CN.md

  advanced/
    index.zh-CN.md
    value-object.zh-CN.md
    domain-service.zh-CN.md
    saga.zh-CN.md
    strong-id.zh-CN.md
    read-only-weak-reference.zh-CN.md

  examples/
    reference-project-overview.zh-CN.md
    content-draft-to-publish.zh-CN.md
    media-processing-callback.zh-CN.md
    media-processing-polling.zh-CN.md
```

### Layer 3: Reference documents

High-density configuration material must not bloat the main guide documents.

Phase one therefore adds:

```text
docs/public/reference/
  generator-dsl.zh-CN.md
```

The main Generator Guide should explain usage and decision flow.
The full DSL field reference belongs in this reference layer.

## Language Strategy

Phase one uses a deliberate split:

- Chinese-first for the full authoring system
- English coverage for:
  - entry layer
  - `authoring/index`
  - `default-happy-path`

Deep English parity is explicitly deferred.

The goal is to make the writing model accurate first, then extend language coverage.

## The Shared Teaching Project

All guides must revolve around one shared teaching project:

- **内容发布与处理示例项目**
- in English docs, the equivalent neutral phrasing should be used

This is not a toy playground. It is the standard example surface for the authoring system.

### Business scope

The project combines:

- content publishing and review
- media processing flow
  - transcoding
  - encryption
  - processing state progression

### Bounded-context scope

The teaching project stays within:

- one bounded context
- multiple aggregate roots

It does not split into multiple bounded contexts in phase one.

### Required aggregate roots

At minimum:

- `Content`
- `MediaProcessingTask`

### Required read-side scope

The teaching project must also include explicit read-side/query-side examples:

- content detail/list query
- media-processing progress query

This is required to teach:

- repository remains write-model only
- read models are separate
- query observation must not pollute write-model boundaries

### Required external boundary

Media processing must be treated as an external system boundary accessed through `cli`.

The result-return path must support both:

- primary path: callback / integration-event return
- fallback path: polling job

The docs must state clearly:

- callback / integration-event return is the recommended main path
- polling is the compatibility fallback path

### Required end-to-end chain

The guides must be able to reuse one common chain:

1. create content draft
2. submit content for review
3. start media processing
4. receive processing result from the external system
5. publish content once review is approved and media processing is complete
6. support retry or rollback when processing fails
7. provide content and processing-progress queries

## The Authoring Center: Overview and Default Happy Path

### `authoring/index`

The overview document is the entry for the authoring system.

It must:

- explain why the authoring system exists
- explain that cap4k expects humans to understand the framework rather than only test outputs
- make Default Happy Path the center
- provide two reading paths:
  - project author
  - framework contributor / deep user
- route readers into the six guide families and the horizontal rule documents

### `default-happy-path`

This is the central normative document of the whole authoring system.

It is not just another topic page.

It must use a two-layer structure:

1. compact hard-rule table
2. flow-based expansion

The flow-based expansion order is fixed:

- modeling
- command
- event
- orchestration
- query
- integration boundary

Every rule must include:

- strength level
- rationale
- non-example
- audit cues

### Rule strength vocabulary

The system uses one fixed strength vocabulary:

- `Must`
- `Default`
- `Avoid`
- `Advanced`

This vocabulary must stay stable across all authoring docs so humans and AI can interpret rule weight consistently.

## The Six Guide Families

The first phase must fully cover the following six guide families.

### 1. Default Happy Path Guide

This is the normative center and governs every other guide.

It must explain:

- the hard rules themselves
- why those rules exist
- what code shape they permit
- what failure shape appears when they are ignored
- what reviewers must check

### 2. Generator Guide

This guide family must be written from the project-author perspective.

It should reuse and restructure valuable material from the current deep module documentation, especially the existing `cap4k-plugin-pipeline-gradle/README.md`, but it must not preserve that README's current mixed audience and mixed responsibility shape.

The Generator Guide family consists of:

- `generator/index`
- `generator/bootstrap`
- `generator/code-generation`
- `generator/code-analysis`

It must include:

- minimal architecture explanation needed by project authors
- hard usage boundaries for:
  - `cap4kPlan`
  - `cap4kGenerate`
  - `cap4kAnalysisPlan`
  - `cap4kAnalysisGenerate`
- when authors should read `plan.json`
- when authors should not skip planning and jump directly into generation
- how generation output and handwritten code connect
- practical failure diagnosis from the author perspective

It must not include:

- second-development guide content
- framework-extension walkthroughs

### 3. Domain Authoring Guide

This guide must explain how to write the domain layer under Default Happy Path constraints.

It must cover:

- Aggregate Root and Entity responsibilities
- Value Object placement and boundaries
- where behavior belongs
- lifecycle and state-machine expression
- Domain Event registration and release
- what does not belong in domain
- common aggregate-boundary mistakes

### 4. Application Authoring Guide

This guide must explain how to write the application layer under Default Happy Path constraints.

It must cover:

- Command, Query, and Handler responsibilities
- how orchestration advances process steps
- when to issue commands
- when to query
- when `cli` is justified
- why application entry surfaces must not directly mutate aggregates
- what does not belong in application

### 5. Adapter Authoring Guide

This guide must explain how to write the adapter layer under Default Happy Path constraints.

It must cover:

- controller entry points
- persistence adapters
- domain event subscriber entry points
- integration event subscriber entry points
- job-based polling entry points
- external boundary input conversion into internal command/query paths
- why adapter is not a domain truth source
- what does not belong in adapter

### 6. Advanced Concepts Guide

This guide must not be written as a flat glossary.

It must start from:

- when the default path is no longer sufficient
- why a deviation is justified
- what trade-off the deviation introduces

The family must include at least:

- `advanced/index`
- `advanced/value-object`
- `advanced/domain-service`
- `advanced/saga`
- `advanced/strong-id`
- `advanced/read-only-weak-reference`

The concept pages should contain short practical examples, while richer example material can continue to expand later through `#21` and later example slices.

## Shared Guide Skeleton

The Domain, Application, and Adapter guides must all share one fixed skeleton.

Each of those guides must answer:

1. what this layer is responsible for
2. what may be written in this layer
3. what must not be written in this layer
4. what the typical directory and file skeleton looks like
5. what common non-examples look like
6. what the minimum verification and audit checks are

This shared skeleton prevents each layer guide from teaching a different worldview.

## Horizontal Contracts

Three horizontal rule documents are mandatory.

### `naming-and-layout`

This document defines:

- recommended directory structure
- naming conventions
- file placement rules
- role-based suffix and prefix conventions

It must cover at least:

- aggregate root naming
- entity naming
- value-object naming
- status / lifecycle naming
- `Cmd`, `Qry`, `Cli`, `DomainEvent`
- `CommandHandler`, `QueryHandler`
- `DomainEventSubscriber`, `IntegrationEventSubscriber`
- repository naming
- processing-task and read-model naming

### `generation-boundaries`

This document defines the global generated-vs-handwritten boundary matrix.

It must explicitly explain:

- what must be generated
- what must be handwritten
- what may be mixed
- what may be overridden but carries maintenance cost

It must also clearly separate:

#### Current Reality

- current template override works at project-level unified `overrideDirs` + preset-path replacement
- current override is not an artifact-level or family-level fine-grained override system
- current override use has real maintenance and upgrade-audit cost

#### Future Strengthening Directions

This section may record future strengthening candidates discovered during the documentation audit, even if there is not yet a dedicated issue.

Examples may include:

- finer-grained template override scopes
- artifact-level override boundaries
- stronger generated-vs-handwritten ownership markers
- plan-time diagnostics for override drift risk
- upgrade-audit support for overridden templates

This section records possible future strengthening only. It does not turn them into promised current capability.

### `example-contract`

This document defines the shared example structure that all authoring examples must follow.

Every example must include:

1. `Scenario`
2. `Why this layer / concept`
3. `Recommended shape`
4. `Non-example / misuse`
5. `Audit cues`

This is a formal requirement, not a writing preference.

## Generated vs Handwritten Boundary Matrix

The guide system must include a global matrix that project authors and reviewers can use directly.

That matrix must help answer:

- which files are generator-owned
- which files are handwritten-only
- which files can safely coexist with generated surfaces
- when template override is the correct tool
- when override is only an advanced path with explicit maintenance cost

This matrix must become the shared audit basis for:

- Generator Guide
- Domain/Application/Adapter guide boundaries
- later AI-collaboration guidance

## Minimal Workflow Contract

`#26` must include a minimal workflow contract for project authors.

It must cover at least:

- when to write spec and plan first
- when to run `cap4kPlan`
- when to run `cap4kGenerate`
- when to enter `cap4kAnalysis*`
- when handwritten work is expected
- when verification is required
- when review is required

This workflow contract is intentionally smaller than the future AI-collaboration guide, but it must still be explicit enough to stop authoring drift.

## Example Appendix Scope

Phase one does not need every possible example, but it does need enough example slices to make the guides actionable.

The example appendix must include at least:

- `reference-project-overview`
- `content-draft-to-publish`
- `media-processing-callback`
- `media-processing-polling`

Each example page must provide enough material that the six guide families can reference a shared chain instead of inventing topic-local samples.

## Required Example Granularity

Guide examples must not stop at abstract prose.

Each major guide must provide, where relevant:

1. directory-tree fragments
2. key file skeletons
3. generated vs handwritten markings
4. an end-to-end chain fragment showing how the current layer connects to its adjacent layers

This is required to meet the project-author standard of "can directly start writing from this guide."

## Testing and Verification Guidance

Every major guide family must include:

- what this layer should be verified for first
- what the minimum verification method is
- what must be checked after generation
- what a reviewer must check before calling the work acceptable

This is not a full testing strategy manual.
It is the minimum verification contract needed for project authors and reviewers.

## Audience Split

The authoring overview must explicitly distinguish two reading paths:

### Project author path

For people trying to build a cap4k project correctly.

### Framework contributor / deep user path

For people who need to understand deeper rationale or repository-level implementation reality.

The primary writing target of `#26` remains the project author path.
The contributor/deep-user path exists so the docs do not lose depth, but it must not dominate the first reading flow.

## Relationship To Existing Issues

### `#15`

`#15` is the positioning and capability baseline.

The authoring guides must obey:

- stable core concepts
- default happy-path rules
- advanced-concept positioning
- runtime/infra background positioning

### `#21`

`#21` remains the first explicit advanced-concept example issue, but it is not the only future source of examples.

The authoring system created here must establish a shared example contract that later example work continues to follow.

### `#23`

Read-only weak-reference modeling remains an advanced concept, not part of the default happy path.

Its guide placement belongs under the Advanced Concepts family.

### `#17`

`#17` must build on the authoring model defined here.

`#26` explains:

- how humans should write cap4k code
- how humans should audit cap4k code

`#17` can later explain:

- how humans and AI should collaborate around that same writing model

## Public vs Deep Documentation Boundary

This slice must not force all deep framework material into the public authoring surface.

In particular:

- do not write a second-development guide
- do not expose framework-extension internals as project-author default reading
- do not keep deep module README files as the main public authoring surface

However, existing deep documentation with project-author value may be restructured and migrated upward.

The current `cap4k-plugin-pipeline-gradle/README.md` is the main example:

- its project-author-valuable usage content should be restructured and moved into the Generator Guide family
- its mixed audience and second-development material should not remain the main authoring entry
- the module-local README can later become a thin pointer instead of a 1500+ line mixed guide

## Completion Criteria

This slice is complete only when the following are true:

1. a project author can start a cap4k project directly from the guide system
2. a reviewer can use the guide system to judge whether code drifted away from Default Happy Path
3. the guide system gives AI later guidance a stable upstream writing model
4. generator, domain, application, adapter, and advanced concepts are all covered in phase one
5. naming, layout, generated-vs-handwritten boundaries, example structure, and minimal workflow are no longer implicit knowledge
6. the deep module README is no longer the de facto primary generator usage manual

## Acceptance Criteria

- a complete authoring-guide tree is defined for phase one
- Default Happy Path is explicitly the center of the authoring model
- the six guide families and horizontal contracts have clear responsibilities
- the shared teaching project and end-to-end chain are fixed and reusable across guides
- generated-vs-handwritten boundaries are explicitly documented as current reality, not wishful future capability
- future strengthening directions can be recorded without pretending they already exist
- `#17` can later depend on this system instead of inventing authoring rules from scratch

# Cap4k Testing Skeleton Feasibility Design

Date: 2026-05-08

Status: Proposed

Scope: evaluate whether `cap4k` should provide built-in testing skeleton support, and if so, where that support should live, with primary attention on:

- `docs/public/authoring/**`
- `docs/public/getting-started*`
- `cap4k-ddd-starter`
- bootstrap / generated project shape
- current functional / fixture / example test organization

Out of scope:

- implementing a complete testing framework
- generic repository-wide test-utilities cleanup
- executing `#27` runnable reference project work
- rewriting the Default Happy Path
- forcing every project onto one heavy testing skeleton
- introducing a new runtime test-support artifact in this slice

## Backlog Source

This design is the evaluation slice for:

- `#18` testing: evaluate built-in testing skeleton feasibility

## Background

`#21` has now completed the public advanced-concept example chain and further stabilized the shared authoring reference project:

- `Content`
- `MediaProcessingTask`
- `MediaProcessingCli`
- callback as the preferred return path
- polling as the fallback return path

The new public docs repeatedly anchor the default handoff at:

- `ApproveContentCmd -> StartMediaProcessingCmd`

That matters for `#18` because testing guidance should not invent a second sample universe. If `cap4k` says anything official about project-level testing, that guidance should reuse the same shared reference project and the same default-path language that the authoring docs already teach.

## Problem

`cap4k` currently has three different testing realities, but no public project-level testing contract:

1. framework-side smoke and runtime fixture tests such as `cap4k-ddd-starter`
2. pipeline/TestKit/functional fixtures that prove generation and compile behavior
3. public authoring docs that explain modeling and boundaries, but do not yet explain what the default project testing shape should look like

This leaves a gap for later issues, especially `#27` and `#17`:

- without a testing contract, a runnable reference project will likely invent its own local testing culture
- if the contract is too heavy, domain behavior risks being hidden behind framework convenience
- if the contract is framed as runtime support too early, authors and AI assistants may mistake framework test magic for the intended modeling path

The real question is not “should `cap4k` ship more testing tools?” The real question is:

- what should count as the official default testing shape
- which layer, if any, should own that shape
- how much structure is enough before it starts distorting domain behavior

## Current Findings

### `#21` strengthens the default-path anchor instead of weakening it

The new advanced-concept docs do not introduce a new testing or runtime layer. They reinforce that authors should first make the default path legible before escalating to heavier concepts.

In particular, the docs now repeatedly treat these as the key default-path review anchors:

- the `ApproveContentCmd -> StartMediaProcessingCmd` handoff
- callback and polling converging into the same internal command semantics
- advanced concepts only after the default path has been expressed clearly

That strengthens the earlier `#18` discussion result: first-version testing guidance should prove default behavior, not advanced framework posture.

### Public docs still lack a project testing contract

The public authoring docs now have:

- a shared reference project
- an example contract
- advanced-concept example boundaries

But they still do not have a page that answers:

- what the default project tests should focus on
- which tests are official default examples versus optional project choice
- what reviewers and AI should look for when deciding whether testing has drifted away from domain behavior

### Current repository tests are mostly framework proof, not authoring shape

The current test tree is rich, but its main purpose is framework verification:

- `cap4k-ddd-starter` tests prove auto-configuration and runtime fixture behavior
- pipeline functional fixtures prove planning, generation, compile, and bootstrap contracts
- compile-smoke files inside functional samples prove generated project slices still compile

These are valuable repository tests, but they are not a good default blueprint for business-project tests.

### Bootstrap and generated shape do not currently own a testing contract

Current bootstrap and generated project shape already define enough structural baseline for project layout. They do not currently promise:

- a built-in project test skeleton
- generated domain/application test stubs
- a shared runtime test DSL

There is no evidence that first-version testing guidance needs bootstrap ownership.

## Goals

This slice should determine:

1. whether a cap4k testing skeleton should be treated as framework support or as project-local guidance
2. whether it belongs in bootstrap, generated artifacts, runtime helpers, or authoring docs
3. how to keep domain behavior visible instead of hiding it behind framework convenience
4. what the narrowest reasonable testing boundary is for `#27`

## Non-Goals

This slice must not:

- define a complete reusable `cap4k-test` framework
- turn starter smoke tests into the author-facing default testing model
- imply that advanced-concept pages require their own first-version testing skeletons
- require adapter, persistence, or integration tests to follow one official template
- convert project-local helpers into a new public runtime contract

## Options Considered

### Option 1: Docs-only testing contract

Create a public authoring page that defines the default testing boundary, but provide no helper layer and no special reference-project discipline beyond documentation.

Pros:

- narrowest possible change
- avoids framework magic entirely
- low risk of overcommitting to unstable testing API shape

Cons:

- easier for `#27` to drift into “just one example project”
- gives authors no patterned support for keeping tests readable

### Option 2: Public testing contract plus reference-project discipline, with only thin project-local helpers

Create a public testing contract, require `#27` to demonstrate it, and allow only thin local helpers inside the reference project when readability needs them.

Pros:

- gives `cap4k` a stable default testing story without turning it into runtime support
- keeps helper ownership local to the project instead of the framework
- best matches the current authoring-doc strategy after `#21`

Cons:

- helper reuse is less centralized than a framework artifact
- the contract must explicitly define the helper boundary to prevent silent growth

### Option 3: Built-in testing skeleton or runtime test-support artifact

Create an official reusable testing artifact or DSL, similar in spirit to heavier framework-owned testing suites.

Pros:

- strongest consistency across projects
- lowest risk that every project invents an entirely different test style

Cons:

- highest risk of hiding domain behavior behind framework ceremony
- prematurely creates a public runtime contract
- not supported by current evidence from bootstrap, docs, or repository testing surfaces

## Recommended Design

Adopt Option 2.

`cap4k` should treat first-version testing skeleton support as:

- a public authoring contract
- a runnable reference-project discipline
- and, only when genuinely needed, a small amount of project-local helper code

It should not treat testing skeleton support as:

- bootstrap output
- generated default test artifacts
- or a framework-owned runtime helper library

## Testing Contract Ownership

### Framework support vs project-local helper

The first-version testing skeleton should be defined as:

- framework-authored guidance

but realized primarily through:

- project-local test organization
- reference-project examples
- reviewer / AI audit cues

That means the answer to “is this framework support or project-local helper?” is:

- the contract is framework-authored
- the helper shape is project-local

### Where it should live

The recommended ownership by layer is:

- bootstrap: no default testing skeleton generation
- generated artifacts: no default generated test skeleton in this slice
- runtime support libraries: no built-in heavy test-support artifact in this slice
- docs / project-local helpers: yes, this is the intended first-version home

## Default Testing Boundary

The official default testing boundary should only shape:

- `domain` behavior tests
- `application` behavior tests

It should not try to standardize, in first version:

- adapter tests
- persistence tests
- integration transport tests
- starter smoke tests
- container-startup tests
- full infrastructure end-to-end project tests

Those tests are allowed, but they are not the first-version default contract.

## Shared Reference Project Anchor

The testing contract should explicitly reuse the same shared reference project already established by the public authoring docs.

That means first-version testing examples should remain anchored to:

- `Content`
- `MediaProcessingTask`
- `MediaProcessingCli`
- callback as the preferred return path
- polling as the fallback path

The default `application` testing anchor should be:

- `ApproveContentCmd -> StartMediaProcessingCmd`

This handoff is now the clearest shared seam across the public docs. It is the right place for the reference project to demonstrate application-layer orchestration without dragging tests into adapter or runtime wiring.

## Testing Shape Rules

The testing contract should establish these default rules:

1. official default tests are behavior-first, not framework-first
2. `domain` tests live with the `domain` module and expose rules, transitions, and rejection conditions directly
3. `application` tests live with the `application` module and expose orchestration, port interaction, and handoff behavior directly
4. tests should make preconditions, action, and business result easy to read without needing container wiring as the primary narrative
5. thin project-local helpers are allowed only when they preserve, rather than hide, business meaning

Examples of acceptable thin local helpers:

- test data builders
- fake ports
- small fixture setup helpers

Examples of helpers that should be rejected in first version:

- helpers that collapse business meaning into opaque one-liners
- project-local DSLs that make the business rule less legible than direct test code
- helpers that smuggle adapter/runtime behavior into the default domain/application test path

## Relationship To `#27`

`#27` should not define the testing contract. It should demonstrate it.

That means `#27` should use this testing boundary to show:

- `Content` domain behavior coverage
- `MediaProcessingTask` domain behavior coverage
- one clear `application`-level handoff test around `ApproveContentCmd -> StartMediaProcessingCmd`

It should not try to prove everything at once.

In particular, `#27` should not be required to establish official default samples for:

- Spring Boot container tests
- repository wiring tests
- callback controller tests
- polling job tests
- message transport tests

Those may exist in a real project, but they are not the first thing `cap4k` should teach as its testing skeleton.

## Relationship To `#17`

This design should also act as a precursor for later AI-validation guidance.

The main benefit is that `#17` will be able to point at explicit review cues instead of vague preferences. A future AI-validation guide can then ask:

- did the project expose domain behavior directly
- did application tests stay on orchestration instead of runtime wiring
- did helpers preserve or hide intent
- did testing drift into infrastructure-first proof without first proving the default path

## Documentation Delivery Shape

If this design is accepted, the intended public contract page should be a standalone authoring page, not a hidden subsection of another file.

Recommended target:

- `docs/public/authoring/testing-contract.zh-CN.md`

This page should be separate from `example-contract.zh-CN.md`.

Reason:

- `example-contract` defines how authoring example pages are written
- `testing-contract` should define how the default project testing shape is framed

The two contracts should align, but they should not be merged into one document.

## Acceptance Criteria

This slice is complete when the accepted spec clearly establishes that:

1. first-version testing skeleton support is not a built-in runtime framework feature
2. bootstrap and generated artifacts do not own the default testing skeleton
3. the official default testing boundary is limited to `domain` and `application` behavior tests
4. `ApproveContentCmd -> StartMediaProcessingCmd` is the default application-level reference seam
5. thin project-local helpers are allowed, but only within explicit readability limits
6. `#27` is positioned as a demonstration consumer of the contract rather than the source of the contract
7. future AI/reviewer audit cues can be derived from the contract without inventing a second rule system

## Follow-up Boundaries

If later work wants to go beyond this design, it should become a separate issue, for example:

- whether `cap4k` ever needs an opt-in test-support artifact
- whether adapter / persistence / integration test guidance should gain a later public contract
- whether bilingual testing-contract public docs should be added in a later docs slice

Those decisions should not be silently pulled into `#18`.

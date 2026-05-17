# Cap4k Advanced Concept Practical Examples Design

Date: 2026-05-08

Status: Proposed

Scope: add practical, cap4k-shaped usage examples for `Value Object`, `Domain Service`, and `Saga` in the public Chinese authoring docs, primarily in:

- `docs/public/authoring/advanced/**`
- `docs/public/authoring/examples/**`
- `docs/public/authoring/example-contract.md`

Out of scope:

- wrapper cleanup or wrapper positioning rewrite
- `docs/public/reference/generator-dsl.md`
- `docs/public/authoring/generation-boundaries.md`
- `docs/public/authoring/generator/code-generation.md`
- `docs/superpowers/capability-matrix.md`
- runtime Saga feature work
- broad Default Happy Path rewrite
- generic DDD tutorial content not anchored to cap4k
- English doc parity in this slice

## Backlog Source

This design is the implementation slice for:

- `#21` docs: write practical usage examples for Domain Service, Value Object, and Saga

## Problem Statement

The current public authoring docs already state the conceptual boundaries for `Value Object`, `Domain Service`, and `Saga`, but they still lean more toward principle pages than reviewable example material.

That leaves four recurring gaps:

1. project authors can read “when this concept exists” but still not know what a reasonable cap4k-shaped example looks like
2. reviewers can tell that a concept is probably misused, but do not yet have a stable example page to point at
3. later authoring issues such as `#27` and `#17` would need to restate the same high-level explanations instead of citing one canonical example set
4. advanced concepts still risk being treated as abstract nouns rather than bounded tools that solve specific default-path limits

Issue `#21` should close those gaps without turning into a documentation-system rewrite.

## Current Findings

### Existing advanced concept pages already exist

The following pages already exist:

- `docs/public/authoring/advanced/value-object.md`
- `docs/public/authoring/advanced/domain-service.md`
- `docs/public/authoring/advanced/saga.md`

They already cover:

- when the concept is needed
- why the default path is not enough
- recommended shape
- common misuse
- audit checks

That is useful, but still not enough to satisfy `#21`, because the issue specifically asks for practical examples and non-examples that are easy to review, compare, and cite.

### Existing examples tree has the shared reference project, but not concept-specific example pages

Current example pages include:

- `content-draft-to-publish.md`
- `media-processing-callback.md`
- `media-processing-polling.md`
- `reference-project-overview.md`

This means the repository already has a stable “content publication and media processing” reference project that the new concept examples should reuse, instead of inventing a second sample universe.

### Example contract already defines a stable shape

`docs/public/authoring/example-contract.md` already defines a five-part structure:

- `Scenario`
- `Why this layer / concept`
- `Recommended shape`
- `Non-example / misuse`
- `Audit cues`

That contract should remain the baseline. `#21` only needs a narrow extension so advanced-concept example pages can add an explicit `Usage boundary` section without forcing a full contract rewrite for every example page in the docs set.

### Existing examples already use bounded structural snippets

The current `examples` subtree is not purely paragraph prose. It already uses small `text` blocks to pin down:

- command chains
- callback / polling return paths
- responsibility handoff shape

That means `#21` does not need a blanket “no snippets at all” rule to stay consistent with the repository. The real boundary is narrower:

- examples should not turn into runnable tutorials or framework walkthroughs
- but they may use short, bounded supporting snippets when prose alone would leave the authoring shape ambiguous

### High-level positioning already warns against premature advanced concepts

Public positioning docs already say, in effect:

- do not start with Saga, Strong ID, or Domain Service unless the problem really requires them

That warning should remain intact. The new example pages are not supposed to normalize premature use of advanced concepts; they are supposed to explain why and when the default path stops being enough.

## Goals

This slice should deliver:

1. one practical positive example and one non-example for each of:
   - `Value Object`
   - `Domain Service`
   - `Saga`
2. a stable explanation of:
   - when the concept is really needed
   - what default-path limit it solves
   - what misuse looks like in cap4k terms
3. stable reference pages that later issues can deep-link instead of re-explaining advanced concepts
4. review-oriented audit cues that let a reviewer distinguish real concept use from decorative concept stacking

## Non-Goals

This issue must not:

- redefine the generator contract
- rewrite default ownership guidance
- introduce or imply a new runtime Saga subsystem
- promote wrapper as a core or recommended default path
- expand into broader public doc cleanup beyond the advanced concept example chain

## Options Considered

### Option 1: Keep `advanced` as concept-boundary pages and add dedicated `examples` pages

This option:

- keeps the current `advanced/*.md` pages as concept-boundary references
- adds new `examples/*.md` pages for practical examples and non-examples
- adds a small overview page for comparison and deep-link entry
- adds only narrow cross-links and one small example-contract extension

Pros:

- narrowest change that still satisfies the issue
- preserves the current public authoring structure
- gives later issues stable deep-link targets
- avoids turning this issue into a doc-architecture rewrite

Cons:

- users sometimes need one extra click from concept page to example page

### Option 2: Move most concept content out of `advanced` and centralize everything under `examples`

Pros:

- could make all practical material live in one subtree

Cons:

- drifts into broad documentation restructuring
- forces larger edits to pages that already carry useful conceptual framing
- exceeds the intended scope of `#21`

### Option 3: Add only one comparison page covering all three concepts

Pros:

- smallest file count

Cons:

- makes deep linking weaker
- compresses examples too aggressively
- gives reviewers less targeted material for individual concept disputes

## Recommended Design

Adopt Option 1.

Keep the `advanced` pages as the “should this concept exist at all?” layer, and add `examples` pages as the “what does it look like in the cap4k reference project?” layer.

## Documentation Topology

### Existing pages to keep and narrow-edit

- `docs/public/authoring/advanced/value-object.md`
- `docs/public/authoring/advanced/domain-service.md`
- `docs/public/authoring/advanced/saga.md`
- `docs/public/authoring/advanced/index.md`
- `docs/public/authoring/examples/reference-project-overview.md`
- `docs/public/authoring/example-contract.md`

### New pages to add

- `docs/public/authoring/examples/advanced-concepts-overview.md`
- `docs/public/authoring/examples/content-publication-value-object.md`
- `docs/public/authoring/examples/content-publication-domain-service.md`
- `docs/public/authoring/examples/content-publication-saga.md`

## Page Responsibilities

### `advanced/*.md`

These pages should remain responsible for:

- when the concept is justified
- what problem shape it solves
- how it differs from nearby concepts
- what misuse looks like

They should not absorb full practical example narratives. Instead, each should point to one corresponding example page.

### `examples/advanced-concepts-overview.md`

This page should act as:

- a reviewer-facing comparison table
- an author-facing triage page
- a future citation hub for `#27` and `#17`

Its job is not to retell every example in full. Its job is to answer, side by side:

- when default path is still enough
- when `Value Object` is the right next step
- when `Domain Service` is the right next step
- when the problem has crossed into `Saga`

### Per-concept example pages

Each per-concept page should provide:

- one positive cap4k-shaped example
- one non-example
- one explicit usage boundary section
- one explanation of why aggregate/entity/command/event orchestration was not enough
- one reviewer-oriented audit section

## Presentation Policy

The default presentation mode should remain structured prose anchored to the shared reference project.

However, bounded supporting snippets are allowed when they materially improve clarity of authoring shape or review surface.

Allowed snippet forms include:

- short `text` blocks for command / event / stage transitions
- short file or directory shape blocks
- short, non-runnable Kotlin-shaped skeletons that clarify ownership, such as:
  - a `Value Object` boundary
  - a `Domain Service` decision surface
  - a `Saga` stage / state skeleton

These snippets are supporting material, not the body of the page. They should stay narrow, typically one or two short blocks per page, and must not turn the example pages into:

- generator DSL reference
- framework-specific implementation walkthroughs
- full runnable code tutorials

## Shared Example World

All three concepts should stay inside the existing shared reference project:

- `Content`
- `MediaProcessingTask`
- `MediaProcessingCli`
- callback as the main result-return path
- polling as the fallback path

This keeps the docs coherent across:

- `Default Happy Path`
- existing examples
- advanced concept guidance

The only controlled expansion is the Saga example: it should be presented as an extension of the same reference project, not as a separate business domain.

## Concept-Specific Example Strategy

### Value Object

Positive example candidates:

- `ContentTitle`
- `ProcessingResultSnapshot`

These examples should illustrate:

- value normalization
- value equality / shared semantics
- unifying callback and polling results into the same internal value expression

Non-examples should include:

- using a value object where an enum is more natural, such as lifecycle status
- using a value object where the real need is only ID anti-mixing, which belongs closer to `Strong ID`
- treating a JSON column layout as the value-object definition itself

### Domain Service

The recommended positive example is “publication eligibility evaluation”.

This example should show:

- the decision is domain truth
- the decision needs facts from more than one domain concept
- the needed facts are already available at decision time
- the service returns a domain conclusion
- actual state change still belongs to aggregate behavior

Non-examples should include:

- orchestration garbage-can services that send commands, call CLI clients, write repositories, and publish messages
- using “domain service” as an excuse for cross-aggregate write ownership
- using a service for a cross-time waiting problem that really belongs to Saga

### Saga

The positive example should not claim that the current default publication chain already needs Saga.

Instead, it should use a controlled extension of the same reference project, for example:

- content is approved
- media processing finishes
- copyright recheck is still pending
- a release window or human release confirmation must still be awaited
- timeout, recovery, or compensation is now part of the model

This keeps the sample grounded in the same project while preserving the correct message:

- Saga is not the default path
- Saga is for long-running, waiting, compensating coordination

Non-examples should include:

- turning short command/event chains into Saga for prestige
- writing Saga as a super application handler
- promoting polling fallback into the primary source of long-running truth

## Example Contract Extension

`docs/public/authoring/example-contract.md` should remain the baseline contract for all examples.

This issue should only add a narrow note:

- advanced-concept examples may add `Usage boundary` between `Non-example / misuse` and `Audit cues`

This preserves compatibility with the rest of the examples tree while giving `#21` the extra section it explicitly needs.

## Link Strategy

### `advanced/index.md`

Add one practical-examples entry that points to:

- `../examples/advanced-concepts-overview.md`

### Per-concept advanced pages

Each concept page should add a lightweight “对应示例 / see practical example” link to its matching `examples` page.

### `reference-project-overview.md`

Add one short section that positions:

- `Value Object`
- `Domain Service`
- `Saga`

as advanced concept lenses over the same reference project, with links to the three new example pages.

## Wrapper Mention Policy

If any of the new advanced concept pages or example pages mention `wrapper`, they must only do so in the constrained form already approved by the issue:

- non-core
- off the default path
- not recommended as the main business entry

This issue must not take ownership of wrapper cleanup narrative.

## Verification Strategy

This slice is complete when all are true:

1. all four new `examples` pages exist
2. each of the three concept example pages includes:
   - one positive example
   - one non-example
   - `Usage boundary`
   - `Audit cues`
   - an explicit explanation of why the default path is not enough
3. `advanced/index.md` links to the new advanced-concepts overview page
4. each advanced concept page links to its practical example page
5. `reference-project-overview.md` links back to the new advanced concept example chain
6. no forbidden `#22` surfaces are modified

## Residual Risks

1. This slice will still be Chinese-only. If public bilingual parity becomes required later, that should be separate work.
2. The new examples may include bounded supporting snippets, but they will not become runnable code tutorials, generator DSL references, or framework implementation guides.
3. Saga remains documentation-only in this slice. If later work needs runtime Saga support, it must not cite these docs as if the feature already exists.

## Acceptance Mapping

This design satisfies `#21` if implementation produces public authoring material that lets:

- project authors decide when `Value Object`, `Domain Service`, and `Saga` are justified
- reviewers identify decorative or mis-scoped usage
- later docs such as `#27` and `#17` cite stable cap4k-shaped examples instead of restating concept theory

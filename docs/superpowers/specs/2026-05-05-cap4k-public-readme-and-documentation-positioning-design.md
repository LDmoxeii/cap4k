# Cap4k Public README and Documentation Positioning Design

Date: 2026-05-05

Status: Proposed

Scope: rewrite the public documentation entry for `cap4k` as a bilingual documentation surface, centered on the capability audit conclusions from `#15`, with `README` as the entry and a small public-doc split for onboarding and positioning.

Out of scope: implementation plan, AI-collaboration guide writing, wrapper cleanup, runtime module cleanup, frontend TypeScript generation, advanced concept teaching examples, and deep installation or migration manuals.

## Background

The existing public `README.md` does not describe `cap4k` at all. It is still generic Gradle boilerplate and provides no usable public project story.

At the same time, the framework capability audit in `#15` already established the public-positioning baseline:

- `cap4k` is a simplified DDD tactical framework
- public first-screen positioning should foreground:
  - Aggregate Root
  - Entity
  - Domain Event
  - Command
  - Query
  - default happy-path rules
- advanced concepts and runtime/infra surfaces should be documented, but should not dominate first-screen identity

That means the README rewrite is not free-form branding work. It must now become a controlled expression of the framework capability audit.

## Problem

There are three documentation problems to solve together:

1. there is no credible public README yet
2. a single monolithic README would become too heavy if it tries to explain:
   - positioning
   - happy-path workflow
   - onboarding
   - concept boundaries
3. the project now needs bilingual public documentation, so structure must remain maintainable instead of duplicating one giant mixed-language file

If we keep everything in one file, the first screen becomes too long and unstable. If we split too aggressively, the docs lose coherence and the README stops acting as the entry point.

## Goals

1. Replace the current boilerplate README with a real public entry for `cap4k`.
2. Provide bilingual public documentation:
   - `README.md`
   - `README.md`
3. Keep README as the entry point rather than the full documentation corpus.
4. Split only a small amount of supporting public documentation under `docs/public/`.
5. Make README align strictly with the capability and positioning audit from `#15`.
6. Make the first screen understandable to:
   - teams that want to land DDD without a heavy framework
   - teams already living in Spring/JPA reality, without making Spring/JPA the framework identity

## Non-Goals

This slice must not:

- turn README into a full concept manual
- move all repository docs into bilingual public docs
- promise support for unstable or non-planned capability lines
- re-open framework-capability decisions already settled in `#15`
- make runtime/infra surfaces the primary identity of the framework
- position advanced modeling or advanced runtime capability as the default path

## Primary Audience

The public documentation should serve two audiences, with intentional weighting:

1. primary first-screen audience:
   - teams that want to land DDD without introducing an overbuilt framework story
2. secondary technical audience:
   - teams already working in Spring/JPA style systems and evaluating whether `cap4k` can provide a clearer tactical boundary

The first screen should visibly optimize for the first audience. Technical landing details can serve the second audience in later sections and support docs.

## Documentation Delivery Shape

## Entry Documents

Two entry documents must exist:

- `README.md` (English)
- `README.md` (Chinese)

These two files should keep the same structure and roughly equivalent information density.

They do not need to be literal line-by-line translations, but they must stay semantically aligned.

## Supporting Public Documents

Create a small public-doc split under `docs/public/`:

- `docs/public/getting-started.md`
- `docs/public/getting-started.md`
- `docs/public/framework-positioning.md`
- `docs/public/framework-positioning.md`

This is the minimum public split needed for maintainability. Do not expand into a large public-doc tree in this slice.

## README Information Architecture

Each README should use this structure.

### 1. Language Navigation

At the top:

- English README links to the Chinese version
- Chinese README links to the English version

The goal is clear language switching, not mixed bilingual sections in one file.

### 2. One-line Positioning Statement

The English README should lead with the positioning direction already agreed during discussion:

> cap4k is a simplified DDD tactical framework designed for AI-assisted implementation and human review.

The Chinese README should express the same meaning in idiomatic Chinese, not as a literal awkward translation.

### 3. Business-modeling Mainline

The first conceptual flow shown on the first screen should be the business-modeling mainline, not the infrastructure mainline.

Recommended shape:

- Aggregate Root
- Entity
- Command / Query
- Domain Event
- Orchestration Surfaces

This can be expressed as a short flow diagram, short structured list, or compact section block. It should not become a long tutorial.

### 4. Short “How to Start”

Immediately after the first-screen positioning, provide a very short start path:

- what to read first
- what the default path looks like
- where to go next

This section must stay intentionally short. Full onboarding details belong in `docs/public/getting-started.*`.

### 5. What cap4k Is

This section should define the framework positively, based on `#15`:

- simplified DDD tactical framework
- aggregate-centered
- command/query driven
- domain-event aware
- designed for AI-assisted implementation and human review
- able to project design/runtime/generation layers without making generation the only identity

### 6. What cap4k Is Not

This section is required.

It prevents public positioning drift by explicitly saying what the framework should not be mistaken for.

It should include points such as:

- not a generic code generator platform
- not a JPA wrapper first and foremost
- not an integration-event platform first and foremost
- not a frontend TypeScript generation framework
- not a “support every DDD pattern equally on the front page” framework

### 7. Default Happy Path

README must summarize the default path rather than listing all capabilities.

This section should reflect `#15`:

- single-command single-aggregate-root mutation
- aggregate root as the write-facing surface
- domain mutation converges into command handling
- domain events released by aggregate root
- no guaranteed order across multiple handlers
- `cli` as anti-corruption boundary rather than process truth source

This should be presented as “how cap4k wants you to work by default,” not as abstract doctrine.

### 8. Documentation Map

README should end its main body with a clear navigation section:

- Getting started
- Framework positioning
- Specs/plans for deeper repository-internal design work

This keeps README as the entry point instead of the entire manual.

## Supporting Document Responsibilities

## `docs/public/getting-started.*`

Purpose:

- minimal onboarding
- first practical reading sequence
- basic workflow from idea to implementation
- where design, specs, and implementation planning fit

This document should remain practical. It should not expand into a full theory document.

## `docs/public/framework-positioning.*`

Purpose:

- express the result of `#15` in public-facing language
- explain foreground vs background concepts
- explain default path vs advanced concepts
- explain why runtime/infra surfaces exist without making them first-screen identity

This is where `Value Object`, `Integration Event`, `Repository contract`, `cli`, `Domain Service`, `Saga`, `Strong ID`, and runtime/infra layers can be properly described without overloading README.

## Public Positioning Rules From `#15`

The public docs must follow these positioning rules.

## Foreground Concepts

The first-screen story must foreground:

- Aggregate Root
- Entity
- Domain Event
- Command
- Query
- default happy-path rules

## Background Concepts

These remain important, but belong in deeper documentation instead of front-page emphasis:

- Value Object
- Integration Event
- Repository contract
- Command Handler / Query Handler
- cli
- Domain Service
- Saga
- Strong ID
- runtime / infra capability surfaces

## Explicitly Non-foreground Capability Lines

The following should not be presented as front-page promises:

- frontend TypeScript generation
- wrapper
- advanced read/write split modeling mode from `#23`
- transport-specific event adapter details
- provider-specific persistence details

If mentioned at all, they should be presented as:

- non-planned
- deprecated
- advanced-only
- or deeper implementation reality

depending on the capability.

## Handling Advanced Read/Write Modeling

The advanced weak-reference read/write split discussion from `#23` should not be promoted into the public default workflow.

Public docs may mention it only as:

- advanced modeling mode
- non-default
- repository contract remains write-model only

It should not appear in first-screen examples or “Why cap4k” claims.

## Bilingual Maintenance Rules

To keep the bilingual docs stable:

1. both languages must share the same section structure
2. links between language pairs must be explicit
3. the Chinese README should be a real Chinese document, not machine-like sentence mirroring
4. support docs must be paired the same way as the READMEs
5. public terminology should stay consistent across both language tracks

## Acceptance Criteria

This slice is complete when:

1. `README.md` and `README.md` are real public entry documents rather than boilerplate
2. `docs/public/getting-started.*` and `docs/public/framework-positioning.*` exist as the minimum support split
3. README first-screen positioning is aligned with `#15`
4. `What cap4k is not` is explicitly documented
5. runtime/infra surfaces are acknowledged without becoming first-screen identity
6. advanced and non-default capability lines are not presented as public promises
7. English and Chinese public docs are structurally aligned and navigable from each other

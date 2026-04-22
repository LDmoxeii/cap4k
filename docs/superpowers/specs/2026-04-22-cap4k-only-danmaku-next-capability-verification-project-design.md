# Cap4k only-danmaku-next Capability Verification Project Design

## Status

This design supersedes the earlier staged tutorial positioning in:

- `docs/superpowers/specs/2026-04-21-cap4k-only-danmaku-next-staged-tutorial-migration-design.md`

The earlier document assumed that `only-danmaku-next` should simultaneously act as:

- a clean tutorial mainline
- a migration rehearsal project
- a real consumer verification project

That combined role is too unstable. It creates unnecessary business-context load for users, and it encourages framework capability coverage to be reconstructed from memory instead of from an explicit source of truth.

This new design narrows `only-danmaku-next` to one primary role:

- a capability verification project for the current Cap4k plugin matrix

The separate low-cognitive-load tutorial project remains explicitly deferred until the framework capability surface is more stable.

## Purpose

This design defines:

1. what `only-danmaku-next` is now for
2. where Cap4k capability truth should live
3. how verification stages should be structured
4. how repository branches and documentation should reflect those stages
5. what the first implementation round should and should not do

## Problem

The previous `only-danmaku-next` direction mixed three different goals:

- teach new users with minimal mental load
- verify real plugin capability coverage
- shadow the historical `only-danmuku` project

Those goals do not align cleanly.

Using the same repository as both the official learning path and the full verification carrier causes predictable problems:

- business terms from `only-danmuku` leak into early stages and raise user cognitive load
- stage boundaries drift, because some stages carry generator input while later stages carry generator output
- repository HEAD stops matching any single clear stage
- the list of "what still needs to be demonstrated" drifts toward memory and chat history instead of a durable framework record

The framework needs a stricter separation:

- Cap4k must define capability truth
- `only-danmaku-next` must verify that truth in a real consumer-shaped project
- a future tutorial project can later optimize for user learning rather than coverage

## Core Positioning

`only-danmaku-next` must now be treated as:

1. a plugin-native consumer verification project
2. a capability-combination proving ground
3. a detector for framework gaps that appear only in a continuously evolving project

`only-danmaku-next` must not be treated as:

- the official low-friction tutorial mainline
- a direct clone of `only-danmuku`
- the primary source of truth for what Cap4k currently supports
- an ad hoc sandbox that accumulates future-stage code in the current public branch

## Relationship To Other Repositories

### Cap4k

`cap4k` remains the framework repository and must own the durable source of capability truth.

That truth is not allowed to live only in:

- chat history
- roadmap prose
- scattered slice specs
- the current state of `only-danmaku-next`

### only-danmaku-next

`only-danmaku-next` consumes the current capability truth and proves it in a real project context.

It is allowed to expose gaps. When it exposes a real gap, the fix flow is:

1. clarify or extend the capability contract in `cap4k`
2. implement the framework change in `cap4k`
3. return to `only-danmaku-next` and update the verification project

### only-danmuku

`only-danmuku` remains useful as a source of real-world demand and historical behavior comparison, but it is not the primary verification baseline for the new plugin stack.

Its role is:

- reveal requirements users actually want
- reveal parity gaps that the current capability matrix does not yet cover

It must not silently redefine what the framework claims to support today.

### Future Tutorial Project

A separate low-cognitive-load tutorial project is explicitly deferred.

It should not be started until:

- the capability matrix is stable enough to teach from
- the verification project has reduced uncertainty around current framework behavior

## Capability Truth Source

The single durable truth source for current plugin capability must live in `cap4k`.

The first-round location should be a human-editable document:

- `docs/superpowers/capability-matrix.md`

This is intentionally simple. The framework does not need a registry service or machine-readable schema first. It needs an explicit, reviewable, durable truth source first.

## Capability Matrix Contract

Each capability entry must have a stable identifier and explicit verification status.

The minimum required fields are:

- `capabilityId`
- `family`
- `status`
- `contract`
- `verificationLayers`
- `verificationTargets`
- `projectRequired`
- `notesOrGaps`

### Field Meanings

`capabilityId`

- a stable identifier such as `bootstrap.in_place_root` or `aggregate.unique_bundle`
- this is what specs, project docs, and verification stages should reference

`family`

- the capability group, such as `bootstrap`, `aggregate`, `query`, `client`, `validator`, or `domain_event`

`status`

- one of `implemented`, `partial`, `deferred`, or `blocked`

`contract`

- a short statement of the real supported boundary
- this must describe the actual current contract, not the intended future contract

`verificationLayers`

- which layers currently verify this capability
- valid layers are `unit`, `compile`, `functional`, `runtime`, and `project`

`verificationTargets`

- the concrete tests, fixtures, or project stages that provide those checks

`projectRequired`

- whether this capability must appear in `only-danmaku-next`

`notesOrGaps`

- real caveats, missing layers, deferred edges, or explicit limitations

## Relationship Between Matrix And Verification Project

The ownership boundary must be strict:

- `cap4k` defines capability truth
- `only-danmaku-next` proves capability combinations against that truth

`only-danmaku-next` must not maintain its own second full capability matrix.

Instead, it should record:

- which `capabilityId` values a given verification stage covers
- what the project demonstrates for those capabilities
- where the formal contract still lives in `cap4k`

This prevents matrix drift between framework docs and project docs.

## Stage Semantics

The earlier staged tutorial design implicitly split some generator flows across multiple stages. That is now rejected.

The correct rule is:

- a stage must represent a complete capability loop

This means a stage may include:

- design input
- plugin configuration
- generator execution
- generated output
- runtime or build proof where appropriate

This also means a stage must not represent only half of a generator story, such as:

- "input written here, output shown in the next stage"
- "DSL introduced here, generator execution deferred to a later stage"

That split creates unstable stage meaning as soon as aggregate, query, or client slices arrive.

## Stage Versus Commit

Git commits must not carry the public teaching or verification contract.

Commits are allowed to contain:

- bug fixes
- cleanup
- review follow-ups
- internal history that is not itself a stable public checkpoint

The public contract must instead live in:

- maintainable stage branches
- stage documentation

This avoids the false assumption that every commit must remain a clean end-user checkpoint.

## Branch Model

`only-danmaku-next` should use cumulative stage branches.

Recommended structure:

- `stage/0-minimal`
- `stage/1-bootstrap-in-place`
- later stage branches such as `stage/2-aggregate-minimal`

The branch rule is:

- each later stage branch grows from the previous stage branch
- fixes to an earlier stage should be merged forward into later stages

`main` should always represent the latest stable completed stage.

At any given time:

- `main` is an entry point for readers
- `stage/*` branches are the stable maintainable verification checkpoints

Annotated tags are not the primary mechanism in this design. They may be added later for publication or release purposes, but they are not the required stage carrier.

## Stage Granularity

The verification project should use medium-grained capability bundles.

The project should avoid two opposite mistakes:

- one tiny stage per individual switch
- one huge stage that combines unrelated capability families

The correct unit is:

- one main capability bundle per stage
- plus any tightly coupled sub-capabilities that share the same lifecycle

Example:

- `unique-query`, `unique-query-handler`, and `unique-validator` should be verified in the same stage because they are lifecycle-coupled

## Initial Stage Model

The first stages should be:

### Stage 0: Minimal Host Baseline

Purpose:

- establish the smallest host project that can later execute Cap4k capabilities

Must include:

- minimal root Gradle host structure
- plugin application prerequisites
- bilingual project overview
- stage 0 documentation

Must not include:

- bootstrap DSL
- bootstrap-generated output
- aggregate or design generation artifacts
- business runtime code from later stages

This stage is not a generator stage. It is the host baseline for later capability stages.

### Stage 1: Bootstrap In-Place Baseline

Purpose:

- prove the current public bootstrap in-place contract in a real consumer repository

Must include the complete bootstrap loop:

- bootstrap DSL
- one `ROOT` slot example
- bootstrap execution
- resulting in-place three-module skeleton
- one language-neutral root-level generated note file, such as `BOOTSTRAP_NOTE.md`

Must not include:

- `bootstrap-preview/`
- later aggregate or runtime feature code
- future-stage documentation bodies

Stage 1 must verify capability identifiers including at least:

- `bootstrap.in_place_root`

`bootstrap.preview_subtree` should remain a capability-matrix entry in `cap4k`, but it does not need to be materialized in the first-round `only-danmaku-next` project state.

### Later Stages

Later stages should continue this same rule:

- each stage owns a complete capability loop
- each stage maps to explicit capability identifiers from the matrix

Examples of later capability bundles include:

- aggregate minimal baseline
- aggregate persistence/schema parity
- aggregate optional surface
- unique capability bundle
- client family
- domain event family

## Repository Content Rules

Any public branch must match the stage it claims to represent.

This means:

- a stage branch must not keep future-stage code or docs just because they are convenient
- repository HEAD must not act like a mixed "preview of later work"

For the first implementation round, this implies:

- current `stage 0/1` branches should contain only stage-aligned code and docs
- later-stage content should be removed or deferred from the current public branch state

## Documentation Contract

`only-danmaku-next` docs should become verification docs, not general learning-path docs.

The bilingual structure should remain:

- `docs/zh-CN/README.md`
- `docs/en/README.md`

The project should also have stage indexes:

- `docs/zh-CN/stages/README.md`
- `docs/en/stages/README.md`

Each stage document should answer:

1. what capability bundle this stage verifies
2. which `capabilityId` values it covers
3. what prior stage state it assumes
4. what repository state should exist after the stage
5. what the stage explicitly does not include

The docs must link capability meaning back to `cap4k`, rather than duplicating the full capability matrix inside `only-danmaku-next`.

## First-Round Scope

The first implementation round should stay deliberately narrow.

It should do the following:

1. create the first usable capability matrix baseline in `cap4k`
2. reposition `only-danmaku-next` as a capability verification project
3. establish the first two maintainable stage branches:
   - `stage/0-minimal`
   - `stage/1-bootstrap-in-place`
4. align `main` with the latest completed stable stage
5. remove or defer repository content that does not belong to those first two stages

## Explicit Non-Goals

This slice must not:

- create the separate beginner-friendly tutorial project
- fully enumerate every imaginable capability in the matrix before the structure exists
- turn commit history into the public stage contract
- jump directly into `stage 2` implementation before the verification-project governance is stable
- silently keep mixed tutorial and verification positioning for `only-danmaku-next`
- use `only-danmuku` as the formal source of truth for supported current plugin behavior

## Success Criteria

This design is successful when all of the following are true:

1. `cap4k` contains a concrete capability matrix document that can be reviewed and expanded
2. `only-danmaku-next` is documented as a capability verification project rather than a staged tutorial mainline
3. the first two stage branches have clear, non-overlapping semantics
4. `main` reflects the latest stable completed stage instead of a mixed future-state branch
5. future capability work can be added by mapping new stages to explicit capability identifiers rather than relying on memory

## Rationale

This design intentionally chooses truth and maintainability over premature teaching polish.

The framework is still moving. At this stage, the most valuable thing is not a polished beginner path. The most valuable thing is a durable way to answer:

- what does Cap4k currently support
- where is that support proven
- what gaps are being revealed by a real consumer-shaped project

Once those answers are stable, a dedicated tutorial project can be built on top of them without inheriting unnecessary business complexity.

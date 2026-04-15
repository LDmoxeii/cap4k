# Cap4k Design Generator Compile-Level Integrated Verification Hardening

Date: 2026-04-15
Status: Draft for review

## Summary

The old design-family migration mainline is now effectively complete through `domain_event` and `domain_event_handler`.

The next original-mainline slice should not add another design family immediately.
It should raise the design-generator quality bar from:

- plan-level correctness
- renderer-level correctness
- generated-text functional correctness

to:

- representative generated artifacts that also participate in real module compilation
- family-level compile verification
- small integrated multi-family compile verification

This slice introduces compile-level hardening for already-migrated design families without reopening generator-core boundaries or mixing in bootstrap or support-track work.

## Goals

- Update the mainline roadmap so the completed `domain_event / domain_event_handler` slice is recorded and the next mainline slice is explicit.
- Add a shared compile-capable functional harness for generated design artifacts.
- Move representative migrated design families from generate-only verification to generate-plus-compile verification.
- Add a small integrated compile-capable sample that proves multiple migrated families can coexist in one representative project.
- Lock compile-critical contracts around metadata, inference, imports, nested types, and generated supertypes more tightly than the current text-only verification layer.

## Non-Goals

- Do not add new design-generator families in this slice.
- Do not reopen pipeline stage architecture or generator-core boundaries.
- Do not widen `use()` or relax short-name resolution rules.
- Do not add sibling design-entry type support.
- Do not start bootstrap or arch-template migration work.
- Do not convert real-project integration findings into new default framework rules without explicit approval.
- Do not turn this slice into end-to-end business behavior verification for generated code.

## Current Context

The current mainline already migrated the major old design families into bounded pipeline-owned generators and templates:

- command / query family
- query-handler family
- client / client_handler family
- validator family
- api_payload family
- domain_event / domain_event_handler family

Current quality proof is strong at several levels:

- canonical assembly tests
- planner tests
- renderer tests
- functional plan / generate tests

But the mainline still lacks a durable compile-level gate for representative generated design output.

That leaves a specific risk class under-verified:

- generated code may look correct in plan or renderer output while still failing to compile in a representative multi-module fixture
- multiple migrated families may work in isolation but drift when composed together
- metadata, dependency inference, and import contracts may remain textually correct while compile behavior regresses

The previous `domain_event` slice exposed this exact category of risk:

- aggregate metadata was required for correct generation
- the pipeline originally allowed silent omission rather than fail-fast behavior
- that issue was only fully closed after a later production-readiness review

This slice should turn those lessons into a stronger mainline verification standard rather than treating them as one-off fixes.

## Design Decision

The next mainline slice should be:

- `design generator compile-level / integrated verification hardening`

This is preferred over immediately migrating another old capability because:

- the representative old design-family migration track is already effectively complete
- the next quality bottleneck is not missing generator families but insufficient compile-level proof
- compile-capable fixtures and integrated verification provide a durable foundation for future mainline work
- this work strengthens the existing mainline contract without widening framework authority

The implementation should be structured as a layered verification hardening effort, not as one giant integrated sample and not as a scattered collection of unrelated compile checks.

## Scope Structure

This slice should contain four linked work blocks.

### 1. Roadmap Advance

The first implementation step in this slice should update:

- `docs/superpowers/mainline-roadmap.md`

That update should:

- mark `design domain_event / domain_event_handler family migration` as completed
- move `Current Next Mainline Slice` to this compile-level hardening slice
- avoid introducing a separate temporary handoff document

This roadmap advance belongs to the slice because it records the transition from old-family migration to post-migration hardening.

This spec intentionally precedes that roadmap edit.
Until implementation begins, the roadmap may still temporarily show the previous completed slice as the current next step.

### 2. Shared Compile Harness

The core of this slice should be a shared functional harness that supports:

- `cap4kGenerate`
- followed by representative module compilation
- with reusable assertions and fixture structure

This harness should make compile-level verification a stable capability of the functional test suite instead of a one-off ad hoc sample.

It should support at least:

- multi-module fixture layout
- compile invocation after generation
- clear failure localization by fixture and family
- reuse across multiple design-family samples

It must also solve compile dependency resolution for generated source in a shared way.
Generated artifacts do not compile against the plugin classpath alone, and the required compile classpath is not uniform across families.

This harness should remain narrow and should not become a general-purpose integration framework.

### 3. Family-Level Compile Verification

The slice should move representative migrated families onto the compile-capable harness in grouped form.

Representative coverage should include at least:

- request family
  - command
  - query
  - query_list
  - query_page
- query-handler family
  - query_handler
  - query_list_handler
  - query_page_handler
- client family
  - client
  - client_handler
- validator family
- api_payload family
- domain_event family
  - domain_event
  - domain_event_handler

This does not require one fully independent fixture per individual template.
The preferred approach is to reuse or extend existing representative fixtures where practical and only add new compile-capable fixtures when necessary.

### 4. Integrated Representative Compile Sample

In addition to family-level compile checks, this slice should add one small integrated compile-capable sample.

Its purpose is not breadth for its own sake.
Its purpose is to prove that multiple migrated families can coexist in one representative project without compile-time drift.

The integrated sample should remain small.
It should not attempt to model a real production application.

A representative integrated sample should be able to cover combinations such as:

- query / query-handler
- client / client-handler
- validator
- api_payload
- domain_event / domain_event_handler

One aggregate plus one representative entry per family is enough if the compile surface is real.

## Verification Philosophy

This slice should explicitly raise the mainline acceptance standard.

After this work, representative design-family quality should be demonstrated across four layers:

1. canonical assembly
2. planner and renderer correctness
3. generate-level functional correctness
4. compile-level representative project correctness

Compile-level verification does not replace the lower layers.
It closes a gap they cannot fully cover.

## Shared Harness Requirements

The shared compile-capable harness should support the following behaviors:

- a fixture can run `cap4kGenerate`
- generated source can then participate in `compileKotlin`
- compilation can target the modules that actually receive generated files
- failure output remains attributable to the fixture under test

The harness should be suitable for:

- existing design-family fixtures that can be made compile-capable with small additions
- new integrated samples that intentionally combine families

### Compile Dependency Resolution

The shared harness must provide one reusable strategy for compile-time dependency resolution.

That strategy must not rely on hand-written, per-fixture bespoke dependency wiring as the default mechanism.
It must be capable of supplying the compile classpath needed by generated source after `cap4kGenerate`.

Acceptable implementation mechanisms include:

- a shared local Maven publication or consumption path
- a shared composite-build or included-build style path
- a shared file- or jar-based dependency path

The exact implementation choice may be deferred to planning, but the spec must treat this as a first-class design requirement rather than an incidental fixture detail.

### Dependency Surface Considerations

The compile dependency surface is not identical across migrated families.

Representative differences include:

- some generated artifacts rely only on Kotlin or JDK-visible types plus sibling generated source
- query- and client-oriented request contracts rely on `ddd-core` application abstractions
- handler-side families additionally pull in Spring compile dependencies
- validator generation relies on validation and Kotlin reflection types rather than the same contract set as query or client generation
- domain-event generation depends on aggregate metadata plus event-side framework annotations

The integrated compile sample therefore needs the union of the family-level compile surfaces it exercises.
The shared harness should make that union manageable without turning every fixture into a large custom build.

The harness should not:

- require large custom build logic per fixture
- hide compile failures behind broad catch-all assertions
- depend on support-track project-specific conventions

## Compile-Critical Contracts This Slice Should Lock

This slice should explicitly harden the contracts most likely to regress only at compile time:

- metadata-dependent generator prerequisites
- task inference for generated metadata producers
- aggregate or event type imports
- generated nested type materialization
- generated supertypes and handler contracts
- coexistence of multiple generated families in one representative sample
- compile-safe use of explicit FQN rules and conservative short-name resolution

This slice may fix issues in those areas when they directly block compile-level hardening.
It should not use compile hardening as a pretext to reopen unrelated architecture questions.

## Fixture Strategy

The preferred fixture strategy is layered, not monolithic.

### Family-Level Fixtures

Use or extend existing representative fixtures where possible:

- `design-sample`
- `design-validator-sample`
- `design-api-payload-sample`
- `design-domain-event-sample`

If a fixture already proves generation semantics for a family, the slice should prefer upgrading that fixture into compile-capable verification over creating a second redundant sample.

### Integrated Fixture

Add one compile-capable integrated sample only after the shared harness exists.

The integrated fixture should:

- stay intentionally small
- prove multi-family coexistence
- avoid real-project-specific layout drift

## Allowed Follow-On Fixes Inside This Slice

This slice may include targeted fixes that are directly exposed by compile-level hardening, such as:

- metadata dependency gating
- task inference gaps
- compile-visible planner or template contract mismatches
- minimal fixture dependency gaps

These are in-scope because they are necessary to establish the new quality gate.

## Explicitly Out Of Scope

The following remain outside this slice even if they appear related:

- bootstrap or arch-template migration
- exploratory parity backlog items such as relation parity, JPA fine-grained control, or user-code preservation
- real-project unblock work
- support-track package-layout customization
- new generator families
- framework runtime extensibility

## Recommended Implementation Order

The implementation order should be:

1. roadmap advance
2. shared compile harness
3. family-level compile-capable fixture upgrades
4. integrated representative compile sample
5. full regression verification

This order keeps the slice understandable and prevents the integrated sample from becoming the only place where compile-level behavior is exercised.

## Success Criteria

This slice is successful when all of the following are true:

- the roadmap records the completed `domain_event` slice and the new next mainline slice
- compile-capable functional infrastructure exists and is reusable
- representative migrated design families are covered by generate-plus-compile verification
- one small integrated multi-family fixture also compiles successfully
- compile-critical dependency and import regressions fail loudly rather than producing silent wrong output

## Next-Step Boundary

After this slice, the roadmap can decide whether the next mainline step should continue post-migration hardening or move to a new class of quality work.

That decision should happen after compile-level hardening is landed.
It should not be pre-committed inside this slice.

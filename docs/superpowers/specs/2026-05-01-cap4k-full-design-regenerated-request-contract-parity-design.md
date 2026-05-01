# 2026-05-01 cap4k Full Design-Regenerated Request Contract Parity Design

## Status

Draft for review.

## Background

The `only-danmuku-zero` dogfood migration exposed a contract ownership problem.

The new pipeline already has generated `command`, `query`, and `client` contract classes. These classes define nested `Request` and `Response` types and are intended to be produced from design input through canonical assembly and the design generator.

During real-project migration, however, old `ListQuery` / `PageQuery` / `Item` contracts, controller payloads, query handlers, and hand-written adapter logic can hide missing generator capability. Hand-editing generated application contracts may make compilation pass, but it breaks the structure-first premise of the pipeline.

The framework decision is stricter:

- all Query/Cmd/Cli `Request` and `Response` contracts must be regenerated from design input
- if a contract cannot be expressed and regenerated, that is a design or generator capability defect
- hand-written contract fixes are temporary migration unblocks only, never the final contract source

This spec records that discipline and defines the first implementation boundary.

## Problem

The dogfood backlog contains two related symptoms:

- old query contracts with list/page semantics were normalized into a single `Response` item, making safe handler migration impossible
- some generated or migrated contracts were manually changed to match old usage instead of repairing the design input or generator capability

Those symptoms have the same root: the migration process lacked a hard rule for contract ownership.

Without that rule, every failed migration can be explained away as a hand-written exception. That makes it impossible to tell whether the pipeline can reproduce a real project from stable inputs.

The result is especially dangerous for Query/Cmd/Cli classes because they sit at the boundary between:

- design input
- application request/response contract
- handler implementation
- adapter/controller payload mapping
- future frontend TypeScript generation

If the contract can drift away from design input, every downstream analysis and generation consumer becomes unreliable.

## Goal

Define a migration and generator contract for Query/Cmd/Cli:

- `command` design entries own generated `*Cmd.Request` and `*Cmd.Response`
- `query` design entries own generated `*Qry.Request` and `*Qry.Response`
- `client` design entries own generated `*Cli.Request` and `*Cli.Response`
- generated contract classes are not manually patched as a final migration strategy
- all structural response semantics must be representable in design input
- unsupported old contract shapes become explicit capability defects

The first implementation should make the dogfood boundary observable. It should answer:

- which Query/Cmd/Cli contracts are generated from design
- which expected old contracts cannot currently be represented
- whether a migration relies on checked-in hand edits of generated contracts

## Non-Goals

This slice does not:

- migrate handler business logic
- generate controller methods
- generate frontend TypeScript
- restore old `ListQuery`, `PageQuery`, `ListQueryParam`, `PageQueryParam`, or `Item` as public default contracts
- add compatibility templates for the old monolithic `cap4k-plugin-codegen`
- make MapStruct converter generation part of the default contract
- make API payload mapping or controller response wrapping a blocker for application contract parity
- rewrite the `only-danmuku-zero` business implementation in this spec

## Contract Scope

### In Scope

The required generated contracts are:

- `CommandModel` to `*Cmd`
- `QueryModel` to `*Qry`
- `ClientModel` to `*Cli`

For each generated class, the nested `Request` and `Response` types are part of the design-owned contract.

If the generated template intentionally omits `Response` for a command variant, that omission must be expressed by canonical model state such as `CommandVariant.VOID`, not by hand editing the output.

`api_payload` is adjacent to this scope. It also defines generated `Request` and `Response` payloads and should follow the same design-owned principle when present. However, this slice's primary verification target is the application-side Query/Cmd/Cli contract because that is where handler and business migration currently fail.

### Out of Scope

The following code remains user-owned or migration-owned:

- handler body logic
- validator body logic
- subscriber orchestration
- controller routing and response wrapping
- adapter query implementation details
- project-specific converter implementation
- temporary TODO bodies used only to keep dogfood compilation moving

The distinction is structural:

- the shape of `Request` / `Response` is generated
- the behavior that fills or consumes those types can be hand-written

## Stable Design Expression Requirements

Design input must be able to express the complete structural contract.

Supported expression categories should include:

- scalar request and response fields
- nullable fields
- default values already accepted by the design default-value formatter
- explicit FQCN or type-registry references
- request and response nested types
- multi-level nested types
- recursive `self` references
- list response containers through explicit `List<Item>` or equivalent generated nested item types
- page response containers through explicit `PageData<Item>` or equivalent generated nested item types
- page request semantics through `RequestTrait.PAGE`

The generator must not infer list/page semantics from names such as `list`, `page`, `items`, or `Item`. Container semantics must come from design fields and traits.

## Defect Classification

When a Query/Cmd/Cli contract cannot be regenerated from design input, classify it as one of these defects.

### Design Input Defect

The generator can already express the contract, but the project input is wrong or incomplete.

Examples:

- a list query response was normalized as a single `Response` item
- a page query response lacks `PageData<Item>`
- a required request trait is missing
- a field uses an unresolved short type that should be an FQCN or type-registry entry
- a container field lacks the direct root declaration required by nested model rules

Repair path:

- fix `codegen/design/design.json`
- fix the standardization script that creates it
- re-run `cap4kGenerate`

### Generator Capability Defect

The design input describes a valid desired contract, but the pipeline cannot generate it.

Examples:

- a supported nested model shape is rejected
- default values accepted by design contracts are lost in rendering
- generated imports or type resolution fail for valid FQCN/type-registry input
- query/page traits are not rendered consistently across `query`, `api_payload`, and handlers

Repair path:

- add targeted source/canonical/planner/renderer coverage
- update the generator
- re-run dogfood from the same input

### Migration Boundary Defect

The generated application contract is correct, but migrated hand-written code still assumes the old contract.

Examples:

- a handler returns old `Item` objects instead of generated `Response`
- a controller expects old MapStruct `Converter` classes
- adapter code expects `ListQueryParam<Item>` or `PageQueryParam<Item>`

Repair path:

- migrate the user-owned implementation to the new generated contract
- keep TODO bodies only as temporary compile unblocks
- do not modify the generated contract to match the old implementation

## Verification Strategy

The implementation should not rely only on unit tests.

Verification should include a dogfood-oriented contract audit:

- run `cap4kPlan` and `cap4kGenerate` from the prepared zero inputs
- identify generated Query/Cmd/Cli contract artifacts
- compare them against expected design entries
- flag checked-in hand-written contract files that are supposed to be generated
- flag skipped or missing generated contracts
- classify each failure as design input defect, generator capability defect, or migration boundary defect

The audit can start as a focused test fixture or script. It does not need to solve every migration failure immediately, but it must make failures visible and hard to misclassify.

## Expected Implementation Shape

The likely implementation should proceed in this order:

1. Add a contract audit fixture or test around design entries and generated artifacts.
2. Encode the rule that `command`, `query`, and `client` entries must have corresponding generated contract artifacts.
3. Add dogfood-oriented checks for missing or skipped contracts.
4. Repair only the smallest generator gaps discovered by the audit if they are already clearly supported by existing design semantics.
5. Leave larger missing capabilities as explicit follow-up items with repro inputs.

The first implementation should not become a broad real-project migration rewrite. Its main value is to make the structure-first contract enforceable.

## Acceptance Criteria

The slice is complete when:

- the spec is implemented by tests or an audit mechanism that can detect Query/Cmd/Cli contract drift
- all `command`, `query`, and `client` design entries in the representative fixture have planned generated contract artifacts
- list and page response semantics are represented through explicit design fields, not hand edits
- valid nested, recursive, list, page, default-value, and type-registry cases remain supported
- unsupported contract shapes fail with actionable diagnostics or are recorded as capability defects
- generated contract classes are not modified in fixture source to compensate for missing generator capability
- handler/controller migration defects are separated from contract-generation defects

## Open Decisions Before Plan

These points should be rechecked before writing the implementation plan:

- whether the first audit should live in `cap4k` functional tests, `only-danmuku-zero` dogfood scripts, or both
- whether `api_payload` should be promoted into the same hard verification gate in the first implementation slice
- whether artifact-level conflict policy overrides should be implemented first, because forced overwrite may be needed to prove regenerated contracts in dirty real-project migration
- whether the audit should compare generated source paths, generated class names, or parsed Kotlin structure

## Deferred Work

The following are intentionally deferred unless the implementation audit proves they are immediate blockers:

- frontend TypeScript generation
- controller generation
- automatic API-to-application mapper generation
- full old-project compatibility aliases
- legacy `Item` contract restoration
- broad irAnalysis restructuring
- artifact-level conflict policy override implementation

## Summary Decision

Cap4k's public direction is contract-first and design-owned:

- Query/Cmd/Cli `Request` and `Response` structures belong to design input
- generator inability is a framework capability defect
- hand-written contract fixes are migration scaffolding only
- business behavior remains hand-written where generation cannot know intent

This distinction should hold for dogfood, future tutorials, and real-project migration.

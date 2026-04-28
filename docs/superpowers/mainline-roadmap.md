# Cap4k Mainline Roadmap

Date: 2026-04-17

## Purpose

This document records durable redesign status and decisions for `cap4k`.

It replaces the temporary handoff style with a longer-lived repository record for:

- completed mainline slices
- the current next mainline slice
- work that must stay on separate tracks
- bootstrap contract decisions that should not be rediscovered from chat history

## Original Mainline

The original mainline is:

1. reset architecture to a fixed-stage pipeline
2. migrate major capability slices into that pipeline
3. improve design-generator output quality until old template migration becomes practical

## Completed Mainline Slices

### Phase A: Pipeline Architecture Reset

Completed:

- pipeline foundation
- db aggregate minimal
- ir flow
- drawing-board
- DSL consolidation

Status:

- complete

Traceability:

- [pipeline redesign design](specs/2026-04-09-cap4k-pipeline-redesign-design.md)
- [pipeline db aggregate minimal design](specs/2026-04-09-cap4k-pipeline-db-aggregate-minimal-design.md)
- [pipeline ir flow design](specs/2026-04-10-cap4k-pipeline-ir-flow-design.md)
- [pipeline drawing-board design](specs/2026-04-10-cap4k-pipeline-drawing-board-design.md)
- [pipeline DSL consolidation design](specs/2026-04-10-cap4k-pipeline-dsl-consolidation-design.md)

### Phase B: Design Generator Quality Mainline

Completed:

- minimal usable
- type/import resolution
- auto-import
- template helpers
- default value
- `use()` helper
- design template migration / helper adoption
- representative old design template / override migration
- design query-handler family migration
- design client / client_handler family migration
- design validator family migration
- design api_payload migration
- design domain_event / domain_event_handler family migration
- design generator compile-level / integrated verification hardening

Status:

- the original design-generator quality mainline is complete through compile-level / integrated verification hardening
- roadmap completion state follows merged repository history; some linked slice specs may still retain pre-merge draft headers

Traceability:

- [design generator minimal usable design](specs/2026-04-10-cap4k-design-generator-minimal-usable-design.md)
- [design generator type/import resolution design](specs/2026-04-10-cap4k-design-generator-type-import-resolution-design.md)
- [design generator auto-import design](specs/2026-04-11-cap4k-design-generator-auto-import-design.md)
- [design generator template helpers design](specs/2026-04-11-cap4k-design-generator-template-helpers-design.md)
- [design generator default value design](specs/2026-04-12-cap4k-design-generator-default-value-design.md)
- [design generator use helper design](specs/2026-04-12-cap4k-design-generator-use-helper-design.md)
- [design template migration / helper adoption design](specs/2026-04-13-cap4k-design-template-migration-helper-adoption-design.md)
- [representative design template / override migration design](specs/2026-04-14-cap4k-representative-design-template-override-migration-design.md)
- [design query-handler family migration design](specs/2026-04-14-cap4k-design-query-handler-family-migration-design.md)
- [design client family migration design](specs/2026-04-14-cap4k-design-client-family-migration-design.md)
- [design validator family migration design](specs/2026-04-15-cap4k-design-validator-family-migration-design.md)
- [design api payload migration design](specs/2026-04-15-cap4k-design-api-payload-migration-design.md)
- [design domain event family migration design](specs/2026-04-15-cap4k-design-domain-event-family-migration-design.md)
- [design generator compile-level / integrated verification hardening design](specs/2026-04-15-cap4k-design-generator-compile-level-integrated-verification-hardening-design.md)

### Phase C: Bootstrap Capability Mainline

Completed:

- bootstrap / arch-template migration
- bootstrap generated-project verification hardening

Status:

- the bounded bootstrap capability line is complete through generated-project verification hardening
- bootstrap now exists as a separate capability with its own DSL, tasks, preset, bounded slots, renderer flow, functional closure, and generated-project verification gate

Traceability:

- [bootstrap / arch-template migration design](specs/2026-04-16-cap4k-bootstrap-arch-template-migration-design.md)
- [bootstrap generated-project verification hardening design](specs/2026-04-16-cap4k-bootstrap-generated-project-verification-hardening-design.md)

### Phase D: Cross-Generator Reference Boundary Mainline

Completed:

- cross-generator type-reference parity
- aggregate factory / specification parity
- aggregate wrapper parity
- aggregate unique-constraint family parity
- aggregate enum / enum-translation parity

Status:

- the cross-generator reference boundary line is complete through aggregate enum / enum-translation parity
- framework-facing type-reference work remains explicitly separated from broader aggregate parity and exploratory full-replacement gaps, and the aggregate-side consumer line now has planner, renderer, functional, and compile closure through bounded enum ownership and translation output

Traceability:

- [cross-generator type-reference parity design](specs/2026-04-17-cap4k-cross-generator-type-reference-parity-design.md)
- [aggregate factory / specification parity design](specs/2026-04-17-cap4k-aggregate-factory-specification-parity-design.md)
- [aggregate wrapper parity design](specs/2026-04-17-cap4k-aggregate-wrapper-parity-design.md)
- [aggregate unique-constraint family parity design](specs/2026-04-17-cap4k-aggregate-unique-constraint-family-parity-design.md)
- [aggregate enum / enum-translation parity design](specs/2026-04-17-cap4k-aggregate-enum-translation-parity-design.md)

### Phase E: Aggregate Relation Semantics Mainline

Completed:

- aggregate relation parity
- aggregate relation-side JPA control parity
- aggregate inverse relation read-only parity

Status:

- the bounded aggregate relation line is complete through inverse relation read-only parity
- aggregate-side relation work now has bounded source carriage, canonical inference, planner and renderer consumption, functional generation coverage, compile verification, first-slice relation-side JPA control for `ONE_TO_MANY`, `MANY_TO_ONE`, and `ONE_TO_ONE`, and bounded inverse/read-only `*ManyToOne` parity, while join-table and many-to-many forms remain explicitly deferred

Traceability:

- [aggregate relation parity design](specs/2026-04-18-cap4k-aggregate-relation-parity-design.md)
- [aggregate relation-side JPA control parity design](specs/2026-04-20-cap4k-aggregate-relation-side-jpa-control-parity-design.md)
- [aggregate inverse relation read-only parity design](specs/2026-04-20-cap4k-aggregate-inverse-relation-read-only-parity-design.md)

### Phase F: Aggregate Persistence Control Mainline

Completed:

- aggregate JPA annotation fine-grained control parity
- aggregate persistence field-behavior parity
- aggregate provider-specific persistence parity
- aggregate generic-generator parity

Status:

- the bounded aggregate persistence-control line is complete through bounded generic-generator parity
- aggregate-side persistence control now has bounded source carriage, canonical enrichment, planner and renderer consumption, functional generation coverage, and compile verification for entity/table/column baseline JPA output, explicit field-behavior control, first-slice provider-specific entity behavior, and explicit custom id-generator output without reopening relation-side ownership or full provider-specific recovery

Traceability:

- [aggregate JPA annotation fine-grained control parity design](specs/2026-04-19-cap4k-aggregate-jpa-annotation-fine-grained-control-parity-design.md)
- [aggregate persistence field-behavior parity design](specs/2026-04-19-cap4k-aggregate-persistence-field-behavior-parity-design.md)
- [aggregate provider-specific persistence parity design](specs/2026-04-19-cap4k-aggregate-provider-specific-persistence-parity-design.md)
- [aggregate generic-generator parity design](specs/2026-04-21-cap4k-aggregate-generic-generator-parity-design.md)

## Current Mainline Contract

These points remain in force:

- `use()` is design-template-only
- `use()` only accepts explicit FQN strings
- `use()` is only for explicit imports, not type resolution
- `imports()` remains the output path for import lines
- `defaultValue` in render models is Kotlin-ready, not raw source text
- short-name handling remains conservative
- explicit FQN and symbol identity remain the truth source

## Capability Verification Track

The framework now keeps a separate capability-verification track in addition to the explicit framework mainline slices.

Rules for that track:

- current capability truth lives in [capability-matrix.md](capability-matrix.md)
- `only-danmaku-next` is a capability verification project, not the official beginner tutorial line
- stage branches in `only-danmaku-next` verify selected capability rows from the matrix
- gaps exposed by the verification project must be fed back into `cap4k` capability contracts before they are treated as supported behavior

Traceability:

- [only-danmaku-next capability verification project design](specs/2026-04-22-cap4k-only-danmaku-next-capability-verification-project-design.md)

## Current Next Mainline Slice

The original design-generator quality mainline is complete, and the bounded bootstrap capability line is complete through generated-project verification hardening.

The cross-generator reference boundary line is complete through aggregate enum / enum-translation parity.

The bounded aggregate relation line is complete through inverse relation read-only parity.

The bounded aggregate persistence-control line is complete through generic-generator parity.

The next explicit framework slice is:

- aggregate JPA runtime defect reproduction and in-place repair

Status:

- spec written
- implementation plan intentionally deferred until execution
- implementation not started

Scope:

- build on the now-stable bounded persistence line rather than starting with a backend replacement
- reproduce the real `only-danmuku` JPA aggregate defects inside representative runtime fixtures
- make preassignable application-side IDs an explicit contract candidate, not a vague "generated or manual" switch
- keep ID generation strategy scoped by project default plus aggregate/entity override, not a project-global singleton
- verify repository/unit-of-work transaction boundaries for aggregate child access before defaulting to eager loading
- verify three-level aggregate whole-save, update, and orphan-removal behavior before classifying it as unsupported
- fix in place only when a focused reproduction proves a cap4k bug or an approved contract gap

Reference:

- [aggregate JPA runtime defect reproduction design](specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md)

Non-goals:

- do not restore mutable shared runtime type maps between generators
- do not widen this slice into relation re-architecture, user-code-preservation parity, or general source-semantic recovery
- do not broaden bootstrap beyond the current bounded contract
- do not silently expand into sequence/table strategy recovery, generator registry redesign, or full provider-specific recovery beyond the reproduced defect
- do not turn runtime defect reproduction into general real-project integration support
- do not start a Jimmer/MyBatis/JOOQ backend comparison before the JPA defects are reproduced and classified
- do not silently reactivate `mappedBy`, `@JoinTable`, or `ManyToMany`; those remain explicitly later-priority work
- do not turn exploratory parity notes into a general rewrite of the current pipeline architecture

## Bootstrap Decision

The bootstrap direction is:

- bootstrap should become a separate framework capability
- the public bootstrap contract should use slot-based extension
- users may add arbitrary numbers of files through bounded slots
- the public contract should not restore arbitrary insertion into any architecture-tree node
- the old `archTemplate` JSON remains migration input or reference material, not the future public runtime contract

## Non-Default Work

The following remain separate from the default mainline path:

- real-project integration boundary work
- project-specific unblock work
- broader bootstrap flexibility beyond slot-based extension

## Upcoming Backlog

Date: 2026-04-28

These items are recorded to preserve scheduling context. They are not implementation plans.

Plan freshness rule:

- A missing implementation plan is usually intentional for backlog items.
- Keep future work at `analysis-only` or `spec-only` until the work is actually selected for execution.
- Before writing a plan, re-read the current spec against the current `master` branch and update the spec if the repository has moved.
- Write the implementation plan only after that review, so the plan is timely and executable.
- The validator projection and generation normalization track is the current exception because a combined plan was explicitly requested before execution.

Recommended order after the current next mainline:

1. Contract-first query contract
2. ddd-core nullability contract stabilization
3. validator projection and generation normalization
4. irAnalysis restructuring analysis
5. unit-of-work and repository backend comparison, only if aggregate JPA runtime reproduction evidence justifies it

The third item is a combined implementation track over:

- analysis design projection normalization
- validator generation capability expansion
- the irAnalysis current-state analysis constraints

### 1. Contract-first query contract

Status:

- spec-only by design
- implementation plan intentionally deferred until execution
- implementation not started

Reference:

- [contract-first query contract design](specs/2026-04-27-cap4k-contract-first-query-contract-design.md)

Next action:

- when this item is selected for execution, re-review the spec against current `master`
- update the spec first if query, payload, or ddd-core contracts have moved
- write the implementation plan only after that review

Notes:

- this is a breaking API cleanup
- default query generation should move to `Query<Request, Response>`
- `Response` should mean complete response payload
- `PageQuery`, `ListQuery`, `PageQueryParam`, and `ListQueryParam` should be removed
- `PageRequest` should carry only `pageNum` and `pageSize` in the first iteration

### 2. ddd-core nullability contract stabilization

Status:

- analysis material exists
- spec-only by design
- implementation plan intentionally deferred until execution
- implementation not started

Reference:

- [ddd-core nullability analysis](../design/ddd-core-nullability/analysis.md)
- [ddd-core nullability contract stabilization design](specs/2026-04-27-cap4k-ddd-core-nullability-contract-stabilization-design.md)

Next action:

- when this item is selected for execution, re-review the spec against current `master`
- update the spec first if repository, aggregate, domain-service, or saga APIs have moved
- write the implementation plan only after that review

Notes:

- this is broader than Optional cleanup
- cap4k Kotlin public lookup APIs should remove Java `Optional<T>` exposure
- normal lookup absence should use Kotlin `T?`
- `DomainServiceSupervisor.getService()` should become non-null and fail fast
- `SagaProcessSupervisor.sendProcess()` and `SagaHandler.execProcess()` should become non-null
- stable nullable result accessors such as async request result and saga record result should remain nullable
- do not mix in `Any` serialization-boundary cleanup
- Spring Data repository boundaries may still use Java `Optional`
- this should be separate from contract-first query contract implementation

### 3. analysis design projection normalization

Status:

- analyzed
- spec written
- combined implementation plan written as an explicit exception to the freshness rule
- implementation not started

Reference:

- [analysis design projection normalization design](specs/2026-04-27-cap4k-analysis-design-projection-normalization-design.md)

Next action:

- before execution, re-review this spec together with validator generation capability expansion
- update the spec or plan first if analysis projection, drawing-board, or validator model code has moved
- execute through the combined validator projection and generation normalization plan after review

Notes:

- this is broader than simply adding `validator` to drawing-board output
- `design-elements.json` is an analysis-side design projection and should speak the new pipeline tag language
- `validator` should be projected only for supported structural validator contracts
- concrete request-type validators are treated as migration defects, not as a new pipeline contract
- this should still be smaller than a full irAnalysis restructuring
- generated `drawing_board_*.json` should be usable as stable input for `cap4kGenerate`
- do not add a normalization layer just to compensate for analysis output gaps
- should be implemented in the same plan as validator generation capability expansion because both share the validator design model

### 4. Validator generation capability expansion

Status:

- existing bounded validator migration exists
- expanded capability spec written
- combined implementation plan written as an explicit exception to the freshness rule
- implementation not started

Reference:

- [design validator family migration design](specs/2026-04-15-cap4k-design-validator-family-migration-design.md)
- [validator generation capability expansion design](specs/2026-04-27-cap4k-validator-generation-capability-expansion-design.md)

Next action:

- before execution, re-review this spec together with analysis design projection normalization
- update the spec or plan first if validator generator, renderer, or template contracts have moved
- execute through the combined validator projection and generation normalization plan after review

Notes:

- the current validator generator is intentionally minimal and fixed around `Long`
- the next version should support structural validator declarations
- first-slice generation should produce compiling skeletons only
- class-level ordinary validators should use `ConstraintValidator<Annotation, Any>`
- `message`, `targets`, `valueType`, and scalar custom parameters should be explicit
- validator bodies should return `true`; business logic remains hand-written or template-overridden
- trait/interface targets, automatic request attachment, and advanced parameter types are deferred
- aggregate unique validators remain separate from ordinary design validators
- should be implemented in the same plan as analysis design projection normalization because both share the validator design model

### 5. irAnalysis restructuring analysis

Status:

- candidate work
- broad restructuring need not confirmed
- analysis written

Reference:

- [irAnalysis current-state analysis](../design/ir-analysis/current-state-analysis.md)

Next action:

- do not open a broad restructuring implementation track yet
- revisit only after analysis design projection normalization and validator generation expansion are implemented

Notes:

- current evidence does not justify a broad irAnalysis rewrite
- `nodes.json` and `rels.json` are graph artifacts and should remain stable
- `design-elements.json` is a design projection and should be normalized under the new pipeline language
- if analysis design projection normalization can be solved without restructuring, keep restructuring deferred
- this should not block smaller drawing-board or validator-generation slices unless evidence shows the current architecture cannot support them

### 6. Unit-of-work and repository backend comparison

Status:

- candidate strategic work
- backend choice not analyzed
- spec not written
- implementation plan not written
- deferred until aggregate JPA runtime defects are reproduced and classified

Next action:

- first execute or re-review the aggregate JPA runtime defect reproduction slice
- perform an analysis/PoC design only if the reproduction evidence shows the current JPA path cannot support the approved contracts safely
- keep this track at analysis/spec level until a backend comparison direction is approved
- do not write an implementation plan before the comparison spec exists and has been reviewed

Notes:

- current unit-of-work and repository implementations are JPA-based
- JPA usage has exposed enough practical problems to justify runtime reproduction first, not immediate replacement
- this should be treated as a persistence backend track only after in-place JPA repair has been evaluated
- the first slice should compare candidates and define a bounded PoC, not replace the current JPA implementation
- likely evaluation dimensions include aggregate loading, dirty tracking, transactions, optimistic locking, relation handling, preassignable application-side ID generation, query ergonomics, Kotlin support, Spring integration, testing, and migration risk
- this has a large blast radius and should not be mixed into contract-first query, nullability contract stabilization, validator work, or irAnalysis work
- the safest route is probably a parallel backend implementation behind existing repository/unit-of-work contracts, then runtime verification against representative aggregate fixtures

## Support Track Docs

Concrete support-track references:

- [real project local integration](specs/2026-04-09-cap4k-only-danmuku-local-integration-design.md)
- [real project integration boundaries](specs/2026-04-11-cap4k-real-project-integration-boundaries-design.md)
- [project type registry](specs/2026-04-12-cap4k-project-type-registry-design.md)

## Continue Rules

- If the user says only "continue", continue the original mainline.
- Do not default into an integration workaround.
- Do not silently merge bootstrap migration into design-template migration.
- Only promote project-specific patterns into framework contract when explicitly approved.

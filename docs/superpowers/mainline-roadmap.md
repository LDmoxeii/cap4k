# Cap4k Mainline Roadmap

Date: 2026-04-29

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

The contract-first query contract and ddd-core nullability contract stabilization cleanup slices are complete.

The validator projection and generation normalization slice is complete.

The generated-source output slice is complete:

- generated source output and entity behavior split

Status:

- spec updated
- implementation plan written
- implementation complete
- verified through targeted pipeline API/core/aggregate/renderer tests and Gradle plugin unit, functional, and compile-functional tests
- reviewed with required subagent boundary and code-quality passes

Scope:

- generated-source output contract and module-local source-set integration are implemented
- aggregate entity files now use build-generated output together with the behavior-safe template contract
- aggregate behavior scaffolds remain checked-in source with skip semantics
- implementation avoided a simple exporter-root switch

Reference:

- [generated source output and entity behavior split design](specs/2026-04-28-cap4k-generated-source-output-and-entity-behavior-split-design.md)
- [generated source output and entity behavior split implementation plan](plans/2026-04-29-cap4k-generated-source-output-and-entity-behavior-split.md)

Non-goals:

- do not implement this as a simple exporter-root switch
- do not move user-owned handlers, validator bodies, subscribers, controllers, or behavior files into generated sources
- do not further change aggregate entity mutability or behavior-file contracts beyond the behavior-safe template contract defined in this slice
- do not silently mix this with broad irAnalysis restructuring or real-project integration work
- do not touch legacy `cap4k-plugin-codegen`

The most recent explicit framework slice is:

- aggregate JPA mapping safety and load-plan semantics

Status:

- implementation complete
- verified through pipeline API/core/aggregate/renderer tests, JPA repository tests, aggregate runtime fixture, and aggregate Gradle plugin compile-functional tests
- generated parent-child aggregate mappings now avoid `CascadeType.ALL` and render explicit `PERSIST`, `MERGE`, and `REMOVE`
- `AggregateLoadPlan` is propagated through `Repository`, `RepositorySupervisor`, `AggregateSupervisor`, `DefaultMediator`, and JPA repository implementations
- JPA `WHOLE_AGGREGATE` initializes owned `@OneToMany` graphs below the repository boundary
- compatibility-only repository/supervisor implementations may fall back for `DEFAULT` and `MINIMAL`, but fail fast for `WHOLE_AGGREGATE`
- command-wide transaction expansion remains deferred

Reference:

- [aggregate JPA runtime defect reproduction design](specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md)
- [aggregate JPA mapping safety and load-plan semantics implementation plan](plans/2026-04-29-cap4k-aggregate-jpa-mapping-safety-and-load-plan-semantics.md)

Notes:

- `persist=true` still means "register this loaded entity into UnitOfWork"; it does not choose load depth
- use-case load depth is explicit through `AggregateLoadPlan`
- JPA whole-load uses a repository-level read-only transaction for initialization; it does not wrap the whole command/request path
- Querydsl repositories accept the public load-plan parameter but fail fast for `WHOLE_AGGREGATE` until provider-specific whole-load behavior is designed

No new default mainline implementation slice has been selected yet; remaining candidates are tracked in the backlog below.

Current persistence decision:

- command-wide transaction expansion remains deferred
- the lazy aggregate fixture still proves an expanded request/command transaction can solve lazy access, but the implemented repair uses explicit repository load plans instead
- future command transaction-boundary expansion should become its own architecture slice only if the work also redesigns real commit/after-transaction semantics for unit-of-work interceptors, domain events, and integration events

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
- legacy `cap4k-plugin-codegen` maintenance; it is frozen and should only be touched for explicit deletion/quarantine work

## Legacy Plugin Decision

`cap4k-plugin-codegen` is the old monolithic plugin and is no longer a maintained capability.

Decision:

- do not add compatibility work, new features, or behavior-preserving cleanup there
- do not treat legacy template names or suffix routing inside that plugin as mainline pipeline obligations
- plan a separate deletion/quarantine cleanup when there is time
- prefer the fixed-stage pipeline for all ongoing generator work

## Upcoming Backlog

Date: 2026-04-29

These items are recorded to preserve scheduling context. They are not implementation plans.

Plan freshness rule:

- A missing implementation plan is usually intentional for backlog items.
- Keep future work at `analysis-only` or `spec-only` until the work is actually selected for execution.
- Before writing a plan, re-read the current spec against the current `master` branch and update the spec if the repository has moved.
- Write the implementation plan only after that review, so the plan is timely and executable.
- The validator projection and generation normalization track was the exception because a combined plan was explicitly requested before execution.

Remaining recommended order from the current mainline handoff:

1. aggregate JPA mapping safety and load-plan semantics, if aggregate persistence work is selected next
2. irAnalysis restructuring analysis
3. unit-of-work and repository backend comparison, only if aggregate JPA runtime reproduction evidence justifies it

The completed validator projection item was a combined implementation track over:

- analysis design projection normalization
- validator generation capability expansion
- the irAnalysis current-state analysis constraints

Recently completed mainline slices:

- contract-first query contract
- ddd-core nullability contract stabilization
- validator projection and generation normalization
- generated source output and entity behavior split

### Completed: Contract-first query contract

Status:

- implementation complete
- verified through pipeline API, design-json source, canonical assembly, design generator, Pebble renderer, codegen template tests, and Gradle functional / compile-functional tests

Reference:

- [contract-first query contract design](specs/2026-04-27-cap4k-contract-first-query-contract-design.md)

Next action:

- no direct follow-up; continue from the current recommended order

Notes:

- this is a breaking API cleanup
- default query generation should move to `Query<Request, Response>`
- `Response` should mean complete response payload
- `PageQuery`, `ListQuery`, `PageQueryParam`, and `ListQueryParam` should be removed
- `PageRequest` should carry only `pageNum` and `pageSize` in the first iteration

### Completed: ddd-core nullability contract stabilization

Status:

- analysis material exists
- spec updated against current `master`
- implementation plan written and executed
- implementation complete
- verified with `:ddd-core:test`, `:ddd-domain-repo-jpa:test`, and `:ddd-domain-repo-jpa-querydsl:test`
- committed as `a175a2cb Stabilize ddd-core nullability contracts`

Reference:

- [ddd-core nullability analysis](../design/ddd-core-nullability/analysis.md)
- [ddd-core nullability contract stabilization design](specs/2026-04-27-cap4k-ddd-core-nullability-contract-stabilization-design.md)
- [ddd-core nullability implementation plan](plans/2026-04-28-cap4k-ddd-core-nullability-contract-stabilization.md)

Next action:

- no direct follow-up; continue from the current recommended order

Notes:

- this is broader than Optional cleanup
- cap4k Kotlin public lookup APIs should remove Java `Optional<T>` exposure
- normal lookup absence should use Kotlin `T?`
- `DomainServiceSupervisor.getService()` should become non-null and fail fast
- `SagaProcessSupervisor.sendProcess()` and `SagaHandler.execProcess()` should become non-null
- stable nullable result accessors such as async request result and saga record result should remain nullable
- do not mix in `Any` serialization-boundary cleanup
- Spring Data repository boundaries may still use Java `Optional`
- this was kept separate from contract-first query contract implementation

### Completed: analysis design projection normalization

Status:

- analyzed
- spec written
- combined implementation plan written as an explicit exception to the freshness rule
- implementation complete
- verified through analysis compiler projection tests, source-ir parsing tests, drawing-board planner/core tests, and validator drawing-board round-trip functional coverage

Reference:

- [analysis design projection normalization design](specs/2026-04-27-cap4k-analysis-design-projection-normalization-design.md)
- [validator projection and generation normalization plan](plans/2026-04-27-cap4k-validator-projection-and-generation-normalization.md)

Next action:

- no direct follow-up; continue from the current recommended order

Notes:

- this is broader than simply adding `validator` to drawing-board output
- `design-elements.json` is an analysis-side design projection and should speak the new pipeline tag language
- `validator` should be projected only for supported structural validator contracts
- concrete request-type validators are treated as migration defects, not as a new pipeline contract
- this should still be smaller than a full irAnalysis restructuring
- generated `drawing_board_*.json` is intended as generate-ready design input for supported projections; PAGE query and `api_payload` traits are now preserved through analysis projection and drawing-board export
- do not add a normalization layer just to compensate for analysis output gaps
- should be implemented in the same plan as validator generation capability expansion because both share the validator design model

### Completed: Validator generation capability expansion

Status:

- existing bounded validator migration exists
- expanded capability spec written
- combined implementation plan written as an explicit exception to the freshness rule
- implementation complete
- verified through design-json parsing tests, canonical validator assembly tests, validator planner/render model tests, and compile-functional coverage for expanded validator skeletons

Reference:

- [design validator family migration design](specs/2026-04-15-cap4k-design-validator-family-migration-design.md)
- [validator generation capability expansion design](specs/2026-04-27-cap4k-validator-generation-capability-expansion-design.md)
- [validator projection and generation normalization plan](plans/2026-04-27-cap4k-validator-projection-and-generation-normalization.md)

Next action:

- no direct follow-up; continue from the current recommended order

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

### 1. Generated source output and entity behavior split

Status:

- implementation complete
- spec updated
- implementation plan written
- implementation complete
- verified through targeted pipeline API/core/aggregate/renderer tests and Gradle plugin unit, functional, and compile-functional tests
- reviewed with required subagent boundary and code-quality passes

Next action:

- no direct follow-up; keep broader irAnalysis restructuring and repository backend work deferred

Reference:

- [generated source output and entity behavior split design](specs/2026-04-28-cap4k-generated-source-output-and-entity-behavior-split-design.md)
- [generated source output and entity behavior split implementation plan](plans/2026-04-29-cap4k-generated-source-output-and-entity-behavior-split.md)

Notes:

- some generated artifacts are pure derived code and should not necessarily live under checked-in `src/main/kotlin`
- generated-source artifacts now include aggregate entity files, schema `S*` classes, standard repositories, generated enums, enum translations, converters, aggregate unique queries, aggregate unique query handlers, and aggregate unique validators
- user-owned artifacts such as handlers, validator bodies, subscribers, controllers, and behavior files should stay checked in
- entity files moved to generated sources after restoring the old plugin's mutable entity shape
- entity templates should generate regular `class` declarations, not `data class`
- constructor parameters should initialize body fields rather than becoming immutable primary-constructor properties
- scalar fields should use `var field = field` with bounded setters such as `internal set`
- owned collections should remain behavior-friendly, typically `MutableList<T>` with `mutableListOf()`
- behavior should be separated into checked-in files such as `<AggregateRootName>Behavior.kt`, generated once with a skip/keep-existing policy
- a generated empty behavior file is useful only as a scaffold; the generated entity contract must make that behavior file able to mutate aggregate state safely
- Gradle integration must register generated Kotlin source directories per module so IDE import and `compileKotlin` see the generated files consistently

### 2. Aggregate JPA mapping safety and load-plan semantics

Status:

- implementation complete
- runtime evidence and repair coverage exist in `AggregateJpaRuntimeDefectReproductionTest`
- generator and runtime repair merged
- command-wide transaction expansion explicitly deferred

Reference:

- [aggregate JPA runtime defect reproduction design](specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md)
- [aggregate JPA mapping safety and load-plan semantics implementation plan](plans/2026-04-29-cap4k-aggregate-jpa-mapping-safety-and-load-plan-semantics.md)

Next action:

- keep command transaction-boundary expansion as a later architecture slice unless the unit-of-work commit/after-transaction semantics are redesigned in the same work
- do not open a persistence backend replacement just for the lazy-loading symptom
- revisit Querydsl provider-specific whole-load semantics only if a real use case needs it

Notes:

- `FetchType.EAGER` is a global mapping policy, not a use-case loading policy
- use-case loading is explicit through `AggregateLoadPlan.DEFAULT`, `AggregateLoadPlan.MINIMAL`, and `AggregateLoadPlan.WHOLE_AGGREGATE`
- JPA implements whole-aggregate loading with explicit owned `@OneToMany` initialization below the repository boundary
- `persist=true` should continue to mean "register this loaded entity into UnitOfWork"; it should not secretly decide load depth or transaction scope
- `persist=false` remains meaningful inside commands because a handler may perform read-only lookups that should not be saved
- command-wide transaction expansion has test evidence, but its blast radius includes `JpaUnitOfWork.save()`, real commit timing, `afterTransaction`, domain events, integration events, and interceptors
- do not choose a persistence backend replacement just to solve this lazy-loading symptom

### 3. irAnalysis restructuring analysis

Status:

- candidate work
- broad restructuring need not confirmed
- analysis written

Reference:

- [irAnalysis current-state analysis](../design/ir-analysis/current-state-analysis.md)

Next action:

- do not open a broad restructuring implementation track yet
- revisit only if new analysis evidence proves the current graph/projection split cannot support planned generator inputs

Notes:

- current evidence does not justify a broad irAnalysis rewrite
- `nodes.json` and `rels.json` are graph artifacts and should remain stable
- `design-elements.json` is a design projection and should be normalized under the new pipeline language
- if analysis design projection normalization can be solved without restructuring, keep restructuring deferred
- this should not block smaller drawing-board or validator-generation slices unless evidence shows the current architecture cannot support them

### 4. Unit-of-work and repository backend comparison

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

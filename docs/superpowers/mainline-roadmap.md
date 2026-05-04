# Cap4k Mainline Roadmap

Date: 2026-05-03

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

The aggregate entity default projection slice is complete.

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
- `AggregateLoadPlan.DEFAULT` has been removed; public load depth is now limited to explicit `MINIMAL` and `WHOLE_AGGREGATE`
- no-plan JPA and Querydsl repository reads default to `WHOLE_AGGREGATE`; performance-sensitive callers must request `MINIMAL`
- command-wide transaction expansion remains deferred

Reference:

- [aggregate JPA runtime defect reproduction design](specs/2026-04-21-cap4k-aggregate-persistence-runtime-verification-hardening-design.md)
- [aggregate JPA mapping safety and load-plan semantics implementation plan](plans/2026-04-29-cap4k-aggregate-jpa-mapping-safety-and-load-plan-semantics.md)

Notes:

- `persist=true` still means "register this loaded entity into UnitOfWork"; it does not choose load depth
- use-case load depth is explicit through `AggregateLoadPlan`
- JPA whole-load uses a repository-level read-only transaction for initialization; it does not wrap the whole command/request path
- Querydsl repositories accept the same public load-plan parameter and initialize owned collections for `WHOLE_AGGREGATE`

No new default mainline implementation slice has been selected yet; remaining candidates are tracked in the backlog below.

Latest completed mainline slice:

- UUID7 ID generator and default ID-generation policy

Status:

- implementation complete
- spec and implementation plan completed
- database identity IDs still render `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- application-side ID strategies render field-level `@ApplicationSideId`
- UUID7 is the default application-side strategy; Snowflake remains explicit as `snowflake-long`
- legacy DB `@IdGenerator` comment support remains unsupported

Reference:

- [UUID7 ID generator and default ID policy design](specs/2026-05-02-cap4k-uuid7-id-generator-default-policy-design.md)
- [UUID7 application-side ID policy implementation plan](plans/2026-05-02-cap4k-uuid7-application-side-id-policy.md)

Latest completed dogfood generator-quality slice:

- aggregate entity default projection

Status:

- spec written
- implementation plan written
- implementation complete
- verified through targeted aggregate generator tests covering scalar defaults, nullable defaults, unsupported SQL defaults, computed defaults, control-character escaping, and enum default projection

Reference:

- [aggregate entity default projection design](specs/2026-05-01-cap4k-aggregate-entity-default-projection-design.md)
- [aggregate entity default projection implementation plan](plans/2026-05-01-cap4k-aggregate-entity-default-projection.md)

Notes:

- constructor defaults now follow explicit-input projection only
- nullable fields may default to `null`
- relation collections keep collection defaults
- scalar DB defaults are normalized only when they can be safely rendered as Kotlin constructor defaults
- enum DB defaults are resolved against known enum item metadata under the numeric enum model
- the generator does not invent primitive fallback defaults, field-name-based technical defaults, implicit enum fallback defaults, or value-object defaults

Current persistence decision:

- command-wide transaction expansion remains deferred
- the lazy aggregate fixture now passes through the normal command path because no-plan repository reads default to `WHOLE_AGGREGATE`; the expanded request/command transaction test remains only as contrast evidence
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

Date: 2026-05-03

These items are recorded to preserve scheduling context. They are not implementation plans.

Plan freshness rule:

- A missing implementation plan is usually intentional for backlog items.
- Keep future work at `analysis-only` or `spec-only` until the work is actually selected for execution.
- Before writing a plan, re-read the current spec against the current `master` branch and update the spec if the repository has moved.
- Write the implementation plan only after that review, so the plan is timely and executable.
- The validator projection and generation normalization track was the exception because a combined plan was explicitly requested before execution.

Remaining recommended order from the current mainline handoff:

1. aggregate inverse-navigation fetch policy decision
2. database special-field declaration contract unification
3. read/write model association-scope separation analysis
4. irAnalysis-backed frontend TypeScript generation analysis
5. framework capability audit for domain service, value object, aggregate wrapper, strong ID, and saga
6. README rewrite after the framework capability audit clarifies the public story
7. DDD + cap4k + AI collaboration guide after the README positioning is stable
8. built-in testing skeleton feasibility analysis
9. irAnalysis restructuring analysis, only if smaller projection or TypeScript-generation needs prove the current shape is insufficient
10. unit-of-work and repository backend comparison, only if aggregate JPA runtime reproduction evidence justifies it
11. cap4k-ddd-starter auto-configuration test fixture isolation

Dogfood-discovered generator quality follow-ups:

11. analysis / drawing-board defaultValue expression projection hardening
12. generated / migrated Kotlin import formatting cleanup
13. artifact-level conflict policy overrides for generator output
14. aggregate factory payload metadata-name parity

Notes:

- These items come from the `only-danmuku-zero` dogfood migration pass and should be reviewed before the next full real-project migration iteration.
- The dogfood decision is that all Query/Cmd/Cli `Request` and `Response` contracts must be regenerated from design input. If a contract cannot be expressed and regenerated, that is a design/generator capability defect, not a permanent hand-written migration exception. Hand edits are temporary unblocks only. This was verified and closed in `only-danmuku-zero` with 206/206 matched contracts and `compileKotlin` passing; see [full design-regenerated request contract parity design](specs/2026-05-01-cap4k-full-design-regenerated-request-contract-parity-design.md) and [implementation plan](plans/2026-05-01-cap4k-full-design-regenerated-request-contract-parity.md).
- Aggregate unique naming and control-field scope customization has been implemented. Query, query handler, and validator naming now share DB-source-driven unique metadata using `uk`, `uk_v_<fragment>`, `<table>_uk`, and `<table>_uk_v_<fragment>` with table-prefix normalization, while soft-delete and optimistic-lock version fields are filtered from generated unique business APIs. See [aggregate unique family naming contract design](specs/2026-05-03-cap4k-aggregate-unique-family-naming-contract-design.md).
- Default value projection should preserve stable expressions such as `null`, scalar literals, empty collection expressions, and enum/constant references through analysis/drawing-board to generate-ready design input.
- Import formatting cleanup is lower priority and should only become a generator bug if fresh generated output still contains unnecessary blank lines.
- Artifact-level conflict policy overrides are an experience optimization, not a current migration blocker. The current global `templates.conflictPolicy` is too coarse for real dogfood because users often need to overwrite generated contracts while preserving handler, validator, subscriber, controller, or behavior bodies. Prefer a direct artifact selector that is visible in `cap4kPlan` over introducing a separate family abstraction.
- Aggregate factory payload metadata-name parity is a low-priority edge cleanup. Generated nested factory payload classes may remain named `Payload`, but the `@Aggregate(name = ...)` metadata should preserve the old semantic name such as `CategoryPayload` instead of the nested class simple name. Current analysis primarily binds factory payloads through `aggregate` and `type`, so this is not a blocker, but it should be fixed before `@Aggregate` metadata is treated as a stable projection input.

The completed validator projection item was a combined implementation track over:

- analysis design projection normalization
- validator generation capability expansion
- the irAnalysis current-state analysis constraints

Recently completed mainline slices:

- contract-first query contract
- ddd-core nullability contract stabilization
- validator projection and generation normalization
- generated source output and entity behavior split
- full design-regenerated Query/Cmd/Cli contract parity
- UUID7 ID generator and default ID-generation policy

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

### Completed: Full design-regenerated Query/Cmd/Cli contract parity

Status:

- dogfood audit implemented in `only-danmuku-zero`
- audit plan corrected to use `cap4kPlan` `outputPath` values instead of assuming generated-source output
- audit accepts generated contract fields declared as either `val` or `var`
- 23 command empty-response drifts were fact-checked by isolating their design entries and regenerating only `designCommand`
- migrated handlers now preserve hand-written business logic while matching the generated `data object Response` empty-response contract
- verified with 206/206 Query/Cmd/Cli contracts matched and 0 drift
- verified with `.\gradlew.bat --no-configuration-cache --no-build-cache compileKotlin`
- closure committed in `only-danmuku-zero` as `f1e12f0 fix: align command empty responses with generated contract`

Reference:

- [full design-regenerated request contract parity design](specs/2026-05-01-cap4k-full-design-regenerated-request-contract-parity-design.md)
- [full design-regenerated request contract parity implementation plan](plans/2026-05-01-cap4k-full-design-regenerated-request-contract-parity.md)

Next action:

- no direct follow-up; continue with the remaining dogfood generator-quality backlog

Notes:

- contract parity is about generated structural ownership, not forcing output into `build/generated`
- user-owned handler logic may stay checked in, but nested `Request` / `Response` contract shape must match design and plan context
- future failures should be classified as bad input, generator capability defect, or migration logic drift rather than hand-patched permanently

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
- keep JPA and Querydsl repository load-plan semantics aligned unless a real backend limitation forces an explicit split

Notes:

- `FetchType.EAGER` is a global mapping policy, not a use-case loading policy
- use-case loading is explicit through `AggregateLoadPlan.MINIMAL` and `AggregateLoadPlan.WHOLE_AGGREGATE`; there is no `DEFAULT` enum value
- JPA implements whole-aggregate loading with explicit owned `@OneToMany` initialization below the repository boundary
- `persist=true` should continue to mean "register this loaded entity into UnitOfWork"; it should not secretly decide load depth or transaction scope
- `persist=false` remains meaningful inside commands because a handler may perform read-only lookups that should not be saved
- command-wide transaction expansion has test evidence, but its blast radius includes `JpaUnitOfWork.save()`, real commit timing, `afterTransaction`, domain events, integration events, and interceptors
- do not choose a persistence backend replacement just to solve this lazy-loading symptom

### Completed: UUID7 ID generator and default ID-generation policy

Status:

- implementation complete
- spec updated to implemented
- implementation plan written and executed

Reference:

- [UUID7 ID generator and default ID policy design](specs/2026-05-02-cap4k-uuid7-id-generator-default-policy-design.md)
- [UUID7 application-side ID policy implementation plan](plans/2026-05-02-cap4k-uuid7-application-side-id-policy.md)

Next action:

- no direct follow-up; continue from the remaining recommended order

Notes:

- this is a default-policy change, not only a utility-class addition
- UUID7 is the desired project default application-side ID strategy
- Snowflake remains explicit as `snowflake-long`, not the default
- application-side ID policy is configured through Gradle DSL, not DB comments
- field-level `@ApplicationSideId` is the runtime contract
- `JpaUnitOfWork` must handle preassigned-ID new entities and save-time owned-child ID assignment
- do not mix this with a repository backend replacement

### Completed: Aggregate unique family naming and control-field scope customization

Status:

- implementation complete
- spec updated to implemented
- implementation plan written and executed

Reference:

- [aggregate unique family naming contract design](specs/2026-05-03-cap4k-aggregate-unique-family-naming-contract-design.md)
- [aggregate unique family naming contract implementation plan](plans/2026-05-03-cap4k-aggregate-unique-family-naming-contract.md)

Next action:

- no direct follow-up; continue from the remaining recommended order

Notes:

- DB source snapshots preserve physical unique names instead of collapsing unique constraints to column lists
- canonical aggregate entities now carry named unique constraints
- aggregate unique query, query handler, and validator planners share one resolved unique-family selection
- `uk` / `<table>_uk` generate `Unique<Entity>` only when at least one business field remains after filtering
- `uk_v_<fragment>`, `<table>_uk_v_<fragment>`, and supported `uk_<fragment>` forms generate `Unique<Entity><Fragment>`
- soft-delete and optimistic-lock version fields are filtered from generated unique request props, handler `whereProps`, and validator field params
- `cap4kPlan` exposes unique physical name, normalized name, resolved suffix, selected business fields, and filtered control fields for review
- H2 raw JDBC `_INDEX_*` suffix handling is intentionally narrow and happens in planner normalization, not in DB source collection

### 1. Aggregate inverse-navigation fetch policy decision

Status:

- candidate mainline work
- spec written
- implementation plan not written

Reference:

- [aggregate inverse-navigation owner and fetch policy design](specs/2026-05-04-cap4k-aggregate-inverse-navigation-owner-and-fetch-policy-design.md)

Next action:

- write an implementation plan before execution
- implement one parent-child ownership contract for both `root-child` and `child-child`
- keep owned parent-child defaults aligned with the current `AggregateLoadPlan.MINIMAL` / `WHOLE_AGGREGATE` contract
- restore real generated-entity audit verification after the duplicated-owner mapping failure is removed

Notes:

- previous JPA work fixed mapping safety and load-plan semantics, but did not settle every inverse-navigation default
- `FetchType.EAGER` is a mapping policy, not a use-case loading policy
- `AggregateLoadPlan` is the approved use-case loading mechanism
- the decision must preserve generated-file consistency and avoid making performance-sensitive projects accidentally expensive
- dogfood evidence in `only-danmuku-zero` shows this track is not only about eager/lazy defaults: current parent-child inverse navigation can render both sides as FK owners (`@OneToMany + @JoinColumn` on parent together with `@ManyToOne + @JoinColumn` on child), which causes Hibernate duplicated-column mapping failures such as `file_post_id`
- the written spec for this item now settles owner/inverse-side generation rules together with fetch defaults, keeps owned parent-child bindings parent-owned, and treats “both sides own the same FK column” as fail-fast-invalid output

### 2. Database special-field declaration contract unification

Status:

- candidate persistence-configuration cleanup work
- spec written
- implementation plan not written
- explicitly deferred until the current ID policy DSL surface is re-evaluated

Reference:

- [database special-field declaration contract unification design](specs/2026-05-03-cap4k-database-special-field-declaration-contract-unification-design.md)

Next action:

- write an analysis/spec before implementation
- unify the declaration contract for database special fields: ID generation policy, soft-delete column, and optimistic-lock version column
- prefer Gradle DSL project-level defaults with DB column annotation entity/field-level overrides
- re-evaluate and likely reduce the current ID-generation DSL surface before adding more special-field configuration
- define how `cap4kPlan` should expose the effective resolved policy so users can review what will be generated
- define fail-fast validation for invalid combinations such as UUID strategies on non-UUID-compatible DB columns

Notes:

- current contracts are inconsistent: ID generation is primarily DSL-driven, soft delete is table-comment driven through `@SoftDeleteColumn=...`, and optimistic locking is column-comment driven through `@Version=true`
- this inconsistency increases user mental load and makes future generator behavior harder to explain
- the target contract is not "more knobs"; it is a smaller, more regular configuration model with clear defaults and explicit local override points
- ID strategy DSL options may currently expose too much internal capability; future spec work should prune the public surface before extending it
- DB annotations should remain close to physical schema intent, while DSL defaults should express project-wide policy
- do not mix this with repository backend replacement, aggregate relation policy changes, or frontend TypeScript generation

### 3. Read/write model association-scope separation analysis

Status:

- candidate architecture analysis work
- spec not written
- implementation plan not written
- explicitly deferred; do not implement before DDD/CQRS review

Next action:

- review DDD reference material and CQRS read-model best practices before deciding framework semantics
- analyze whether DB/source annotations should distinguish strong associations from weak/logical associations
- decide whether strong associations should feed aggregate write-model generation while weak associations feed read-model/query-model projection only
- define how this would interact with repository restrictions, aggregate child-entity access, generated queries, analysis output, and future TypeScript generation

Notes:

- write models should preserve aggregate consistency boundaries; owned child entities are loaded and modified through the aggregate root and repository, not exposed as independent repository targets
- read models optimize information completeness and may need broader logical associations than aggregate write models
- strong association means aggregate ownership / child-entity containment; weak association means logical read-side relation without write-model ownership
- this is not a request to immediately add new DB annotation behavior; it is a design question about whether the source model should carry separate association strengths for different consumers
- avoid using this to weaken aggregate invariants or to turn read-model convenience into write-model coupling

### 4. irAnalysis-backed frontend TypeScript generation analysis

Status:

- candidate support/mainline-adjacent work
- spec not written
- implementation plan not written

Next action:

- write an analysis/spec before implementation
- review `only-danmuku-admin-ui/src/api` as reference material, not as a confirmed best practice
- investigate frontend API client / type-generation conventions before committing to output shape
- decide whether generation should use irAnalysis output, drawing-board/design projection, or another canonical contract
- separate endpoint/API functions from strong TypeScript types unless analysis proves a different split is better

Notes:

- the user is not assuming the current frontend project shape is ideal
- this track may expose irAnalysis projection gaps or read/write model association-scope questions, but should not automatically trigger broad irAnalysis restructuring
- TypeScript output is a new consumer boundary, so stable input contracts matter more than copying the existing frontend folder style
- do not implement this as a one-off only-danmuku-admin-ui generator

### 5. Framework capability audit and pruning/optimization review

Status:

- candidate analysis work
- spec not written
- implementation plan not written

Next action:

- audit domain service, value object, aggregate wrapper generation, strong ID mechanism, and saga mechanism
- document what each capability currently does, who should use it, and whether it should be optimized, simplified, deprecated, or removed
- write follow-up specs only for capabilities that survive the audit

Notes:

- this is a prerequisite for credible public documentation
- do not optimize or delete capabilities before their current semantics and users are understood
- the result should clarify whether each capability belongs to core framework, optional extension, generated scaffold, or legacy/quarantine

### 6. Public README rewrite and project positioning

Status:

- candidate documentation work
- blocked on capability audit
- spec not written
- implementation plan not written

Next action:

- after the capability audit, rewrite README around what Cap4k does, who it is for, and what workflow it enables
- explain the fixed pipeline, bootstrap, code generation, analysis outputs, DDD runtime pieces, and real-project verification story in a user-facing way
- avoid documenting unstable or undecided capabilities as public promises

Notes:

- this is impact/communication work, not just README cleanup
- the goal is to make the project understandable to people beyond the current maintainer
- README should not require readers to know the chat history or roadmap terminology

### 7. DDD + cap4k + AI collaboration guide

Status:

- candidate documentation/process work
- should follow README positioning
- spec not written
- implementation plan not written

Next action:

- define a practical code and collaboration guide for AI-driven Cap4k projects
- cover project structure, DDD type placement, requirement iteration specs/plans, skeleton generation, testing, frontend integration, and use of generated analysis artifacts
- identify any missing topics during the guide design rather than forcing the first draft to be exhaustive

Notes:

- this should become project guidance for humans working with AI agents, not generic DDD theory
- it should encode how to use Cap4k safely: when to generate, when to hand-write, when to review, and when to stop
- it should not be written before the public capability story is clear

### 8. Built-in testing skeleton feasibility analysis

Status:

- candidate framework-support work
- inspired by Wow-style DDD tactical testing support
- spec not written
- implementation plan not written

Next action:

- write a feasibility analysis before implementation
- evaluate whether Cap4k should provide a test skeleton suite to reduce repetitive test setup
- decide how the testing skeleton can fit the structure-first and code-generation workflow
- identify whether this belongs in bootstrap slots, generated source, runtime test support libraries, or documentation templates

Notes:

- this should improve user testing discipline without hiding domain behavior behind excessive framework magic
- it must integrate with generated aggregates, repositories, commands, events, and analysis artifacts if it becomes a framework capability
- do not implement only a project-specific test helper unless it can become a stable Cap4k pattern

### 9. irAnalysis restructuring analysis

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

### 10. Unit-of-work and repository backend comparison

Status:

- candidate strategic work
- Spring Data JPA/JDBC feasibility lightly analyzed
- spec not written
- implementation plan not written
- deferred; no current evidence justifies replacing the JPA path

Next action:

- first execute or re-review the aggregate JPA runtime defect reproduction slice
- perform an analysis/PoC design only if the reproduction evidence shows the current JPA path cannot support the approved contracts safely
- keep this track at analysis/spec level until a backend comparison direction is approved
- do not write an implementation plan before the comparison spec exists and has been reviewed

Notes:

- current unit-of-work and repository implementations are JPA-based
- JPA usage has exposed enough practical problems to justify runtime reproduction first, not immediate replacement
- this should be treated as a persistence backend track only after in-place JPA repair has been evaluated
- Spring Data JPA does not remove the core preassigned-ID issue because `save(...)` still routes through new-state detection and then `EntityManager.persist(...)` or `merge(...)`; assigned-ID support typically requires `Persistable.isNew()` or custom `EntityInformation`
- Spring Data JDBC has a stronger aggregate-root repository model and avoids JPA lazy-loading, but existing aggregate saves can delete and recreate referenced child rows, which may conflict with cap4k's current dirty-tracking, child-ID stability, soft-delete, audit, optimistic-locking, and partial-update expectations
- Spring Data JDBC can remain a future PoC candidate, but it does not change the completed UUID7/application-side ID policy or justify replacing the current JPA path by default
- the first slice should compare candidates and define a bounded PoC, not replace the current JPA implementation
- likely evaluation dimensions include aggregate loading, dirty tracking, transactions, optimistic locking, relation handling, preassignable application-side ID generation, query ergonomics, Kotlin support, Spring integration, testing, and migration risk
- this has a large blast radius and should not be mixed into contract-first query, nullability contract stabilization, validator work, or irAnalysis work
- the safest route is probably a parallel backend implementation behind existing repository/unit-of-work contracts, then runtime verification against representative aggregate fixtures

### 11. cap4k-ddd-starter auto-configuration test fixture isolation

Status:

- candidate test-maintenance work
- spec not written
- implementation plan not written
- failure root causes have been characterized from `:cap4k-ddd-starter:test`

Next action:

- write a focused maintenance spec before fixing the tests
- isolate starter auto-configuration test applications so they do not package-scan unrelated test fixtures, runtime fixtures, or other nested test applications
- replace broad `@ComponentScan`, `@EntityScan`, and `@EnableJpaRepositories` over `com.only4.cap4k.ddd` with scoped fixture packages or `basePackageClasses`
- correct or remove stale Snowflake test properties, and either provide the `__worker_id` schema or explicitly disable Snowflake when it is not under test
- provide required event scan package configuration when domain-event auto-configuration is under test, or disable that auto-configuration in unrelated context-startup tests
- avoid enabling JPA repositories in contexts that intentionally exclude Hibernate JPA auto-configuration

Notes:

- full `:cap4k-ddd-starter:test` currently fails around old `@SpringBootTest` context fixtures, while focused UUID7/application-side ID tests and JPA runtime fixtures pass
- observed failures include repository bean definition collisions, missing Snowflake worker table, blank `eventScanPackage` reaching `ScanUtils.scanClass`, and missing `entityManagerFactory`
- this is a test fixture isolation debt, not evidence that the UUID7/application-side ID policy implementation is broken
- this item should not be mixed with application-side ID runtime semantics, repository backend replacement, or command transaction-boundary design
- prefer `ApplicationContextRunner` or narrowly scoped Spring Boot test apps where possible; avoid hiding scan pollution with global bean-definition overriding unless the test explicitly verifies overriding behavior

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

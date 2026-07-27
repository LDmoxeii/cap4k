# cap4k Database-Entrusted Fields Construction Design

**Status:** Design decisions approved; reconciled specification awaiting human review
**Date:** 2026-07-26
**Last reconciled:** 2026-07-27
**Baseline:** `master` at `c49e12f5`, after PR #138 soft-delete implementation and PR #139 documentation merge
**Ordering:** The soft-delete dependency is satisfied; implementation starts from this baseline

## Reader Contract

A new implementation agent with no access to prior chat history must be able to answer all of the following after reading this document:

1. Why database identity and optimistic-lock version fields must not appear in generated entity constructors or factory payloads.
2. Why their generated Kotlin properties are nullable even when the physical database columns are `NOT NULL`.
3. Which version property types are supported and which types must fail fast.
4. What `factory.create()` may guarantee before persistence and what `UnitOfWork.save()` must guarantee after persistence.
5. Why the aggregate factory still registers only the aggregate root with `PersistIntent.CREATE`.
6. Why owned-child parent-reference scalar properties are removed by default.
7. Why automatic child-to-parent entity navigation is removed rather than deprecated.
8. Why parent-side Schema joins continue to work without child parent-ID properties or database foreign-key constraints.
9. Why an owned child may not use the parent-reference column as its own primary key.
10. Which old generated shapes and canonical API types are intentionally deleted.
11. Which tests prove constructor, factory, persistence, nested owned-graph, Schema join, and fail-fast behavior.
12. Which files an implementation agent may change and which adjacent feature lines must remain untouched.
13. Why `READ_ONLY` governs the user write surface but does not by itself define constructor shape, assignment owner, or lifecycle timing.
14. Why this iteration changes only resolved database identity and version roles, not generic managed audit fields or only-engine lifecycle behavior.

This document is the complete contract for the iteration. Prior chat messages, old implementation plans, and historical inverse-navigation behavior are context only and must not override this specification.

## Current Evidence

### Baseline And Dependency

The current baseline is commit `c49e12f5`. PR #136 completed Strong ID create-time injection, PR #138 completed soft-delete IdStrategy support, and PR #139 merged the corresponding design and plan documents. This design must not reopen or rewrite either completed iteration.

The former soft-delete ordering dependency is now satisfied. Its merged planner and entity-template behavior is current evidence, not future work to be duplicated or replaced.

Relevant documents:

- `docs/superpowers/specs/2026-07-24-cap4k-strong-id-create-time-injection-design.md`
- `docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md`
- `docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md`

### Database Nullability Is Copied Into Canonical Fields

`DefaultCanonicalAssembler` currently creates each `FieldModel` with the database column nullability unchanged:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt:178-200`

The same `fields` list is currently supplied to both `SchemaModel` and `EntityModel`:

- `DefaultCanonicalAssembler.kt:205-219`

Therefore a physical `BIGINT NOT NULL` identity column currently becomes a non-null Kotlin field unless a later planner deliberately separates construction nullability from persistence nullability.

The database schema remains the source of physical storage truth. This design does not mutate `DbColumnSnapshot.nullable` or `FieldModel.nullable` merely to make construction convenient.

### ID And Version Policies Already Identify Database-Entrusted Fields

`AggregateIdPolicyResolver` maps `db_identity` to `AggregateIdPolicyKind.DATABASE_SIDE`:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdPolicyResolver.kt:25`

`AggregateSpecialFieldPolicyResolver` maps a database-side ID to `READ_ONLY`:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt:71-142`

The same resolver recognizes the version marker and registers it as a protected managed field:

- `AggregateSpecialFieldPolicyResolver.kt:99-106`
- `AggregateSpecialFieldPolicyResolver.kt:254-263`

The resolved write surface already excludes `READ_ONLY` and `SYSTEM_TRANSITION_ONLY` fields from create writes:

- `AggregateSpecialFieldPolicyResolver.kt:321-340`

The existing managed-write-surface design explicitly states that `writePolicy` is a user write-surface contract, not a complete ORM lifecycle contract:

- `docs/superpowers/specs/2026-05-06-cap4k-aggregate-managed-write-surface-and-factory-payload-metadata-design.md:157-169`

Therefore `READ_ONLY` alone is not permission to remove an arbitrary field from constructors, make it nullable, choose an initializer, or assign it during a persistence lifecycle event. The missing behavior in this iteration is role-specific projection for resolved database identity and version only.

### Resolved Version Policy Already Precedes Provider Projection

`AggregateSpecialFieldPolicyResolver` resolves version from either an explicit `DbManagedRole.VERSION` column or `versionDefaultColumn`, then publishes one `ResolvedMarkerPolicy`:

- `AggregateSpecialFieldPolicyResolver.kt:99-106`
- `AggregateSpecialFieldPolicyResolver.kt:188-233`

`AggregatePersistenceProviderInference` consumes that resolved policy and copies its field name into `AggregatePersistenceProviderControl.versionFieldName`:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt:9-35`

By contrast, `AggregatePersistenceFieldBehaviorInference` publishes `AggregatePersistenceFieldControl.version` only for a DB-explicit `DbManagedRole.VERSION` column:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt:24-41`

The entity planner therefore already prefers resolved version policy over the field-control fallback, and a committed test proves that a DSL-default resolved version works without an explicit version control:

- `EntityArtifactPlanner.kt:189-193`
- `AggregateArtifactPlannerTest.kt:2188-2248`

This evidence fixes the direction of authority: resolved version policy classifies; provider control projects and must remain consistent; explicit field control cannot be a required second classifier.

### Merged Soft-Delete Construction Behavior Is Role-Specific

The merged soft-delete iteration excludes `SYSTEM_TRANSITION_ONLY` fields from entity constructors and supplies a semantic active-sentinel initializer:

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt:194-235`
- `EntityArtifactPlanner.kt:328-331`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt:217-229`

That behavior is valid because the resolved deleted role owns a complete transition and initialization contract. It must remain unchanged, but it must not be generalized into a rule that all `READ_ONLY` or managed fields leave constructors.

### Generated Entity Constructors Still Leak Entrusted Fields

`EntityArtifactPlanner` currently selects constructor fields by removing a generated application-side own Strong ID and merged `SYSTEM_TRANSITION_ONLY` fields:

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt:328-331`

The effective rule is:

~~~kotlin
constructorFields = scalarFields.filterNot {
    it["generatedOwnId"] == true ||
        it["writePolicy"] == SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY.name
}
~~~

Database identity and version are `READ_ONLY`, so they still enter the constructor. The current database-identity planner test proves that both `id` and `title` remain constructor fields:

- `AggregateArtifactPlannerTest.kt:2343-2387`

`entity.kt.peb` initializes each ordinary scalar property from the constructor parameter:

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb:73-96`

That template currently has no distinct property-initialization shape for a database-entrusted nullable field.

### Factory Payload Filtering Is Ahead Of Constructor Mapping

`FactoryArtifactPlanner` already derives payload fields from the resolved create write surface, so database-side ID and version fields are not intended factory inputs.

However `planConstructorMapping` still compares the payload against all entity fields:

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt:106-171`

It has partial deferral logic for read-only managed fields:

- `FactoryArtifactPlanner.kt:200-213`

but the currently committed tests still prove that database identity and version factory construction remain unresolved:

- database identity expectation: `AggregateArtifactPlannerTest.kt:6084-6157`
- read-only version expectation: `AggregateArtifactPlannerTest.kt:6400-6495`

The current assertions require `constructorMappingResolved == false`.

When constructor mapping is unresolved, `factory.kt.peb` emits:

~~~kotlin
TODO("Implement aggregate construction")
~~~

Evidence:

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb:28-35`

This is the concrete factory blocker that this iteration must remove.

### Aggregate Factory Registration Is Root-Oriented

`DefaultAggregateFactorySupervisor.create()` registers only the returned aggregate root:

~~~kotlin
unitOfWork.persist(instance, PersistIntent.CREATE)
~~~

Evidence:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisor.kt:34-40`

PR #135 and PR #136 established the root-oriented UoW contract:

- pending UoW entries retain the outermost root;
- owned children are represented by the root's generated JPA graph;
- explicit child entries that are reachable from a pending root are reconciled into that root entry;
- JPA cascade persists the graph;
- Strong ID completion traverses the forward owned graph.

Existing tests prove root absorption of one-level and multi-level child CREATE entries:

- `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt:574-627`

This iteration does not add a public child factory and does not make independent child `persist(CREATE)` part of the aggregate-factory happy path.

### ParentRef Is Currently Both Structural Metadata And A Generated Scalar

The DB source marks a direct owned-parent column with `parentRef = true`. `OwnedParentBindingResolver` requires exactly one such column:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedParentBindingResolver.kt:21-45`

`AggregateRelationInference` uses that column to generate the parent-owned relation and JoinColumn metadata:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt:64-106`

At the same time, `DefaultCanonicalAssembler` copies the parentRef column into ordinary entity and schema fields:

- `DefaultCanonicalAssembler.kt:178-219`

The entity planner then renders it as a scalar property, and current renderer tests explicitly expect a read-only scalar mirror:

- `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt:4960-5018`

For a database-identity parent, the parent ID is null during aggregate construction. Requiring the child constructor to accept the parent ID makes a valid new graph impossible to construct without placeholder values or save-time backfill.

### Automatic Inverse Navigation Is Currently Unconditional

`AggregateInverseRelationInference` derives a child-to-parent read-only `MANY_TO_ONE` for each inferred parent-owned `ONE_TO_MANY`:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt:47-82`

The generated relation is fixed to:

- `LAZY`;
- `insertable = false`;
- `updatable = false`.

`DefaultCanonicalAssembler` invokes the inference unconditionally:

- `DefaultCanonicalAssembler.kt:283-287`

`EntityArtifactPlanner` consumes `model.aggregateInverseRelations`:

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt:42-47`

The canonical API currently exposes:

- `AggregateInverseRelationModel` at `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt:305-317`;
- `CanonicalModel.aggregateInverseRelations` at `PipelineModels.kt:589-602`.

That model describes only a JPA render shape. It does not describe explicit opt-in, in-memory parent binding, collection mutation consistency, factory graph construction, or Schema navigation policy.

### Schema Forward Joins Already Ignore Inverse Relations

`SchemaArtifactPlanner` explicitly calls relation planning with:

~~~kotlin
inverseRelations = emptyList()
~~~

Evidence:

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt:106-166`

Schema relation joins are generated only for forward owned `ONE_TO_MANY_JOIN_COLUMN` relations. The renderer calls:

~~~kotlin
root.join(persistencePathName, joinType)
~~~

Evidence:

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb:211-251`

Therefore removing child parentRef scalar fields and inverse relations does not remove parent-to-child Schema joins.

A physical database `FOREIGN KEY` constraint is not required for JPA Criteria join generation. The physical join column must exist and remain mapped on the parent relation; the database constraint only enforces referential integrity.

### Shared-Primary-Key Owned Child Is An Unclosed Edge

`OwnedRelationCardinalityInference` currently classifies a child as owned-one when its only primary-key column is the parentRef:

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInference.kt:7-14`

The repository contains a unit test for that inference:

- `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInferenceTest.kt:13-19`

No runtime evidence was found proving that a parent-owned unidirectional `ONE_TO_MANY_JOIN_COLUMN` can persist a database-identity shared-primary-key child without `@MapsId`, reverse ownership, or save-time ID propagation.

The same resolver already supports owned-one through a complete unique constraint containing parentRef and only neutral scope/deleted columns:

- `OwnedRelationCardinalityInference.kt:16-43`

This design removes the unclosed shared-primary-key case while retaining owned-one through uniqueness.

## Problem Statement

The pipeline can now classify application-side Strong IDs and inject them at create time, but database-entrusted fields still leak into generated construction APIs.

The immediate failures are:

1. A database identity field is physically non-null but has no value at `factory.create()` time.
2. A version field is physically non-null but is initialized by the persistence provider.
3. The entity constructor still requires those fields.
4. The factory payload correctly omits them, so constructor mapping becomes unresolved and the generated factory contains a TODO.
5. An owned child constructor can still require a parentRef scalar whose value is unavailable when the parent uses database identity.
6. Automatic inverse navigation silently exposes parent access even when the default model is intended to expose neither parent ID nor parent entity.
7. A shared-primary-key child cannot lose its parentRef scalar without losing its own JPA identity.

This iteration must establish one coherent construction and persistence contract:

> Database-entrusted values are absent from aggregate construction, become observable after persistence, and do not force parent identity into child domain APIs.

## Scope

This iteration includes:

1. Database-side identity fields generated as nullable provider-assigned entity properties.
2. Optimistic-lock version fields generated as nullable provider-assigned entity properties.
3. Removal of both field kinds from generated entity constructors and aggregate factory payload/constructor requirements.
4. Resolved aggregate factory bodies for supported database identity and version combinations.
5. Root-oriented UoW persistence proof for nested owned graphs with database identities and versions.
6. Default removal of owned-child parentRef scalar properties from Entity and child Schema surfaces.
7. Preservation of structural parentRef metadata and parent-side JPA/Schema relations.
8. Complete removal of automatic inverse-navigation canonical and planning infrastructure.
9. Fail-fast rejection when an owned child's parentRef is its own primary key.
10. Owned-one preservation through a unique parentRef constraint plus an independent child primary key.
11. Migration and verification guidance for generated-code consumers.

## Non-Goals

This iteration does not:

1. Add public child factories.
2. Add owned-child specs to the generated aggregate factory payload if the current generator does not already support them.
3. Expand `UnitOfWork.persist` into a general child persistence API.
4. Add explicit parent access modes such as `NONE`, `ID`, or `ENTITY` to authoring inputs.
5. Reintroduce child-to-parent entity navigation behind a feature flag.
6. Add parent-ID save-time backfill.
7. Add `@MapsId` or shared-primary-key one-to-one support.
8. Add database-generated timestamp versions.
9. Add `Byte`, decimal, string, UUID, or temporal version types.
10. Change application-side Strong ID create-time injection.
11. Change soft-delete sentinel, storage, SQL dialect, or constructor policy.
12. Remove the physical parentRef column.
13. Require a physical database foreign-key constraint.
14. Change parent-side Schema join APIs.
15. Clear provider-assigned identity/version values from in-memory entities after transaction rollback.
16. Preserve source or binary compatibility for canonical API types deleted by this design.
17. Define lifecycle, constructor, value-source, or assignment behavior for generic managed fields such as `createdAt`, `updatedAt`, `updatedBy`, or `createdBy`.
18. Add a managed-field lifecycle SPI, clock/operator provider, audit convention, or field-name-based fill rule.
19. Modify only-engine audit behavior or choose between UoW, JPA callback, and Hibernate event implementations for future generic managed fields.
20. Change `UnitOfWorkInterceptor`, add generic create/update field-fill events, or replace the remaining technical uses of Spring Data `EntityInformation.isNew()`.

## Terms

### Database-Entrusted Field

A field whose initial value is assigned by the database or JPA provider rather than by aggregate construction. In this iteration the term includes database identity and optimistic-lock version.

This is a closed term for this iteration. A generic field does not become database-entrusted merely because its write policy is `READ_ONLY`.

### Database Identity

An own entity ID declared with `@IdStrategy=db_identity` and mapped with JPA `GenerationType.IDENTITY`.

### Optimistic-Lock Version

A field marked as the aggregate version and mapped with JPA `@Version`. Its initial value and subsequent increments are controlled by the persistence provider.

### User Write-Surface Contract

The resolved `SpecialFieldWritePolicy` governing whether a user-facing create or update input may write a field. It does not, by itself, define constructor inclusion, Kotlin nullability, assignment owner, lifecycle timing, JPA mutability, or refresh behavior.

### Role Management Contract

The complete behavior attached to one resolved semantic role. For this iteration:

- database identity is assigned by the database during INSERT;
- optimistic-lock version is initialized and advanced by the JPA provider;
- both are absent from aggregate construction and represented as nullable entity properties initialized to `null`.

No generic managed-field role management contract is introduced.

### Construction Nullability

Whether a value is available while constructing a new in-memory aggregate. Database identity and version are construction-nullable.

### Persistence Nullability

Whether the physical database column accepts SQL `NULL`. Database identity and version columns may remain physically `NOT NULL` even though their Kotlin properties are nullable before persistence.

### Structural ParentRef

The DB-source fact identifying the child column used by the parent-owned JoinColumn relation. It remains part of `DbColumnSnapshot`, owned binding, relation inference, cardinality inference, and SQL/JPA mapping.

### ParentRef Scalar

A generated child Entity or Schema property such as `videoId` that mirrors the structural parentRef column. The default scalar projection is removed by this design.

### Automatic Inverse Navigation

The current unconditional derivation of a child property such as `videoFile.video` from the parent-owned relation. It is removed by this design.

### Default Parent Access

The post-iteration default in which an owned child exposes neither a parent-ID scalar nor a parent-entity navigation. Conceptually this is `NONE`, but this iteration does not add an authoring enum or configurable mode.

### Independent Child Primary Key

A single child primary-key column that is not the structural parentRef column. Every generated owned child must have one.

### Root Entry

The outermost aggregate root retained in the UoW pending-entry set. Owned descendants are expressed by the generated forward JPA graph rather than independent final entries.

### Save Postcondition

The observable state required after a successful `UnitOfWork.save()` and flush: all newly persisted database-identity entities and all versioned entities in the pending root graph have provider-assigned values.

## Design Decisions

1. Database identity and version are omitted from generated constructors.
2. Database identity and version are omitted from factory payloads and factory constructor requirements.
3. Generated database identity and version properties are nullable and initialized to `null`.
4. Their getters remain publicly readable; setters remain `internal`.
5. Physical DB nullability remains unchanged and authoritative.
6. Supported version Kotlin types are `Short?`, `Int?`, and `Long?`.
7. Unsupported version types fail during canonical/policy resolution before rendering.
8. `factory.create()` does not flush and returns database identity/version as null.
9. `UnitOfWork.save()` persists the root graph, flushes, and makes provider-assigned values observable.
10. The UoW retains only the root final entry; children remain graph members.
11. Rollback does not trigger framework cleanup of already assigned in-memory identity/version values.
12. Owned-child parentRef scalar properties are removed from Entity constructors, Entity properties, factory requirements, and child Schema fields.
13. Structural parentRef metadata and parent-owned JoinColumn mappings are retained.
14. Automatic inverse navigation is completely removed, including its canonical API types and planner branches.
15. No deprecated compatibility shell is retained.
16. Owned children whose primary key is the parentRef fail fast.
17. Owned-one remains supported through uniqueness on parentRef with an independent child primary key.
18. Parent-side Schema joins remain supported without child scalar FK properties or physical FK constraints.
19. Future parent `ID` or `ENTITY` exposure requires a separate approved design and must not reactivate the old inference directly.
20. `READ_ONLY` remains a user write-surface policy and is not a general constructor or lifecycle policy.
21. Identity/version construction behavior is selected by their resolved semantic roles, not by scanning for every `READ_ONLY` field.
22. Generic managed audit fields retain their current constructor, property, and lifecycle behavior in this iteration.
23. No managed-field lifecycle SPI, only-engine audit change, UoW interceptor change, or generic `isNew()` replacement is part of this iteration.

## Supported Type Matrix

### Database Identity

The iteration preserves the existing accepted database-identity types from `AggregateIdPolicyResolver.DatabaseIdentityTypes`:

| Effective Kotlin type | Generated property | Construction value | After successful save |
|---|---|---|---|
| `Short` | `Short?` | `null` | provider-assigned non-null |
| `Int` / `Integer` | `Int?` | `null` | provider-assigned non-null |
| `Long` | `Long?` | `null` | provider-assigned non-null |

Qualified Kotlin and Java wrapper aliases already accepted by `AggregateIdPolicyResolver` remain accepted:

- `kotlin.Short` / `java.lang.Short`;
- `kotlin.Int` / `Integer` / `java.lang.Integer`;
- `kotlin.Long` / `java.lang.Long`.

This iteration does not add identity support for `Byte`, decimal, string, UUID, temporal, composite, or custom value-object property types.

Application-side `uuid7` and `snowflake` IDs remain Strong ID value objects and remain create-time assigned. They do not become nullable provider-assigned primitives.

### Optimistic-Lock Version

Supported version types are:

| Physical intent | Effective Kotlin field type | Generated property |
|---|---|---|
| small integral version | `Short` | `Short?` |
| standard integral version | `Int` | `Int?` |
| large integral version | `Long` | `Long?` |

The resolver must accept qualified equivalents for these types and normalize them to the rendered Kotlin type already used by the pipeline.

The resolver must reject:

- `Byte`;
- `BigInteger`;
- `BigDecimal`;
- floating-point types;
- `String`;
- UUID;
- date/time types;
- custom value objects.

The fail-fast message must identify:

1. table;
2. version column;
3. resolved Kotlin type;
4. supported types.

Example:

~~~text
version field video.version has unsupported type String; supported version types are Short, Int, and Long
~~~

The framework adds no overflow guards, rollover warnings, or automatic widening. Schema authors own the capacity choice:

- `Short` has a small rollover range;
- `Int` has a larger rollover range;
- `Long` is the practical default for long-lived rows.

Provider behavior, transaction conflicts, and overflow consequences remain the application/database owner's responsibility.

## Semantic Classification Contract

This iteration must use the existing resolved special-field policy as the semantic source of truth.

### Classification And Projection Are Separate

Semantic classification answers which field owns an identity or version role. Provider and renderer controls project that already-resolved role into downstream concerns; they do not independently rediscover it.

| Concern | Source of truth | Required downstream projection |
|---|---|---|
| database identity role | resolved own-ID policy with `DATABASE_SIDE` kind | `IDENTITY` JPA generation plus entrusted construction shape |
| version role | `AggregateSpecialFieldResolvedPolicy.version` | `@Version`, provider control consistency, and entrusted construction shape |
| user write surface | resolved `SpecialFieldWritePolicy` | factory/update input filtering only |

`READ_ONLY` is an invariant of the accepted identity/version roles, not the classifier that grants their construction shape.

### Database Identity Classification

A field is a database-entrusted identity when all of the following hold:

1. it is the entity's own ID field;
2. the resolved ID policy kind is `DATABASE_SIDE`;
3. the resolved ID strategy projects to JPA `IDENTITY` generation.

The resolved ID write policy must be `READ_ONLY`; any other value is an internal-model inconsistency and must fail validation. It is not sufficient on its own to classify an ordinary field as identity.

The implementation must not infer database identity merely from:

- Kotlin type;
- numeric DB type;
- column name `id`;
- physical nullability;
- presence of `@GeneratedValue` text in a template context.

### Version Classification

A field is a database-entrusted version when all of the following hold:

1. the resolved version policy is enabled;
2. the resolved version field name matches the field.

The resolved version write policy must be `READ_ONLY`; any other value is an internal-model inconsistency and must fail validation. `READ_ONLY` is not sufficient to classify another managed field as version.

`AggregatePersistenceProviderControl.versionFieldName` is derived from the resolved version policy by `AggregatePersistenceProviderInference`. When the resolved version policy is enabled, the canonical provider projection must carry the same field name. A mismatch is an internal-model error, but provider control is not a second classification source.

`AggregatePersistenceFieldControl.version` is inferred directly only for a DB-explicit `DbManagedRole.VERSION`. It is absent for a version resolved through `versionDefaultColumn`, so it must not be required for aggregate construction classification. Any current fallback use outside the resolved aggregate path is not expanded by this design.

The implementation must not treat an ordinary field named `version` as provider-assigned when the version marker/default policy is disabled.

### ParentRef Classification

ParentRef is determined only from `DbColumnSnapshot.parentRef` and the resolved owned binding.

After this iteration:

- `DbColumnSnapshot.parentRef` remains;
- `OwnedParentBinding.parentRefColumn` remains;
- `AggregateRelationModel.parentRefColumn` remains;
- the physical column remains in table and unique-constraint metadata;
- the parentRef column does not become an `EntityModel.fields` or `SchemaModel.fields` member;
- `FieldModel.parentRef` is no longer a supported canonical domain-field concept and must be removed with its planner/test plumbing.

This keeps structural persistence metadata separate from domain scalar fields.

## Canonical Assembly Contract

### Field Projection

Canonical assembly must separate physical table columns from generated domain fields.

The conceptual flow is:

~~~text
DbTableSnapshot.columns
    |
    +-- physical identity/version/business columns
    |       -> EntityModel.fields / SchemaModel.fields
    |
    +-- structural parentRef column
            -> OwnedParentBinding
            -> AggregateRelationModel.joinColumn
            -> AggregateRelationModel.parentRefColumn
            -> not an EntityModel field
            -> not a SchemaModel field
~~~

The implementation may construct temporary local field descriptors while assembling the model, but the published canonical result must satisfy:

1. every Entity ID field remains present in `EntityModel.fields`;
2. every ordinary business field remains present;
3. database identity and version remain present as entity/schema fields because they are observable state;
4. structural parentRef columns are absent from entity/schema fields;
5. physical unique-constraint column names remain unchanged;
6. aggregate relation metadata retains the JoinColumn name.

### Shared-Primary-Key Rejection

Before filtering parentRef from domain fields, canonical assembly must reject an owned child whose single primary key is the parentRef column.

Required error shape:

~~~text
owned child video_file cannot use parent reference column video_id as its primary key; declare an independent child primary key
~~~

The check must occur before:

- EntityModel construction;
- Strong ID planning;
- factory planning;
- entity rendering;
- unique artifact planning.

Composite primary keys already remain unsupported by the aggregate generator and are outside this design.

### Owned-One Through Uniqueness

Owned-one remains valid when the child has an independent primary key and a complete unconditional unique constraint proves parent uniqueness.

Valid example:

~~~sql
create table video_file (
    id bigint generated by default as identity primary key,
    video_id bigint not null,
    constraint uk_video_file_video unique (video_id)
);
~~~

Soft-delete/scope variants remain valid when the existing cardinality resolver recognizes only neutral non-null scope/deleted columns in addition to parentRef.

The parentRef unique constraint remains physical relation/cardinality evidence even though parentRef is absent from generated child fields.

## Generated Entity Contract

### Database Identity Shape

For:

~~~sql
id bigint generated by default as identity primary key
~~~

the generated entity shape must be equivalent to:

~~~kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
var id: Long? = null
    internal set
~~~

Requirements:

1. `id` is absent from the primary constructor.
2. `id` is initialized to `null` in the property declaration.
3. `id` is publicly readable.
4. `id` is not publicly settable.
5. `@Id` and `@GeneratedValue(IDENTITY)` remain.
6. The physical DB column's `NOT NULL` truth is not rewritten to nullable.
7. The property type after persistence remains the same nullable Kotlin type; the framework does not expose a second non-null getter.

The generator must not emit placeholder values such as `0`, `-1`, or a random application-side ID for database identity.

### Version Shape

For:

~~~sql
version bigint not null
~~~

marked as the version field, the generated entity shape must be equivalent to:

~~~kotlin
@Version
var version: Long? = null
    internal set
~~~

The same shape applies to `Short?` and `Int?`.

Requirements:

1. version is absent from the constructor;
2. version is initialized to `null`;
3. version is publicly readable;
4. version has an `internal` setter;
5. `@Version` remains;
6. no generated code assumes whether the provider starts at `0` or `1`;
7. tests assert non-null after successful persistence, not an exact initial numeric value unless a provider-specific fixture explicitly owns that assertion.

### Ordinary Fields

Ordinary business fields continue to use existing constructor and default-projection rules.

Application-side Strong IDs continue to use the approved Phase 4 create-time injection shape.

Merged soft-delete constructor behavior remains exactly as implemented by the preceding iteration.

Generic managed fields, including conventional audit names such as `createdAt`, `updatedAt`, and `updatedBy`, retain their current constructor, nullability, initializer, and lifecycle behavior. This iteration must not omit or initialize such a field merely because its resolved write policy is `READ_ONLY`.

### ParentRef And Inverse Shape

Given:

~~~text
Video -> VideoFile -> VideoFileVariant
~~~

the generated child must not contain:

~~~kotlin
var videoId: Long?
lateinit var video: Video
~~~

The parent still contains its forward owned relation:

~~~kotlin
@OneToMany(
    fetch = FetchType.LAZY,
    cascade = [
        CascadeType.PERSIST,
        CascadeType.MERGE,
        CascadeType.REMOVE,
    ],
    orphanRemoval = true,
)
@JoinColumn(name = "video_id", nullable = false)
private var _videoFiles: MutableList<VideoFile> = mutableListOf()
~~~

The exact backing name follows the existing relation planner. This design does not rename collection facades.

## Entity Planner And Template Contract

The entity planner must distinguish at least these render roles:

| Role | Constructor | Property | Initial value |
|---|---:|---:|---|
| ordinary business field | existing rule | yes | constructor/default |
| application-side own Strong ID | no | yes | Phase 4 assignment shape |
| database identity | no | yes | `null` |
| version | no | yes | `null` |
| merged soft-delete role | no | yes | approved active sentinel |
| generic managed `READ_ONLY` field | existing rule | yes | existing rule |
| structural parentRef | no | no | none |
| automatic inverse navigation | no | no | none |

The renderer must stay mechanical. It must not rediscover policy by field name or DB type.

Recommended planner context is explicit booleans or a bounded render role, for example:

~~~text
providerAssignedIdentity
providerAssignedVersion
constructorIncluded
propertyInitializer
~~~

The exact context key names are an implementation-plan decision, but the template must receive enough explicit truth to avoid nested name/type heuristics.

The implementation must remove the current broad constructor rule:

~~~kotlin
scalarFields.filterNot { it["generatedOwnId"] == true }
~~~

and replace it with explicit role-based constructor selection. The selection may consume the resolved identity/version roles and the already-implemented soft-delete semantic context; it must not use `writePolicy == READ_ONLY` as a general exclusion predicate.

## Factory Contract

### Payload

Factory payloads contain only construction inputs.

They must exclude:

- database identity;
- version;
- structural parentRef;
- automatic inverse navigation;
- preceding-iteration system-transition fields excluded by the resolved write surface.

This design does not add owned-child spec payload support. If a future generator iteration adds child specs, the same entrusted-field rules apply recursively.

### Constructor Mapping

Factory constructor mapping must compare payload fields only with actual constructor requirements.

It must not treat the following as missing required constructor fields:

- database identity;
- version;
- parentRef;
- generated application-side own ID;
- fields with valid existing constructor defaults;
- the merged soft-delete system-transition field with its approved semantic initializer.

A generic managed `READ_ONLY` field is not automatically deferrable. If such a field is excluded from the payload but remains a required constructor input under current behavior, its factory mapping may remain unresolved; solving that known generic managed-field gap belongs to the future managed-field lifecycle SPI iteration.

For every supported database-identity/version combination with no independent out-of-scope constructor blocker, `constructorMappingResolved` must be `true`. A generic managed `READ_ONLY` field that remains a required constructor input is an independent blocker and is not solved by this iteration.

The generated factory must contain a real aggregate constructor call and must not contain:

~~~kotlin
TODO("Implement aggregate construction")
~~~

### Create-Time State

Immediately after:

~~~kotlin
val aggregate = factory.create(payload)
~~~

the expected state is:

~~~text
aggregate.id == null              for database identity
aggregate.version == null         when versioned
ownedChild.id == null             for child database identity
ownedChild.version == null        when child is versioned
~~~

Application-side Strong IDs remain non-null at the same point.

The factory must not:

- flush;
- call `EntityManager`;
- synthesize database IDs;
- initialize version numerically;
- backfill parent IDs;
- create child UoW entries.

`DefaultAggregateFactorySupervisor` continues to register only the returned root with `PersistIntent.CREATE`.

## Unit Of Work And JPA Runtime Contract

### Root-Oriented Persistence

The happy path remains:

~~~text
factory.create(payload)
    -> construct root and any generator-supported owned graph
    -> register root CREATE
    -> application transaction calls save
    -> UoW persists root
    -> JPA cascades PERSIST through forward owned relations
    -> flush
    -> provider-assigned IDs and versions become observable
~~~

The final pending entry set must contain the outermost root, not one entry per descendant.

No new root/child marker is added. The existing root entry plus forward graph remains sufficient.

### Save Postcondition

After successful `UnitOfWork.save()`:

1. each newly persisted database-identity root has a non-null ID;
2. each newly persisted database-identity owned child reachable through the root graph has a non-null ID;
3. each newly persisted versioned root has a non-null version;
4. each newly persisted versioned owned child has a non-null version;
5. no owned child was independently passed to `EntityManager.persist` when it was absorbed into the root graph;
6. the graph remains navigable from root to descendants.

The contract is observational. It does not require a new generic ID-completion registry for database identity because the JPA provider mutates the mapped properties directly.

### Flush And Refresh

The existing UoW flush/refresh contract remains unless implementation evidence proves a narrow adjustment is required.

This iteration must not reintroduce `CascadeType.REFRESH` through `CascadeType.ALL`. Forward owned relations retain explicit:

- `PERSIST`;
- `MERGE`;
- `REMOVE`.

Removing inverse navigation further reduces refresh cycles but is not permission to broaden cascade types.

### PersistIntent And `isNew()` Boundary

The UoW entry kind derived from `PersistIntent.CREATE` or `PersistIntent.EXISTING` remains responsible for choosing the create or existing persistence path. Application-side IDs being non-null does not change that routing.

The current JPA UoW still uses Spring Data `EntityInformation.isNew()` for bounded technical purposes such as refresh selection, identity observation, and rejecting an unidentified `EXISTING` registration. This iteration neither treats that helper as a generic lifecycle truth nor replaces those existing uses.

Identity/version construction support must not add audit-field filling, modify `UnitOfWorkInterceptor`, or infer generic create/update lifecycle events from `isNew()`.

### Rollback

If the provider assigns identity/version values and the transaction later rolls back, cap4k does not clear those in-memory properties.

Reasons:

1. JPA provider behavior may assign identity before final transaction completion;
2. clearing would require provider-specific lifecycle tracking;
3. the entity/persistence-context lifecycle belongs to transaction boundaries;
4. retrying a rolled-back aggregate instance is not guaranteed by this iteration.

Applications must discard or reload rolled-back aggregate instances according to their transaction policy.

## Parent-Side Schema Join Contract

### Forward Join Remains

For:

~~~text
Video -> VideoFile -> VideoFileVariant
~~~

the generated Schema path remains:

~~~kotlin
VideoSchema
    .joinVideoFiles()
    .joinVariants()
~~~

Each join follows a forward mapped relation field. It does not require:

- `VideoFile.videoId`;
- `VideoFile.video`;
- `VideoFileSchema.videoId`;
- `VideoFileSchema.joinVideo()`.

### No Physical Foreign-Key Constraint Required

This remains a valid query mapping:

~~~sql
create table video_file (
    id bigint generated by default as identity primary key,
    video_id bigint not null
);
~~~

even when no SQL `FOREIGN KEY` constraint is declared.

JPA uses:

~~~kotlin
@JoinColumn(name = "video_id")
~~~

to generate the SQL join. Without a physical FK constraint, the database does not prevent orphan rows; that is a data-integrity difference, not a Criteria mapping requirement.

The physical `video_id` column and structural parentRef metadata are mandatory. A missing or ambiguous parentRef remains a fail-fast source error.

### Child Schema Surface

The child Schema must not expose a field that is absent from the mapped Entity.

Therefore `VideoFileSchema.videoId` is removed with `VideoFile.videoId`.

Keeping it would generate `root.get("videoId")` against a non-existent mapped attribute and create a compile-success/runtime-failure API.

### Schema Nullability Remains Physical

Entity construction nullability does not change persisted query nullability.

For a physical `BIGINT NOT NULL` database identity:

~~~text
Entity property: Long?
Schema field:    Field<Long>
~~~

For a physical `BIGINT NOT NULL` version:

~~~text
Entity property: Long?
Schema field:    Field<Long>
~~~

The Entity property is nullable because a transient instance has not yet been assigned by the provider. The Schema field describes persisted rows where the physical column remains non-null.

The implementation must not mutate `FieldModel.nullable` to produce the Entity's construction-nullable property. Entity rendering needs a separate provider-assigned/property-nullability decision.

## Automatic Inverse Navigation Deletion

### Production Types And Paths To Remove

The implementation must delete:

1. `AggregateInverseRelationModel`;
2. `CanonicalModel.aggregateInverseRelations`;
3. `AggregateInverseRelationInference`;
4. inverse-relation parameters and branches in `AggregateRelationPlanning`;
5. Entity planner consumption of inverse relations;
6. Projection planner consumption of inverse relations;
7. inverse-only import/render context;
8. tests whose expected behavior is unconditional child-to-parent navigation.

The parent-owned `AggregateRelationModel` remains.

### Why No Deprecation Shell

`@Deprecated` is rejected because:

1. there are no external consumers requiring a migration window;
2. a warning-level API would remain callable and could still reactivate the old behavior;
3. an error/hidden API would preserve dead structure only for compatibility that is not required;
4. the old model expresses JPA rendering, not the future parent-access semantics;
5. dormant canonical properties would mislead a future agent into restoring an incomplete feature.

### Historical Reference

The old behavior remains discoverable through:

- commit `05242a86`, `feat: derive aggregate inverse relations`;
- `docs/superpowers/specs/2026-04-20-cap4k-aggregate-inverse-relation-read-only-parity-design.md`;
- `docs/superpowers/plans/2026-04-20-cap4k-aggregate-inverse-relation-read-only-parity.md`;
- `docs/superpowers/specs/2026-05-04-cap4k-aggregate-inverse-navigation-owner-and-fetch-policy-design.md`.

Future implementation must not restore the old inference directly.

### Future Reintroduction Gate

Child-to-parent entity navigation may return only after a separate approved design specifies:

1. explicit opt-in authoring semantics;
2. default `NONE` behavior;
3. creation-time in-memory parent binding;
4. replace/remove consistency;
5. factory graph construction;
6. rehydration semantics;
7. Schema navigation;
8. JPA ownership and read-only mapping;
9. nested graph runtime verification.

## Unique Artifact Boundary

The current aggregate unique Query/Handler/Validator family is optional and defaults to disabled:

- `Cap4kExtension.kt:371` sets `unique` convention to `false`;
- `AggregateArtifactPlanner.kt:34-37` plans unique artifacts only when `artifact.unique == true`.

The user intends to move this family out of the aggregate generator mainline into an addon. This iteration does not modify:

- `AggregateUniqueConstraintPlanning`;
- `UniqueQueryArtifactPlanner`;
- `UniqueQueryHandlerArtifactPlanner`;
- `UniqueValidatorArtifactPlanner`;
- their templates.

Until the addon redesign:

1. `artifact.unique=false` is the supported default for aggregates whose unique constraints contain parentRef;
2. enabling the old unique family with such a constraint is outside this iteration's guarantee;
3. the implementation must not silently remove parentRef from a compound constraint and generate a global uniqueness check;
4. database unique constraints continue to enforce storage integrity;
5. owned-one cardinality inference continues to consume the physical constraint metadata.

The approved future addon behavior is:

> A unique constraint containing structural parentRef is skipped as a whole by automatic unique artifacts unless a future parent-scoped uniqueness design explicitly supports it.

Examples:

| Constraint | Current relation use | Future addon automatic unique artifacts |
|---|---|---|
| `unique(video_id)` | owned-one proof | skip |
| `unique(video_id, deleted)` | owned-one proof when valid | skip |
| `unique(video_id, variant_code)` | DB parent-scoped uniqueness | skip |
| `unique(external_code)` | ordinary business uniqueness | generate |

This deferred addon decision must not expand the present implementation plan.

## Complete Generator Flow

### Database Source

For each supported table:

1. DB source captures physical columns, primary key, nullability, IdStrategy, managed roles, parent table, parentRef, and unique constraints.
2. ParentRef remains a physical column fact.
3. Database identity remains declared through `DbIdStrategy.DB_IDENTITY`.
4. Version remains declared through the explicit/default managed version policy.

No new DB annotation is introduced by this design.

### Canonical Assembly

Canonical assembly performs this order:

~~~text
validate supported single-column primary key
    -> resolve direct owned-parent binding
    -> reject parentRef-as-child-primary-key
    -> infer forward owned relations and cardinality
    -> build domain field projections without parentRef
    -> resolve ID/version policies
    -> publish EntityModel, SchemaModel, relations, provider controls
~~~

Automatic inverse relation inference is absent.

The order matters. Removing parentRef from domain fields must not erase the evidence needed for binding/cardinality inference.

### Policy Resolution

For each entity:

1. ID policy resolves application-side versus database-side ownership.
2. Resolved version policy is the sole version-role classifier; it resolves enabled/disabled, field identity, source, and write policy, then validates the integral type.
3. Provider control projects the resolved version field name and must remain consistent with it; it does not reclassify the field.
4. Write surface excludes database identity and version from create/update inputs as already required.
5. ParentRef does not enter the write surface because it is no longer a domain field.
6. Generic managed `READ_ONLY` fields gain no identity/version construction semantics.

### Artifact Planning

Entity planning:

- keeps observable identity/version properties;
- excludes them from constructor fields;
- preserves merged soft-delete behavior and existing generic managed-field behavior;
- excludes parentRef entirely;
- receives only forward relations.

Schema planning:

- keeps identity/version fields;
- excludes parentRef scalar;
- plans forward owned joins;
- receives no inverse relations.

Factory planning:

- excludes identity/version/parentRef from payload and constructor requirements;
- resolves a real constructor call for supported identity/version combinations;
- does not claim to solve unresolved generic managed-field constructor mappings.

Projection planning:

- receives only forward relations;
- must not retain inverse canonical plumbing.

Unique artifact planning:

- remains unchanged and optional in this iteration;
- is outside the supported parentRef-constraint path until moved to an addon.

### Rendering

Templates render explicit planner truth.

They must not:

- inspect raw DB type names to decide constructor participation;
- infer version by field name;
- infer parentRef by column suffix;
- infer inverse relations;
- add a fallback TODO for supported combinations.

### Runtime

At runtime:

1. factory creates the in-memory aggregate;
2. database identity/version fields are null;
3. root is registered CREATE;
4. UoW persists root;
5. provider inserts root and descendants through cascade;
6. provider assigns identity/version fields;
7. flush completes;
8. application observes non-null assigned values.

## Complete Runtime Flows

### Root With Database Identity And Version

Schema:

~~~sql
create table video (
    id bigint generated by default as identity primary key,
    version bigint not null,
    title varchar(255) not null
);
~~~

Generated shape:

~~~kotlin
@Entity
class Video internal constructor(
    title: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        internal set

    @Version
    var version: Long? = null
        internal set

    var title: String = title
        internal set
}
~~~

Factory flow:

~~~kotlin
val video = videoFactory.create(
    VideoFactory.Payload(
        title = "example",
    )
)

check(video.id == null)
check(video.version == null)
~~~

After save:

~~~kotlin
unitOfWork.save()

check(video.id != null)
check(video.version != null)
~~~

The factory payload must not contain `id` or `version`.

### Three-Level Database-Identity Owned Graph

Physical model:

~~~text
Video
    id identity
    version

VideoFile
    id identity
    video_id parentRef
    version

VideoFileVariant
    id identity
    video_file_id parentRef
    version
~~~

Construction state:

~~~text
Video.id = null
Video.version = null
VideoFile.id = null
VideoFile.version = null
VideoFileVariant.id = null
VideoFileVariant.version = null
~~~

Generated child APIs expose neither:

~~~text
VideoFile.videoId
VideoFile.video
VideoFileVariant.videoFileId
VideoFileVariant.videoFile
~~~

Only the root is registered:

~~~kotlin
unitOfWork.persist(video, PersistIntent.CREATE)
~~~

After save, every own database identity and version is non-null.

The required runtime proof may construct the fixture graph through test-only constructors or a handwritten test factory. It must not expand the production generated factory into owned-child spec support.

### Existing Aggregate Adds New Database-Identity Child

An existing managed root adds a new child through its generated owned collection facade:

~~~kotlin
val child = VideoFile(/* business constructor fields only */)
video.files.add(child)
~~~

Before save:

~~~text
child.id == null
child.version == null
~~~

The current owned graph participates in root persistence/flush semantics. No separate public child factory is introduced.

After the transaction's supported save path completes, the new child ID/version are non-null.

This flow must be verified against current `EXISTING` root semantics rather than by inventing a new child `persist(CREATE)` contract.

### Application-Side Strong ID Regression Guard

For `uuid7` or `snowflake`:

~~~text
factory.create()
    -> root own Strong ID non-null
    -> owned child own Strong ID non-null when constructed through supported lifecycle
~~~

This iteration must not make application-side Strong ID properties nullable or provider-assigned.

### Owned-One With Independent ID

Valid:

~~~sql
create table video_file (
    id bigint generated by default as identity primary key,
    video_id bigint not null,
    constraint uk_video_file_video unique (video_id)
);
~~~

Generated domain shape:

~~~text
Video.file: VideoFile?
VideoFile.id: Long?
no VideoFile.videoId
no VideoFile.video
~~~

The unique constraint proves owned-one but does not become a parent scalar.

### Schema Join Without FK Constraint

Physical schema:

~~~sql
create table video (
    id bigint generated by default as identity primary key
);

create table video_file (
    id bigint generated by default as identity primary key,
    video_id bigint not null
);
~~~

No `FOREIGN KEY` constraint is declared.

Required behavior:

1. persistence writes `video_file.video_id` through the parent JoinColumn;
2. `VideoSchema.joinVideoFiles()` compiles;
3. the generated Criteria query returns the associated rows;
4. child Schema exposes no `videoId`.

## Invalid And Boundary Examples

### Invalid: Constructor Requires Database Identity

Forbidden:

~~~kotlin
class Video internal constructor(
    id: Long,
    title: String,
)
~~~

Reason: `id` is unavailable during construction.

### Invalid: Placeholder Identity

Forbidden:

~~~kotlin
Video(
    id = 0L,
    title = payload.title,
)
~~~

Reason: placeholder identity creates false persisted-state semantics and can interfere with provider newness detection.

### Invalid: Constructor Requires Version

Forbidden:

~~~kotlin
Video(
    version = 0L,
    title = payload.title,
)
~~~

Reason: initial version is provider-controlled.

### Invalid: Generic `READ_ONLY` Becomes Entrusted

Forbidden inference:

~~~kotlin
val constructorIncluded = field.writePolicy != READ_ONLY
val propertyInitializer = if (field.writePolicy == READ_ONLY) null else field.name
~~~

Reason: `READ_ONLY` only governs the user write surface. A generic audit field has no approved assignment owner, lifecycle phase, construction nullability, or initializer in this iteration.

### Invalid: Version Requires Two Independent Classifiers

Forbidden inference:

~~~kotlin
val providerAssignedVersion =
    resolvedPolicy.version.fieldName == field.name &&
        persistenceFieldControl.version == true
~~~

Reason: `AggregatePersistenceFieldControl.version` exists only for a DB-explicit marker and is absent for a version resolved through `versionDefaultColumn`. Resolved version policy is authoritative; provider control is a derived consistency projection.

### Invalid: ParentRef Scalar Survives

Forbidden:

~~~kotlin
class VideoFile internal constructor(
    videoId: Long,
    name: String,
)
~~~

Reason: database-identity parent ID is unavailable and default parent access is NONE.

### Invalid: Automatic Parent Entity Survives

Forbidden:

~~~kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
    name = "video_id",
    insertable = false,
    updatable = false,
)
lateinit var video: Video
~~~

Reason: this is implicit `ENTITY` access without binding semantics or explicit opt-in.

### Invalid: Shared Primary Key

Rejected schema:

~~~sql
create table video_file (
    video_id bigint primary key
);
~~~

when `video_id` is parentRef.

Required result: canonical assembly fails before artifact planning.

### Invalid: Globalized Parent-Scoped Uniqueness

Given:

~~~sql
unique(video_id, variant_code)
~~~

forbidden future behavior:

~~~text
remove video_id
generate unique(variant_code)
~~~

Reason: the meaning changes from per-parent uniqueness to global uniqueness.

### Boundary: No Version Field

When version policy is disabled:

- no `@Version` property is generated;
- an ordinary business column named differently behaves normally;
- an unmarked column named `version` follows existing default-column configuration, not a hard-coded name rule.

### Boundary: Nullable Business Field

Ordinary nullable business fields retain existing constructor/default behavior. This design does not make all nullable fields provider-assigned.

## Error Contract

### Unsupported Version Type

Fail during policy/canonical resolution.

Required message data:

- table/entity;
- field/column;
- resolved type;
- supported types.

### Shared Primary-Key ParentRef

Fail during owned binding/canonical assembly before domain field projection.

Required message:

~~~text
owned child <table> cannot use parent reference column <column> as its primary key; declare an independent child primary key
~~~

### Missing Or Ambiguous ParentRef

Existing errors remain:

~~~text
missing parent reference column for table: <table>
ambiguous parent reference columns for table <table>: <columns>
~~~

### Supported Factory Still Unresolved

This is an implementation defect, not a supported generated outcome.

For this error contract, a supported factory has no missing required constructor field other than roles explicitly handled by the approved Strong ID, soft-delete, identity/version, and parentRef decisions. An unrelated generic managed `READ_ONLY` constructor field keeps its existing out-of-scope behavior and does not make that broader combination supported here.

Tests must fail if a supported database identity/version factory has:

~~~text
constructorMappingResolved == false
~~~

or renders:

~~~text
TODO("Implement aggregate construction")
~~~

### Resolved Version Projection Mismatch

If resolved version policy is enabled but the canonical provider control names a different version field, fail as an internal canonical-model inconsistency before rendering. Do not choose one value heuristically and do not consult `AggregatePersistenceFieldControl.version` as a tie-breaker.

### Unique Addon Boundary

Before unique extraction is redesigned, parentRef-containing unique artifacts are not part of this iteration's guarantee.

The present implementation must not add a fallback that drops parentRef and proceeds.

## Migration And Breaking Changes

### Generated Entity Constructors

Before:

~~~kotlin
Video(
    id = 0L,
    version = 0L,
    title = title,
)
~~~

After:

~~~kotlin
Video(
    title = title,
)
~~~

Handwritten generated-code consumers must remove identity/version arguments.

### Generated Child Constructors

Before:

~~~kotlin
VideoFile(
    id = 0L,
    videoId = video.id,
    version = 0L,
    name = name,
)
~~~

After:

~~~kotlin
VideoFile(
    name = name,
)
~~~

### Child Scalar Access

Calls such as:

~~~kotlin
child.videoId
schema.videoId
~~~

no longer compile and must be removed or rewritten from the aggregate-root query path.

### Child Entity Navigation

Calls such as:

~~~kotlin
child.video
child.videoFile
~~~

no longer compile.

No compatibility accessor is generated.

### Canonical API

The following APIs are deleted:

- `AggregateInverseRelationModel`;
- `CanonicalModel.aggregateInverseRelations`;
- `FieldModel.parentRef` as a domain-field concept.

In-repository tests/addons constructing these models must migrate immediately.

There is no deprecation cycle or binary compatibility promise.

### Unique Artifacts

The unique family remains optional/default-off and unchanged in this iteration.

Consumers that explicitly enabled `artifact.unique=true` for constraints containing parentRef must disable it until the addon redesign or accept that the combination is outside the supported contract.

### Generic Managed Fields

No migration is expected for ordinary managed audit/system fields. Their constructor inclusion, Kotlin nullability, initializer, and runtime fill behavior remain unchanged.

If regeneration changes `createdAt`, `updatedAt`, `updatedBy`, or another generic field solely because its write policy is `READ_ONLY`, that change is an out-of-scope regression and must be reverted.

### Regeneration

Generated outputs must be regenerated rather than manually edited.

Expected source-breaking diffs include:

- constructor parameter removal;
- nullable identity/version property types;
- parentRef property removal;
- inverse navigation removal;
- canonical API compilation fixes in tests/addons.

## Verification Strategy

### 1. API And Canonical Model Tests

Add/update tests proving:

1. `AggregateInverseRelationModel` is absent.
2. `CanonicalModel` has no `aggregateInverseRelations` property.
3. `FieldModel` no longer carries parentRef domain-field truth.
4. `DbColumnSnapshot.parentRef` remains.
5. `AggregateRelationModel.parentRefColumn` remains.
6. canonical Entity/Schema fields exclude parentRef.
7. physical unique constraints retain parentRef column names.

Primary areas:

- `cap4k-plugin-pipeline-api/src/test/kotlin`
- `cap4k-plugin-pipeline-core/src/test/kotlin`

### 2. Policy And Type Tests

Add tests for:

- database identity `Short`, `Int`, and `Long` accepted;
- version `Short`, `Int`, and `Long` accepted;
- qualified type aliases accepted where the pipeline already accepts them;
- unsupported version types rejected with required error data;
- unmarked ordinary fields are not treated as version;
- DB-explicit and `versionDefaultColumn` sources both resolve the same authoritative version role;
- enabled resolved version policy projects the same field name into `AggregatePersistenceProviderControl.versionFieldName`;
- a DSL-default version remains recognized even when no explicit `AggregatePersistenceFieldControl.version` exists;
- a generic managed `READ_ONLY` field is not classified as identity or version;
- application-side Strong ID policy unchanged.

### 3. Parent Binding And Cardinality Tests

Prove:

1. missing parentRef still fails;
2. ambiguous parentRef still fails;
3. parentRef equal to child primary key now fails with the new error;
4. unique parentRef plus independent child primary key still infers owned-one;
5. ordinary parentRef plus independent child primary key infers owned-many;
6. soft-delete/scope neutral uniqueness behavior remains unchanged.

The old test named like `primary key parent ref infers one` must be replaced by the fail-fast expectation.

### 4. Entity Planner Tests

For each supported combination, inspect planner context:

| ID | Version | Expected |
|---|---|---|
| database identity | absent | ID property nullable/null, not constructor |
| database identity | `Short` | both nullable/null, neither constructor |
| database identity | `Int` | both nullable/null, neither constructor |
| database identity | `Long` | both nullable/null, neither constructor |
| application Strong ID | `Long` | Strong ID Phase 4 shape plus nullable version |

Also prove:

- parentRef absent from scalar fields;
- parentRef absent from constructor fields;
- inverse relation absent from relation fields;
- forward owned relation retained;
- merged soft-delete role retains its approved constructor exclusion and active-sentinel initializer;
- a generic managed `READ_ONLY` field retains its existing constructor/nullability/initializer shape.

### 5. Factory Planner And Renderer Tests

Replace current negative identity/version factory expectations with:

~~~text
constructorMappingResolved == true
~~~

Required assertions:

1. payload excludes ID/version/parentRef;
2. constructor mapping excludes them;
3. generated factory contains a real constructor call;
4. generated factory contains no construction TODO;
5. ordinary business fields still map correctly;
6. a generic managed `READ_ONLY` constructor gap is not silently treated as a supported entrusted-field mapping;
7. no child-spec feature is accidentally added.

### 6. Entity Renderer Tests

Assert rendered source contains equivalents of:

~~~kotlin
@GeneratedValue(strategy = GenerationType.IDENTITY)
var id: Long? = null
    internal set
~~~

and:

~~~kotlin
@Version
var version: Long? = null
    internal set
~~~

Assert source does not contain:

- identity/version constructor parameters;
- parentRef scalar properties;
- child `@ManyToOne` back-references for owned relations;
- inverse-relation imports when no ordinary many-to-one exists.

The same renderer suite must retain the merged soft-delete matrix and must prove that no generic managed `READ_ONLY` field receives a synthetic `null` or sentinel initializer from this iteration.

### 7. Schema Planner And Renderer Tests

Prove:

1. child Schema has no parentRef scalar field;
2. parent Schema retains forward owned relation field;
3. `joinVideoFiles()` still renders;
4. nested forward joins still render;
5. Schema planning contains no inverse canonical input;
6. identity/version Schema fields retain physical DB nullability, independently of nullable Entity property types.

### 8. Functional Generation And Compilation

Generate and compile at least these fixtures:

1. root database identity without version;
2. root database identity with Long version;
3. root plus one owned child, both database identity/version;
4. three-level owned graph with database identity/version;
5. owned-one through unique parentRef and independent child ID;
6. application-side Strong ID regression fixture;
7. no physical FK constraint fixture.

Compilation must prove:

- factory body is real;
- generated entities compile;
- deleted inverse APIs are absent;
- parent-side Schema joins compile.

The fixture must keep `artifact.unique=false` when parentRef-containing unique constraints are present.

### 9. UoW Unit Tests

Prove:

1. factory supervisor registers one root CREATE;
2. root/child/grandchild explicit entries, where existing tests exercise reconciliation, collapse to root;
3. save calls `EntityManager.persist(root)`;
4. save does not independently call persist on absorbed child/grandchild;
5. flush occurs once according to existing UoW semantics;
6. no new public child persistence contract is introduced;
7. no generic managed-field lifecycle callback or `UnitOfWorkInterceptor` contract is introduced;
8. existing bounded `isNew()` uses are not repurposed as identity/version or audit lifecycle classifiers.

### 10. JPA Runtime Evidence

Mandatory H2 runtime test:

1. create root, child, and grandchild tables;
2. use identity IDs and integral versions at all levels;
3. omit physical FK constraints while retaining join columns;
4. construct one root graph;
5. register only root CREATE;
6. call the supported save path;
7. assert every ID/version is non-null;
8. query persisted rows and verify join-column values;
9. execute parent-side Schema joins and verify returned descendants;
10. verify no inverse child navigation is required.

The test must not rely only on mocked `EntityManager` behavior.

If an existing MySQL/PostgreSQL integration harness is available without adding dependencies or infrastructure, a focused provider test is welcome but not mandatory for this design.

### 11. Static Deletion Checks

After implementation:

~~~powershell
rg -n "AggregateInverseRelationModel|aggregateInverseRelations|AggregateInverseRelationInference" `
  cap4k-plugin-pipeline-api `
  cap4k-plugin-pipeline-core `
  cap4k-plugin-pipeline-generator-aggregate
~~~

Expected: no active production references.

Check parentRef projection:

~~~powershell
rg -n '"parentRef" to field\.parentRef|structuralParentRef|filter \{ it\.parentRef \}' `
  cap4k-plugin-pipeline-generator-aggregate/src/main
~~~

Expected: no active Entity/Factory scalar projection path.

Check generated TODO:

~~~powershell
rg -n 'TODO\("Implement aggregate construction"\)' <generated-fixture-root>
~~~

Expected: no match for supported fixtures.

### 12. Suggested Verification Commands

Use the repository Gradle wrapper and focused tests first:

~~~powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test
.\gradlew.bat :cap4k-plugin-pipeline-core:test
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test
.\gradlew.bat :ddd-domain-repo-jpa:test
.\gradlew.bat :cap4k-ddd-starter:test
~~~

Then run the relevant pipeline functional/compile tests:

~~~powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test
~~~

The implementation plan may narrow individual test selectors during TDD, but final verification must cover every modified module and the real JPA runtime fixture.

No dependency installation is authorized by this design.

## Rollback Triggers

Stop implementation and return to design if any of the following evidence appears:

1. A supported JPA provider cannot populate a nullable Kotlin identity property mapped with `@GeneratedValue(IDENTITY)`.
2. A supported provider cannot initialize a nullable `Short?`, `Int?`, or `Long?` version property.
3. Root-only cascade persistence cannot make nested child identities observable after flush.
4. Parent-owned `ONE_TO_MANY_JOIN_COLUMN` cannot persist a database-identity graph without requiring a child parent scalar or inverse `@MapsId` relation.
5. Parent-side Schema joins require the child scalar property despite the retained parent relation mapping.
6. Removing inverse navigation breaks a runtime path that is not expressible through the forward owned graph and is required by a current accepted contract.
7. ParentRef removal makes another default-enabled artifact family generate incorrect code or incorrect semantics.
8. Identity/version construction support cannot be implemented without changing the merged soft-delete constructor or physical-storage contract.
9. Supporting the accepted version matrix requires provider-specific branches beyond bounded type validation and rendering.
10. The only implementation path requires modifying Phase 4 Strong ID create-time injection semantics.
11. The only implementation path requires treating every generic `READ_ONLY` field as construction-nullable or provider-assigned.
12. Resolved version policy cannot be projected consistently to provider control without introducing an independent second classifier.

Do not respond to a rollback trigger with:

- placeholder IDs;
- reflection fallback;
- hidden inverse navigation;
- silent global uniqueness;
- child-level flushes;
- a compatibility shell.

Bring the evidence back to design review.

## Agent Handoff Notes

### Required Implementation Order

1. Start from baseline `c49e12f5` or a later descendant containing the merged soft-delete implementation.
2. Re-read the current entity planner/template and preserve the role-specific soft-delete behavior before applying this spec.
3. Add failing focused tests for one decision at a time.
4. Implement canonical parentRef/shared-PK changes.
5. Remove inverse canonical/planner infrastructure.
6. Implement identity/version constructor and property projection.
7. Resolve factory constructor mapping.
8. Add Schema forward-join regression tests.
9. Add UoW/JPA runtime proof.
10. Run static deletion checks and focused/full verification.

### Allowed Production Areas

Expected production areas include:

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`;
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`;
- `AggregateSpecialFieldPolicyResolver.kt` or a narrowly related type validator;
- `OwnedRelationCardinalityInference.kt` or the owned binding validation path;
- deletion of `AggregateInverseRelationInference.kt`;
- `cap4k-plugin-pipeline-generator-aggregate` Entity, Factory, Schema, Projection, and relation planning;
- `cap4k-plugin-pipeline-renderer-pebble` entity/factory/schema templates only where required;
- focused tests in the same modules;
- `ddd-domain-repo-jpa` and `cap4k-ddd-starter` tests/fixtures for runtime evidence.

### Areas That Must Not Be Changed

Do not change:

1. Strong ID generation algorithm or module catalog behavior.
2. `IdentifierGenerator` public contract.
3. `UnitOfWork.persist` public signature or PersistIntent semantics.
4. aggregate factory public child creation APIs.
5. soft-delete storage, sentinel, SQL dialect, or assignment rules.
6. Unique Query/Handler/Validator production planners or templates.
7. authoring syntax for parent access.
8. `OwnedEntityList` public API except a proven compile-only adjustment directly required by deleted types.
9. repository ownership or aggregate boundary rules.
10. general JPA relationship support such as ManyToMany, JoinTable, or MapsId.
11. build dependencies or external infrastructure.
12. generic managed-field constructor, nullability, initializer, or lifecycle semantics.
13. only-engine audit configuration, operator providers, JPA callbacks, or Hibernate event listeners.
14. `UnitOfWorkInterceptor`, generic create/update lifecycle events, or the existing bounded `EntityInformation.isNew()` uses.

### Implementation Discipline

- Preserve unrelated dirty worktree changes.
- Use TDD for each behavior change.
- Do not keep deleted behavior behind fallback flags.
- Do not broaden managed-field constructor policy beyond the accepted identity/version and preceding soft-delete contracts.
- Use resolved identity/version roles for constructor projection; never use `READ_ONLY` as a general constructor-exclusion predicate.
- Treat resolved version policy as authoritative and provider control as its derived consistency projection, not a second classifier.
- Do not require `AggregatePersistenceFieldControl.version` for a DSL-default version.
- Preserve generic managed audit fields exactly unless a separate approved managed-field lifecycle SPI design changes them later.
- Do not infer semantic roles in templates.
- Keep runtime assertions provider-observable rather than tied to a guessed exact version seed.
- If generated fixture construction needs handwritten test support, keep it test-only.
- A passing compile test is not enough; nested JPA persistence and Schema join runtime evidence are mandatory.

## Resolved Decisions

The following decisions are closed for implementation planning:

1. The baseline contains completed soft-delete support and Phase 4; this iteration modifies neither contract.
2. Database identity and version are database-entrusted.
3. Both are absent from constructors and factory payloads.
4. Both are nullable entity properties initialized to null.
5. Database physical nullability is unchanged.
6. Version supports Short, Int, and Long.
7. Version overflow is not framework-managed.
8. Factory create does not flush.
9. UoW save makes assigned values observable.
10. Root remains the only final UoW entry for an aggregate graph.
11. Rollback does not clear assigned values.
12. Default parentRef scalar projection is deleted.
13. Default automatic inverse entity navigation is deleted.
14. Inverse canonical API is deleted without deprecation.
15. Parent-side Schema joins remain.
16. Physical FK constraints are optional for query mapping.
17. ParentRef physical columns remain mandatory.
18. Shared-primary-key owned children are rejected.
19. Owned-one uses unique parentRef plus an independent child ID.
20. Unique artifact production code is unchanged in this iteration.
21. Future unique addon skips parentRef-containing constraints unless an explicit scoped design replaces that rule.
22. Future parent ID/entity access requires a separate design.
23. Entity construction nullability does not change Schema physical-query nullability.
24. `READ_ONLY` governs the user write surface and does not by itself define construction or persistence lifecycle behavior.
25. Database identity construction behavior is selected by the resolved database-side own-ID role.
26. Resolved version policy is the sole semantic classifier for provider-assigned version construction behavior.
27. `AggregatePersistenceProviderControl.versionFieldName` is a derived consistency projection, not an independent version classifier.
28. `AggregatePersistenceFieldControl.version` is not required for a version resolved through `versionDefaultColumn`.
29. Generic managed audit fields retain their current construction and lifecycle behavior.
30. Managed-field lifecycle SPI, only-engine audit changes, UoW interceptor changes, and generic `isNew()` replacement are deferred to a separate iteration.

## Related Documents

- `docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md`
- `docs/superpowers/specs/2026-07-23-cap4k-uow-owned-entity-lifecycle-classification-design.md`
- `docs/superpowers/specs/2026-07-24-cap4k-strong-id-create-time-injection-design.md`
- `docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md`
- `docs/superpowers/specs/2026-05-06-cap4k-aggregate-managed-write-surface-and-factory-payload-metadata-design.md`
- `docs/superpowers/specs/2026-05-03-cap4k-special-fields-managed-write-surface-and-only-engine-audit-alignment-design.md`
- `docs/superpowers/specs/2026-07-23-cap4k-default-schema-owned-relation-join-design.md`
- `docs/superpowers/specs/2026-04-20-cap4k-aggregate-inverse-relation-read-only-parity-design.md`
- `docs/superpowers/specs/2026-05-04-cap4k-aggregate-inverse-navigation-owner-and-fetch-policy-design.md`

# cap4k Advanced Weak-reference Projection Design

Date: 2026-05-12

Status: Proposed

Scope: expand `#23` from a generator-only weak-reference template-context issue into an advanced, built-in cap4k weak-reference projection capability that includes DB metadata, runtime projection access through `Mediator.prj`, a first JPA provider, and opt-in generated projection artifacts.

Out of scope:

- changing repository or unit-of-work semantics
- making weak references part of command-side aggregate invariants
- allowing weak-reference fields to participate in predicates or ordering
- adding Jimmer as a first-version built-in provider
- replacing query handlers with projections
- making weak-reference projection generation part of the default happy path
- generating a single unified write/read class

## Backlog Source

This design covers:

- `#23` generator: support advanced read-write model split with read-only weak-reference template context

The issue is intentionally expanded. The weak-reference output is not useful enough if cap4k only emits metadata and leaves every project to invent its own read-side access pattern. The accepted direction is one issue that defines both:

- the generator artifact surface
- the standard runtime consumption surface

## Background

The current default cap4k model separates write and read responsibilities:

- write models are aggregate-root-centered domain models
- repositories load and track write aggregates
- `JpaUnitOfWork` records and saves write-side entity changes
- query handlers read and compose read information, often directly through JPA, Jimmer, SQL, or another read technology

`#15` and later advanced-modeling discussions established that read-only weak references are valid only as an advanced modeling mode. The user-facing purpose is not cross-aggregate mutation. The purpose is to make read/navigation results more expressive while keeping the write model unchanged.

The earlier issue wording focused on exposing weak-reference metadata to templates. That was too narrow. A project author should not have to distinguish between "cap4k built-in artifact" and "external addon artifact" to consume this. cap4k should own the core capability, but keep it disabled by default.

## Problem

Today an aggregate may store only an ID for an associated object:

```text
Content
  reviewerId
```

The default write model is correct: `reviewerId` is scalar state owned by `Content`, not a writable `Reviewer` object graph.

For read/navigation results, however, callers often need:

```text
ContentProjection
  reviewerId
  reviewer: ReviewerRef
```

Without a standard cap4k concept, projects drift into one of several bad shapes:

- query handlers inject raw `EntityManager`, `KSqlClient`, or SQL clients everywhere
- weak references become ad hoc DTO fragments with inconsistent names
- read-side helper classes start looking like repositories
- repository contracts are widened to load read models
- weak-reference joins leak into filtering or ordering APIs

The missing capability is a constrained read-side projection access model:

```text
query -> Mediator.prj -> projection provider -> projection result with select-only weak refs
```

This should feel familiar to repository users, but it must not become another write-model repository or a generic query framework.

## Goals

1. Add explicit DB metadata for weak-reference-only fields.
2. Treat existing strong `@Reference` fields as weak-reference metadata sources too.
3. Add a runtime projection abstraction that can be reached through `Mediator.projections` / `Mediator.prj`.
4. Keep projection access read-only and detached from `UnitOfWork`.
5. Add a first built-in JPA projection provider.
6. Leave Jimmer and custom providers as future or project-provided extensions.
7. Generate weak-reference projection artifacts only when explicitly enabled.
8. Generate projection results where weak references appear only in the returned object graph.
9. Keep weak-reference fields out of predicate and ordering surfaces forever.
10. Preserve repository semantics as write-model-only.

## Non-Goals

- Do not add `ReadRepository`.
- Do not rename or weaken the existing repository contract.
- Do not let projections call `Mediator.uow`.
- Do not auto-persist projection results.
- Do not let commands depend on projections by default.
- Do not let controllers bypass queries and call projections as the normal API path.
- Do not make projection predicates a route for weak-reference filtering.
- Do not support weak-reference ordering.
- Do not make Jimmer a cap4k default dependency.
- Do not force projects to generate projections when they only need default command/query artifacts.

## Options Considered

### Option 1: metadata-only generator context

Expose weak-reference metadata in template context and let projects decide how to consume it.

Pros:

- smallest generator-only change
- no runtime surface

Cons:

- does not solve consumption
- every project invents its own projection naming and access pattern
- fails to make this feel like a cap4k built-in capability

Decision: reject.

### Option 2: split runtime projection into a separate issue

Keep `#23` focused on generator metadata and create a separate issue for `Mediator.prj`.

Pros:

- smaller individual issues
- runtime work can be reviewed independently

Cons:

- generated artifacts and runtime consumption are inseparable for this feature
- `#23` would land a partial capability
- sequencing would become artificial

Decision: reject.

### Option 3: one advanced built-in weak-reference projection capability

Expand `#23` to include metadata, runtime projection access, JPA provider, and opt-in generator artifacts.

Pros:

- one coherent author experience
- keeps weak-reference projection as a cap4k built-in advanced capability
- prevents projects from inventing incompatible consumption patterns
- keeps repository boundaries explicit
- leaves room for future Jimmer/custom providers without making them first-version scope

Cons:

- larger than a pure generator issue
- touches runtime, starter wiring, generator API, DB source parsing, renderer templates, and docs

Decision: choose this option.

## Chosen Design

The chosen capability is:

```text
DB @WeakReference / @Reference
  -> canonical weak-reference metadata
  -> opt-in projection artifacts
  -> JPA projection provider
  -> Mediator.prj
  -> projection result with select-only Ref fragments
```

The projection result is a read model, not a write aggregate.

Example shape:

```kotlin
data class ContentProjection(
    val id: UUID,
    val title: String,
    val reviewerId: UUID?,
    val reviewer: ReviewerRef?,
)

data class ReviewerRef(
    val id: UUID,
    val displayName: String?,
)
```

The query handler can read this through:

```kotlin
val content = Mediator.prj.findOne(SContentProjection.predicateById(contentId))
```

But the weak-reference fragment must not become a query surface:

```kotlin
// Permanently unsupported.
SContentProjection.predicate { s ->
    s.reviewer.displayName like "%alice%"
}

// Permanently unsupported.
SContentProjection.orderBy { s ->
    s.reviewer.displayName.asc()
}
```

The JPA provider may join the reviewer table internally to select `ReviewerRef` fields. That internal join is an implementation detail for select enrichment only.

## DB Annotation Contract

Add a DB column-level annotation:

```sql
reviewer_id uuid comment '@WeakReference=user_profile;'
```

Alias:

```sql
reviewer_id uuid comment '@WeakRef=user_profile;'
```

Rules:

- The annotation value is a table name, matching the existing DB-source convention used by `@Reference`.
- The target table must exist in the DB source and map to a canonical entity.
- The annotated column remains a scalar field on the generated entity.
- `@WeakReference` does not generate a JPA relation field.
- `@Reference` continues to generate the existing relation path and also contributes weak-reference metadata.
- `@Reference` and `@WeakReference` are not mutually exclusive.
- If both annotations appear on the same column with the same target, the weak-reference metadata is deduplicated.
- If both annotations appear with different targets, generation fails fast.

Example:

```sql
-- Strong relation plus weak-reference metadata.
author_id uuid comment '@Reference=user_profile;@Relation=ManyToOne;'

-- Weak-reference metadata only.
reviewer_id uuid comment '@WeakReference=user_profile;'
```

## Canonical Model

Add a distinct canonical model for weak references. Do not overload `AggregateRelationModel`, because relation models currently represent fields that may be rendered as JPA relation members.

Conceptual model:

```kotlin
data class AggregateWeakReferenceModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val ownerAggregateRootName: String,
    val fieldName: String,
    val columnName: String,
    val fieldType: String,
    val nullable: Boolean,
    val targetTableName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String,
    val targetAggregateRootName: String,
    val sourceKind: WeakReferenceSourceKind,
    val generatesRelation: Boolean,
)

enum class WeakReferenceSourceKind {
    REFERENCE,
    WEAK_REFERENCE,
    BOTH,
}
```

`generatesRelation` is true only when the source includes `@Reference`.

This model is read-side metadata. It must not change aggregate ownership, repository loading, cascade behavior, or `JpaUnitOfWork`.

## Runtime Projection Core

Add application-side projection abstractions under `ddd-core`.

Conceptual package:

```text
com.only4.cap4k.ddd.core.application.projection
```

Core interfaces:

```kotlin
interface ProjectionPredicate<MODEL : Any>

interface Projection<MODEL : Any> {
    fun supportPredicateClass(): Class<*>

    fun find(
        predicate: ProjectionPredicate<MODEL>,
        orders: Collection<OrderInfo> = emptyList(),
    ): List<MODEL>

    fun findOne(predicate: ProjectionPredicate<MODEL>): MODEL?

    fun findFirst(
        predicate: ProjectionPredicate<MODEL>,
        orders: Collection<OrderInfo> = emptyList(),
    ): MODEL?

    fun findPage(
        predicate: ProjectionPredicate<MODEL>,
        pageParam: PageParam,
    ): PageData<MODEL>

    fun count(predicate: ProjectionPredicate<MODEL>): Long

    fun exists(predicate: ProjectionPredicate<MODEL>): Boolean
}
```

Supervisor:

```kotlin
interface ProjectionSupervisor {
    fun <MODEL : Any> find(
        predicate: ProjectionPredicate<MODEL>,
        orders: Collection<OrderInfo> = emptyList(),
    ): List<MODEL>

    fun <MODEL : Any> findOne(predicate: ProjectionPredicate<MODEL>): MODEL?

    fun <MODEL : Any> findFirst(
        predicate: ProjectionPredicate<MODEL>,
        orders: Collection<OrderInfo> = emptyList(),
    ): MODEL?

    fun <MODEL : Any> findPage(
        predicate: ProjectionPredicate<MODEL>,
        pageParam: PageParam,
    ): PageData<MODEL>

    fun <MODEL : Any> count(predicate: ProjectionPredicate<MODEL>): Long

    fun <MODEL : Any> exists(predicate: ProjectionPredicate<MODEL>): Boolean
}
```

`DefaultProjectionSupervisor` should:

- collect `Projection<*>` beans
- dispatch by projection model class and predicate class
- fail fast when no compatible projection exists
- never interact with `UnitOfWork`
- never persist returned objects
- never require JPA or Jimmer types

`Mediator` should expose:

```kotlin
Mediator.projections
Mediator.prj
```

`X` should mirror the shortcut for consistency if `X` continues to mirror `Mediator` surfaces.

## JPA Projection Provider

First-version built-in provider: JPA only.

Preferred module:

```text
ddd-application-projection-jpa
```

The provider should contain JPA-specific predicate and projection helpers, for example:

```kotlin
class JpaProjectionPredicate<MODEL : Any>(...)

abstract class AbstractJpaProjection<MODEL : Any> : Projection<MODEL>
```

Generated projection artifacts should not import `com.only4.cap4k.ddd.domain.repo.JpaPredicate`, because that name belongs to write-model repository access.

It is acceptable for first-version JPA projection helpers to mirror the existing repository `S`-class authoring experience. The package names and type names must make the read-side ownership clear.

Jimmer is not a first-version built-in provider. The runtime abstraction must allow a project or later cap4k module to provide:

```text
ddd-application-projection-jimmer
```

but that module is not part of this issue's implementation scope.

## Projection Schema And `S` Class Experience

Repository ergonomics currently depend on generated `S` classes:

```kotlin
Mediator.repo.findOne(SContent.predicateById(contentId))

Mediator.repo.findFirst(
    SContent.predicate { s ->
        s.title like "%cap4k%"
    }
)
```

Projection should preserve the same author habit:

```kotlin
Mediator.prj.findOne(SContentProjection.predicateById(contentId))

Mediator.prj.findFirst(
    SContentProjection.predicate { s ->
        s.title like "%cap4k%"
    }
)
```

But projection `S` classes must obey the select-only weak-reference boundary.

Allowed:

- root projection scalar fields
- fields from the projection root shape
- provider-supported owned/root navigation needed to query the projection root
- scalar weak-reference ID fields, because they are owned by the root row

Permanently unsupported:

- weak-reference object fields as predicate fields
- weak-reference object fields as order fields
- generated schema navigation from a weak ref into target fields

Example:

```kotlin
data class ContentProjection(
    val id: UUID,
    val reviewerId: UUID?,
    val reviewer: ReviewerRef?,
)
```

Allowed:

```kotlin
SContentProjection.predicate { s ->
    s.reviewerId eq reviewerId
}
```

Unsupported:

```kotlin
SContentProjection.predicate { s ->
    s.reviewer.displayName like "%alice%"
}
```

The template must not generate `s.reviewer.displayName` or equivalent field-builder access.

## Generator Artifact Surface

The capability is built into cap4k, but disabled by default.

Recommended DSL:

```kotlin
cap4k {
    aggregate {
        artifacts {
            weakReferenceProjections.set(true)
        }
    }
}
```

Rationale:

- the weak-reference metadata comes from aggregate/DB source modeling
- the generated files include application and adapter artifacts
- the name says what is enabled without implying default behavior

The first version does not need a provider selector because JPA is the only built-in provider. Future Jimmer support can add a provider switch or a separate artifact family after the second provider exists.

Generated artifacts should include:

```text
application projection model:
  <application-module>/src/main/kotlin/<base>/application/projections/<owner>/<OwnerProjection>.kt

application projection schema:
  <application-module>/src/main/kotlin/<base>/application/projections/<owner>/S<OwnerProjection>.kt

weak-reference ref fragment:
  <application-module>/src/main/kotlin/<base>/application/projections/<owner>/<TargetRef>.kt

adapter JPA projection implementation:
  <adapter-module>/src/main/kotlin/<base>/adapter/application/projections/<owner>/<OwnerProjection>JpaProjection.kt
```

Exact naming may be normalized in the plan, but the placement rules are fixed:

- projection result types are application read models
- JPA implementation lives in adapter
- weak-reference fragments are read-side return fragments
- domain aggregate files are not changed to hold weak-reference objects

Recommended template ids:

```text
application/projection/model.kt.peb
application/projection/schema.kt.peb
application/projection/ref.kt.peb
adapter/projection/jpa_projection.kt.peb
```

Conflict policy:

- generated read-model skeletons should default to `SKIP` if they are meant to be project-maintained
- purely generated snapshots may use generated output roots, but the author-facing default should be conservative

The implementation plan should inspect existing output-kind patterns before finalizing the exact `SOURCE` versus `GENERATED_SOURCE` split.

## Data Flow

Command/write flow remains unchanged:

```text
CommandHandler
  -> Mediator.repo
  -> Repository
  -> aggregate whole load
  -> aggregate behavior
  -> Mediator.uow
```

Read/projection flow:

```text
QueryHandler
  -> Mediator.prj
  -> ProjectionSupervisor
  -> JPA Projection
  -> projection result with weak-reference Ref fragments
  -> Query Response
```

Controllers still call queries:

```text
Controller -> Mediator.qry -> QueryHandler
```

They should not call `Mediator.prj` as the normal API entry.

## Hard Boundaries

The following are permanent design boundaries, not first-version limitations:

- Weak-reference objects appear only in projection return values.
- Weak-reference object fields are never predicate fields.
- Weak-reference object fields are never order fields.
- Weak-reference objects are never command inputs for business decisions.
- Weak-reference objects are never domain aggregate members.
- Weak-reference joins are provider-internal select-enrichment details.
- Repository remains write-model-only.
- Unit of work remains write-model-only.
- Projection results are never auto-persisted.

If a project needs filtering or sorting by target object fields, that is a separate read-model/query design. It is not weak-reference projection.

## Error Handling

Fail fast when:

- `@WeakReference` has no value
- `@WeakReference` value is blank
- `@WeakReference` target table is unknown
- the target table cannot be mapped to a canonical entity
- `@Reference` and `@WeakReference` on the same column point to different targets
- `Mediator.prj` is used with no configured `ProjectionSupervisor`
- no projection supports the predicate/model pair
- multiple projections claim the same model and predicate pair

Diagnostics should name the table, column, annotation, and target where possible.

## Compatibility

Existing projects are unaffected because:

- the artifact switch defaults to disabled
- `@WeakReference` is new metadata
- `@Reference` keeps its existing relation behavior
- repository and unit-of-work contracts are unchanged
- default entity templates do not consume weak-reference projection objects

The only behavior added for existing `@Reference` columns is additional canonical weak-reference metadata. That metadata should not change default output unless the advanced artifact switch is enabled.

## Documentation Updates

Implementation should update public authoring docs after code lands:

- DB input-source annotation table
- tactical model page for `Mediator.prj`
- advanced read-only weak-reference page
- generator DSL reference

The docs must emphasize:

- advanced mode
- default off
- JPA-only first provider
- weak refs are select-only return fragments
- no weak-ref predicate/order support

## Testing Strategy

Tests should cover:

- DB parser accepts `@WeakReference` and `@WeakRef`
- DB parser rejects blank/missing weak-reference values
- DB parser strips recognized annotations from comments
- canonical assembly records weak references from `@WeakReference`
- canonical assembly records weak references from `@Reference`
- same-column `@Reference` + `@WeakReference` same target deduplicates
- same-column `@Reference` + `@WeakReference` target mismatch fails
- default aggregate/entity generation remains unchanged when artifact switch is disabled
- enabled weak-reference projections generate application projection model, schema, ref fragment, and adapter JPA projection skeleton
- generated projection schema exposes root scalar fields but does not expose weak-ref object fields for predicate/order
- `DefaultProjectionSupervisor` dispatches to a compatible projection
- `DefaultProjectionSupervisor` fails on missing or duplicate projection matches
- `Mediator.prj` exposes the projection supervisor
- JPA projection provider returns projection results without persisting them

Functional tests should include one weak-only reference and one strong `@Reference` that also contributes weak-reference metadata.

## Acceptance Criteria

- `#23` is implemented as an advanced built-in cap4k capability.
- `@WeakReference/@WeakRef=<table>` works as DB column metadata.
- `@Reference` also contributes weak-reference metadata without changing existing relation output.
- `Mediator.prj` is available as the standard projection access surface.
- Runtime projection access is read-only and does not interact with `UnitOfWork`.
- First-version built-in provider is JPA only.
- Weak-reference projection generation is disabled by default.
- When enabled, generated projection results can include weak-reference Ref fragments.
- Generated projection schemas do not expose weak-reference object fields for predicate/order.
- Repository semantics remain write-model-only.
- Public docs clearly distinguish projection from repository and query.

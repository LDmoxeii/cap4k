# cap4k UUID7 ID Generator and Default ID Policy Design

> Date: 2026-05-02
> Status: Implemented
> Scope: application-side ID policy, UUID7 default strategy, ID allocation registry, aggregate code generation, JPA unit-of-work assigned-id handling
> Out of scope: database schema migration tooling, strong ID type system, composite IDs, repository backend replacement, DB comment ID-strategy compatibility, legacy `@IdGenerator` support

> Completion note: Implemented by the UUID7 application-side ID policy slice. The implementation keeps `@GeneratedValue(strategy = GenerationType.IDENTITY)` for database identity IDs and uses `@ApplicationSideId` for application-side strategies.

## Background

cap4k currently has multiple ID-generation concepts that are not unified.

The legacy and current aggregate generation path can emit Hibernate provider-side generation annotations:

```kotlin
@GeneratedValue(generator = "snowflakeIdGenerator")
@GenericGenerator(name = "snowflakeIdGenerator", strategy = "snowflakeIdGenerator")
```

That path was useful for omitted IDs, but it did not provide a stable cap4k contract for preassignable application-side IDs. The runtime fixture `AggregateJpaRuntimeDefectReproductionTest.preassignedApplicationSideIdIsPreservedForNewRoot` captured that historical problem and now asserts `SUPPORTED` for the implemented application-side ID contract.

cap4k also already contains `UuidV7IdentifierGenerator` in `ddd-domain-repo-jpa`, backed by `UuidCreator.getTimeOrderedEpoch()`. That implementation is currently another Hibernate `IdentifierGenerator`; it is not yet a framework-level ID policy.

The desired direction is different:

- UUID7 should become the default application-side ID strategy for new generated projects.
- Snowflake should remain available as an explicit `Long` strategy, not the framework default.
- Application-side IDs must be preassignable before persistence.
- JPA `UnitOfWork` must correctly persist new entities that already have an application-side ID.

This spec intentionally does not preserve the old `@IdGenerator=...` DB-comment or `@GenericGenerator` pipeline semantics.

## External Reference

UUID version 7 is defined by RFC 9562 as a time-ordered UUID format based on Unix Epoch milliseconds plus version, variant, and random or monotonic bits. This makes UUID7 suitable for database primary keys that benefit from chronological locality.

The NetCorePal Cloud Framework uses a related model through EF Core property-level value generators. DeepWiki analysis of `netcorepal/netcorepal-cloud-framework` identifies `UseGuidVersion7ValueGenerator`, `StrongTypeGuidVersion7ValueGenerator<TEntityId>`, `UseSnowFlakeValueGenerator`, and `SnowflakeValueGenerator<TEntityId>` as property-level generators invoked when EF Core tracks new entities whose ID property still has its default value. cap4k should not copy EF Core APIs directly, but the important architectural lesson is that ID generation belongs to a property-level strategy contract, not to a table-level schema comment.

Hibernate 6.5 also provides a useful reference point through `org.hibernate.generator.Generator.allowAssignedIdentifiers()`. A generator that returns `true` declares that existing assigned identifier values may be kept while default identifiers can still fall back to generated values. Hibernate 6.5.3.Final uses this during session-factory generator creation to set the identifier unsaved-value strategy to `undefined`, so transient/detached detection is not based only on the non-default identifier value. The save path can then call a before-execution generator, which may read the current identifier from the entity and either return that existing value or generate a new one.

The Hibernate model is useful, but it is not sufficient as the cap4k contract:

- it is Hibernate-specific, not portable JPA
- it still depends on the caller reaching `entityManager.persist(...)`
- cap4k currently routes entities through `JpaUnitOfWork` before Hibernate sees them
- root-ID preallocation still needs a framework allocator for command handlers and aggregate factories

Therefore this spec borrows the two core lessons from Hibernate 6.5, but keeps cap4k's own framework-level policy as the source of truth:

- assigned-or-generated ID behavior must handle both unsaved/new-state classification and value generation
- the strategy must be attached to the ID member, not inferred from table comments

## Problem

`@GeneratedValue` is not the right contract for preassignable application-side IDs.

Provider-side generation means the persistence provider is responsible for supplying the ID. Preassignment means application code can allocate the ID before persistence and pass it into the aggregate constructor, command result, child entity construction, or domain event payload.

The current JPA unit-of-work logic also misclassifies assigned IDs. `JpaEntityInformation.isNew(entity)` treats a non-default ID as evidence that the entity may already exist. That is reasonable for provider-generated IDs, but it is wrong for application-side assigned IDs. A newly constructed aggregate with a preallocated UUID7 or Snowflake ID must be inserted, not merged as a detached existing entity.

The current DB-comment `@IdGenerator=...` model is also too implementation-specific. It exposes Hibernate generator names through database comments and does not express:

- application-side vs database-side generation
- preassignable vs non-preassignable strategy
- strategy output type
- project default strategy
- aggregate/entity overrides

## Goals

Introduce a first-class cap4k ID policy that:

- uses UUID7 as the new project default application-side ID strategy
- supports Snowflake `Long` as an explicit application-side strategy
- keeps database identity as a database-side non-preassignable strategy
- provides a runtime `IdStrategyRegistry` and allocator API
- stores the final runtime contract on the ID field
- allows project default, aggregate override, and entity override through Gradle DSL
- validates strategy output type against generated ID field type during code generation
- fills missing application-side IDs before JPA persistence
- preserves already preassigned application-side IDs
- fixes `JpaUnitOfWork` so a preassigned-ID new entity is persisted
- removes the new pipeline dependency on `@GeneratedValue(generator = ...)` and `@GenericGenerator(...)` for application-side IDs

## Non-Goals

- Do not add a DB comment `@IdStrategy`.
- Do not keep `@IdGenerator=...` as a legacy pipeline input.
- Do not keep `AggregateIdGeneratorControl.entityIdGenerator` as a Hibernate generator concept.
- Do not implement schema migration from `BIGINT` to UUID columns.
- Do not force every existing numeric ID table to use UUID7.
- Do not add strong typed ID wrappers in this slice.
- Do not support composite IDs in this slice.
- Do not implement a MySQL `BINARY(16)` UUID storage policy unless the existing type system can already represent it as `java.util.UUID`.
- Do not change repository backend, query generation, domain event design, or aggregate relation modeling.

## Concepts

An ID strategy has:

- a stable strategy name
- a generation kind: `APPLICATION_SIDE` or `DATABASE_SIDE`
- an output JVM type
- a default-value predicate
- an allocation function when it is application-side
- a preassignable flag

Initial built-in strategies:

| Strategy | Kind | Output type | Preassignable | Default value |
| --- | --- | --- | --- | --- |
| `uuid7` | `APPLICATION_SIDE` | `java.util.UUID` | yes | Nil UUID |
| `snowflake-long` | `APPLICATION_SIDE` | `Long` | yes | `0L` |
| `database-identity` | `DATABASE_SIDE` | numeric provider-generated ID | no | provider-specific |

`uuid7-string` is not part of the first slice. If a project wants UUID7 as a string, that should be a later explicit storage-policy decision, not an accidental fallback.

## Configuration Contract

The new user-facing application-side ID policy entry point is Gradle DSL only.

Example shape:

```kotlin
cap4kPipeline {
    aggregate {
        idPolicy {
            defaultStrategy.set("uuid7")

            aggregate("message.UserMessage") {
                strategy.set("uuid7")
            }

            entity("video.VideoFile") {
                strategy.set("snowflake-long")
            }
        }
    }
}
```

The exact Gradle API names can be refined during implementation, but the semantics are fixed:

- project default strategy is required by convention and defaults to `uuid7`
- aggregate override applies to the aggregate root and all owned entities in that aggregate
- entity override applies to one canonical entity and wins over aggregate/project defaults
- database comments do not configure application-side ID strategy
- generated runtime annotations are the final source of truth during execution

Resolution order:

```text
entity override
> aggregate override
> project default
```

If a resolved strategy is incompatible with the entity ID field type, generation fails.

Database identity remains a database-side persistence fact. An ID field marked with `@GeneratedValue=IDENTITY` is classified as `database-identity` and must not receive `@ApplicationSideId`, even when the project default is `uuid7`. If DSL explicitly assigns an application-side strategy to an identity ID field, generation fails because the two contracts conflict.

This keeps the no-DB-`@IdStrategy` decision intact while still respecting real identity-column metadata.

## Runtime Contract

The generated ID field carries the final application-side strategy metadata.

Conceptual annotation:

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationSideId(
    val strategy: String
)
```

Generated Kotlin should use the correct use-site target so Java reflection can find the annotation on the field or getter used by JPA.

Application-side UUID7 mapping:

```kotlin
@Id
@field:ApplicationSideId(strategy = "uuid7")
@Column(name = "id", updatable = false)
var id: UUID = id
    internal set
```

Application-side Snowflake mapping:

```kotlin
@Id
@field:ApplicationSideId(strategy = "snowflake-long")
@Column(name = "id", updatable = false)
var id: Long = id
    internal set
```

Database identity mapping:

```kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "id", insertable = false, updatable = false)
var id: Long = id
    internal set
```

`@ApplicationSideId` and `@GeneratedValue` are mutually exclusive. If both are present on the same ID member, runtime bootstrap or unit-of-work processing must fail fast with a clear error.

## Allocation Contract

cap4k exposes a framework allocator for code that needs an ID before constructing an aggregate:

```kotlin
interface IdAllocator {
    fun <T : Any> next(strategy: String, type: KClass<T>): T
}
```

Convenience helpers can be added later, but the core contract is strategy-name plus expected output type.

Aggregate factories and command handlers may allocate root IDs explicitly:

```kotlin
val id = idAllocator.next("uuid7", UUID::class)
val aggregate = Category(id = id, name = request.name)
```

This is the preferred path when the aggregate root ID must be returned to callers, used in a domain event, used as an external reference, or used as an idempotency key before `UnitOfWork.save()`.

For aggregate-internal entities, cap4k may fill missing IDs during `UnitOfWork.save()` before JPA persistence. This is acceptable because current cap4k aggregate rules do not expose child entity IDs externally and domain events are emitted by aggregate roots.

## Default ID Values

Application-side strategies need a default-value predicate so the unit of work can distinguish "not allocated yet" from "already preassigned".

Built-in defaults:

- `uuid7` treats Nil UUID (`00000000-0000-0000-0000-000000000000`) as unallocated.
- `snowflake-long` treats `0L` as unallocated.

Generated application-side ID fields remain non-nullable where possible. For UUID7, generated constructors should use the Nil UUID sentinel as the default value rather than making the ID property nullable by default.

This keeps generated entity fields stable and avoids nullable IDs leaking through the domain model. If user code needs a real aggregate root ID before save, it must call the allocator explicitly.

## JPA UnitOfWork Contract

`JpaUnitOfWork` must process application-side IDs before deciding between `persist` and `merge`.

High-level flow:

```text
UnitOfWork.save()
  -> collect explicitly persisted entities
  -> assign missing application-side IDs in the owned aggregate object graph
  -> run unit-of-work interceptors with real IDs available
  -> decide persist vs merge for top-level entities
  -> call entityManager.persist/merge
  -> let JPA cascade persist/merge already-identified owned children
```

The ID assignment scan is intentionally bounded:

- start from entities explicitly registered through `UnitOfWork.persist(...)`
- inspect fields/properties in the aggregate-owned object graph
- enter owned `@OneToMany` and owned `@OneToOne` relations when cascade includes `PERSIST`, `MERGE`, or `ALL`
- do not follow `@ManyToOne` reverse navigation
- do not follow weak/read-model associations
- use identity-based cycle detection
- only assign fields marked with `@ApplicationSideId`

This mirrors the write-model aggregate boundary. It avoids traversing arbitrary JPA graphs and avoids generating IDs for unrelated referenced entities.

For top-level entities with `@ApplicationSideId`, new/existing detection must not rely only on `JpaEntityInformation.isNew(entity)`.

Required behavior:

```text
if ID is still default after assignment:
    fail fast
else if entityManager.contains(entity):
    treat as managed update
else if entityManager.find(entityClass, id) == null:
    entityManager.persist(entity)
else:
    entityManager.merge(entity)
```

This existence query is intentional. For assigned IDs, the ID value alone cannot tell whether the object is new, detached, or conflicting with an existing row.

## Code Generation Contract

Aggregate generation resolves one concrete ID strategy for each generated entity.

For application-side strategies, generated entity templates must:

- emit `@ApplicationSideId(strategy = "...")` on the ID field
- not emit `@GeneratedValue(generator = ...)`
- not emit `@GenericGenerator(...)`
- keep `@Column(updatable = false)` for immutable IDs
- generate a non-null default sentinel when the strategy requires save-time allocation support

For database identity, generated entity templates must:

- emit `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- not emit `@ApplicationSideId`
- keep the existing provider-side identity semantics

The old custom Hibernate generator branch is removed from the new pipeline.

## Type Validation

Code generation must fail before writing invalid Kotlin when the resolved ID strategy cannot produce the generated ID field type.

Initial validation matrix:

| Strategy | Required generated ID type | Notes |
| --- | --- | --- |
| `uuid7` | `java.util.UUID` or imported `UUID` | SQL/JDBC source must resolve to UUID type before generation |
| `snowflake-long` | `Long` | intended for `BIGINT` style IDs |
| `database-identity` | numeric ID type with identity metadata | not preassignable |

Examples:

```text
ID strategy uuid7 cannot be applied to aggregate video.Video id field id:
SQL type BIGINT maps to Long. Use snowflake-long for this aggregate/entity
or migrate the id column to a UUID-compatible type.
```

```text
ID strategy snowflake-long cannot be applied to aggregate upload.UploadTask id field id:
generated ID type is java.util.UUID, but snowflake-long produces Long.
```

The DB source should improve UUID detection where practical, especially native UUID columns reported by H2 or PostgreSQL. MySQL `BINARY(16)` or `CHAR(36)` UUID storage is a separate storage-policy decision unless the current type-binding path already resolves the ID field to `java.util.UUID` safely.

## Module Boundaries

The public contract should not live in a JPA-only implementation class.

Recommended placement:

- `ddd-core`: `ApplicationSideId`, strategy metadata interfaces, allocator-facing interfaces, default-value helpers
- `cap4k-ddd-starter`: default registry wiring for built-in strategies
- `ddd-domain-repo-jpa`: JPA unit-of-work integration that reads `@ApplicationSideId`
- `ddd-distributed-snowflake`: Snowflake algorithm and optional runtime bean for `snowflake-long`
- pipeline modules: Gradle DSL, canonical strategy resolution, type validation, and entity template output

The existing `UuidV7IdentifierGenerator` can be reused as implementation reference, but the final UUID7 allocator should not be modeled primarily as a Hibernate `IdentifierGenerator`.

Hibernate 6.5+ can be evaluated as an implementation aid after the framework-level contract is in place. If cap4k later upgrades to a Hibernate version whose generator APIs satisfy the needed lifecycle, a Hibernate generator may delegate to `IdStrategyRegistry` and use `allowAssignedIdentifiers()` to reduce duplicated provider work. That remains an optimization detail. The public cap4k behavior must continue to be defined by `@ApplicationSideId`, `IdStrategyRegistry`, and `JpaUnitOfWork` processing.

## Snowflake Policy

Snowflake remains supported, but it is no longer the default.

Required policy:

- `snowflake-long` is an explicit application-side strategy.
- Snowflake worker-id infrastructure is required only when `snowflake-long` is used.
- Starter auto-configuration must not silently make Snowflake the default ID policy.
- If a project configures `snowflake-long` but no Snowflake generator is available at runtime, startup or first allocation fails fast.

This is a breaking change. That is acceptable for this slice because no legacy compatibility is required.

## Removal Of Legacy Generator Semantics

The new pipeline should remove the old ID-generator line instead of preserving parallel behavior.

Implementation should remove or replace:

- DB table comment parsing for `@IdGenerator=...`
- canonical `AggregateIdGeneratorControl.entityIdGenerator`
- aggregate entity render-model fields named around `generatedValueGenerator`, `genericGeneratorName`, or `genericGeneratorStrategy`
- Pebble template branches that emit `@GenericGenerator`
- tests that assert `snowflakeIdGenerator` string pass-through

If old input contains `@IdGenerator`, the new behavior should be a clear unsupported-input failure, not silent compatibility.

This removal does not remove `@GeneratedValue=IDENTITY`. Identity metadata is still the supported database-side strategy and remains separate from application-side ID policy.

## Runtime Examples

Explicit root preallocation:

```kotlin
class CreateCategoryCmd(
    private val idAllocator: IdAllocator,
    private val unitOfWork: UnitOfWork
) {
    fun exec(request: Request): Response {
        val categoryId = idAllocator.next("uuid7", UUID::class)
        val category = Category(id = categoryId, name = request.name)

        unitOfWork.persist(category)
        unitOfWork.save()

        return Response(id = categoryId)
    }
}
```

Save-time child allocation:

```kotlin
val video = Video(id = idAllocator.next("uuid7", UUID::class), title = request.title)
video.files += VideoFile(name = "source.mp4") // ID remains Nil UUID until UnitOfWork.save()

unitOfWork.persist(video)
unitOfWork.save()
```

Before JPA sees `video`, cap4k assigns a UUID7 to `VideoFile.id` because the field is marked with `@ApplicationSideId(strategy = "uuid7")` and still has the strategy default value.

## Testing Strategy

Focused tests should cover the policy at four layers.

Runtime strategy tests:

- UUID7 allocator returns RFC 9562 version-7 UUIDs.
- UUID7 allocator returns non-default IDs.
- UUID7 allocator values are unique under concurrent generation.
- Snowflake strategy returns `Long` IDs and is not required for the default UUID7 path.
- Strategy registry fails for unknown strategy names.
- Strategy registry fails when requested output type does not match strategy output type.

Code-generation tests:

- project default `uuid7` emits `@ApplicationSideId(strategy = "uuid7")`
- application-side strategies do not emit `@GeneratedValue(generator = ...)`
- application-side strategies do not emit `@GenericGenerator(...)`
- `database-identity` still emits `GenerationType.IDENTITY`
- aggregate override applies to owned entities
- entity override wins over aggregate/project defaults
- `uuid7` on `Long` ID fails before writing artifacts
- `snowflake-long` on `UUID` ID fails before writing artifacts

JPA runtime tests:

- omitted UUID7 root ID is assigned before persist
- explicitly preassigned UUID7 root ID is preserved and inserted
- explicitly preassigned Snowflake root ID is preserved and inserted
- owned child with default application-side ID gets an ID before cascade persist
- assigned-ID existing entity routes to merge/update
- `@ApplicationSideId` combined with `@GeneratedValue` fails fast
- `preassignedApplicationSideIdIsPreservedForNewRoot` fixture asserts `SUPPORTED`

Gradle/plugin integration tests:

- DSL default strategy is available without per-entity configuration
- aggregate/entity overrides appear in the generated plan/report
- invalid strategy/type combinations fail in `cap4kPlan` and `cap4kGenerate`
- generated Kotlin compiles without Hibernate `@GenericGenerator`

## Success Criteria

This slice is complete when:

- UUID7 is the default project ID policy for generated UUID ID fields.
- Application-side ID policy is configured through Gradle DSL, not DB comments.
- Generated application-side ID fields use `@ApplicationSideId`.
- Generated application-side ID fields do not use custom Hibernate `@GenericGenerator`.
- `JpaUnitOfWork` assigns missing application-side IDs before persistence.
- `JpaUnitOfWork` persists new entities with preassigned IDs instead of misrouting them to merge.
- Existing preassignable-ID runtime characterization is converted from known defect to supported behavior.
- Strategy/type mismatch fails during plan/generation with actionable messages.
- Snowflake remains available only as an explicit `snowflake-long` strategy.
- The new implementation has one ID-policy path, not a legacy generator path plus a new strategy path.

## Residual Risks

UUID storage remains database-specific. Native UUID columns are straightforward, but MySQL projects must still decide whether they use `BINARY(16)`, `CHAR(36)`, or another representation. This spec does not solve that storage-policy design.

Save-time ID assignment requires bounded aggregate-object-graph scanning. If mappings do not clearly express owned relations, cap4k may either miss a child ID or traverse too much. The first implementation must stay conservative and only traverse owned cascade relations.

Assigned-ID new/existing detection requires an existence query. That is the correct tradeoff for correctness, but high-volume bulk insert paths may need future batch-aware optimization.

Switching Snowflake away from default is intentionally breaking. Projects with `BIGINT` IDs must configure `snowflake-long`, use database identity, or migrate their ID columns.

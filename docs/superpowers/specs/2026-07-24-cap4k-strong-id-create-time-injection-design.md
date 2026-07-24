# cap4k Strong ID Create-Time Injection Design

**Date:** 2026-07-24
**Revised:** 2026-07-25
**Status:** Approved for implementation
**Phase:** Identity roadmap Phase 4

## Reader Contract

After reading this document, an implementation agent with no chat history must be able to answer all of the following without guessing:

- Which entity IDs are application-side IDs and which remain database identity IDs?
- How do `uuid7` and `snowflake` select `String`, `UUID`, or `Long` Strong ID backing values?
- Which JDBC metadata is authoritative, and what happens when that evidence is absent or contradictory?
- What are the JPA and JSON representations for every supported Strong ID backing type?
- Which component allocates a primitive identifier and which component constructs the entity-specific Strong ID value object?
- At which lifecycle entry points must root and owned-child IDs already be available?
- How does an owned child that was separately enrolled return to its pending aggregate root?
- What generated SPI replaces reflection and the removed `@ApplicationSideId` path?
- Which existing code and compatibility behavior must be deleted rather than preserved?
- Which files are in scope, which areas are explicitly out of scope, and which evidence proves completion?

If an implementation choice is not derivable from this document or current code evidence cited below, implementation must stop and revise the design. It must not add a fallback.

## Current Evidence

The design is based on current `master` after PR #135, not on prior chat context.

### Identifier Runtime

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongId.kt` currently fixes every Strong ID to `String`.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIds.kt` currently owns UUIDv7 String generation and validation.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdentifierGenerator.kt` delegates allocation to a named `IdentifierStrategy` and requires a requested output `KClass`.
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/Uuid7IdentifierStrategy.kt` already produces either `UUID` or `String`.
- `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/SnowflakeIdentifierStrategy.kt` already produces either `Long` or `String`, and its public strategy name is `snowflake`.
- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/Mediator.kt` exposes the configured generator as `Mediator.identifiers`.

Therefore Phase 4 does not need an identifier conversion layer. A generated accessor can request the storage-nearest primitive type directly from `Mediator.identifiers`.

### Pipeline Metadata And Generation

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt` currently defines only `DB_IDENTITY` and `UUID7` in `DbIdStrategy`.
- The same file defines `DbColumnSnapshot` without JDBC `DATA_TYPE` or `COLUMN_SIZE` evidence.
- `StrongIdModel.valueType` exists but current assembly always emits own IDs with `valueType = "String"`.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt` already reads JDBC column rows but currently discards `DATA_TYPE` after Kotlin type mapping and does not retain `COLUMN_SIZE`.
- `JdbcTypeMapper.kt` recognizes native UUID only when JDBC type is `OTHER` or `BINARY` and the vendor type name is `uuid`.
- `DefaultCanonicalAssembler.kt` currently accepts an own Strong ID only for `@IdStrategy=uuid7` on a String column.
- `AggregateIdPolicyResolver.kt` still contains the obsolete `snowflake-long` name even though the runtime strategy is already `snowflake`.
- `strong_id.kt.peb` hardcodes String backing, UUIDv7 validation, `length = 36`, and a Strong ID `new()` function.
- `entity.kt.peb` also hardcodes `length = 36` for every embedded Strong ID override.
- `EntityArtifactPlanner.kt` still publishes dead `applicationSideIdStrategy` and `hasApplicationSideIdFields` context even though the strategy value is always `null`.

### Runtime Lifecycle

- `DefaultAggregateFactorySupervisor` enrolls a newly constructed aggregate through `UnitOfWork.persist(root, PersistIntent.CREATE)` before returning the created root.
- `OwnedEntityList` is the official generated owned-relation carrier but currently has no pre-mutation ID hook.
- `JpaUnitOfWork` already has bounded generated-owned-relation traversal and repository observation baseline support from PR #135.
- `JpaGeneratedStrongIdSupport` currently completes generated Strong IDs through reflection. This is transitional code and must be replaced, not retained as fallback.
- PR #135 rejects a pending owned child that is also separately enrolled. The current traversal can locate reachable owned children, so Phase 4 can reconcile that child into the top-level root entry instead.

### Legacy Application-Side Path

- `ddd-core/.../ApplicationSideId.kt` still exposes `@ApplicationSideId`.
- `ddd-domain-repo-jpa/.../JpaApplicationSideIdSupport.kt` reflects over that annotation and invokes `IdentifierStrategyRegistry` directly.
- `JpaUnitOfWork` contains dedicated annotation assignment, identity, refresh, and validation branches.
- `JpaRepositoryAutoConfiguration` injects `IdentifierStrategyRegistry` into UoW only for that legacy path.
- `Cap4kIrGenerationExtension.kt` still uses `@ApplicationSideId` as an implicit generated-entity discovery signal.
- Dedicated annotation fixtures and tests remain in `ddd-core`, `ddd-domain-repo-jpa`, and `cap4k-ddd-starter`.

This residue is an iteration leak. There are no external customers and no compatibility requirement. Phase 4 deletes it completely from active code and tests.

## Problem Statement

The ID strategy infrastructure can allocate UUID7 and Snowflake values, and PR #135 can classify aggregate persistence intent, but application-side entity IDs are not yet one coherent framework feature.

Today:

- generated Strong IDs support only UUIDv7 String values;
- database storage evidence is too weak to choose String, UUID, or Long safely;
- Strong ID classes allocate their own IDs, coupling value objects to infrastructure;
- generated roots and children may not have IDs at the domain lifecycle moment where callers need them;
- JPA completion discovers generated IDs reflectively;
- owned children separately enrolled beside a pending root are rejected rather than reconciled;
- the obsolete annotation path and `snowflake-long` vocabulary remain active.

Phase 4 must make every supported application-side entity ID a generated Strong ID, select its backing from physical storage evidence, allocate it at official aggregate lifecycle entry points, retain UoW as an idempotent graph-completion backstop, and remove all old discovery and compatibility paths.

## Scope

Phase 4 includes:

- generic `StrongId<V>` value semantics;
- UUID7 and Snowflake application-side own IDs;
- storage-nearest String, UUID, and Long backing selection;
- JDBC type and capacity evidence capture;
- scalar-string JSON representation for all Strong IDs;
- direct JPA embeddable mapping without converters;
- generated typed own-ID accessors;
- one generated module-level accessor catalog;
- starter registry assembly;
- factory-create root and graph completion;
- owned-relation pre-mutation child completion;
- UoW root-graph completion and pending-child reconciliation;
- removal of generated Strong ID reflection;
- full deletion of `@ApplicationSideId` and `snowflake-long` active behavior.

## Non-Goals

Phase 4 does not:

- change Soft Delete SQL, tombstones, sentinels, capacity rules, or templates;
- add native UUID soft-delete support;
- create an independent owned-child factory or child-factory supervisor;
- expose entity ID assignment as a user-facing domain API;
- add root, child, factory provenance, or ownership marker metadata;
- broaden traversal to weak references, inverse relations, arbitrary JPA associations, or `@RefAggregate` edges;
- allow public `UnitOfWork.persist(child, ...)` as a documented child lifecycle API;
- add JPA converters between String, UUID, and Long;
- allow author metadata to override Strong ID backing selection;
- add aliases, deprecated wrappers, or fallback behavior for removed names;
- modify the completed Phase 3.75 spec or plan;
- rewrite historical `docs/superpowers/specs` or `docs/superpowers/plans` to erase old vocabulary.

Soft Delete will receive its own follow-up spec and plan after Phase 4 is complete.

## Terms

### Application-Side ID

An entity ID allocated by application infrastructure before SQL insert. In Phase 4 every supported application-side entity ID is an own Strong ID.

### Database Identity ID

An ordinary provider-managed JPA ID using `GenerationType.IDENTITY`. It is not an application-side Strong ID and remains assigned after insert/flush.

### Strong ID

An entity-specific value object implementing `StrongId<V>`. Its type distinguishes IDs that share the same primitive representation.

### Backing Type

The Kotlin value type held by a Strong ID: `String`, `UUID`, or `Long`.

### Storage-Nearest

Choosing the backing type that directly matches the physical database representation. Storage-nearest mapping does not use a persistence converter.

### Own Strong ID

A Strong ID that is the primary key of its owner entity. Only own Strong IDs participate in lifecycle allocation.

### Reference Strong ID

A Strong ID used as a reference value. Reference and aggregate-reference IDs are value carriers and are never allocated by lifecycle hooks.

### Create-Time Injection

Framework allocation of a missing own Strong ID at an official aggregate lifecycle entry point before that operation returns.

### Lifecycle Entry Point

One of:

- aggregate factory supervisor `create(...)` enrollment;
- generated owned-relation `add/replace` mutation;
- UoW `persist(root, CREATE)` graph completion.

### Generated Own-ID Accessor

A generated, typed, reflection-free bridge that reads, assigns, and allocates one entity type's own Strong ID.

### Module Catalog

One generated Spring contribution per generation target containing every generated own-ID accessor in that module.

### Pending Root Entry

A root already enrolled in the current UoW processing window with `CREATE` or `EXISTING` intent.

### Reconciliation

Removing a separately pending owned-child entry and retaining only its reachable top-level root entry, with the child represented by the root graph.

## Design Decisions

| Decision | Reason | Excluded Alternative |
|---|---|---|
| Every application-side entity ID is a Strong ID | Entity identity must remain type-safe through domain, persistence, and transport | primitive UUID/String/Long application-side IDs |
| Keep strategy names `uuid7`, `snowflake`, and `db_identity` | Runtime has already converged on `snowflake`; old vocabulary is stale | `snowflake-long` alias or deprecation |
| Resolve backing from JDBC storage evidence | Direct representation preserves value-object and persistence semantics | author override or heuristic fallback |
| Strong ID owns value semantics only | A value object should not locate infrastructure | generated Strong ID `new()` |
| Generated accessor owns allocation orchestration | It knows strategy, backing type, entity type, and Strong ID wrapper at compile time | reflection or a generic runtime converter |
| All Strong IDs use JSON strings | UUIDs are naturally textual and Snowflake Long exceeds safe JavaScript integer precision | numeric JSON for Long backing |
| Use direct JPA mapping | Storage-nearest backing makes converters unnecessary | String/UUID/Long cross converters |
| Generate one accessor per eligible entity | Typed code can assign an `internal set` property without reflection | annotation scanning or companion reflection |
| Generate one module catalog | Framework runtime needs deterministic cross-package discovery | one bean per accessor or classpath scanning |
| Keep `OwnedEntityList` infrastructure-free | The domain carrier should not depend on application or persistence services | injecting Mediator/UoW into the carrier |
| Reconcile pending children to the outermost root | Persistence intent is root-oriented and graph-owned | standalone child entry or PR #135 fail-fast |
| Delete legacy paths | There are no external users and dual semantics would obscure the contract | fallback, compatibility annotation, alias |

## Supported Strategy And Storage Matrix

| ID strategy | Required physical storage | Required evidence | Strong ID backing | JSON | Minimum capacity |
|---|---|---|---|---|---|
| `uuid7` | character | JDBC character type plus known `COLUMN_SIZE` | `String` | string | 36 |
| `uuid7` | native UUID | explicit native UUID type evidence | `UUID` | string | native |
| `snowflake` | character | JDBC character type plus known `COLUMN_SIZE` | `String` | string | 19 |
| `snowflake` | BIGINT | JDBC `BIGINT`, mapped directly to Kotlin `Long` | `Long` | string | 64-bit signed |
| `db_identity` | supported integral numeric | existing provider policy | ordinary JPA primitive/wrapper | existing behavior | existing policy |

No other combination is supported.

In particular:

- UUID7 on BIGINT is rejected.
- Snowflake on native UUID is rejected.
- Snowflake on `INT`, `SMALLINT`, `DECIMAL`, or `NUMERIC` is rejected.
- UUID7 character storage below 36 is rejected.
- Snowflake character storage below 19 is rejected.
- Missing JDBC type or required column size is rejected.
- A generic `String` Kotlin mapping without character JDBC evidence is not enough.
- Native UUID must be evidenced by the existing JDBC type-name rule, not inferred from a default or dialect guess.

MySQL UUID7 therefore defaults naturally to `StrongId<String>`. PostgreSQL/H2 native UUID columns use `StrongId<UUID>`. Snowflake BIGINT uses `StrongId<Long>`, while a character Snowflake column uses `StrongId<String>`.

## Metadata Flow

The physical evidence flow is fixed:

```text
DatabaseMetaData.getColumns(...)
        |
        | DATA_TYPE, TYPE_NAME, COLUMN_SIZE
        v
DbColumnSnapshot
        |
        +--> ID strategy/storage validation
        |          |
        |          v
        |    StrongIdModel.valueType
        |
        +--> JPA projection
                   |
                   v
        AggregateColumnJpaModel.columnLength
                   |
                   v
          entity @AttributeOverride
```

Required model changes:

```kotlin
enum class DbIdStrategy {
    DB_IDENTITY,
    UUID7,
    SNOWFLAKE,
}

data class DbColumnSnapshot(
    // existing fields first
    val jdbcType: Int? = null,
    val columnSize: Int? = null,
)

data class AggregateColumnJpaModel(
    // existing fields
    val columnLength: Int? = null,
)
```

The new snapshot fields stay nullable with defaults because in-repository tests and addon call sites construct snapshots directly. This is source convenience, not a runtime fallback: resolving an application-side Strong ID from a snapshot with missing required evidence must fail.

`FieldModel` does not receive JDBC fields. It remains the general canonical field model. `StrongIdModel` stores the resolved `valueType`; it does not carry raw JDBC codes. The JPA projection carries only the semantic `columnLength` needed by rendering.

Delete `StrongIdModel.canGenerateNew`. It is redundant with own-ID kind plus application-side strategy and its name encodes the removed Strong ID `new()` design. Accessor eligibility is derived from `kind == OWN_ID`, `idStrategy`, embedded-ID status, resolved backing, and complete owner metadata.

Aggregate-reference columns that reuse an aggregate own Strong ID must be directly compatible with the owner's resolved backing. A character reference to a UUID-backed own ID, or a native UUID reference to a String-backed own ID, is rejected rather than converted.

Standalone `@RefId` allocation semantics are not expanded in Phase 4. They retain the current UUIDv7 String value contract, remain reference values, never receive accessors, and must not be treated as own IDs merely because they implement `StrongId`.

## Strong ID Value Contract

The framework contract becomes:

```kotlin
interface StrongId<out V : Any> {
    val value: V
}
```

Strong ID responsibilities are limited to:

- validating and canonicalizing an input value;
- exposing `of(backingValue)` for code that already has the backing type;
- exposing `parse(text)` for transport and textual input;
- value equality, hash code, and `toString()`;
- direct JPA representation;
- scalar-string JSON representation.

A generated Strong ID must not:

- call `Mediator`;
- locate an `IdentifierGenerator`;
- know its owner entity;
- expose an infrastructure-dependent `new()` method;
- contain a JPA converter.

### UUID7 String Backing

Required semantics:

- value is canonical lowercase UUID text;
- UUID version is 7 and variant is RFC 4122 variant 2;
- nil UUID is rejected;
- whitespace, uppercase, malformed, or non-v7 input is rejected;
- JPA uses direct String mapping;
- JSON reads and writes a string.

Conceptual generated shape:

```kotlin
@Embeddable
class OrderId protected constructor() : StrongId<String>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override lateinit var value: String
        protected set

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "OrderId")
    }

    @JsonValue
    fun jsonValue(): String = value

    companion object {
        fun of(value: String): OrderId = OrderId(value)
        fun parse(value: String): OrderId = OrderId(value)
    }

    // value equality, hashCode, toString
}
```

### UUID7 UUID Backing

Required semantics are identical, but `value` is `UUID`. `parse(text)` parses and validates text, while `of(value)` validates a UUID instance. JSON still emits canonical text.

Conceptual API:

```kotlin
class OrderId protected constructor() : StrongId<UUID>, Serializable {
    override lateinit var value: UUID
        protected set

    companion object {
        fun of(value: UUID): OrderId
        fun parse(value: String): OrderId
    }

    @JsonValue
    fun jsonValue(): String = value.toString()
}
```

The exact constructor arrangement may vary to satisfy Kotlin and Jackson, but the public semantic surface is `of(UUID)` plus `parse(String)`, and the JPA no-arg constructor remains non-public.

### Snowflake String Backing

Required semantics:

- value is a canonical positive base-10 integer string;
- zero, negative values, leading zeros, signs, whitespace, decimals, and overflow beyond signed Long are rejected;
- maximum textual length is 19;
- JPA uses direct String mapping;
- JSON reads and writes a string.

### Snowflake Long Backing

Required semantics:

- value is a positive `Long`;
- zero and negative values are rejected by public `of/parse` paths;
- the protected JPA no-arg state may initialize the field to `0L` because Kotlin primitive properties cannot use `lateinit`;
- `0L` is an internal hydration placeholder only and is not a valid public Strong ID;
- JSON reads a decimal string and writes a decimal string, never a JSON number.

Conceptual generated shape:

```kotlin
@Embeddable
class OrderId protected constructor() : StrongId<Long>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
    override var value: Long = 0L
        protected set

    companion object {
        fun of(value: Long): OrderId
        fun parse(value: String): OrderId
    }

    @JsonValue
    fun jsonValue(): String = value.toString()
}
```

Jackson must reject a numeric Snowflake token. String-only input is part of the HTTP contract and prevents accidental JavaScript precision loss.

### Shared Validation Helpers

`StrongIds` may expose overloads for the supported primitives, but it must not allocate new IDs after Phase 4. At minimum, tests must prove:

```kotlin
StrongIds.requireUuidV7(value: String, typeName: String): String
StrongIds.requireUuidV7(value: UUID, typeName: String): UUID
StrongIds.requireSnowflake(value: String, typeName: String): String
StrongIds.requireSnowflake(value: Long, typeName: String): Long
```

Equivalent reviewed names are allowed only if the implementation plan and generated templates use them consistently. `newUuidV7String()` is deleted when no active caller remains.

## JPA Mapping Contract

All supported Strong IDs use `@Embeddable` with a `value` property and entity-side `@EmbeddedId` or `@Embedded`.

Direct mappings only:

| Backing | Physical storage | Mapping |
|---|---|---|
| `String` | character | direct String column |
| `UUID` | native UUID | direct UUID column |
| `Long` | BIGINT | direct Long column |

`strong_id.kt.peb` must remove hardcoded `length = 36`. The entity's `@AttributeOverride` is authoritative:

- String backing renders `length = columnLength` from JDBC metadata;
- UUID and Long backing omit `length`;
- embedded own IDs remain `updatable = false`;
- reference Strong IDs retain the owning field's nullability and write controls.

No `AttributeConverter` may bridge String, UUID, or Long. If Hibernate requires such a bridge for a purportedly supported combination, that is a rollback trigger.

JPA hydration must not allocate an identifier. It may populate an uninitialized String/UUID property or replace the Long `0L` placeholder through field access.

## JSON Contract

Every Strong ID is a scalar JSON string:

```json
{
  "orderId": "019c0000-0000-7000-8000-000000000001",
  "lineId": "7288198123456789012"
}
```

Object form is forbidden:

```json
{ "orderId": { "value": "..." } }
```

Numeric Snowflake form is also forbidden:

```json
{ "lineId": 7288198123456789012 }
```

Serialization and deserialization must be tested for all four application-side combinations. Equality and persistence remain based on the backing value, not on JSON text nodes.

## Generated Own-ID Assignment SPI

### Shared Assignment Helper

`ddd-core` owns idempotent assignment mechanics:

```kotlin
object GeneratedOwnId {
    fun <ID : Any> assignIfMissing(
        current: () -> ID?,
        assign: (ID) -> Unit,
        next: () -> ID,
        label: String,
    ): ID {
        val existing = current()
        if (existing != null) return existing

        val generated = next()
        assign(generated)
        return current() ?: error("generated own ID assignment failed: $label")
    }
}
```

Required behavior:

- read first;
- preserve an existing ID;
- call `next()` exactly once when missing;
- assign once;
- read back and fail with `label` if assignment did not stick;
- never swallow getter, generator, or setter failures.

The only missing-value helper for `lateinit` is:

```kotlin
inline fun <ID : Any> readInitializedOrNull(read: () -> ID): ID? =
    try {
        read()
    } catch (_: UninitializedPropertyAccessException) {
        null
    }
```

It catches only `UninitializedPropertyAccessException`.

### Typed Accessor

Required framework SPI:

```kotlin
interface GeneratedOwnIdAccessor<E : Any, ID : Any> {
    val entityType: KClass<E>
    val label: String

    fun current(entity: E): ID?
    fun assign(entity: E, id: ID)
    fun next(): ID

    fun assignIfMissing(entity: E): ID =
        GeneratedOwnId.assignIfMissing(
            current = { current(entity) },
            assign = { assign(entity, it) },
            next = ::next,
            label = label,
        )
}
```

The accessor contains no `idType`, erased handle, reflection metadata, Spring dependency, or owner/root marker.

For each application-side own Strong ID entity, generate an object in the entity package:

```kotlin
object OrderLineGeneratedOwnIdAccessor :
    GeneratedOwnIdAccessor<OrderLine, OrderLineId> {

    override val entityType: KClass<OrderLine> = OrderLine::class
    override val label: String = "OrderLine.id"

    override fun current(entity: OrderLine): OrderLineId? =
        readInitializedOrNull { entity.id }

    override fun assign(entity: OrderLine, id: OrderLineId) {
        entity.id = id
    }

    override fun next(): OrderLineId =
        OrderLineId.of(
            Mediator.identifiers.next("uuid7", UUID::class)
        )
}
```

The generator substitutes the resolved strategy and backing type. The accessor is in the entity's Kotlin module and package so it can use the generated `internal set` ID property. It is stateless and is not a Spring bean.

### Catalog And Registry

Required contracts:

```kotlin
interface GeneratedOwnIdCatalog {
    val accessors: List<GeneratedOwnIdAccessor<*, *>>
}

interface GeneratedOwnIdRegistry {
    fun accessorFor(entityType: KClass<*>): GeneratedOwnIdAccessor<Any, Any>?
}
```

`MapBackedGeneratedOwnIdRegistry` owns the only unchecked generic cast. It flattens catalogs, indexes by `entityType`, and fails immediately on duplicates.

Generate exactly one catalog contribution per generation target in `<basePackage>.domain._share.identity`:

```kotlin
@Component("com.example.domain._share.identity.generatedOwnIdCatalogContribution")
class GeneratedOwnIdCatalogContribution : GeneratedOwnIdCatalog {
    override val accessors: List<GeneratedOwnIdAccessor<*, *>> = listOf(
        OrderGeneratedOwnIdAccessor,
        OrderLineGeneratedOwnIdAccessor,
    )
}
```

The class deliberately has no `Impl` suffix. The fully qualified bean name prevents collisions between domain modules. Two generation targets producing the same FQN are a configuration collision and must fail rather than receive a random suffix.

Starter auto-configuration collects `List<GeneratedOwnIdCatalog>` and publishes a default registry. With no catalogs, the default registry is empty. A user-provided registry may replace it through normal conditional-bean behavior.

Handwritten advanced code may opt into framework-managed application-side IDs only by providing a Strong ID type, a typed accessor, and a catalog contribution. A plain handwritten JPA entity is not inferred as an application-side domain entity.

## Generated Entity And Factory Shape

An application-side own Strong ID is removed from generated constructor parameters and factory payloads.

Before Phase 4:

```kotlin
class Order internal constructor(
    id: OrderId,
    title: String,
) {
    @EmbeddedId
    var id: OrderId = id
        internal set
}
```

After Phase 4 for String or UUID backing:

```kotlin
class Order internal constructor(
    title: String,
) {
    @EmbeddedId
    lateinit var id: OrderId
        internal set
}
```

After Phase 4 for Long-backed Strong ID, the entity property is still a Strong ID reference and can therefore remain `lateinit`; only the embeddable's inner primitive `value` needs the protected `0L` JPA placeholder.

Rules:

- database identity constructor behavior remains unchanged;
- reference Strong IDs remain normal business inputs where the model requires them;
- preassigned advanced IDs may be set through generated/internal code before lifecycle entry and are preserved;
- ordinary factory payloads do not expose framework-managed own IDs;
- aggregate factory templates construct the graph but do not allocate IDs directly;
- `DefaultAggregateFactorySupervisor.create(...)` enrolls the returned root as `CREATE`, and that enrollment makes the root graph ID-ready before `create(...)` returns.

An aggregate factory may accept owned-child specs in its payload and call generated entity constructors while constructing the graph. No independent child factory is introduced.

## Owned Relation Contract

`OwnedEntityList` remains the business-facing relation abstraction. It must not import or locate identifier infrastructure.

The generated implementation/base receives a narrow pre-mutation hook:

```kotlin
protected open fun prepareEntry(entity: E) = Unit
```

Generated owned relations override or supply this hook with a direct typed call:

```kotlin
OrderLineGeneratedOwnIdAccessor.assignIfMissing(line)
```

Required ordering:

- `add(child)` calls `prepareEntry(child)` before changing the backing collection;
- owned-one `replace(child)` calls `prepareEntry(child)` before clearing or replacing the old child;
- `replace(null)` performs no ID allocation;
- a failed accessor call leaves the previous relation unchanged;
- a preassigned ID is preserved;
- the child ID is readable when the aggregate method returns.

Only generated owning relations receive this hook. Database-identity children, reference relationships, inverse relationships, and non-owned relations do not.

The generated hook may reference the generated accessor object directly. Entity instances are not Spring-managed, and no runtime lookup is required for the domain mutation path.

## UoW Contract

### Root-Oriented Public Meaning

`UnitOfWork.persist(entity, intent)` remains publicly shaped as today, but framework semantics are root-oriented:

- `CREATE` means a newly created top-level persistence intent;
- `EXISTING` means a repository-observed or provider-managed existing top-level persistence intent;
- owned children are represented by the root graph, not independent final entries.

No new public marker is added. The pending root entry created by `persist(root, intent)` is sufficient runtime evidence.

An isolated caller-declared `persist(entity, CREATE)` with no reachable pending owner cannot be proven to be a child without adding static root/child metadata, which this phase rejects. It remains a caller-declared top-level entry. The framework does not add defensive metadata solely to police that misuse.

A plain database-identity JPA entity continues through provider-managed behavior. A handwritten application-side entity receives framework allocation only when it contributes the explicit accessor/catalog SPI.

### Registry-Only Completion

At `persist(root, CREATE)` and final save preparation:

1. normalize a Hibernate proxy to its persistent entity class when necessary;
2. look up each reachable generated entity type in `GeneratedOwnIdRegistry`;
3. call `assignIfMissing` when an accessor exists;
4. leave database identity and non-own reference values alone;
5. propagate any accessor failure;
6. never fall back to reflection.

For `EXISTING` roots:

- validate repository observation evidence first;
- preserve observed root and baseline child identities;
- allocate only missing IDs on newly reachable owned entities;
- do not classify a clean root as dirty merely because completion was checked.

JPA UoW must not discover generated own IDs by:

- scanning `@EmbeddedId`;
- checking `StrongId` assignability;
- reading a companion object;
- invoking `new()` reflectively;
- finding `@ApplicationSideId`.

### Pending Owned-Child Reconciliation

The final pending set contains top-level entries only.

Root-first flow:

```text
persist(root, EXISTING)
root.lines.add(child)
persist(child, CREATE)
        |
        v
child is reachable from pending root
        |
        v
complete child ID, keep root entry, remove child entry
```

Child-first flow:

```text
persist(child, CREATE)
persist(root, EXISTING)
        |
        v
final save reconciliation traverses pending top-level graphs
        |
        v
child is absorbed into outermost root entry
```

Rules:

- reconcile before interceptor, processing, persisted, removed, or JPA application sets are finalized;
- find the outermost pending top-level root through existing bounded owned traversal;
- assign the child's missing ID idempotently while completing the root graph;
- remove the standalone child entry;
- never call `EntityManager.persist/merge` independently for the reconciled child;
- root-oriented interceptors and listeners receive the root, not a synthetic child persistence claim;
- the same child reachable from two unrelated pending roots fails deterministically;
- an observed existing child with no pending owner remains invalid as a standalone top-level `EXISTING` target;
- direct `remove(child)` remains rejected;
- UoW never mutates aggregate relations to perform reconciliation.

## Lifecycle Timing Matrix

| Path | Root ID ready | New owned-child ID ready | Final UoW backstop |
|---|---|---|---|
| aggregate factory with child specs | before `factorySupervisor.create()` returns | before `create()` returns | yes |
| aggregate method adds child | already present on loaded root | before relation mutation returns | yes |
| direct `persist(root, CREATE)` | before `persist` returns | before `persist` returns for reachable graph | yes |
| `persist(root, EXISTING)` with new child | preserved | before `persist` returns | yes |
| JPA hydration | loaded from storage | loaded from storage | no allocation during hydration |
| database identity root/child | after provider insert/flush | after provider insert/flush | not accessor-managed |

Repeated lifecycle entry and final save completion are expected and must be idempotent.

## Complete Runtime Flows

### Factory Creates A New Aggregate Graph

```text
external caller supplies aggregate factory payload
        |
        v
generated aggregate factory constructs root and children from specs
        |
        v
DefaultAggregateFactorySupervisor persists root with CREATE intent
        |
        v
UoW traverses generated owning graph
        |
        v
registry accessors allocate backing primitives through Mediator.identifiers
        |
        v
accessors wrap primitives with entity-specific StrongId.of(...)
        |
        v
create(...) returns ID-ready root and graph
```

### Existing Aggregate Adds A Child

```text
repository loads existing root and records observation baseline
        |
        v
aggregate method constructs child
        |
        v
generated owned-relation hook calls child accessor
        |
        v
child receives ID before relation mutation completes
        |
        v
persist(root, EXISTING) completes graph idempotently
        |
        v
JPA cascades child through root graph
```

### Pending Child Returns To Its Root

```text
root and child both appear in pending entries
        |
        v
bounded owned traversal proves one top-level owner
        |
        v
root graph completion guarantees child ID
        |
        v
child pending entry is removed
        |
        v
interceptors and JPA receive root-oriented entry set
```

## Generator Flow

The generator must perform these steps in order:

1. read `DbIdStrategy` and raw JDBC evidence from the primary-key column;
2. reject unsupported or incomplete strategy/storage combinations;
3. resolve own Strong ID type name and backing `valueType`;
4. validate aggregate-reference physical compatibility;
5. project `columnLength` only for character mapping;
6. plan the Strong ID artifact with strategy-specific validation and generic backing;
7. plan one generated own-ID accessor for every application-side own ID entity;
8. plan one module catalog when at least one accessor exists;
9. remove application-side own IDs from entity constructors and factory payloads;
10. plan direct child accessor hooks on generated owned relations;
11. render generated source artifacts with overwrite ownership;
12. compile a generated consumer fixture before claiming output validity.

Do not emit an empty catalog artifact. Database identity entities, reference Strong IDs, aggregate-reference-only Strong IDs, parent references, and unsupported composite IDs do not receive accessors.

## Generated Artifact Ownership

The two new artifact families pass the Skeleton Generation Gate as follows:

| Artifact | Output | Ownership | Conflict policy | Handwritten slots |
|---|---|---|---|---|
| per-entity own-ID accessor | generated Kotlin source beside entity package | generator | overwrite | none |
| module catalog contribution | `<basePackage>.domain._share.identity` generated Kotlin source | generator | overwrite | none |

Both artifacts are deterministic generated source under the existing generated-source root. They are not handwritten skeletons and must not use merge/preserve conflict policy.

## Legacy Deletion Contract

Phase 4 deletes all active support for `@ApplicationSideId`:

- annotation type from `ddd-core`;
- `JpaApplicationSideIdSupport`;
- UoW registry constructor dependency and annotation-specific assignment/identity/refresh/validation branches;
- starter injection of `IdentifierStrategyRegistry` into UoW;
- dedicated unit/runtime fixtures and annotation tests;
- compiler-plugin implicit aggregate inference from the annotation;
- dead generator planning and renderer context fields.

`IdentifierStrategyRegistry`, `IdentifierGenerator`, `Uuid7IdentifierStrategy`, and `SnowflakeIdentifierStrategy` remain. They are the formal primitive allocation infrastructure used by generated accessors.

Phase 4 also deletes active `snowflake-long` vocabulary:

- no strategy constant;
- no parser alias;
- no deprecated wrapper;
- no dedicated compatibility error branch;
- no active test fixture using the old name.

Historical superpowers specs and plans remain unchanged as records of earlier decisions. Static zero-residue scans exclude `docs/superpowers/specs/**` and `docs/superpowers/plans/**`. The active root `AGENTS.md` architectural statement must be updated during implementation.

## Examples

### Mainline Factory Creation

```kotlin
val order = mediator.create(
    CreateOrder.Payload(
        customerId = customerId,
        lines = listOf(CreateOrderLineSpec("SKU-1")),
    )
)

val orderId: OrderId = order.id
val lineId: OrderLineId = order.lines.single().id
```

The caller supplies neither ID. Both are available before `create()` returns.

### Mainline Aggregate Method

```kotlin
val line = order.addLine("SKU-2")
val lineId: OrderLineId = line.id
```

The generated relation hook assigns `line.id` before the relation mutation returns.

### Preassigned Advanced ID

```kotlin
val id = OrderId.parse("019c0000-0000-7000-8000-000000000001")
// generated/internal advanced construction assigns id before lifecycle entry
unitOfWork.persist(order, PersistIntent.CREATE)
check(order.id == id)
```

Completion preserves the existing value.

### Invalid Snowflake JSON

```json
{ "orderId": 7288198123456789012 }
```

This fails deserialization. The accepted form is:

```json
{ "orderId": "7288198123456789012" }
```

### Unsupported Storage

```sql
id INT PRIMARY KEY COMMENT '@IdStrategy=snowflake;'
```

Generation fails because Snowflake application-side IDs require BIGINT/Long or a character column of at least 19 characters.

### Missing Metadata

A synthetic `DbColumnSnapshot` declares `UUID7` but has `jdbcType = null`. Canonical assembly fails. It must not infer character storage from `kotlinType = "String"`.

### Plain Handwritten JPA Entity

A handwritten entity with an application-assigned primitive ID and no generated accessor/catalog receives no framework allocation. It is not recognized through annotations, field names, `@EmbeddedId`, or primitive type heuristics.

## Rejected Alternatives

### Keep Strong ID `new()`

Rejected because it makes a value object locate or embed allocation infrastructure. Allocation belongs in the generated accessor, which already knows strategy and backing type.

### Accessor Converts Primitive Output

Rejected because both built-in strategies already support their required String/native output types. The accessor requests the correct type directly.

### JPA Converter For Storage Differences

Rejected because it weakens storage-nearest semantics and hides incompatible schema. Unsupported crossed storage fails generation.

### Backing Override In Author Metadata

Rejected because storage is the source of truth. An override would allow domain output to contradict persistence evidence.

### Reflection Fallback

Rejected because missing accessors or catalogs are generation/assembly defects. Reflection would hide those defects and recreate dual semantics.

### Preserve `@ApplicationSideId`

Rejected because it is an unneeded handwritten compatibility path with a second discovery and assignment model. There are no external users.

### Preserve `snowflake-long` Alias

Rejected because the runtime already uses `snowflake`. An alias preserves vocabulary and test surface without user value.

### Inject Identifier Infrastructure Into `OwnedEntityList`

Rejected because the business relation carrier should not know Mediator, Repository, UoW, JPA, or identifier generation. Generated relation code invokes the typed accessor.

### Public Child Factory

Rejected because factory creation remains aggregate-oriented. Owned child specs are aggregate factory payload members, and aggregate methods are the other child creation entry point.

### Public `persist(child)` Contract

Rejected because persistence intent is root-oriented. Reconciliation tolerates a child that is already reachable from a pending root, but this is not a new documented child persistence API.

### Additional Root/Child Markers

Rejected because pending root entries plus generated owning traversal are sufficient for the supported reconciliation cases. The framework does not add metadata to defend against an isolated caller-declared misuse.

### Child-Level Listener Publication

Rejected for Phase 4. Reconciled children remain graph state and do not create independent lifecycle/audit claims.

## Migration

This is an intentional breaking internal release.

### Generated Code

- regenerate all affected aggregates;
- application-side own IDs disappear from entity constructors and factory payloads;
- generated Strong IDs change to `StrongId<V>` and lose `new()`;
- generated accessors and one module catalog appear;
- generated owned relations call typed accessors before mutation;
- character `AttributeOverride` length follows database metadata.

### Handwritten Calls

- replace `SomeId.new()` with official aggregate creation or aggregate methods;
- tests that need a fixed ID use `SomeId.of(...)` or `SomeId.parse(...)`;
- handwritten application-side entities must provide Strong ID/accessor/catalog or stop expecting framework allocation;
- `@ApplicationSideId` imports and annotations must be deleted;
- `snowflake-long` configuration must change to `snowflake`.

### Runtime

- UoW no longer receives `IdentifierStrategyRegistry`;
- UoW receives `GeneratedOwnIdRegistry`;
- generated ID completion becomes registry-only;
- pending children reconcile into root entries;
- database identity remains provider-managed.

### Tests

Do not merely delete behavior coverage with the legacy fixtures. Migrate useful assertions to generated-style Strong ID fixtures:

- preassigned ID preservation;
- assignment before transaction interceptors;
- existing merge without identity lookup regression;
- root and child ID readiness;
- repeated completion idempotence.

Historical docs are not migration targets.

## Verification Strategy

Claim strength must match executed evidence. Static inspection proves structure only. Compilation proves generated Kotlin shape only. Runtime behavior requires the focused Gradle tests.

### Metadata And Resolution Evidence

Tests must prove:

- DB source retains JDBC `DATA_TYPE` and `COLUMN_SIZE`;
- parser accepts `snowflake` and no old alias;
- UUID7 character/native UUID resolution;
- Snowflake character/BIGINT resolution;
- exact backing type in `StrongIdModel`;
- character `columnLength` projection;
- missing metadata rejection;
- insufficient character capacity rejection;
- unsupported INT/DECIMAL/native mismatch rejection;
- aggregate-reference storage compatibility rejection.

### Strong ID Value Evidence

For each supported backing, prove:

- valid `of` and `parse`;
- canonical `toString`;
- equality and hash code;
- invalid, zero, negative, noncanonical, wrong-version, nil, or overflow rejection as applicable;
- scalar-string JSON serialization;
- string-only Snowflake deserialization;
- no object JSON shape;
- JPA hydration/persist/reload using direct mapping.

### Generator Evidence

Prove:

- one accessor per application-side own ID entity;
- one module catalog containing all accessors;
- stable package-qualified bean name;
- no catalog when no accessor exists;
- no accessor for database identity or reference IDs;
- accessor calls `Mediator.identifiers.next(strategy, BackingType::class)` then `StrongId.of`;
- constructor and payload own-ID exclusion;
- child relation direct accessor hook;
- actual length only for String mapping;
- no converter, `new()`, or legacy context;
- generated consumer sources compile.

### Lifecycle Evidence

Prove:

- aggregate factory root and spec-created children are ID-ready before `create()` returns;
- aggregate-method child is ID-ready before relation mutation returns;
- failed child allocation leaves relation unchanged;
- direct root `CREATE` completes the reachable graph before returning;
- existing IDs are preserved;
- final save completion is idempotent;
- existing root baseline identities remain unchanged;
- new child under existing root persists;
- clean root is not reported as an update only due to completion;
- JPA hydration never allocates;
- database identity remains provider-managed.

### Reconciliation Evidence

Prove:

- root-first child enrollment retains root entry only;
- child-first enrollment converges to the same root entry set;
- nested child converges to the outermost root;
- two unrelated owners fail deterministically;
- observed child without pending owner remains rejected;
- direct child remove remains rejected;
- reconciled child is absent from independent JPA/interceptor/listener sets.

### Legacy Deletion Evidence

Active source and tests must contain no:

- `ApplicationSideId` type or import;
- `JpaApplicationSideIdSupport`;
- compiler inference based on that annotation;
- `applicationSideIdStrategy` or `hasApplicationSideIdFields` context;
- `snowflake-long` strategy vocabulary;
- legacy runtime fixture.

Static scans deliberately exclude historical `docs/superpowers/specs/**` and `docs/superpowers/plans/**`.

### Required Static Checks

The implementation plan must run equivalent checks with repository-valid PowerShell syntax:

```powershell
rg -n 'getAnnotation\(EmbeddedId|StrongId::class\.java\.isAssignableFrom|type\.getField\("Companion"\)|method\.name == "new"' ddd-domain-repo-jpa/src/main/kotlin

rg -n 'ApplicationSideId|JpaApplicationSideIdSupport|applicationSideIdStrategy|hasApplicationSideIdFields|snowflake-long' `
  ddd-core/src ddd-domain-repo-jpa/src cap4k-ddd-starter/src `
  cap4k-plugin-pipeline-api/src cap4k-plugin-pipeline-core/src `
  cap4k-plugin-pipeline-source-db/src cap4k-plugin-pipeline-generator-aggregate/src `
  cap4k-plugin-pipeline-renderer-pebble/src cap4k-plugin-code-analysis-compiler/src AGENTS.md

rg -n 'fun new\(' cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb

rg -n 'UnitOfWork|Repository|EntityManager|IdentifierGenerator|Mediator' `
  ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt
```

All checks above expect no match after implementation. A match must be explained and reviewed; do not create a broad allowlist.

## Rollback Triggers

Stop implementation and revise this design if any of these facts appear:

- supported database metadata cannot reliably distinguish character, native UUID, and BIGINT/Long storage;
- a supported combination requires a cross-representation JPA converter;
- Hibernate cannot hydrate the generic embeddable shapes without public mutable construction or allocation side effects;
- Jackson cannot enforce scalar-string output and string-only Snowflake input;
- generated accessors cannot assign `internal set` IDs from generated code without making setters public;
- factory supervisor enrollment cannot make the complete factory-built graph ID-ready before returning;
- owned relation code cannot allocate before mutation while preserving old state on failure;
- registry lookup cannot normalize Hibernate proxies reliably;
- pending reconciliation cannot occur before interceptor/JPA input construction;
- root-first and child-first enrollment do not converge to the same final entry set;
- bounded traversal cannot distinguish nested ownership from two unrelated top-level owners;
- catalog FQNs or bean names require random or filesystem-derived identity;
- completing Phase 4 requires broad weak/inverse relation traversal;
- child-level lifecycle notifications become a required acceptance criterion.

The following are not rollback triggers:

- old compatibility tests fail;
- old annotation imports stop compiling;
- `snowflake-long` is no longer recognized;
- a snapshot without JDBC evidence now fails;
- a crossed storage schema now fails;
- user code calling generated Strong ID `new()` must migrate.

Do not respond to a rollback trigger by adding reflection, a converter, an alias, guessed metadata, or a compatibility annotation.

## Agent Handoff Notes

Implementation may modify:

- `ddd-core` Strong ID contracts, helpers, accessor/catalog/registry SPI, `OwnedEntityList`, and focused tests;
- `cap4k-plugin-pipeline-api` metadata models;
- `cap4k-plugin-pipeline-source-db` parser, JDBC snapshot capture, and tests;
- `cap4k-plugin-pipeline-core` ID/storage resolution, canonical projection, and tests;
- aggregate generator planners, contexts, artifact IDs, templates, renderer tests, and generated-consumer fixtures;
- `ddd-domain-repo-jpa` registry completion, UoW reconciliation, legacy deletion, and tests;
- `cap4k-ddd-starter` registry assembly and runtime fixtures;
- code-analysis compiler fallback deletion and tests;
- active root `AGENTS.md` identity statement.

Implementation must not modify:

- `AggregateSoftDeletePolicyResolver`, soft-delete SQL planning, or soft-delete templates;
- completed Phase 3.75 spec/plan;
- unrelated dirty roadmap and historical design files;
- historical specs/plans solely to remove old words;
- public child factories, root/child marker metadata, or listener semantics;
- weak reference, inverse relation, or arbitrary association traversal.

Implementation rules:

- follow the implementation plan task order and failing-test-first steps;
- keep every task compiling before moving to the next task;
- use generated-source overwrite ownership for accessor and catalog artifacts;
- delete old code when its replacement task lands;
- do not leave temporary fallback code in the final tree;
- record actual Gradle command results and skipped checks;
- preserve unrelated user changes in the worktree.

## Final Authoring Story

```kotlin
val order = mediator.create(CreateOrder.Payload(...))
val orderId = order.id

val line = order.addLine("SKU-1")
val lineId = line.id

mediator.save()
```

The user supplies neither primary key. The Strong IDs remain real entity-specific value objects, their backing matches storage directly, and cap4k makes them available at the domain lifecycle moment where they become useful.

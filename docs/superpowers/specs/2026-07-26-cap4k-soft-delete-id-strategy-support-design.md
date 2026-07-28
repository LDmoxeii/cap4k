# cap4k Soft Delete Existing IdStrategy Support Design

**Date:** 2026-07-26
**Status:** Approved for implementation
**Depends on:** PR #136 / completed Strong ID create-time injection Phase 4

## Reader Contract

After reading this document, an implementation agent with no chat history must be able to answer all of the following without guessing:

- Why the current soft-delete implementation supports numeric IDs but does not close the UUID7 path?
- Which ID strategies and physical storage combinations are supported?
- Why Snowflake remains supported, including both String and Long Strong ID backings?
- Which metadata proves integral, character, or native UUID storage?
- Which active sentinel belongs to each strategy and storage combination?
- Why the deleted property is not a Strong ID even though it stores the row ID after deletion?
- Why the deleted property must remain mapped but must not appear in entity constructors or factory payloads?
- Which layer validates semantics, which layer renders SQL, and which layer performs the runtime transition?
- How MySQL, MariaDB, H2, and PostgreSQL render identifiers and sentinel literals?
- What happens when JDBC metadata, a database default, a storage combination, or a SQL dialect is unsupported?
- Which old API fields and generated constructor behavior are intentionally broken?
- Which tests prove that create, query filtering, and soft delete work against a real database?
- Which files are in scope and which adjacent ID, UoW, Snowflake, and historical-document areas must not be changed?

If an implementation choice is not derivable from this document or the current code evidence below, implementation must stop and revise the design. It must not add a fallback.

## Current Evidence

This design is based on current master after PR #136. It supersedes only the numeric-only limitations of the earlier soft-delete discriminator design; it does not rewrite that historical document.

Key current locations:

| Current behavior | File and current line |
|---|---|
| DbIdStrategy and AggregateSoftDeletePolicy API | cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt:42 and :363 |
| numeric-only soft-delete resolution | cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt:10 |
| String, UUID, and Long Strong ID backing resolution | cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolver.kt:8 |
| Snowflake and UUID7 generated own-ID planning | cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdPlanning.kt:20 |
| deleted currently leaks into constructorFields | cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt:253 |
| DB URL quote-style fallback | cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt:353 |
| generated entity constructor/property rendering | cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb:69 |

### Numeric-Only Soft Delete

The current resolver:

- fixes the active value to zero;
- requires the deleted column to be non-null with default zero;
- parses only TINYINT, SMALLINT, MEDIUMINT, INT, INTEGER, and BIGINT storage;
- validates signedness and bit capacity;
- publishes final SQL fragments from core.

Evidence:

- cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt
- cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt

The current AggregateSoftDeletePolicy carries:

~~~kotlin
val activeValue: String
val activePredicateSql: String
val deleteAssignmentSql: String
~~~

This mixes semantic policy with provider-specific SQL rendering.

### Strong ID Storage Evidence

PR #136 established:

- generic StrongId<V> value objects;
- UUID7 String and native UUID backings;
- Snowflake String and Long backings;
- JDBC DATA_TYPE and COLUMN_SIZE retention;
- generated own-ID accessors and one module catalog;
- create-time allocation through the aggregate lifecycle and UoW graph-completion backstop;
- removal of the old ApplicationSideId annotation path.

AggregateStrongIdBackingResolver already classifies character and native UUID storage, while AggregateSoftDeletePolicyResolver independently classifies integral storage. These two resolvers currently duplicate responsibility instead of consuming one physical-storage catalog.

Evidence:

- cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolver.kt
- cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdPlanning.kt
- cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb
- cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/generated_own_id_accessor.kt.peb

Snowflake is already a complete application-side Strong ID path. Generated accessors request a String or Long primitive from Mediator.identifiers and immediately wrap it with the entity-specific Strong ID factory. Therefore deleting Snowflake is not required to preserve the rule that every application-side entity ID is strongly typed.

### Generated Entity Constructor Leakage

EntityArtifactPlanner currently computes constructor fields as every scalar field except a generated own ID:

~~~kotlin
constructorFields = scalarFields.filterNot { generatedOwnId }
~~~

The default entity template initializes every ordinary scalar property from a constructor parameter. As a result, the soft-delete field can remain in the entity constructor even though AggregateSpecialFieldPolicyResolver classifies it as SYSTEM_TRANSITION_ONLY.

FactoryArtifactPlanner can defer SYSTEM_TRANSITION_ONLY fields when a default is available, but that only hides the parameter from some factory paths. It does not fix the generated entity constructor contract.

Evidence:

- cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt
- cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt
- cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb

### SQL Dialect Fallback

EntityArtifactPlanner currently derives identifier quoting from the DB URL but defaults to double quotes when the URL is missing or unrecognized. MySQL, MariaDB, and H2 MySQL mode use backticks; other inputs use double quotes.

The fallback is not evidence that an unknown database supports the emitted SQL. It must be removed for soft-delete SQL generation.

### JDBC Default Evidence

An actual metadata probe against the repository's H2 2.3.232 dependency produced:

~~~text
BIGINT DEFAULT 0
-> 0

VARCHAR DEFAULT '00000000-0000-0000-0000-000000000000'
-> '00000000-0000-0000-0000-000000000000'

UUID DEFAULT '00000000-0000-0000-0000-000000000000'
-> '00000000-0000-0000-0000-000000000000'
~~~

Default validation must compare semantic sentinel values while recognizing only explicit, tested JDBC wrapper forms. It must not evaluate arbitrary SQL expressions.

A direct H2 2.3.232 MySQL-mode probe also verified the proposed native UUID SQL:

~~~text
active predicate before delete -> 1 row
update deleted = id            -> 1 row updated
active predicate after delete  -> 0 rows
physical deleted = id          -> true
~~~

The probe used backtick identifiers and:

~~~sql
deleted =
CAST('00000000-0000-0000-0000-000000000000' AS UUID)
~~~

This is evidence for H2 only. It is not PostgreSQL verification.

## Problem Statement

The ID infrastructure now supports database identity, UUID7 Strong IDs, and Snowflake Strong IDs, but soft delete still assumes:

~~~text
numeric id
numeric deleted column
active sentinel 0
deleted tombstone self id
~~~

That leaves several gaps:

- UUID7 character storage cannot use the numeric zero contract.
- UUID7 native UUID storage cannot be represented.
- Snowflake String storage is not modeled even though Snowflake Strong IDs support it.
- core publishes SQL instead of semantic policy.
- duplicated type classification can drift between Strong ID and soft-delete decisions.
- generated constructors expose a system-transition field.
- unknown database URLs silently receive double-quoted SQL.
- existing tests mostly compare planned strings and do not close the Hibernate/database runtime loop.

The framework must support every currently approved entity ID strategy without weakening Strong ID value-object semantics or adding conversion fallbacks.

## Scope

This iteration includes:

- one shared module-level AggregateIdStorageCatalog in pipeline core;
- integral, character, and native UUID physical storage classification;
- identity, Snowflake, and UUID7 soft-delete policy resolution;
- ZERO and NIL_UUID active sentinel semantics;
- strict deleted-column nullability, default, capacity, and assignability validation;
- removal of SQL strings from AggregateSoftDeletePolicy;
- dialect-aware SQL rendering in the aggregate generator;
- explicit failure for missing or unsupported soft-delete SQL dialects;
- generated deleted property initialization without a constructor parameter;
- updated default aggregate entity template context and output;
- functional generation and entity compilation for every supported strategy/storage combination;
- real aggregate factory construction for the four application-side ID combinations;
- actual H2 runtime verification;
- actual PostgreSQL verification before native PostgreSQL UUID support is claimed complete.

## Non-Goals

This iteration does not:

- add hard delete, restore, undelete, or purge APIs;
- add configurable/custom sentinel values;
- support multiple deleted columns;
- support timestamp or boolean tombstone strategies;
- support MySQL BINARY(16) UUID storage;
- support cross-storage assignment through casts or converters;
- add soft-delete behavior to arbitrary handwritten JPA entities;
- replace Hibernate SQLDelete or Where with a new UoW soft-delete engine;
- change UoW persistence intent, create-time ID injection, owned-child reconciliation, or graph traversal;
- add an owned-child factory or public child persist API;
- resolve the existing database-identity factory `constructorMappingResolved=false` / `TODO("Implement aggregate construction")` capability gap;
- reintroduce ApplicationSideId;
- remove Snowflake Strong IDs or the distributed Snowflake infrastructure;
- redesign general identifier generation outside aggregate entity IdStrategy;
- migrate historical specs or plans;
- add aliases, deprecated wrappers, or unknown-dialect fallbacks.

## Terms

### Self-ID Tombstone

The deleted row stores its own physical ID value in the deleted column:

~~~sql
deleted = id
~~~

### Active Sentinel

The physical value that marks a row as active. This design supports semantic ZERO and NIL_UUID sentinels.

### Storage Kind

The coarse physical representation used by both ID and deleted columns: INTEGRAL, CHARACTER, or NATIVE_UUID.

### Storage Descriptor

The internal, evidence-bearing result of physical classification. It retains numeric bits and signedness or character capacity in addition to the coarse storage kind.

### Strong ID Backing

The primitive value stored inside an entity-specific Strong ID: String, UUID, or Long.

### Identity Strategy Vocabulary

The DB comment token is db_identity, the snapshot enum is DbIdStrategy.DB_IDENTITY, and the canonical strategy value is identity. They are three representations of the same database-side identity strategy. This document uses identity when discussing semantics and names the concrete token or enum when discussing a code boundary.

### SQL Dialect

The generator-side capability selected from a recognized JDBC URL. It owns identifier quoting and database literal rendering, not core policy.

### System Transition Field

A persisted field that is not supplied by domain creation or update input and changes only through framework-managed lifecycle transitions. The deleted field is currently the relevant SYSTEM_TRANSITION_ONLY field.

## Design Decisions

| Decision | Reason | Excluded Alternative |
|---|---|---|
| Keep Snowflake String and Long Strong IDs | PR #136 already provides strong typing and create-time injection for both backings | deleting Snowflake to reduce the soft-delete matrix |
| Share one AggregateIdStorageCatalog | Strong ID and soft-delete validation must consume the same physical evidence | duplicated JDBC type sets and DB-type parsers |
| Keep storage details internal | Capacity and signedness are validation evidence, not generator API | exposing JDBC details through AggregateSoftDeletePolicy |
| Publish semantic sentinel and storage kind | The generator needs meaning, not pre-rendered SQL | activeValue and SQL fragments in core |
| Require matching storage kinds | SELF_ID must be physically assignable without database-specific casts | character/native or integral/character conversion |
| Compare full physical capacity | The database assignment must be safe for the declared ID column value domain | assuming generated values will always fit a narrower deleted column |
| Keep deleted as a raw storage property | It is a lifecycle discriminator, not a second domain identity | wrapping nil UUID or zero in the entity Strong ID type |
| Remove deleted from constructors | System transition state must not be user creation input | relying on factory defaults while leaking the entity parameter |
| Initialize deleted in generated code | New transient entities need deterministic active state before INSERT | requiring factories or users to pass the sentinel |
| Render SQL only in the generator | SQL quoting and literals depend on the selected database | core-produced SQL strings |
| Reject unknown dialects | There is no evidence an unknown database accepts the generated SQL | defaulting to double quotes |
| Keep Hibernate SQLDelete and Where runtime | Existing provider behavior already expresses the transition and filter | adding a second UoW deletion path |
| Preserve generated Strong ID value-object semantics | The ID property remains StrongId<V>; only deleted uses V | making generated entity IDs primitive again |
| Limit factory-construction evidence to application-side IDs | Snowflake and UUID7 factories already have generated-own-ID construction semantics; database-identity factory mapping is an independent existing capability gap | treating a compiled identity factory `TODO` body as construction evidence or expanding this soft-delete iteration to redesign identity factories |

## Supported Strategy And Storage Matrix

| ID strategy | ID storage/backing | Required deleted storage | Active sentinel | Supported |
|---|---|---|---|---|
| identity / DB_IDENTITY | integral | capacity-compatible integral | ZERO | yes |
| snowflake | integral / Long | capacity-compatible integral | ZERO | yes |
| snowflake | character / String | capacity-compatible character | ZERO | yes |
| uuid7 | character / String | capacity-compatible character | NIL_UUID | yes |
| uuid7 | native UUID / UUID | native UUID | NIL_UUID | yes |
| identity / DB_IDENTITY | non-integral | any | none | no |
| snowflake integral | character or native UUID | none | no |
| snowflake character | integral or native UUID | none | no |
| uuid7 character | integral or native UUID | none | no |
| uuid7 native UUID | integral or character | none | no |

The canonical nil UUID is:

~~~text
00000000-0000-0000-0000-000000000000
~~~

The strategy name is snowflake. No snowflake-long strategy or compatibility alias exists.

## Shared Physical Storage Catalog

Introduce one internal core catalog:

~~~kotlin
internal object AggregateIdStorageCatalog {
    fun resolve(
        tableName: String,
        column: DbColumnSnapshot,
    ): ResolvedAggregateIdStorage
}
~~~

The exact implementation type may be sealed classes or data classes, but it must preserve this information:

~~~kotlin
internal sealed interface ResolvedAggregateIdStorage {
    val kind: AggregateIdStorageKind
    val kotlinType: String

    data class Integral(
        val bits: Int,
        val unsigned: Boolean,
        override val kotlinType: String,
    ) : ResolvedAggregateIdStorage

    data class Character(
        val capacity: Int,
        override val kotlinType: String,
    ) : ResolvedAggregateIdStorage

    data class NativeUuid(
        override val kotlinType: String,
    ) : ResolvedAggregateIdStorage
}
~~~

The catalog is internal to pipeline core. There is no interface plus Impl pair and no service-loader extension point in this iteration.

### Evidence Rules

The catalog uses JDBC metadata as the primary evidence:

- jdbcType identifies the broad SQL family;
- dbType confirms vendor-specific details such as MEDIUMINT, UNSIGNED, or native UUID;
- kotlinType must agree with the physical family;
- columnSize is mandatory for character capacity checks.

Supported integral families are:

~~~text
TINYINT    8 bits
SMALLINT   16 bits
MEDIUMINT  24 bits
INT        32 bits
INTEGER    32 bits
BIGINT     64 bits
~~~

DECIMAL, NUMERIC, floating-point, boolean, date/time, binary UUID encodings, and arbitrary OTHER types are not integral ID storage for this contract.

Supported character JDBC families are:

~~~text
CHAR
VARCHAR
LONGVARCHAR
NCHAR
NVARCHAR
LONGNVARCHAR
~~~

Character storage requires:

~~~text
kotlinType = String or kotlin.String
columnSize is present and positive
~~~

Native UUID requires all of:

~~~text
jdbcType = OTHER or BINARY
dbType = uuid, case-insensitive
kotlinType = UUID or java.util.UUID
~~~

The BINARY JDBC code is accepted only when the vendor type name is exactly uuid. MySQL BINARY(16) is not inferred as native UUID.

Missing or contradictory evidence is an error. The catalog does not return UNKNOWN and callers do not retry with Kotlin-type heuristics.

### Catalog Consumers

AggregateStrongIdBackingResolver must consume the catalog rather than owning independent character/native UUID detection.

AggregateSoftDeletePolicyResolver must consume the same catalog for both the ID column and deleted column rather than owning an independent numeric parser.

Strong ID rules remain strategy-specific:

- UUID7 accepts CHARACTER or NATIVE_UUID;
- Snowflake accepts CHARACTER or signed BIGINT;
- database identity remains governed by its existing supported integral policy.

The shared catalog classifies storage. It does not decide which strategies may use that storage.

## Semantic API Model

Add these public pipeline API enums:

~~~kotlin
enum class AggregateIdStorageKind {
    INTEGRAL,
    CHARACTER,
    NATIVE_UUID,
}

enum class SoftDeleteActiveSentinel {
    ZERO,
    NIL_UUID,
}
~~~

Replace AggregateSoftDeletePolicy with:

~~~kotlin
data class AggregateSoftDeletePolicy(
    val fieldName: String,
    val columnName: String,
    val storageKind: AggregateIdStorageKind,
    val activeSentinel: SoftDeleteActiveSentinel,
    val tombstoneStrategy: SoftDeleteTombstoneStrategy,
)
~~~

SoftDeleteTombstoneStrategy remains:

~~~kotlin
enum class SoftDeleteTombstoneStrategy {
    SELF_ID,
}
~~~

Delete these fields without compatibility aliases:

~~~kotlin
activeValue
activePredicateSql
deleteAssignmentSql
~~~

The policy contains only facts that remain true across supported SQL dialects.

## Policy Resolution

AggregateSoftDeletePolicyResolver resolves in this order:

1. Return null when the deleted marker is disabled.
2. Resolve the physical ID and deleted columns.
3. Resolve both physical storage descriptors through AggregateIdStorageCatalog.
4. Resolve the ID strategy.
5. Select the required active sentinel from the strategy.
6. Validate storage-kind compatibility.
7. Validate physical capacity and Kotlin-type compatibility.
8. Validate deleted nullability.
9. Validate the declared database default against the selected sentinel.
10. Publish AggregateSoftDeletePolicy.

Strategy-to-sentinel selection is:

~~~text
identity   -> ZERO
snowflake   -> ZERO
uuid7       -> NIL_UUID
~~~

The active sentinel belongs to the strategy semantics, not merely to the physical column type. CHARACTER + ZERO means Snowflake String storage, while CHARACTER + NIL_UUID means UUID7 String storage.

## Assignability Rules

### Integral

The deleted integral descriptor must be able to contain the complete ID descriptor range.

~~~kotlin
fun canStore(source: Integral): Boolean = when {
    source.unsigned == unsigned -> bits >= source.bits
    !source.unsigned && unsigned -> false
    else -> bits > source.bits
}
~~~

Examples:

~~~text
id INT                -> deleted BIGINT            allowed
id BIGINT             -> deleted INT               rejected
id INT UNSIGNED       -> deleted BIGINT            allowed
id BIGINT UNSIGNED    -> deleted BIGINT            rejected
id INT signed         -> deleted INT UNSIGNED      rejected
~~~

Snowflake Long storage is signed BIGINT. It therefore requires a deleted integral column that can contain the signed 64-bit source range.

### Character

Both columns must resolve to CHARACTER and:

~~~text
deleted.capacity >= id.capacity
id Kotlin backing = String
deleted Kotlin type = String
~~~

Examples:

~~~text
id CHAR(36)       -> deleted CHAR(36)       allowed
id VARCHAR(36)    -> deleted VARCHAR(64)    allowed
id VARCHAR(64)    -> deleted VARCHAR(36)    rejected
~~~

The comparison uses declared physical capacity. It does not assume that all historical values happen to be shorter than the ID column permits.

### Native UUID

Both columns must resolve to NATIVE_UUID and map to UUID. Native UUID has no character-capacity comparison.

### Cross-Storage Assignment

All cross-storage combinations are rejected, even if one database can implicitly cast them:

~~~text
INTEGRAL -> CHARACTER
CHARACTER -> INTEGRAL
CHARACTER -> NATIVE_UUID
NATIVE_UUID -> CHARACTER
INTEGRAL -> NATIVE_UUID
NATIVE_UUID -> INTEGRAL
~~~

The default generated SQL is always a direct physical assignment:

~~~sql
deleted = id
~~~

It never inserts a cast between the two columns.

## Deleted Column Contract

For every supported combination, the deleted column must:

- exist;
- be non-null;
- use the same supported storage kind as the ID column;
- have sufficient capacity;
- map to the expected raw Kotlin storage type;
- declare a database default semantically equal to the active sentinel.

The generated entity initializer and the database default must agree, but they serve different boundaries:

- the generated initializer gives a new transient entity deterministic active state and supplies the INSERT value;
- the database default protects the schema contract and non-ORM inserts.

The framework does not remove the database-default requirement merely because generated entities initialize the property.

## Default Sentinel Normalization

Default validation compares a bounded semantic literal rather than raw JDBC text.

The normalizer may:

1. trim whitespace;
2. repeatedly remove balanced outer parentheses;
3. unwrap one SQL single-quoted literal, including escaped single quotes;
4. unwrap a supported typed UUID literal;
5. unwrap a standard CAST expression whose target agrees with the resolved storage kind;
6. unwrap a PostgreSQL postfix cast whose target agrees with the resolved storage kind.

Examples that normalize successfully:

~~~sql
0
(0)
'0'
0::bigint
'0'::character varying

'00000000-0000-0000-0000-000000000000'
UUID '00000000-0000-0000-0000-000000000000'
CAST('00000000-0000-0000-0000-000000000000' AS UUID)
'00000000-0000-0000-0000-000000000000'::uuid
~~~

The accepted semantic values are exactly:

~~~text
ZERO     -> 0
NIL_UUID -> 00000000-0000-0000-0000-000000000000
~~~

The normalizer must reject:

~~~sql
NULL
''
1
gen_random_uuid()
uuid_nil()
current_timestamp
an arbitrary function or expression
~~~

The implementation must not evaluate SQL, connect to the database to compute a default, or treat an unrecognized expression as equivalent.

When a supported JDBC driver returns a new wrapper form, first capture that exact metadata in a test, then add the smallest explicit normalizer branch.

## Error Contract

Failures must identify the physical path and the evidence that caused rejection.

Storage mismatch example:

~~~text
soft delete storage mismatch for category.deleted:
id category.id is NATIVE_UUID(kotlinType=UUID),
deleted is CHARACTER(capacity=36, kotlinType=String);
cross-storage SELF_ID assignment is not supported
~~~

Capacity example:

~~~text
soft delete storage mismatch for category.deleted:
id category.id is CHARACTER(capacity=64, kotlinType=String),
deleted is CHARACTER(capacity=36, kotlinType=String);
deleted must store the complete id column value
~~~

Default example:

~~~text
soft delete column category.deleted requires active default
00000000-0000-0000-0000-000000000000 for uuid7,
got 0
~~~

Errors must not silently disable soft delete, change the sentinel, change the ID strategy, or select a different physical backing.

## Runtime And Generator Flow

The complete flow is:

~~~text
DB schema metadata
  |
  | idStrategy / jdbcType / dbType / kotlinType
  | columnSize / nullable / defaultValue
  v
AggregateIdStorageCatalog
  |
  |-- resolved ID storage
  '-- resolved deleted storage
  v
AggregateSoftDeletePolicyResolver
  |
  |-- validates strategy and assignability
  |-- validates nullability and default
  '-- emits semantic AggregateSoftDeletePolicy
  v
EntityArtifactPlanner
  |
  |-- resolves a supported SQL dialect
  |-- quotes physical identifiers
  |-- renders the active SQL literal
  |-- renders the Kotlin property initializer
  |-- builds SQLDelete and Where context
  '-- removes deleted from constructor fields
  v
entity.kt.peb
  |
  '-- generates the mapped entity
  v
Hibernate runtime
  |-- CREATE writes the active sentinel
  |-- QUERY applies the active predicate
  '-- DELETE executes deleted = id
~~~

Core never produces SQL. The generator never reclassifies JDBC storage. Hibernate runtime never chooses a sentinel.

## SQL Dialect Contract

Introduce a generator-internal aggregate SQL dialect model. It may use this shape:

~~~kotlin
internal enum class AggregateSqlDialect {
    MYSQL,
    MARIADB,
    H2,
    H2_MYSQL,
    POSTGRESQL,
}
~~~

Resolution is case-insensitive:

| JDBC URL | Dialect | Identifier quote |
|---|---|---|
| jdbc:mysql: | MYSQL | backtick |
| jdbc:mariadb: | MARIADB | backtick |
| jdbc:h2: with MODE=MySQL | H2_MYSQL | backtick |
| other jdbc:h2: | H2 | double quote |
| jdbc:postgresql: | POSTGRESQL | double quote |

Dialect resolution is lazy:

- an entity without semantic soft-delete policy does not need a soft-delete SQL dialect;
- an entity with semantic soft-delete policy requires a recognized DB URL;
- missing DB source URL is an error;
- an unsupported URL is an error.

There is no default dialect and no DOUBLE_QUOTE fallback.

### Identifier Quoting

The generator quotes the exact physical table, deleted, ID, and version names from canonical metadata.

Examples:

~~~sql
-- MySQL / MariaDB / H2 MySQL mode
update `category`
set `deleted` = `id`
where `id` = ?

-- PostgreSQL / standard H2
update "CATEGORY"
set "DELETED" = "ID"
where "ID" = ?
~~~

Quoting is not required because the word deleted is special. It is a deterministic physical-identifier policy that also protects reserved names and exact case.

The H2 runtime test must cover:

- an unquoted schema whose metadata exposes normalized uppercase physical names;
- a quoted mixed-case schema;
- H2 MySQL mode with backticks.

The implementation must fix physical-name propagation if those cases fail. It must not respond by dropping identifier quoting globally.

### Active Literal Rendering

The generator maps semantic policy to SQL:

| Storage kind | Sentinel | SQL literal |
|---|---|---|
| INTEGRAL | ZERO | 0 |
| CHARACTER | ZERO | '0' |
| CHARACTER | NIL_UUID | '00000000-0000-0000-0000-000000000000' |
| NATIVE_UUID | NIL_UUID | CAST('00000000-0000-0000-0000-000000000000' AS UUID) |

NATIVE_UUID + NIL_UUID is initially allowed only for H2, H2 MySQL mode, and PostgreSQL.

The generator uses an explicit UUID cast instead of relying on implicit conversion from a character literal.

Unsupported storage/sentinel or dialect/storage combinations are planner errors.

### SQLDelete Rendering

Versionless:

~~~sql
update <table>
set <deleted> = <id>
where <id> = ?
~~~

Versioned:

~~~sql
update <table>
set <deleted> = <id>
where <id> = ? and <version> = ?
~~~

The parameter order remains the existing Hibernate contract: identifier columns first and version after the identifier.

The SQL is first constructed as raw SQL, then converted to one Kotlin string literal through the existing Kotlin-string-literal renderer.

## Generated Entity Contract

The resolved deleted field remains in scalarFields because it is a persisted JPA property.

It must not remain in constructorFields.

Constructor eligibility must exclude a resolved SYSTEM_TRANSITION_ONLY field. In the current model this applies to deleted. If a future SYSTEM_TRANSITION_ONLY field has no defined initializer semantics, planning must reject it rather than invent a value.

The deleted property uses:

~~~text
raw physical storage Kotlin type
+ semantic active sentinel
= generated property initializer
~~~

Initializer rendering is:

| Kotlin type | Sentinel | Initializer |
|---|---|---|
| Byte | ZERO | 0 |
| Short | ZERO | 0 |
| Int | ZERO | 0 |
| Long | ZERO | 0L |
| String | ZERO | "0" |
| String | NIL_UUID | "00000000-0000-0000-0000-000000000000" |
| UUID | NIL_UUID | UUID(0L, 0L) |

The initializer is derived from semantic policy, not copied from the raw database default expression.

### Strong ID Value-Object Boundary

Given:

~~~kotlin
class CategoryId : StrongId<UUID>
~~~

the generated entity uses:

~~~kotlin
lateinit var id: CategoryId

var deleted: UUID = UUID(0L, 0L)
~~~

It must not use:

~~~kotlin
var deleted: CategoryId = CategoryId.of(UUID(0L, 0L))
~~~

The deleted column is a discriminator. During active state it contains a value that is deliberately invalid as a UUID7 or Snowflake Strong ID. After deletion it contains a physical copy of the ID, but it still is not a second identity property.

No JPA converter is introduced between the Strong ID backing and deleted.

### Write Surface

The deleted field must not appear in:

- entity constructor parameters;
- aggregate factory payloads;
- owned-child specs;
- generated create command input;
- generated update input;
- domain method parameters.

The property remains generated and mapped. Its setter may retain the current generated visibility; this iteration does not add runtime guards against handwritten module-internal mutation.

## Runtime Semantics

### Create

~~~text
aggregate factory or aggregate method
  -> constructs root and owned children from domain inputs
  -> generated deleted properties already hold active sentinels
  -> framework completes missing application-side Strong IDs
  -> UoW persist(root, CREATE)
  -> INSERT includes IDs and active sentinels
~~~

No factory or constructor manually supplies deleted.

For database identity, this iteration proves that the generated entity is constructible without a deleted argument and that create/query/delete persistence works through the existing identity path. It does not claim that the generated database-identity aggregate factory has a resolved construction body.

### Query

Hibernate entity queries apply the generated Where clause:

~~~sql
deleted = <active sentinel>
~~~

Unique-query planning continues to treat the semantic soft-delete column as a control field. A deleted marker without semantic providerControl.softDelete is not enough to hide a request property.

### Delete

~~~text
Repository/UoW removal
  -> Hibernate entity delete
  -> generated SQLDelete
  -> database UPDATE sets deleted = id
~~~

There is no physical DELETE for the default soft-delete path.

The in-memory deleted property does not need to be refreshed after successful removal. The entity has left its valid managed lifecycle, and the discriminator is not a domain-observable state API.

No new UoW soft-delete branch is added.

## Generated Examples

### Mainline: MySQL UUID7 Character Storage

Schema:

~~~sql
create table category (
    id varchar(36) primary key,
    name varchar(255) not null,
    version bigint not null default 0,
    deleted varchar(36) not null
        default '00000000-0000-0000-0000-000000000000'
);
~~~

Generated shape:

~~~kotlin
@Entity
@Table(name = "category")
@SQLDelete(
    sql = "update `category` set `deleted` = `id` where `id` = ? and `version` = ?"
)
@Where(
    clause = "`deleted` = '00000000-0000-0000-0000-000000000000'"
)
class Category internal constructor(
    name: String,
) {
    @EmbeddedId
    lateinit var id: CategoryId
        internal set

    @Column(name = "name")
    var name: String = name
        internal set

    @Column(name = "deleted")
    var deleted: String =
        "00000000-0000-0000-0000-000000000000"
        internal set
}
~~~

### Boundary: Snowflake String Storage

Schema:

~~~sql
id varchar(19) primary key
deleted varchar(19) not null default '0'
~~~

Generated discriminator:

~~~kotlin
var deleted: String = "0"
    internal set
~~~

Generated filter:

~~~sql
deleted = '0'
~~~

Snowflake Strong ID validation rejects zero as a positive canonical Snowflake value, so the sentinel does not collide with a valid generated ID.

### Native UUID: PostgreSQL Or H2

Schema:

~~~sql
id uuid primary key
deleted uuid not null
    default '00000000-0000-0000-0000-000000000000'
~~~

Generated property:

~~~kotlin
var deleted: UUID = UUID(0L, 0L)
    internal set
~~~

Generated filter SQL:

~~~sql
"deleted" =
CAST('00000000-0000-0000-0000-000000000000' AS UUID)
~~~

### Identity

Schema:

~~~sql
id bigint auto_increment primary key
deleted bigint not null default 0
~~~

Generated property:

~~~kotlin
var deleted: Long = 0L
    internal set
~~~

Generated filter:

~~~sql
deleted = 0
~~~

## Error Examples

UUID7 character ID with numeric deleted:

~~~text
id VARCHAR(36)
deleted BIGINT DEFAULT 0
-> reject: CHARACTER cannot assign to INTEGRAL
~~~

UUID7 native ID with character deleted:

~~~text
id UUID
deleted VARCHAR(36) DEFAULT '00000000-0000-0000-0000-000000000000'
-> reject: NATIVE_UUID cannot assign to CHARACTER
~~~

Snowflake character storage with UUID sentinel:

~~~text
id VARCHAR(19) strategy snowflake
deleted VARCHAR(19)
default '00000000-0000-0000-0000-000000000000'
-> reject: snowflake requires ZERO, got NIL_UUID value
~~~

Missing dialect:

~~~text
semantic soft delete is enabled
DB source URL is absent
-> reject: soft-delete SQL dialect cannot be resolved
~~~

Unknown dialect:

~~~text
jdbc:oracle:thin:...
-> reject with the supported dialect list
~~~

## Migration And Breaking Changes

There are no external customers and no compatibility requirement. Migration is explicit rather than dual-path.

### AggregateSoftDeletePolicy API

Consumers must replace:

~~~kotlin
activeValue
activePredicateSql
deleteAssignmentSql
~~~

with:

~~~kotlin
storageKind
activeSentinel
tombstoneStrategy
~~~

No deprecated properties, secondary constructor, adapter, or map alias is retained.

### Template Context

The semantic softDelete context becomes:

~~~text
softDelete.enabled
softDelete.columnName
softDelete.storageKind
softDelete.activeSentinel
softDelete.tombstoneStrategy
~~~

The default template may continue receiving final generator products:

~~~text
softDeleteSql
softDeleteWhereClause
softDeleteSqlKotlinStringLiteral
softDeleteWhereClauseKotlinStringLiteral
~~~

These are generator output, not fields on the core policy.

Template overrides that read softDelete.activeValue, softDelete.activePredicateSql, or softDelete.deleteAssignmentSql must migrate immediately. The generator does not publish both shapes.

### Generated Constructor

Before:

~~~kotlin
Category(
    name = "demo",
    deleted = 0L,
)
~~~

After regeneration:

~~~kotlin
Category(
    name = "demo",
)
~~~

There is no compatibility constructor because the generated constructor is internal and deleted was never valid domain creation input.

Generated factories, specs, fixtures, and renderer assertions must be updated together.

### UUID7 Schema

This old numeric shape is rejected:

~~~sql
id varchar(36) primary key
deleted bigint not null default 0
~~~

MySQL character migration:

~~~sql
id varchar(36) primary key
deleted varchar(36) not null
    default '00000000-0000-0000-0000-000000000000'
~~~

PostgreSQL/H2 native migration:

~~~sql
id uuid primary key
deleted uuid not null
    default '00000000-0000-0000-0000-000000000000'
~~~

The framework does not generate ALTER TABLE or data-migration scripts.

### Snowflake Schema

Snowflake remains supported.

Long backing:

~~~sql
id bigint
deleted bigint not null default 0
~~~

String backing:

~~~sql
id varchar(19)
deleted varchar(19) not null default '0'
~~~

No snowflake-long strategy name is introduced or preserved. Field names such as snowflake_long in tests are ordinary physical names, not strategy aliases.

### SQL Dialect

Projects with semantic soft delete must provide a recognized DB source URL. A previously accepted missing or unknown URL may now fail generation. The required migration is to provide a supported URL or implement a separately designed dialect; it is not to select a fallback flag.

### ApplicationSideId

ApplicationSideId was deleted by Phase 4 and remains deleted. This iteration neither restores the annotation nor adds metadata-based handwritten-entity discovery.

## Verification Strategy

Completion requires evidence at five layers.

### 1. Storage Catalog Unit Tests

Cover:

- each supported integral width;
- signed and unsigned assignability;
- each supported character JDBC family;
- character capacity retention;
- native UUID recognition;
- MySQL BINARY(16) rejection;
- DECIMAL and NUMERIC rejection;
- Kotlin type mismatch;
- missing jdbcType;
- missing character columnSize;
- unsupported vendor type.

AggregateStrongIdBackingResolver tests must prove that UUID7 and Snowflake still select the same String, UUID, and Long backings after migration to the shared catalog.

### 2. Core Soft-Delete Policy Tests

At least one success case for:

~~~text
identity / DB_IDENTITY + integral ZERO
snowflake Long + integral ZERO
snowflake String + character ZERO
uuid7 String + character NIL_UUID
uuid7 UUID + native UUID NIL_UUID
~~~

At least one rejection case for:

~~~text
nullable deleted
missing default
wrong default
storage-kind mismatch
insufficient character capacity
insufficient numeric capacity
signed/unsigned mismatch
unsupported ID storage
unsupported deleted storage
~~~

Default normalization tests must cover:

- the actual H2 2.3.232 metadata forms recorded in Current Evidence;
- PostgreSQL postfix casts;
- standard CAST;
- typed UUID literal;
- nested balanced parentheses;
- arbitrary expression rejection.

Policy assertions must prove that no SQL text remains in AggregateSoftDeletePolicy.

### 3. Generator And Renderer Tests

Cover:

- MySQL backtick rendering;
- MariaDB backtick rendering;
- H2 standard double-quote rendering;
- H2 MySQL-mode backtick rendering;
- PostgreSQL double-quote rendering;
- physical identifier escaping;
- missing URL rejection when soft delete is enabled;
- unsupported URL rejection;
- no dialect requirement when soft delete is absent;
- integral ZERO SQL and Kotlin rendering;
- character ZERO SQL and Kotlin rendering;
- character NIL_UUID SQL and Kotlin rendering;
- native UUID NIL_UUID SQL and Kotlin rendering;
- versioned and versionless SQLDelete;
- deleted absent from constructorFields;
- deleted present in scalarFields;
- deleted initializer rendered in the property;
- factory payload and owned-child spec exclude deleted;
- Strong ID property remains strongly typed;
- template output compiles with required UUID imports.

Update existing tests that assert activeValue, activePredicateSql, or deleteAssignmentSql. Do not retain old assertions beside new ones.

### 4. Functional Generation And Compilation

Generate and compile fixtures for:

~~~text
identity integral
Snowflake Long
Snowflake String
UUID7 String
UUID7 native UUID
~~~

The functional evidence for all five combinations must include:

- generated entity constructor;
- generated deleted property;
- SQLDelete and Where annotations;

The four application-side ID rows must additionally include:

- generated Strong ID;
- generated own-ID accessor and module catalog;
- real aggregate factory construction.

Database identity retains its existing generated-value entity ID path and is not required to provide application-side Strong ID/accessor/catalog evidence.

Real aggregate factory construction is required only for:

~~~text
Snowflake Long
Snowflake String
UUID7 String
UUID7 native UUID
~~~

For those four rows, the rendered factory must call the entity constructor, accept no deleted input, and contain no `TODO("Implement aggregate construction")` fallback.

Identity integral must instead prove all of:

- the generated entity can be constructed without passing deleted;
- deleted uses the raw integral Kotlin type and ZERO initializer;
- deleted is absent from the entity constructor and every user write surface;
- versioned SQLDelete and Where are generated correctly;
- the real H2 create/query/delete lifecycle executes successfully.

The existing database-identity factory may still have `constructorMappingResolved=false` and a `TODO` body. Factory-file existence, payload compilation, or compilation of that `TODO` body is not factory-construction evidence and must not be reported as such.

Compilation alone is not runtime proof, but it catches template context, imports, visibility, constructor calls, and Strong ID/backing mismatches.

### 5. Database Runtime Evidence

#### H2

H2 tests must execute the real lifecycle:

1. create a new entity;
2. prove application-side ID allocation occurs before create returns where applicable;
3. persist and flush;
4. verify deleted contains the active sentinel;
5. remove and flush;
6. verify ordinary Hibernate queries no longer return the entity;
7. use direct JDBC to prove the row still exists;
8. prove deleted physically equals id;
9. cover a versioned entity;
10. cover a versionless entity.

The H2 suite must include:

- standard H2 identifier normalization;
- quoted mixed-case identifiers;
- H2 MySQL mode;
- Snowflake Long `StrongId<Long>` SQLDelete identifier binding and physical `deleted = id` execution;
- character sentinel execution;
- native UUID sentinel execution.

#### PostgreSQL

Native PostgreSQL UUID support is not complete until a real PostgreSQL JDBC test proves:

- source metadata is classified as NATIVE_UUID;
- the nil UUID default is normalized from the actual driver result;
- generated quoted SQL executes;
- the active predicate executes;
- deleted = id executes;
- the physical row remains and is filtered from the active view.

H2 MODE=PostgreSQL is not evidence for PostgreSQL.

The implementation may add a PostgreSQL service to the CI workflow and expose its URL through environment variables. A local test may skip only when that environment is absent; CI must provide it and execute the test.

### Verification Commands

At minimum run the focused module tests affected by the implementation, then:

~~~text
./gradlew check
~~~

If PostgreSQL verification is environment-gated, the final evidence summary must state whether the PostgreSQL test actually ran. A skipped PostgreSQL test cannot support a claim that native PostgreSQL soft delete is fully verified.

## Rollback Triggers

The implementation must return to design review when any of these occurs:

1. Hibernate binds SQLDelete placeholders in an order incompatible with ID followed by version.
2. Generated Strong ID mapping cannot bind the expected primitive value to SQLDelete.
3. Real PostgreSQL or H2 rejects the explicit native UUID CAST.
4. JDBC metadata cannot distinguish supported native UUID from binary UUID encoding.
5. A supported driver's default expression cannot be normalized without evaluating arbitrary SQL.
6. Removing deleted from the constructor prevents Hibernate instantiation or generated factory compilation.
7. CREATE does not write the generated initializer and produces a different active value from the database default.
8. Snowflake zero becomes a valid Strong ID under current validation.
9. Physical identifier case cannot be preserved through metadata and dialect quoting.
10. Unique-query planning begins treating a mere deleted marker as semantic soft delete.

A rollback changes the smallest responsible design point. It must not reintroduce:

- numeric-only soft delete;
- raw application-side IDs;
- ApplicationSideId;
- unknown-dialect fallback;
- cross-storage implicit conversion;
- silent soft-delete disablement;
- user-supplied deleted constructor parameters.

## Agent Handoff Notes

### Allowed Production Areas

The implementation agent may modify:

- cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
- a new internal storage catalog under cap4k-plugin-pipeline-core
- AggregateStrongIdBackingResolver.kt
- AggregateSoftDeletePolicyResolver.kt
- AggregatePersistenceProviderInference.kt when required by the policy shape
- EntityArtifactPlanner.kt
- the default aggregate entity Pebble template
- narrowly related generator/context helpers
- the CI workflow only for mandatory PostgreSQL verification.

The agent may add or update directly corresponding tests in:

- cap4k-plugin-pipeline-core
- cap4k-plugin-pipeline-source-db
- cap4k-plugin-pipeline-generator-aggregate
- cap4k-plugin-pipeline-renderer-pebble
- cap4k-plugin-pipeline-gradle functional fixtures
- cap4k-ddd-starter runtime fixtures when needed for real Hibernate evidence.

### Areas That Must Not Be Changed

Do not modify:

- the completed Phase 4 design or implementation plan;
- current UoW ID completion, root enrollment, owned-child reconciliation, or graph traversal unless a runtime test demonstrates a defect;
- ddd-distributed-snowflake;
- BuiltInIdentifierStrategies.SNOWFLAKE;
- SnowflakeIdentifierStrategy;
- Strong ID JSON semantics;
- ApplicationSideId or any deleted compatibility path;
- owned-child factory or child persist API boundaries;
- `FactoryArtifactPlanner` changes intended to resolve the existing database-identity constructor mapping or remove its `TODO` body;
- unrelated handwritten JPA entity behavior;
- historical soft-delete and identity specs;
- unrelated dirty worktree files.

Do not rename Snowflake fields, delete Snowflake test coverage, or infer that removal of the obsolete snowflake-long strategy vocabulary authorizes removal of the current snowflake strategy.

### Implementation Discipline

- The factory-evidence scope is resolved; implementation may proceed using the fixed five-row entity matrix and four-row application-side factory matrix.
- Start with failing tests for the storage catalog and policy matrix.
- Preserve current behavior for supported numeric identity soft delete.
- Reuse the catalog from Strong ID and soft-delete resolution before adding new generator behavior.
- Keep core semantic and generator rendering changes in separate reviewable steps.
- Do not add a compatibility branch to make old tests pass.
- Do not claim PostgreSQL support without executed PostgreSQL evidence.
- Do not modify the user's existing uncommitted documentation changes.

## Resolved Decisions

1. SELF_ID remains the only tombstone strategy.
2. Database identity, Snowflake, and UUID7 are all supported.
3. Snowflake String and Long remain Strong ID backings.
4. ZERO is used by identity and Snowflake.
5. NIL_UUID is used by UUID7.
6. Character ZERO renders as SQL '0' and Kotlin "0".
7. Character NIL_UUID renders as the canonical nil UUID string.
8. Native UUID NIL_UUID renders as an explicit UUID CAST and UUID(0L, 0L).
9. ID and deleted storage kinds must match.
10. Numeric range and character capacity must be sufficient for the full declared ID column.
11. Cross-storage casts are unsupported.
12. The deleted property uses the raw backing type, not the Strong ID wrapper.
13. The deleted property remains mapped but leaves the constructor and every user write surface.
14. The database default remains mandatory.
15. Core publishes semantics and no SQL.
16. Generator resolves dialect and renders all SQL.
17. Missing or unsupported dialects fail without fallback.
18. Identifier quoting is retained and verified against physical metadata case.
19. Hibernate SQLDelete and Where remain the runtime mechanism.
20. No UoW soft-delete branch is added.
21. ApplicationSideId remains deleted.
22. Snowflake infrastructure remains in place.
23. Real H2 runtime evidence is mandatory.
24. Real PostgreSQL evidence is mandatory before native PostgreSQL UUID support is declared complete.
25. Aggregate factory-construction evidence applies to Snowflake Long, Snowflake String, UUID7 String, and UUID7 native UUID only.
26. Identity integral is verified through generated entity construction, raw ZERO-initialized deleted state, write-surface exclusion, versioned SQL rendering, and the real H2 lifecycle.
27. The existing database-identity factory unresolved mapping and `TODO` body are outside this iteration and are not valid construction evidence.

## Related Documents

- docs/superpowers/specs/2026-07-20-cap4k-soft-delete-discriminator-policy-design.md
- docs/superpowers/specs/2026-07-24-cap4k-strong-id-create-time-injection-design.md
- docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md

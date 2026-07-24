# cap4k Default Schema Owned Relation Join Design

Date: 2026-07-23

Phase: 3.625

## Reader Contract

This spec is written for an implementation agent who has no chat history.

After reading it, the agent should be able to answer these questions without asking for prior conversation context:

- What query capability is being restored, and why it is a regression from original cap4j behavior.
- Which generated relations may receive schema join APIs.
- Which relations must not receive schema join APIs.
- What the public Kotlin API should feel like for owned-many, owned-one, and chained owned joins.
- Why public schema names must use domain relation names while Criteria paths may use private persistence backing names.
- Which runtime helper types are needed and which existing repository APIs must remain unchanged.
- How join caching behaves, including the fail-fast rule for conflicting join types.
- Which files an implementation agent may change, which files are out of bounds, and how to verify the change.

This spec is not an implementation plan. It fixes the design contract for the Phase 3.625 implementation slice.

## Status

Design draft reviewed against current `master` after PR #132 and PR #133 were merged.

Implementation has not started in this task. Do not treat this document as proof that the feature exists in current code.

## Current Evidence

Current cap4k evidence:

- [schema.kt.peb](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb>) renders schema scalar fields from `fields` and creates each public field with `Field(root.get("{{ field.fieldName }}"), criteriaBuilder)` around lines 172-180. There is no generated relation join method in this template today.
- [SchemaArtifactPlanner.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt>) currently builds only scalar `fields` from `schema.fields` and passes `fields` into `aggregate/schema.kt.peb`. It does not pass relation-aware render data to the schema template.
- [JoinType.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/JoinType.kt>) already exists and maps cap4k `INNER`, `LEFT`, and `RIGHT` to JPA Criteria join types. The runtime has a join type vocabulary, but the default schema generator does not use it for relation APIs.
- [Field.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/Field.kt>) already supports scalar predicates, ordering, `isEmpty`, and `isNotEmpty` over its Criteria `Path`. It is too broad and scalar-shaped to express the owned relation public API cleanly now that Phase 3.5 uses private backing collection paths for owned relations.
- [PipelineModels.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt>) defines `AggregateRelationModel` with `owned`, `ownedCardinality`, `persistenceShape`, `backingCollectionName`, and `singleAccessorName`. The canonical model already has enough relation facts to distinguish generated owned relations from ordinary relations.
- [AggregateRelationPlanning.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt>) now normalizes render-only relation names such as `domainName`, `persistencePathName`, `backingCollectionName`, and `singleAccessorName` for entity/projection rendering, but this normalized planning is not reused by schema rendering.
- [entity.kt.peb](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb>) now renders owned-many as a private backing collection plus public `OwnedEntityList<T>` facade, and owned-one as a private backing collection plus transient nullable accessor delegated through `OwnedEntityList`. Phase 3.625 must consume the Phase 3.5 public-vs-persistence naming split instead of assuming the public relation name and Criteria persistence path are identical.
- [JpaGeneratedOwnedRelationTraversal.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt>) exists for PR #132 UoW/Strong ID owned-relation traversal. It is runtime lifecycle infrastructure, not schema query infrastructure.
- [2026-07-23-cap4k-default-aggregate-template-structure-design.md](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-07-23-cap4k-default-aggregate-template-structure-design.md>) explicitly keeps schema relation join restoration out of Phase 3.5 and names it as a follow-up slice. It also requires schema public APIs not to leak private `_` backing field names.

Original cap4j evidence:

- [AdminUserSchema.java](</C:/Users/LD_moxeii/Documents/code/GitHub/only4-KSP-cap4j/only4-KSP-cap4j-domain/src/main/java/com/only4/domain/aggregates/admin_user/meta/AdminUserSchema.java>) generated methods such as `joinAdminUserRole(Schema.JoinType joinType)` and `joinAdminUserPermission(Schema.JoinType joinType)`. The methods cast the schema root to JPA `Root`, call `root.join("adminUserRoles", type)`, and return the joined child schema.
- [GenEntityMojo.java](</C:/Users/LD_moxeii/Documents/code/GitHub/cap4j/cap4j-ddd-codegen-maven-plugin/src/main/java/org/netcorepal/cap4j/ddd/codegen/GenEntityMojo.java>) contains the old `schema_join` template segment that generated `join${joinEntityType}(${SchemaBase}.JoinType joinType)` methods. This confirms the current cap4k default schema template lost a real historical capability.

Jimmer reference evidence:

- [CodeGeneratorTest.java](</C:/Users/LD_moxeii/Documents/code/GitHub/jimmer/project/jimmer-sql/src/test/java/org/babyfish/jimmer/sql/util/CodeGeneratorTest.java>) verifies that Jimmer generates table join methods selectively. Some associations have join methods on base tables, some only on extended tables, and some list associations are intentionally not exposed as normal joins.
- [PropsGenerator.java](</C:/Users/LD_moxeii/Documents/code/GitHub/jimmer/project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/generator/PropsGenerator.java>) has separate logic for association return types and collection `exists` APIs. The useful design idea is a controlled association surface, not a stringly arbitrary join surface.
- [TableImpl.java](</C:/Users/LD_moxeii/Documents/code/GitHub/jimmer/project/jimmer-sql/src/main/java/org/babyfish/jimmer/sql/ast/impl/table/TableImpl.java>) caches joins by a key containing join name and join type. cap4k should borrow this path-controlled reuse idea, not Jimmer's full SQL AST implementation.

## Problem Statement

cap4k currently generates a schema DSL that can filter by scalar fields but cannot naturally query through aggregate-owned child entities. That is a regression relative to original cap4j schema generation.

The missing capability matters because repository queries should be able to express aggregate-root filters that depend on owned children, for example:

- find orders that have any line item;
- find orders whose line item sku equals a request value;
- find video posts that have a file;
- find video posts whose file mime type is `video/mp4`;
- find orders whose line item adjustment has reason `PROMOTION`.

Phase 3.5 also introduced a naming hazard. Owned-many public API is `lineItems` backed by a private JPA collection such as `_lineItems`, so schema public APIs must continue to expose `lineItems`. Only internal Criteria calls may use `_lineItems`.

Phase 3.625 solves only this schema/query capability gap. It does not change persistence lifecycle, Unit of Work behavior, ID generation, repository interface shape, or aggregate entity mutation rules.

## Goals

- Restore generated schema relation query capability for aggregate owned child entities.
- Support owned-many collection existence predicates.
- Support owned-many join to child schema fields.
- Support owned-one nullable existence predicates.
- Support owned-one join to child schema fields.
- Support chained owned joins across generated owned relation paths.
- Preserve current repository interfaces and Spring Data JPA `Specification` usage.
- Keep public schema API names aligned with modeled domain names.
- Allow internal Criteria paths to use private backing collection names introduced by Phase 3.5.
- Reuse joins within one schema instance and fail fast on conflicting join types for the same relation path.

## Non Goals

- Do not change repository interfaces or adapters.
- Do not add a new repository query engine.
- Do not add Querydsl back.
- Do not change UoW, `PersistIntent`, save flow, lifecycle classification, audit, dirty detection, or ID completion.
- Do not implement Strong ID create-time injection.
- Do not reuse or extend PR #132 UoW/Strong ID owned-relation traversal helpers for schema query joins.
- Do not change aggregate entity structure beyond what Phase 3.5 owns.
- Do not generate joins for ordinary JPA relations.
- Do not generate joins for inverse relations.
- Do not generate joins for reference ID fields, `@RefAggregate`, `@RefId`, or weak reference IDs.
- Do not support arbitrary string path joins.
- Do not support general cross-aggregate joins.
- Do not promise fetch joins or load-plan behavior.
- Do not implement Jimmer's SQL AST, table model, or delayed join optimizer.

## Terms

### Default Schema

The generated Kotlin query DSL class rendered by `aggregate/schema.kt.peb`, for example `SOrder` or `SVideoPost`.

### Scalar Field

A generated schema field backed by `schema.fields`, for example `orderNo`, `sku`, `quantity`, or `mimeType`. Scalar fields continue to use `Field<T>`.

### Relation Field

A generated schema field that represents an owned relation itself, not a child scalar. Relation fields are used for existence/nullability predicates such as `order.lineItems.isNotEmpty()` and `post.file.isNotNull()`.

### Owned Many

A generated aggregate relation where `AggregateRelationModel.owned == true` and `ownedCardinality == MANY`. It is a collection relation in the public domain API.

Example public API name: `lineItems`.

Example Phase 3.5 persistence path: `_lineItems`.

### Owned One

A generated aggregate relation where `AggregateRelationModel.owned == true` and `ownedCardinality == ONE`. In the default entity template it is represented as a private backing collection plus a public nullable single accessor.

Example public API name: `file`.

Example Phase 3.5 persistence path: `_files`.

Owned-one in this spec does not mean arbitrary direct JPA `@OneToOne`. It means the generated cap4k owned-one shape backed by an owned collection.

### Domain Name

The public domain/schema API name. This is what application authors see and what generated schema properties and join methods use.

Examples: `lineItems`, `file`.

### Persistence Path Name

The JPA Criteria path used internally to access the persistent relation field.

Examples: `_lineItems`, `_files` in the current Phase 3.5 default entity template.

The persistence path name must not appear in public generated schema APIs.

### Relation Join

A generated method on a schema that creates or reuses a JPA Criteria join and returns the target entity schema.

Examples: `joinLineItems()`, `joinLineItems(JoinType.LEFT)`, `joinFile()`.

### Chained Owned Join

A relation join from a schema that was itself returned by a relation join.

Example: `order.joinLineItems().joinAdjustments()`.

### Join Cache

A small per-schema-instance cache that stores generated Criteria joins and joined schema wrappers by relation persistence path and join type. The cache is query-local and must not be static or global.

### PersistIntent, Strong ID, Application-Side ID, Create-Time Injection

These terms belong to the identity roadmap. They are intentionally out of scope for this spec:

- `PersistIntent` is the Unit of Work input classification before persistence.
- Strong ID is a generated identity wrapper around an entity ID value.
- Application-side ID means an ID allocated before database insert.
- Create-time injection means assigning an ID during construction or generated creation, before save.

Phase 3.625 must not alter these contracts.

## API Contract

### Owned Many Existence And Join

Generated schema usage:

```kotlin
SOrder.predicate(distinct = true) { order ->
    val item = order.joinLineItems()

    order.all(
        order.lineItems.isNotEmpty(),
        item.sku eq sku,
        item.quantity gt 0,
    )
}
```

Required generated members on `SOrder`:

```kotlin
val lineItems: RelationCollectionField<OrderLine>

fun joinLineItems(): SOrderLine
fun joinLineItems(joinType: JoinType): SOrderLine
```

`joinLineItems()` defaults to `JoinType.INNER`.

`order.lineItems.isEmpty()` and `order.lineItems.isNotEmpty()` query collection existence. They do not expose child scalar fields. Child scalar predicates require `joinLineItems(...)`.

### Owned One Nullable Existence And Join

Generated schema usage:

```kotlin
SVideoPost.predicate { post ->
    post.file.isNotNull()
}
```

Generated schema usage with child scalar filtering:

```kotlin
SVideoPost.predicate { post ->
    val file = post.joinFile(JoinType.LEFT)

    post.all(
        post.file.isNotNull(),
        file.mimeType eq "video/mp4",
    )
}
```

Required generated members on `SVideoPost`:

```kotlin
val file: RelationOptionalField<VideoPostFile>

fun joinFile(): SVideoPostFile
fun joinFile(joinType: JoinType): SVideoPostFile
```

`joinFile()` defaults to `JoinType.INNER`.

`post.file.isNull()` and `post.file.isNotNull()` query the generated owned-one nullable accessor semantics. For the default owned-one persistence shape, these predicates are implemented against the backing collection emptiness, not against a direct nullable JPA `@OneToOne` field.

### Chained Owned Join

Generated schema usage:

```kotlin
SOrder.predicate(distinct = true) { order ->
    val item = order.joinLineItems()
    val adjustment = item.joinAdjustments()

    order.all(
        item.sku eq sku,
        adjustment.reason eq "PROMOTION",
    )
}
```

Chained joins are generated only when the child schema's entity owns another generated owned relation. The target schema returned by a join must be able to create its own owned joins.

### Predicate Overloads

Existing root schema predicate helpers must continue to work.

Add a distinct-friendly overload for aggregate-root schemas so collection joins can avoid duplicate root rows when the caller needs it:

```kotlin
SOrder.predicate(distinct = true) { order ->
    val item = order.joinLineItems()
    item.sku eq sku
}
```

Required aggregate-root overloads:

```kotlin
fun predicate(builder: PredicateBuilder<SOrder>): JpaPredicate<Order>
fun predicate(distinct: Boolean, builder: PredicateBuilder<SOrder>): JpaPredicate<Order>
fun predicate(specifier: SchemaSpecification<Order, SOrder>): JpaPredicate<Order>
```

The old `predicate(builder)` remains source-compatible and delegates to `distinct = false`.

Do not automatically force `distinct = true` for every relation join. The caller controls distinctness because not every query wants root de-duplication and because existing `specify(..., distinct)` already treats distinct as an explicit choice.

### Property Name Constants

Scalar property names continue to be exposed through `props`:

```kotlin
SOrder.props.id
SOrder.props.orderNo
```

Relation names must be exposed through a separate `relations` holder:

```kotlin
SOrder.relations.lineItems
SVideoPost.relations.file
```

`props` must not gain relation names. `relations` must not expose persistence backing names.

Expected generated shape:

```kotlin
class PROPERTY_NAMES {
    val id = "id"
    val orderNo = "orderNo"
}

class RELATION_NAMES {
    val lineItems = "lineItems"
}

companion object {
    val props = PROPERTY_NAMES()
    val relations = RELATION_NAMES()
}
```

## Design Decisions

### Decision 1: Keep Phase 3.625 Separate From Phase 3.5

Decision: Phase 3.5 owns aggregate entity structure and `OwnedEntityList`. Phase 3.625 owns default schema owned relation query recovery.

Reason: Phase 3.5 already changes entity field visibility and relation backing shape. Folding query join restoration into it would mix domain mutation API design, Criteria query API design, render model naming, and tests into one broad slice.

Excluded alternative: put relation join restoration directly into Phase 3.5. This is rejected because it increases blast radius and makes it harder to review whether `OwnedEntityList` semantics are correct.

Required coordination: Phase 3.5 already leaves enough normalized render model information for 3.625 to distinguish `domainName` from `persistencePathName`. Phase 3.625 must reuse that normalized naming contract or extract the same helper instead of adding a second raw-name derivation path.

### Decision 2: Generate Joins Only For Aggregate-Owned Child Relations

Decision: `relationJoins` are generated only from `model.aggregateRelations` where:

- `ownerEntityName` and `ownerEntityPackageName` match the schema entity;
- `owned == true`;
- `relationType == ONE_TO_MANY`;
- `persistenceShape == ONE_TO_MANY_JOIN_COLUMN`;
- `ownedCardinality` is `MANY` or `ONE`;
- a schema exists for the target entity.

Reason: The requested repository query capability is aggregate-owned child filtering. That capability can be implemented from existing generated relation metadata without opening arbitrary JPA join behavior.

Excluded alternatives:

- Generate joins for every relation field. Rejected because it would include ordinary `MANY_TO_ONE`, direct `ONE_TO_ONE`, inverse relations, and cross-aggregate object relations that are outside the aggregate write-model boundary.
- Generate string path joins such as `schema.join("lineItems")`. Rejected because it leaks persistence details and bypasses generator-known ownership checks.

### Decision 3: Public API Uses Domain Names, Criteria Uses Persistence Paths

Decision: Schema render model must split relation names:

```kotlin
domainName: String
persistencePathName: String
```

Public generated API uses `domainName`:

- `val lineItems`;
- `fun joinLineItems()`;
- `SOrder.relations.lineItems`.

Internal Criteria code uses `persistencePathName`:

- `root.get("_lineItems")` for relation existence after Phase 3.5;
- `root.join("_lineItems", joinType.toJpaJoinType())` for relation joins after Phase 3.5.

Reason: Phase 3.5 intentionally hides raw backing collections from domain code. Schema is a public domain query API and must not leak `_lineItems` or `_files` as public names.

Excluded alternative: keep using `fieldName` everywhere. Rejected because it breaks as soon as public relation facade names and JPA backing field names diverge.

### Decision 4: Use Dedicated Relation Field Types

Decision: Add two small runtime schema helper types:

```kotlin
RelationCollectionField<E>
RelationOptionalField<E>
```

`RelationCollectionField<E>` supports:

```kotlin
fun isEmpty(): Predicate
fun isNotEmpty(): Predicate
```

`RelationOptionalField<E>` supports:

```kotlin
fun isNull(): Predicate
fun isNotNull(): Predicate
```

Reason: Owned relation existence is not the same concept as scalar value comparison. Dedicated types make unsupported operations impossible or clearly absent.

Excluded alternatives:

- Reuse `Field<Collection<E>>` for owned-many. Rejected because it exposes unrelated scalar-style APIs and does not communicate relation semantics.
- Reuse `Field<E?>` for owned-one. Rejected because current owned-one is not a direct nullable persistent object path; it is a nullable public accessor backed by a collection.

### Decision 5: Owned-One Existence Uses Backing Collection Emptiness

Decision: For the default owned-one persistence shape, `RelationOptionalField.isNull()` means the backing collection is empty, and `isNotNull()` means it is not empty.

Reason: Current and Phase 3.5 owned-one structure models a public nullable accessor over a private `@OneToMany + @JoinColumn` collection. Query semantics must follow the actual persistent shape.

Excluded alternative: implement owned-one `isNull()` by checking a direct `@OneToOne` path. Rejected because that is not the default generated owned-one shape covered by this spec.

If future generator evidence shows a direct owned `ONE_TO_ONE` write-model relation, stop and write a new spec or revise this one before supporting it.

### Decision 6: Join Methods Return Target Schemas

Decision: A relation join method returns the generated schema for the target entity.

Example:

```kotlin
val item: SOrderLine = order.joinLineItems()
```

Reason: This preserves the original cap4j mental model while fitting the current Kotlin schema DSL. Application authors query child scalar fields through the same `Field<T>` API they already use on root schemas.

Excluded alternative: make relation fields directly expose child scalar fields, for example `order.lineItems.sku`. Rejected because collection relation navigation, join type selection, and chain joins need explicit query-shape control.

### Decision 7: Join Cache Is Query-Local And Conflict-Aware

Decision: Each generated schema instance maintains a small join cache. Within that schema instance:

- same persistence path plus same join type returns the same Criteria join;
- same persistence path plus different join type fails fast;
- different persistence paths may use different join types independently.

Reason: This borrows Jimmer's controlled path reuse idea while staying inside Spring Data JPA Criteria. It prevents accidental duplicate joins and prevents one query from silently mixing `INNER` and `LEFT` joins for the same relation path.

Excluded alternatives:

- Always call `root.join(...)` on every accessor invocation. Rejected because repeated calls can produce duplicate joins and confusing SQL.
- Let different join types coexist for the same relation. Rejected because it makes predicates against the same domain relation ambiguous.
- Use a global/static cache. Rejected because Criteria joins are tied to one query root and must never cross query instances.

### Decision 8: Do Not Change Repository APIs

Decision: Repository code continues to accept existing `JpaPredicate` and Spring Data JPA `Specification` flows generated by schema helpers.

Reason: The requested capability is query expression power inside generated schemas, not a new repository abstraction.

Excluded alternative: add new repository methods for relation joins. Rejected because it expands the public persistence boundary and duplicates `Specification` capabilities already present in the default repository adapter.

## Runtime Flow

The runtime flow stays inside generated schema helpers and Spring Data JPA Criteria.

1. Application code calls an aggregate-root schema helper such as `SOrder.predicate(distinct = true) { ... }`.
2. The generated helper creates a Spring Data JPA `Specification<Order>`.
3. Spring Data JPA invokes the specification with a JPA `Root<Order>`, `CriteriaQuery`, and `CriteriaBuilder`.
4. The generated schema instance wraps the JPA root and criteria builder.
5. Scalar field access creates `Field<T>` from `root.get(scalarPathName)`.
6. Relation field access creates `RelationCollectionField<T>` or `RelationOptionalField<T>` from the internal persistence collection path.
7. Relation join method calls use the schema's query-local join cache.
8. The join cache either returns an existing Criteria `Join` for the same path/type or creates one through `root.join(persistencePathName, joinType.toJpaJoinType())`.
9. The joined Criteria `Join` is wrapped in the target schema type.
10. Chained join calls repeat the same process on the joined child schema.
11. The user predicate is applied to the Criteria query. Distinct is set only when the chosen schema helper receives `distinct = true`.

No Unit of Work object is touched. No repository method changes. No entity graph fetch/load plan changes. No ID generation or lifecycle classification occurs.

## Generator Flow

### Input Facts

Use the canonical model as source truth:

- `model.schemas` identifies generated schema classes and their entity names.
- `model.entities` identifies entity packages, aggregate root flags, and aggregate ownership.
- `model.aggregateRelations` identifies generated relation metadata.
- `AggregateRelationModel.owned` distinguishes owned relations from ordinary relations.
- `AggregateRelationModel.ownedCardinality` distinguishes owned-many and owned-one public semantics.
- `AggregateRelationModel.backingCollectionName` gives the raw backing collection source name where available. It is not the final schema Criteria path until Phase 3.5 relation planning normalizes it.
- `AggregateRelationModel.singleAccessorName` gives the owned-one public nullable accessor where available.

Do not infer relation join support by scanning field names in `schema.fields`.

### Schema Planner Output

`SchemaArtifactPlanner` must pass a new render model collection to `schema.kt.peb`, suggested name:

```kotlin
relationJoins: List<Map<String, Any?>>
```

Each item should carry at least:

```kotlin
domainName: String
persistencePathName: String
methodName: String
relationKind: "OWNED_MANY" | "OWNED_ONE"
targetEntityName: String
targetEntityTypeFqn: String
targetSchemaName: String
targetSchemaFqn: String
relationFieldType: "RelationCollectionField" | "RelationOptionalField"
nullable: Boolean
ownedCardinality: "MANY" | "ONE"
persistenceShape: "ONE_TO_MANY_JOIN_COLUMN"
```

Recommended derivation after Phase 3.5:

- build owner-side relation render facts by reusing `AggregateRelationPlanning.planFor(...)` with inverse relations omitted, or by extracting and reusing the same owner-relation naming helper;
- filter those render facts to eligible owned `ONE_TO_MANY` relations with `persistenceShape == ONE_TO_MANY_JOIN_COLUMN` and `ownedCardinality` of `MANY` or `ONE`;
- owned-many `domainName` comes from the normalized relation render fact, normally `relation.fieldName`;
- owned-one `domainName` comes from the normalized relation render fact, normally `relation.singleAccessorName`;
- `persistencePathName` comes from the normalized relation render fact, not directly from raw `AggregateRelationModel.backingCollectionName`;
- method name uses `join` plus upper-camel `domainName`, for example `joinLineItems` or `joinFile`.

Do not compute schema `persistencePathName` directly as `relation.backingCollectionName ?: relation.fieldName`. In the current Phase 3.5 contract, a raw backing name such as `files` is normalized to the persistent field path `_files`; using `files` would target a non-persistent public facade/accessor name.

The planner must fail fast with a generator diagnostic if an owned relation is eligible for schema join generation but the target entity schema cannot be found unambiguously.

The planner must not generate relation joins for inverse relation metadata.

### Template Imports

`schema.kt.peb` should import these runtime types only when `relationJoins` is not empty:

```kotlin
jakarta.persistence.criteria.From
jakarta.persistence.criteria.Join
com.only4.cap4k.ddd.domain.repo.schema.JoinType
com.only4.cap4k.ddd.domain.repo.schema.RelationCollectionField
com.only4.cap4k.ddd.domain.repo.schema.RelationOptionalField
```

The schema constructor should use a root type that supports both scalar path access and relation join access. Recommended shape:

```kotlin
class SOrder(
    private val root: From<*, Order>,
    private val criteriaBuilder: CriteriaBuilder,
)
```

`Root<Order>` and `Join<*, Order>` are both valid `From<*, Order>` values, so this supports root schemas and joined child schemas with one constructor shape.

The public `_root()` helper may continue returning `Path<Order>` if callers only need path-like behavior. If implementation keeps `_root(): Path<E>`, add a private `_from(): From<*, E>` or equivalent only if tests require it.

### Relation Field Rendering

Owned-many relation field example:

```kotlin
val lineItems: RelationCollectionField<OrderLine> by lazy {
    RelationCollectionField(root.get<Collection<OrderLine>>("_lineItems"), criteriaBuilder)
}
```

Owned-one relation field example:

```kotlin
val file: RelationOptionalField<VideoPostFile> by lazy {
    RelationOptionalField(root.get<Collection<VideoPostFile>>("_files"), criteriaBuilder)
}
```

The current Phase 3.5 default entity template uses private backing fields for owned relations. Default schema generation must not treat public facade/accessor names such as `lineItems` or `files` as temporary Criteria paths; it must use the normalized `persistencePathName`.

Do not generate:

```kotlin
val _lineItems: RelationCollectionField<OrderLine>
fun join_lineItems()
SOrder.relations._lineItems
```

### Join Method Rendering

Owned-many join example:

```kotlin
fun joinLineItems(): SOrderLine = joinLineItems(JoinType.INNER)

fun joinLineItems(joinType: JoinType): SOrderLine {
    val join = _join<OrderLine>("lineItems", "_lineItems", joinType)
    return _joinedSchema("lineItems", joinType) {
        SOrderLine(join, criteriaBuilder)
    }
}
```

Owned-one join example:

```kotlin
fun joinFile(): SVideoPostFile = joinFile(JoinType.INNER)

fun joinFile(joinType: JoinType): SVideoPostFile {
    val join = _join<VideoPostFile>("file", "_files", joinType)
    return _joinedSchema("file", joinType) {
        SVideoPostFile(join, criteriaBuilder)
    }
}
```

The exact private helper names may differ. Required behavior:

- public method name uses `domainName`;
- Criteria join uses `persistencePathName`;
- no-arg overload defaults to `JoinType.INNER`;
- explicit overload accepts cap4k schema `JoinType`;
- returned schema type is the target entity schema;
- generated code compiles for root schemas and child schemas.

### Join Cache Helper

Suggested generated helper shape:

```kotlin
private data class JoinCacheKey(
    val domainName: String,
    val persistencePathName: String,
)

private val joinTypesByPath = mutableMapOf<JoinCacheKey, JoinType>()
private val joinsByPath = mutableMapOf<JoinCacheKey, Join<*, *>>()
private val schemasByPath = mutableMapOf<Pair<JoinCacheKey, JoinType>, Any>()

@Suppress("UNCHECKED_CAST")
private fun <T> _join(
    domainName: String,
    persistencePathName: String,
    joinType: JoinType,
): Join<*, T> {
    val key = JoinCacheKey(domainName, persistencePathName)
    val existingType = joinTypesByPath[key]
    if (existingType != null && existingType != joinType) {
        error(
            "schema relation $domainName is already joined as $existingType; " +
                "cannot join it again as $joinType in the same schema instance"
        )
    }
    joinTypesByPath[key] = joinType
    return joinsByPath.getOrPut(key) {
        root.join<T>(persistencePathName, joinType.toJpaJoinType())
    } as Join<*, T>
}

@Suppress("UNCHECKED_CAST")
private fun <S : Any> _joinedSchema(
    domainName: String,
    persistencePathName: String,
    joinType: JoinType,
    factory: () -> S,
): S {
    val key = JoinCacheKey(domainName, persistencePathName) to joinType
    return schemasByPath.getOrPut(key) { factory() } as S
}
```

This code is illustrative. The implementation may use a smaller shape, but it must preserve the public behavior.

The cache key includes both domain and persistence path to improve diagnostics. The conflict rule is keyed by the relation path in the current schema instance. If `joinLineItems()` is called twice, both calls reuse the same join. If `joinLineItems()` and `joinLineItems(JoinType.LEFT)` are called on the same schema instance, the second call fails fast.

### Relation Name Constants

`schema.kt.peb` must generate relation names only from `domainName`:

```kotlin
class RELATION_NAMES {
    val lineItems = "lineItems"
    val file = "file"
}
```

Do not include persistence backing names in public constants.

### Distinct-Friendly Root Predicate Overload

For aggregate-root schemas, add:

```kotlin
@JvmStatic
fun predicate(distinct: Boolean, builder: PredicateBuilder<SOrder>): JpaPredicate<Order> {
    return JpaPredicate.bySpecification(Order::class.java, specify(builder, distinct))
}
```

Keep the existing no-distinct overload:

```kotlin
@JvmStatic
fun predicate(builder: PredicateBuilder<SOrder>): JpaPredicate<Order> {
    return predicate(false, builder)
}
```

If the exact existing `specify` overload names differ during implementation, preserve source compatibility and add only the minimum overload needed to support `SOrder.predicate(distinct = true) { ... }`.

## Expected Implementation Areas

Allowed production areas:

- [ddd-domain-repo-jpa schema package](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema>) for `RelationCollectionField`, `RelationOptionalField`, and possibly one small join helper if template-local helpers become too repetitive.
- [SchemaArtifactPlanner.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt>) for `relationJoins` render model generation.
- [schema.kt.peb](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb>) for relation fields, relation constants, join methods, constructor root type, and distinct-friendly root predicate overload.
- Existing generator/render/functional tests that already cover aggregate schema and relation generation.

Allowed test areas:

- [AggregateArtifactPlannerTest.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt>)
- [PebbleArtifactRendererTest.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt>)
- [PipelinePluginCompileFunctionalTest.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt>)
- [ddd-domain-repo-jpa schema tests](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema>)

Do not change:

- repository interfaces or repository adapter inheritance;
- Unit of Work API or implementation;
- aggregate factory supervisor;
- Strong ID models/templates;
- PR #132 UoW/Strong ID owned-relation traversal helpers such as `JpaGeneratedOwnedRelationTraversal`;
- entity mutation semantics beyond consuming Phase 3.5 naming output;
- Querydsl support decisions;
- non-owned relation generation.

## Examples

### Mainline Owned-Many Filter

```kotlin
val predicate = SOrder.predicate(distinct = true) { order ->
    val item = order.joinLineItems()

    order.all(
        order.lineItems.isNotEmpty(),
        item.sku eq request.sku,
        item.quantity gt 0,
    )
}
```

Expected behavior:

- query root is `Order`;
- `lineItems.isNotEmpty()` becomes a Criteria collection-not-empty predicate against the persistence collection path;
- `joinLineItems()` creates or reuses an inner join to the same persistence collection path;
- `item.sku` and `item.quantity` are scalar fields on the child schema;
- distinct is enabled because the caller passed `distinct = true`.

### Mainline Owned-One Existence

```kotlin
val predicate = SVideoPost.predicate { post ->
    post.file.isNotNull()
}
```

Expected behavior:

- public API uses `file`;
- internal Criteria path may use `_files` after Phase 3.5;
- predicate means the backing owned-one collection is not empty;
- no child schema join is required.

### Mainline Owned-One Child Field Filter

```kotlin
val predicate = SVideoPost.predicate { post ->
    val file = post.joinFile(JoinType.LEFT)

    post.all(
        post.file.isNotNull(),
        file.mimeType eq "video/mp4",
    )
}
```

Expected behavior:

- `joinFile(JoinType.LEFT)` uses the cap4k schema `JoinType` enum;
- child scalar predicates use `SVideoPostFile`;
- the relation field remains nullable-existence focused.

### Chained Owned Join

```kotlin
val predicate = SOrder.predicate(distinct = true) { order ->
    val item = order.joinLineItems()
    val adjustment = item.joinAdjustments()

    order.all(
        item.sku eq request.sku,
        adjustment.reason eq "PROMOTION",
    )
}
```

Expected behavior:

- `SOrder` generates `joinLineItems()` because `Order` owns `OrderLine`;
- `SOrderLine` generates `joinAdjustments()` only if `OrderLine` owns `OrderAdjustment` in `model.aggregateRelations`;
- each schema instance manages its own query-local join cache;
- no repository API changes are required.

### Backing Name Does Not Leak

The current Phase 3.5 default template renders:

```kotlin
private var _lineItems: MutableList<OrderLine> = mutableListOf()

@get:Transient
val lineItems: OwnedEntityList<OrderLine>
```

Phase 3.625 schema must render public APIs like:

```kotlin
val lineItems: RelationCollectionField<OrderLine>
fun joinLineItems(): SOrderLine
SOrder.relations.lineItems
```

It may render internal Criteria calls like:

```kotlin
root.get<Collection<OrderLine>>("_lineItems")
root.join<OrderLine>("_lineItems", JoinType.INNER.toJpaJoinType())
```

It must not render public APIs like:

```kotlin
val _lineItems: RelationCollectionField<OrderLine>
fun join_lineItems(): SOrderLine
SOrder.relations._lineItems
```

### Conflicting Join Type Fails Fast

```kotlin
SOrder.predicate { order ->
    val inner = order.joinLineItems()
    val left = order.joinLineItems(JoinType.LEFT)
    order.all(inner.sku eq "A", left.sku eq "B")
}
```

Expected behavior: fail fast inside schema join handling with a diagnostic that the relation was already joined with a different join type in the same schema instance.

### Unsupported Ordinary Relation

If `Content.authorId` is a reference ID or `VideoPost.author` is an ordinary non-owned `MANY_TO_ONE`, do not generate:

```kotlin
post.joinAuthor()
content.joinAuthorId()
```

Expected behavior: the methods do not exist, so misuse fails at compile time.

### Unsupported Direct Child Field Without Join

Do not support:

```kotlin
SOrder.predicate { order ->
    order.lineItems.sku eq sku
}
```

Expected behavior: `RelationCollectionField` has no `sku`; the caller must call `joinLineItems()`.

## Migration

### From Current cap4k

Current cap4k users do not have generated default schema relation join methods. This phase adds new APIs and should not break existing scalar schema queries.

Existing scalar query code remains valid:

```kotlin
SOrder.predicate { order ->
    order.orderNo eq request.orderNo
}
```

New owned relation query code becomes available:

```kotlin
SOrder.predicate(distinct = true) { order ->
    val item = order.joinLineItems()
    item.sku eq request.sku
}
```

Potential source change: generated schema constructor type may change from `Path<E>` to `From<*, E>`. This is generated code, so normal users should not instantiate schemas directly. If tests instantiate schema classes manually, update them to use JPA `Root` or `Join`, not arbitrary `Path` mocks.

### From Original cap4j

Original cap4j Java schema API used methods like:

```java
schema.joinAdminUserRole(Schema.JoinType.LEFT)
```

cap4k Kotlin default schema should not be forced to match that exact Java method name. It should follow relation public/domain names:

```kotlin
schema.joinAdminUserRoles(JoinType.LEFT)
```

The target is capability restoration, not byte-for-byte old API restoration.

### Phase 3.5 Naming Interaction

Phase 3.5 has changed owned relation fields to private backing names plus public facades/accessors. Phase 3.625 must use those normalized private backing names only as `persistencePathName`, and must keep public schema properties, join methods, and relation constants on `domainName`.

If implementation cannot consume the existing Phase 3.5 normalized naming contract from `AggregateRelationPlanning` or a shared extracted helper, stop and revise the schema planner design instead of introducing a second derivation rule.

## Verification Strategy

Use static, renderer, planner, compile, and runtime-helper tests. Do not claim the feature works from template inspection alone.

### Planner Tests

Add or update planner tests to prove:

- owned-many relations produce `relationJoins` entries;
- owned-one relations produce `relationJoins` entries using `singleAccessorName` as `domainName`;
- `persistencePathName` uses the normalized Phase 3.5 backing path, for example raw `files` becomes `_files`;
- relation join target schema type and FQN resolve correctly;
- ordinary `MANY_TO_ONE`, direct non-owned `ONE_TO_ONE`, inverse relation, `@RefAggregate`, and `@RefId` fields do not produce relation joins;
- missing or ambiguous target schema for an eligible owned relation fails fast.

Recommended location:

- [AggregateArtifactPlannerTest.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt>)

### Renderer Tests

Add or update renderer tests to prove generated schema output contains:

- imports for `JoinType`, `RelationCollectionField`, and `RelationOptionalField` when relation joins exist;
- scalar fields still rendered as `Field<T>`;
- relation fields rendered as `RelationCollectionField<T>` or `RelationOptionalField<T>`;
- `RELATION_NAMES` and `companion object val relations`;
- `joinLineItems()` and `joinLineItems(joinType: JoinType)`;
- `joinFile()` and `joinFile(joinType: JoinType)`;
- `predicate(distinct: Boolean, builder: PredicateBuilder<SOrder>)` on aggregate-root schema;
- no `_lineItems`, `_files`, or other backing names in public constants or public method/property names;
- backing names appear only in internal Criteria path strings when the render model says they should.

Recommended location:

- [PebbleArtifactRendererTest.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt>)

### Runtime Helper Tests

Add tests for `RelationCollectionField` and `RelationOptionalField`:

- `RelationCollectionField.isEmpty()` delegates to `CriteriaBuilder.isEmpty`;
- `RelationCollectionField.isNotEmpty()` delegates to `CriteriaBuilder.isNotEmpty`;
- `RelationOptionalField.isNull()` delegates to backing collection emptiness;
- `RelationOptionalField.isNotNull()` delegates to backing collection non-emptiness;
- helpers do not expose scalar comparison methods such as `eq`, `gt`, or ordering.

Add a focused test for join cache behavior if the cache is implemented in a runtime helper. If join cache is template-local, verify behavior with a generated compile/runtime fixture or a narrow rendered-code test.

Recommended location:

- [ddd-domain-repo-jpa schema tests](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema>)

### Compile Functional Tests

Add or extend a compile functional fixture with:

- aggregate root with owned-many child;
- aggregate root with owned-one child;
- child entity with its own owned-many child to prove chained join generation;
- generated query code that compiles with `SOrder.predicate(distinct = true) { ... }`;
- generated query code that compiles with `post.file.isNotNull()`;
- generated query code that compiles with `post.joinFile(JoinType.LEFT).mimeType eq "video/mp4"`.

Recommended location:

- [PipelinePluginCompileFunctionalTest.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt>)

### Static Searches

After implementation, static search should show:

- generated schema templates contain relation join rendering;
- current default template no longer treats owned relation query support as absent;
- no public schema API string exposes `_lineItems` or `_files`;
- no generated relation join is produced for non-owned relation kinds.

### Suggested Verification Commands

Use focused tests first:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.schema.*"
```

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

The implementation agent may run a narrower subset if local runtime cost is high, but the final claim must disclose skipped checks.

## Rollback Triggers

Stop implementation and revise this spec if any of these appear:

- JPA Criteria cannot join private backing collection fields generated by Phase 3.5 without exposing those names publicly.
- `From<*, E>` constructor shape breaks generated scalar schema usage in a way that cannot be repaired locally.
- Owned-one nullable existence cannot be represented correctly through backing collection emptiness.
- Join caching requires global/static state or query-crossing state.
- Supporting chained owned joins requires enabling ordinary or inverse relation joins.
- Repository APIs must change to express the required queries.
- UoW, lifecycle classification, or ID generation needs to change for relation query support.
- Tests require generating relation joins for reference IDs or non-owned relations to pass.
- The generated public API leaks `_lineItems`, `_files`, or any other private backing name.

Rollback target: return to this Phase 3.625 design, or to Phase 3.5 if the root cause is the entity backing-name contract.

## Agent Handoff Notes

An implementation agent may change:

- `ddd-domain-repo-jpa` schema helper classes;
- aggregate schema planner code;
- aggregate schema Pebble template;
- focused generator, renderer, schema helper, and compile functional tests.

An implementation agent must not change:

- repository public interfaces;
- repository adapter inheritance model;
- UoW API or JPA UoW implementation;
- Strong ID generation or identity roadmap phases;
- PR #132 owned-relation traversal/runtime lifecycle helpers;
- aggregate factory behavior;
- generated entity mutation API except for consuming Phase 3.5 naming facts;
- non-owned relation behavior;
- Querydsl decisions.

Before implementation, read these files:

1. [2026-07-23-cap4k-default-aggregate-template-structure-design.md](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/docs/superpowers/specs/2026-07-23-cap4k-default-aggregate-template-structure-design.md>)
2. [schema.kt.peb](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb>)
3. [SchemaArtifactPlanner.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt>)
4. [AggregateRelationPlanning.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt>)
5. [PipelineModels.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt>)
6. [JoinType.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/JoinType.kt>)
7. [Field.kt](</C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/Field.kt>)

Implementation should be split into small reviewable tasks:

1. Add relation helper runtime types and tests.
2. Add `relationJoins` planning and tests.
3. Update `schema.kt.peb` for relation fields, constants, join methods, and distinct overload.
4. Add renderer assertions.
5. Add compile functional coverage for owned-many, owned-one, chained join, and backing-name privacy.

Do not start from a broad runtime refactor. The smallest correct implementation is generator/template/query-helper work.

## Open Decisions

None blocking.

The API shape is fixed by this spec:

- `joinXxx()` defaulting to `INNER`;
- `joinXxx(JoinType)` for explicit join type;
- `RelationCollectionField` for owned-many existence;
- `RelationOptionalField` for owned-one nullable existence;
- `props` for scalar names;
- `relations` for relation names;
- no repository interface changes.

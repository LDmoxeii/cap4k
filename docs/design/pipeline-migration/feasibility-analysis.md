# Pipeline Full-Replacement Gap Analysis

> Date: 2026-04-15
> Status: Draft
> Scope: Exploratory analysis of parity gaps that would need explicit decisions if `cap4k-plugin-pipeline-*` were ever pushed toward full replacement of `cap4k-plugin-codegen`
> Non-goal: This document does not define the repository's current default mainline. Current default direction remains governed by `AGENTS.md` and `docs/superpowers/mainline-roadmap.md`.

---

## Background

The cap4k project has two code generation systems:

- **Old codegen** (`cap4k-plugin-codegen`): Monolithic Gradle plugin, mature and feature-complete
- **New pipeline** (`cap4k-plugin-pipeline-*`): Modular pipeline architecture, clean but incomplete

The pipeline has already absorbed multiple architecture and design-family slices, but full parity with the old codegen system is not the current default repository goal.

This document analyzes the **five parity gaps** that would require explicit decisions if full replacement ever became an active goal.

## Current Stable Direction

Current repository direction remains:

- fixed-stage pipeline architecture
- bounded design-family migration as the default mainline
- bootstrap / arch-template migration as a separate capability
- real-project integration work as a separate support track

The sections below are parity research notes, not the active repository roadmap.

---

## 1. Relation / Association Generation

### Gap

Old codegen extracts relationship metadata from **DB comment annotations**:

```sql
-- Table comment example
COMMENT ON TABLE order_item IS '订单明细 @Parent=order; @Lazy=true'
COMMENT ON COLUMN order_item.product_id IS '@Reference=product; @Relation=ManyToOne'
```

Recognized annotations: `@Parent`/`@P`, `@Reference`/`@Ref`, `@Relation`/`@Rel`, `@Lazy`/`@L`, `@Count`/`@C`

Supported relation types: `OneToMany`, `ManyToOne`, `*ManyToOne` (inverse nav), `OneToOne`, `ManyToMany`, `*ManyToMany`

The old `RelationContextBuilder` (~120 lines) resolves internal (parent-child) and external (ManyToMany via join table) relationships, then populates `relationsMap` for `EntityGenerator` to emit JPA annotations.

The new pipeline's `DbSchemaSourceProvider` uses standard JDBC `DatabaseMetaData` and **does not parse comment annotations**. `DbColumnSnapshot` / `DbTableSnapshot` have no relation fields. `CanonicalModel` has no relation model.

### Proposed Solutions

#### Option A: Extend SourceProvider with annotation parsing

Add annotation extraction to `DbSchemaSourceProvider`:

```kotlin
data class DbColumnSnapshot(
    // ... existing fields
    val annotations: Map<String, String> = emptyMap(),
)

data class DbTableSnapshot(
    // ... existing fields
    val annotations: Map<String, String> = emptyMap(),
    val parentTable: String? = null,
)
```

- **Pro**: Keeps pipeline stage separation; annotation parsing stays in Source layer
- **Con**: `DbColumnSnapshot` must tolerate "no annotations" mode for pure-JDBC users

#### Option B: Model relations in CanonicalModel

```kotlin
data class RelationModel(
    val type: RelationType,        // ONE_TO_MANY, MANY_TO_ONE, etc.
    val sourceEntity: String,
    val targetEntity: String,
    val joinColumn: String,
    val fetchType: FetchType = FetchType.EAGER,
    val cascade: List<CascadeType> = emptyList(),
    val inverseJoinColumn: String? = null,
    val joinTable: String? = null,
)

data class CanonicalModel(
    // ... existing fields
    val relations: List<RelationModel> = emptyList(),
)
```

- **Pro**: Relations are first-class citizens; generators consume strongly-typed models
- **Con**: Assembler complexity increases; must re-implement ~120 lines of inference logic

#### Option C: Standalone RelationSourceProvider

New `SourceProvider` specifically for relation metadata, separate from DB schema:

```kotlin
data class RelationSnapshot(
    override val id: String = "relations",
    val relations: List<RelationEntry>,
) : SourceSnapshot
```

- **Pro**: Does not pollute pure-JDBC source; future-proof for YAML/GUI sources
- **Con**: Additional source component; more configuration surface

### Recommended Direction If Activated

**A + B combined**: Extend `DbColumnSnapshot` to carry annotations (Option A), then build `RelationModel` in the Assembler (Option B). Option C as a future optimization.

### Assessment

| Dimension | Rating |
|-----------|--------|
| Technical feasibility | **High** - inference logic already proven in old code |
| Effort | **Medium** - ~500-800 lines new code |
| Risk | Implicit conventions in old code (e.g. `*` prefix for inverse nav) need explicit design decisions |

---

## 2. JPA Annotation Fine-grained Control

### Gap

Old `EntityGenerator.buildContext()` + `processAnnotationLines()` contains ~300 lines of annotation logic:

| Category | Annotations | Decision basis |
|----------|-------------|----------------|
| Basic JPA | `@Entity`, `@Table`, `@DynamicInsert`, `@DynamicUpdate` | Every entity |
| ID strategy | `@Id`, `@GeneratedValue`, `@GenericGenerator`, `@IdClass` | Single/composite PK, ValueObject, custom ID generator |
| Optimistic lock | `@Version` | `versionField` config |
| Soft delete | `@SQLDelete`, `@Where` | `deletedField` config + version field presence |
| Field control | `@Column(insertable, updatable)` | `readonlyFields`, `ignoreInsert`/`ignoreUpdate` |
| Type conversion | `@Convert(converter = ...)` | `@Type`/`@T` annotation |
| Relations | `@OneToMany`, `@ManyToOne`, `@JoinColumn`, `@JoinTable`, `@Fetch` | Relation model (see section 1) |
| Framework | `@Aggregate(aggregate, type, root, name, description)` | Cap4k DDD annotation |
| Inheritance | Base class resolution with `${Entity}`, `${IdentityType}` placeholders | `rootEntityBaseClass`, `entityBaseClass` config |

New pipeline's `EntityArtifactPlanner` passes only 6 bare fields to template context.

### Proposed Solutions

#### Option A: Annotation model (Planner-side)

Build full annotation model in Planner, template just renders:

```kotlin
data class JpaAnnotationModel(
    val classAnnotations: List<AnnotationModel>,
    val fieldAnnotations: Map<String, List<AnnotationModel>>,
)
```

- **Pro**: Templates are trivial; annotation logic is unit-testable
- **Con**: `EntityModel` bloats; Assembler/Generator coupling increases

#### Option B: Rich EntityModel metadata, annotations inferred in template

Provide sufficient metadata in `EntityModel`, let Pebble templates decide annotation output:

```kotlin
data class EntityModel(
    // ... existing fields
    val isAggregateRoot: Boolean = false,
    val isValueObject: Boolean = false,
    val aggregateName: String = "",
    val baseClass: String? = null,
    val softDelete: SoftDeleteConfig? = null,
    val versionField: String? = null,
    val idStrategy: IdStrategyConfig,
    val relations: List<RelationModel> = emptyList(),
)

data class FieldModel(
    // ... existing fields
    val isPrimaryKey: Boolean = false,
    val isReadOnly: Boolean = false,
    val isVersion: Boolean = false,
    val customType: String? = null,
    val ignoreInsert: Boolean = false,
    val ignoreUpdate: Boolean = false,
)
```

Template example:

```pebble
{% if isAggregateRoot %}
@Aggregate(aggregate = "{{ aggregateName }}", root = true, type = Aggregate.TYPE_ENTITY)
{% endif %}
@Entity
@Table(name = "`{{ tableName }}`")
{% if softDelete is not null %}
@SQLDelete(sql = "update `{{ tableName }}` set `{{ softDelete.column }}` = ...")
@Where(clause = "`{{ softDelete.column }}` = 0")
{% endif %}
```

- **Pro**: Separation of concerns (data vs presentation); templates are user-overridable via `overrideDirs`
- **Con**: Template logic becomes complex (but templates *are* the presentation layer)

#### Option C: Hybrid

Simple annotations hardcoded in template; complex annotations (SQL string building, strategy selection) pre-built as strings in Planner.

### Recommended Direction If Activated

**Option B** - aligns with Pipeline's "data model + template" philosophy; users can customize annotation output by overriding templates.

### Assessment

| Dimension | Rating |
|-----------|--------|
| Technical feasibility | **High** - Pebble's conditional/loop syntax handles this easily |
| Effort | Option A ~600 lines, Option B ~400 lines |
| Risk | Intersection with "user code preservation" (old code reads existing annotations from files) |

---

## 3. User Code Preservation

### Gap

Old codegen's `EntityGenerator.processEntityCustomerSourceFile()` implements **in-file region preservation**:

```kotlin
// Existing Entity.kt structure:
import xxx                          // ← preserved: user imports
@Aggregate(...)                     // ← preserved: user annotations
class Order {
    // 【字段映射开始】
    @Id var id: Long = 0            // ← overwrite zone: regenerated
    @Column var name: String = ""
    // 【字段映射结束】

    fun customMethod() { ... }       // ← preserved: user code
}
```

New pipeline's `ConflictPolicy` only has `SKIP` / `OVERWRITE` / `FAIL` - file-level granularity only.

### Proposed Solutions

#### Option A: MERGE ConflictPolicy + marker-based merger

```kotlin
enum class ConflictPolicy {
    SKIP, OVERWRITE, FAIL,
    MERGE,  // new
}

interface ContentMerger {
    fun merge(existingContent: String, generatedContent: String): String
}

class MarkerBasedMerger(
    val beginMarker: String = "// 【字段映射开始】",
    val endMarker: String = "// 【字段映射结束】",
) : ContentMerger
```

- **Pro**: Semantically identical to old codegen; zero-friction migration
- **Con**: Exporter must understand file content semantics; merge logic is fragile

#### Option B: Generated base + user extension separation

Split generated and user code into separate files:

```kotlin
// Generated: OrderBase.kt (always overwritten)
@Entity @Table(name = "order")
@MappedSuperclass
abstract class OrderBase {
    @Id var id: Long = 0
    @Column var name: String = ""
}

// User: Order.kt (SKIP - generated only if missing)
class Order : OrderBase() {
    fun customMethod() { ... }
}
```

- **Pro**: Eliminates mixed-file parsing entirely; user code is 100% safe
- **Con**: **Breaking change** - alters project file structure; existing projects cannot directly migrate

#### Option C: AST-level merge

Use Kotlin compiler frontend / PSI parser for precise AST-level merge.

- **Pro**: Most precise, no text markers needed
- **Con**: Heavy dependency (kotlin-compiler); extreme complexity; fidelity hard to guarantee

#### Option D: Keep current behavior + documentation

Accept `SKIP`/`OVERWRITE` granularity. First generation uses `OVERWRITE`; subsequent uses `SKIP`. Users manually delete files to regenerate.

- **Pro**: Zero development cost
- **Con**: UX regression - old codegen supported field regeneration while preserving custom code

### Recommended Direction If Activated

Any merge-based preservation strategy expands exporter and file-merging responsibility, so this area should be treated as exploratory parity work rather than a default extension of the current fixed-stage pipeline boundary.

**Short-term: Option A** (MERGE + markers) for migration compatibility.
**Long-term: Option B** (base + extension separation) for new projects.

### Assessment

| Dimension | Option A | Option B | Option C | Option D |
|-----------|:---:|:---:|:---:|:---:|
| Technical feasibility | High | High | Medium | - |
| Migration compatibility | **Good** | Poor (breaking) | Good | Good |
| Maintenance cost | Medium | Low | High | None |
| Effort | ~300 lines | ~200 lines | ~1000+ lines | 0 |
| Correctness risk | Medium | Low | Low (if correct) | None |
| User experience | **Matches old** | Better (long-term) | Best | Regression |

---

## 4. Cross-Generator Type References

### Gap

Old codegen uses `typeMapping: MutableMap<String, String>` as shared runtime state between generators:

```kotlin
// EntityGenerator.onGenerated():
typeMapping["Order"] = "com.example.domain.aggregates.order.Order"
typeMapping["QOrder"] = "com.example.domain.aggregates.order.QOrder"

// FactoryGenerator.buildContext():
val fullEntityType = ctx.typeMapping[entityType]!!  // references registered FQN
```

New pipeline's `CanonicalModel` is immutable and built once. No runtime state sharing between generators.

### Proposed Solutions

#### Option A: Pre-compute all type mappings in Assembler

```kotlin
data class CanonicalModel(
    // ... existing fields
    val typeRegistry: Map<String, String> = emptyMap(),
)

// In Assembler:
val typeRegistry = mutableMapOf<String, String>()
aggregateModels.forEach { (_, entity, _) ->
    typeRegistry[entity.name] = "${entity.packageName}.${entity.name}"
    typeRegistry["Q${entity.name}"] = "${entity.packageName}.Q${entity.name}"
}
```

- **Pro**: CanonicalModel stays immutable; centralized; testable
- **Con**: Assembler must "know" what generators will produce (implicit coupling)

#### Option B: Two-pass pipeline

```
Pass 1: Source → Assembler → Model → Generator.plan() → ArtifactPlanItem[]
        ↓ extract all type names from PlanItems ↓
Pass 2: TypeRegistry injected → Generator.plan() (with full type refs) → Render → Export
```

- **Pro**: No immutability violation; generators remain independent
- **Con**: `plan()` called twice; type name extraction from `outputPath` is fragile

#### Option C: Eliminate the need entirely

In DDD frameworks, type naming is highly conventional. All type references can be derived from `CanonicalModel` fields:

```pebble
{# Instead of: #}
import {{ typeMapping["Order"] }}
{# Use: #}
import {{ entity.packageName }}.{{ entity.name }}
```

For QueryDSL Q-types: convention `Q` + entityName, same package as entity.

- **Pro**: Simplest; no extra data structures; no coupling
- **Con**: Must verify all template type references are derivable from model fields

### Recommended Direction If Activated

**Option C first** - audit all `typeMapping` usages in old templates; confirm they can be derived from `CanonicalModel` fields. Highly likely given DDD's naming conventions. Fall back to Option A for edge cases.

### Assessment

| Dimension | Option A | Option B | Option C |
|-----------|:---:|:---:|:---:|
| Technical feasibility | High | High | High |
| Complexity | Low | Medium | **Lowest** |
| Coupling | Medium | Low | **Lowest** |
| Effort | ~100 lines | ~200 lines | **~50 lines** |

---

## 5. Bootstrap / Scaffolding Parity

### Gap

Old codegen's `GenArchTask` creates project directory structure and boilerplate files from a JSON template tree + Pebble rendering.

New pipeline has no equivalent.

This parity gap overlaps directly with the repository's separate bootstrap / arch-template migration track.

Any future work in this area must align with the already accepted bootstrap direction:

- bootstrap remains a separate capability
- the public bootstrap contract uses slot-based extension
- users may add arbitrary numbers of files through bounded slots
- the old `archTemplate` JSON remains migration input or reference material, not the future public runtime contract

That means this gap should not be treated as a routine `GeneratorProvider` extension without first resolving the bootstrap contract at the framework level.

### Assessment

| Dimension | Rating |
|-----------|--------|
| Technical feasibility | **High**, once bootstrap contract is explicitly activated |
| Effort | Depends on chosen bootstrap contract; not yet a routine generator-sized task |

---

## Activation Conditions

This document should only be reactivated as planning input when full replacement or parity work is explicitly requested.

Typical activation conditions would be:

- the user explicitly asks for old-codegen parity analysis or full replacement work
- the repository roadmap is intentionally moved from bounded family migration to parity track work
- bootstrap / arch-template migration is explicitly reopened as a separate capability slice

## Not Current Default Work

The topics in this document are not the repository's current default mainline.

Current default mainline remains recorded in:

- `AGENTS.md`
- `docs/superpowers/mainline-roadmap.md`

This document therefore serves as exploratory parity analysis only, not as an actionable execution roadmap.

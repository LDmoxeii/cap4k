# Cap4k Adapter Aggregate Projection Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan. The implementation must use exploration subagents before code edits and review subagents after implementation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in, generation-only aggregate projection capability that emits adapter-layer JPA-flavored scalar read models from canonical aggregate metadata, while keeping query execution on existing `QueryHandler` / `Mediator.qry` flow and leaving Jimmer support to template overrides.

**Architecture:** Introduce a disabled-by-default Gradle generator block named `aggregateProjection`, map it to pipeline generator id `aggregate-projection`, plan build-owned Kotlin generated-source artifacts under the adapter module, and render them through a neutral template id `aggregate_projection/entity.kt.peb`. The built-in planner will expose scalar field metadata plus relation metadata in the template context, but the default template will render only scalar fields to prevent unbounded object graph reads.

**Tech Stack:** Kotlin, Gradle plugin API, existing Cap4k pipeline API/core, aggregate canonical model, Pebble templates, JUnit 5, Gradle TestKit.

---

## Source Specs And Constraints

- Approved spec: `docs/superpowers/specs/2026-05-12-cap4k-advanced-weak-reference-projection-design.md`
- Relevant capability maps:
  - `docs/superpowers/analysis/2026-05-11-cap4k-generator-input-output-and-verification-map.md`
  - `docs/superpowers/analysis/2026-05-11-cap4k-extension-spi-addon-and-gap-map.md`
  - `docs/superpowers/analysis/2026-05-11-cap4k-public-tactical-model-and-layering-map.md`

Non-goals for this slice:

- Do not add `ProjectionSupervisor`, `ReadShape`, `Mediator.prj`, `Mediator.ext`, or any read-model runtime API.
- Do not add a Jimmer dependency, Jimmer generator, or Jimmer-specific template id.
- Do not generate Jimmer DTO/fetcher classes by default.
- Do not add configurable package layout for projection files. The package root is fixed to `adapter.application.projections`.
- Do not rename existing aggregate generator ids or move existing aggregate entity generation.
- Do not change query contracts or handler invocation semantics.

User-facing DSL target:

```kotlin
cap4k {
    generators {
        aggregateProjection {
            enabled.set(true)
        }
    }
}
```

Expected default output path shape:

```text
<adapter-module>/build/generated/cap4k/main/kotlin/<basePackage>/adapter/application/projections/<aggregate-path>/<EntityName>Projection.kt
```

Expected neutral override path:

```text
<override-dir>/aggregate_projection/entity.kt.peb
```

---

## Required Subagent Flow

- [ ] **Step 1: Pre-implementation exploration agents**

Spawn two read-only exploration agents before code edits:

```text
Agent A: Gradle DSL/config/task integration for adding a new generated-source generator id.
Agent B: aggregate planner/template context reuse for scalar fields, relation metadata, and enum/type import handling.
```

Record findings before editing. If either finding contradicts this plan, update this plan first.

- [ ] **Step 2: Main-context implementation**

The main context owns all edits unless a worker is explicitly assigned a disjoint write scope. Keep changes additive and local to pipeline/renderer/docs.

- [ ] **Step 3: Post-implementation review agents**

After implementation and first verification, spawn two reviewers:

```text
Reviewer A: spec boundary audit, especially "no runtime Projection/Jimmer".
Reviewer B: generated-source/task/template/test coverage audit.
```

Evaluate every finding before changing code.

- [ ] **Step 4: Final verification**

Run the verification commands listed below after review fixes. Do not report completion until commands finish and outputs are checked.

---

## Implementation Tasks

- [ ] **Task 1: Add Gradle DSL surface**

Edit `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`.

Add `aggregateProjection` to `Cap4kGeneratorsExtension`:

```kotlin
val aggregateProjection: AggregateProjectionGeneratorExtension =
    objects.newInstance(AggregateProjectionGeneratorExtension::class.java)

fun aggregateProjection(block: AggregateProjectionGeneratorExtension.() -> Unit) {
    aggregateProjection.block()
}
```

Add the extension class near other generator extension classes:

```kotlin
open class AggregateProjectionGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}
```

Tests to add in `Cap4kProjectConfigFactoryTest.kt`:

- disabled defaults include `assertFalse(extension.generators.aggregateProjection.enabled.get())`
- enabling block compiles through Kotlin DSL style in unit setup:

```kotlin
extension.generators {
    aggregateProjection { enabled.set(true) }
}
```

- [ ] **Task 2: Map `aggregateProjection` into project config**

Edit `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`.

Add `aggregateProjectionEnabled` to `GeneratorStates`, populate it from `extension.generators.aggregateProjection.enabled.get()`, and add these rules:

```kotlin
if (generators.aggregateProjectionEnabled) {
    extension.project.adapterModulePath.requiredWhenEnabled(
        "project.adapterModulePath",
        "aggregateProjection"
    )
}
```

In `buildModules`, add:

```kotlin
if (generators.aggregateProjectionEnabled) {
    put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
}
```

In `buildGenerators`, add:

```kotlin
if (states.aggregateProjectionEnabled) {
    put("aggregate-projection", GeneratorConfig(enabled = true))
}
```

In `validateGeneratorDependencies`, add:

```kotlin
if (generators.aggregateProjectionEnabled && !sources.dbEnabled) {
    throw IllegalArgumentException("aggregateProjection generator requires enabled db source.")
}
```

Tests to add in `Cap4kProjectConfigFactoryTest.kt`:

- `factory includes adapter module and aggregate projection generator when enabled`
- `aggregate projection generator requires adapter module path`
- `aggregate projection generator requires enabled db source`
- `aggregate projection can be enabled without aggregate generator`

- [ ] **Task 3: Register generator with Gradle tasks and generated source wiring**

Edit `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`.

Add import:

```kotlin
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateProjectionArtifactPlanner
```

Update task id sets:

```kotlin
private val SOURCE_TASK_GENERATOR_IDS = setOf(
    ...
    "aggregate",
    "aggregate-projection",
)
private val GENERATED_SOURCE_TASK_GENERATOR_IDS = setOf("aggregate", "aggregate-projection")
```

Update `hasEnabledRegularGenerator` to include:

```kotlin
extension.generators.aggregateProjection.enabled
```

Update `generatedSourceModuleRoles` so aggregate projection contributes only `adapter`:

```kotlin
val roles = linkedSetOf<String>()
val aggregate = config.generators["aggregate"]
if (aggregate?.enabled == true) {
    roles += "domain"
    roles += "adapter"
    if (aggregate.options["artifact.unique"] as? Boolean == true) {
        roles += "application"
    }
}
if (config.generators["aggregate-projection"]?.enabled == true) {
    roles += "adapter"
}
return roles.filterTo(linkedSetOf()) { role -> role in config.modules }
```

Update `generatedSourceTaskInputSnapshot` to include:

```kotlin
"aggregateProjection" to sanitizedGeneratorSnapshot(config.generators["aggregate-projection"])
```

Register `AggregateProjectionArtifactPlanner()` in `buildSourceRunner` generator list immediately after `AggregateArtifactPlanner()`.

Tests to add in Gradle plugin tests:

- unit test for `generatedSourceModuleRoles` with only `aggregate-projection` enabled returns `setOf("adapter")`
- unit test for `generatedSourceTaskConfig` retains generator id `aggregate-projection`
- unit or functional test that `cap4kGenerateSources` includes aggregate projection artifacts when enabled

- [ ] **Task 4: Add projection artifact planner**

Add `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateProjectionArtifactPlanner.kt`.

Planner contract:

```kotlin
class AggregateProjectionArtifactPlanner : GeneratorProvider {
    override val id: String = "aggregate-projection"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)

        return model.entities.map { entity ->
            ...
        }
    }
}
```

For each `EntityModel`:

- Use adapter module role.
- Use template id `aggregate_projection/entity.kt.peb`.
- Use type name `${entity.name}Projection`.
- Use conflict policy `OVERWRITE`.
- Use output kind `GENERATED_SOURCE`.
- Use resolved output root from `artifactLayout.generatedKotlinSourceRoot(adapterRoot)`.
- Use package root `ArtifactLayoutResolver.joinPackage(config.basePackage, "adapter.application.projections", relativeAggregatePackage(entity.packageName, config))`.

Add helper in the same file:

```kotlin
private fun projectionPackageName(config: ProjectConfig, entityPackage: String): String {
    val aggregateRoot = ArtifactLayoutResolver.joinPackage(
        config.basePackage,
        config.artifactLayout.aggregate.packageRoot,
    )
    val suffix = entityPackage.trim('.')
        .removePrefix(aggregateRoot)
        .trim('.')
    return ArtifactLayoutResolver.joinPackage(
        config.basePackage,
        "adapter.application.projections",
        suffix,
    )
}
```

Do not use `artifactLayout.designQueryHandlerPackage` or introduce `artifactLayout.aggregateProjection`.

To support a generator id different from existing `generatedKotlinArtifact`, either:

- add a local `projectionGeneratedKotlinArtifact(...)` in the new file, or
- generalize `generatedKotlinArtifact(...)` in `AggregateArtifactOutputs.kt` with a default `generatorId: String = "aggregate"` parameter.

Use the second option only if it keeps existing aggregate tests unchanged.

- [ ] **Task 5: Build scalar and relation template context**

In `AggregateProjectionArtifactPlanner.kt`, compute context using existing aggregate helpers:

- `AggregateEnumPlanning.from(...)` for field type resolution and enum items.
- `AggregateRelationPlanning.planFor(...)` for `relationFields`, `imports`, and relation metadata.
- `model.aggregateEntityJpa` for table/column metadata.
- `model.aggregatePersistenceFieldControls` for insertable/updatable/converter hints.
- `model.aggregateSpecialFieldResolvedPolicies` for version/deleted/managed-field flags.
- `model.aggregatePersistenceProviderControls` for table and soft-delete metadata.

Required context keys:

```kotlin
mapOf(
    "packageName" to projectionPackageName,
    "typeName" to "${entity.name}Projection",
    "sourceTypeName" to entity.name,
    "sourcePackageName" to entity.packageName,
    "comment" to entity.comment,
    "tableName" to entity.tableName,
    "entityJpa" to mapOf(
        "entityEnabled" to (entityJpa?.entityEnabled ?: true),
        "tableName" to (entityJpa?.tableName ?: entity.tableName),
    ),
    "idField" to entity.idField,
    "hasConverterFields" to scalarFields.any { it["converterClassRef"] != null },
    "hasVersionFields" to scalarFields.any { it["isVersion"] == true },
    "imports" to scalarImports.distinct(),
    "fields" to scalarFields,
    "scalarFields" to scalarFields,
    "relationFields" to relationPlan.relationFields,
    "relations" to relationPlan.relationFields,
)
```

Each scalar field map must include at least:

```kotlin
mapOf(
    "fieldName" to field.name,
    "name" to field.name,
    "fieldType" to fieldType,
    "type" to fieldType,
    "nullable" to field.nullable,
    "defaultValue" to null,
    "typeBinding" to field.typeBinding,
    "enumItems" to planning.resolveEnumItems(entity.packageName, field),
    "columnName" to jpa.columnName,
    "isId" to jpa.isId,
    "isVersion" to isVersionField,
    "converterTypeRef" to jpa.converterTypeFqn,
    "converterClassRef" to jpa.converterClassFqn,
    "insertable" to null,
    "updatable" to null,
)
```

Default values should be `null` for projection constructor parameters unless an existing helper proves a DB-safe scalar default is needed. The projection read model should not inherit application-side id defaults.

Relation join columns must not cause relation object graph fields in the default template. Keep relation metadata in context for overrides, but default output renders only `scalarFields`.

Tests to add in `AggregateArtifactPlannerTest.kt` or a new `AggregateProjectionArtifactPlannerTest.kt`:

- plans one projection per canonical entity
- output path is under adapter generated source root
- generator id is `aggregate-projection`
- template id is `aggregate_projection/entity.kt.peb`
- type name is `<EntityName>Projection`
- package is `<basePackage>.adapter.application.projections.<aggregate suffix>`
- context contains `scalarFields` and `relationFields`
- default scalar fields include FK/id/version/converter metadata but do not include relation object fields as scalar fields
- planner errors clearly if required JPA metadata for a scalar field is missing, matching existing aggregate planner style

- [ ] **Task 6: Add neutral default projection template**

Add `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate_projection/entity.kt.peb`.

Template shape:

```pebble
package {{ packageName }}

{{ use("jakarta.persistence.Column") -}}
{{ use("jakarta.persistence.Entity") -}}
{{ use("jakarta.persistence.Id") -}}
{{ use("jakarta.persistence.Table") -}}
{% if hasVersionFields -%}
{{ use("jakarta.persistence.Version") -}}
{% endif -%}
{% if hasConverterFields -%}
{{ use("jakarta.persistence.Convert") -}}
{% endif -%}
{% for import in imports -%}
{{ use(import) -}}
{% endfor -%}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}
@Entity
@Table(name = "{{ entityJpa.tableName }}")
class {{ typeName }}(
{% for field in scalarFields -%}
{{ "    " }}{{ field.name }}: {{ field.type }}{% if field.nullable %}?{% endif %}{% if not loop.last %},{% endif %}
{% endfor -%}
) {
{% for field in scalarFields %}
{% if field.isId %}    @Id
{% endif %}{% if field.isVersion %}    @Version
{% endif %}    @Column(name = "{{ field.columnName }}")
{% if field.converterClassRef %}    @Convert(converter = {{ field.converterClassRef }}::class)
{% endif %}    var {{ field.name }}: {{ field.type }}{% if field.nullable %}?{% endif %} = {{ field.name }}
        internal set

{% endfor -%}
}
```

Do not render `relationFields` in this default template. Do not add `@GeneratedValue`, `ApplicationSideId`, Hibernate soft-delete annotations, or relation annotations to default projection output.

Tests to add in `PebbleArtifactRendererTest.kt`:

- renders scalar JPA projection with `@Entity`, `@Table`, `@Id`, `@Column`
- renders `@Version` only when `hasVersionFields` is true
- renders converter annotation when field has `converterClassRef`
- does not render `ManyToOne`, `OneToMany`, `OneToOne`, or relation property names even when `relationFields` is present in context
- template override resolves with `aggregate_projection/entity.kt.peb`

- [ ] **Task 7: Document template override and public generator behavior**

Update docs that describe generator input/output and template customization:

- `docs/superpowers/analysis/2026-05-11-cap4k-generator-input-output-and-verification-map.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-extension-spi-addon-and-gap-map.md`
- `docs/superpowers/analysis/2026-05-11-cap4k-public-tactical-model-and-layering-map.md`

Required documentation points:

- `aggregateProjection` is disabled by default.
- It is enabled with `generators.aggregateProjection.enabled`.
- It uses DB/canonical aggregate metadata and requires `sources.db`.
- It emits adapter generated source under fixed package root `adapter.application.projections`.
- The neutral template id is `aggregate_projection/entity.kt.peb`.
- Default output is JPA-flavored scalar projection only.
- Jimmer users should override `aggregate_projection/entity.kt.peb`; they should not override a file named `jpa_projection` or `projection/jpa_projection`.
- No runtime Projection/Mediator/Jimmer abstraction is provided in this slice.

- [ ] **Task 8: Guard against accidental old naming**

Search after implementation:

```powershell
rg -n "jpa_projection|readmodels|ReadShape|ProjectionSupervisor|Mediator\\.prj|Mediator\\.ext|aggregateProjectionPackage|layout\\.aggregateProjection" cap4k
```

Allowed hits:

- historical spec discussion in `docs/superpowers/specs/2026-05-12-cap4k-advanced-weak-reference-projection-design.md` if present
- this plan's guard list

No production code should contain those runtime or old-template names.

---

## Verification Commands

Run from `cap4k`:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
./gradlew :cap4k-plugin-pipeline-core:test
./gradlew :cap4k-plugin-pipeline-gradle:test
```

Run a final naming/boundary search from workspace root:

```powershell
rg -n "jpa_projection|readmodels|ReadShape|ProjectionSupervisor|Mediator\\.prj|Mediator\\.ext|aggregateProjectionPackage|layout\\.aggregateProjection" cap4k
```

If functional tests exist for generated-source compilation in the current branch, also run the Gradle functional test task that owns `cap4kGenerateSources`. If no such source set exists, record that explicitly in the final implementation notes.

---

## Self-Review Checklist

- [ ] `aggregateProjection` defaults to disabled and requires explicit enablement.
- [ ] `aggregateProjection` requires `sources.db` but does not require `generators.aggregate`.
- [ ] Adapter module is the only required module for projection generation.
- [ ] Planned artifacts are build-owned generated source with overwrite conflict policy.
- [ ] Projection package root is fixed to `adapter.application.projections`.
- [ ] Template id is exactly `aggregate_projection/entity.kt.peb`.
- [ ] Built-in template is JPA-flavored and scalar-only.
- [ ] Relation metadata is present in context for overrides but not rendered by default.
- [ ] No runtime projection mediator, read shape, or Jimmer abstraction is added.
- [ ] Generated source roots and `cap4kGenerateSources` include adapter output when only aggregate projection is enabled.
- [ ] Docs describe Jimmer as a template override path, not a built-in generator.

# cap4k Artifact Addon SPI And only-engine Enum Translation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove only-engine enum translation from cap4k core, expose a general post-canonical artifact addon SPI, and migrate only-engine enum translation into a same-cycle addon module.

**Architecture:** cap4k owns canonical assembly, addon loading, plan item normalization, template resolution, rendering, and export. Addons are build-time dependencies that inspect `ProjectConfig` and `CanonicalModel`, return normal `ArtifactPlanItem`s, and use the same template override and conflict-policy mechanics as built-in artifacts. only-engine adds `engine-cap4k-addon` as the first reference addon and owns only-engine translation templates.

**Tech Stack:** Kotlin JVM, Gradle plugin API, Java `ServiceLoader`, Pebble templates, JUnit 5, Gradle TestKit, cap4k pipeline modules, only-engine Gradle multi-module build.

---

## Repository Scope

This plan spans two repositories:

- `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`
- `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine`

Use separate worktrees or separate commits per repository. The cap4k issue `#33` remains the governing issue. If implementation execution creates an only-engine tracking issue, link it from `#33` before closing `#33`.

## File Map

### cap4k API and Core

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineContracts.kt`
  - Add `ArtifactAddonProvider` and `ArtifactAddonContext`.
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Remove `SharedEnumDefinition.generateTranslation`.
  - Add canonical enum catalog types that addon providers can reuse.
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/CanonicalEnumCatalog.kt`
  - Move the public enum ownership/type-resolution helper here.
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
  - Remove `ArtifactLayoutConfig.aggregateEnumTranslation`.
  - Keep `TemplateConfig.templateConflictPolicies`.
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
  - Remove `aggregateEnumTranslationPackage`.
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
  - Accept addon providers and append addon plan items after built-in generator plan items.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt`
  - Resolve addon templates from project override dirs first, then addon jar resources.

### cap4k Gradle Plugin

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
  - Create the `cap4kAddon` configuration.
  - Load addon providers from that configuration with `ServiceLoader`.
  - Pass addon providers and addon template classloaders into source and analysis runners.
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/ArtifactAddonLoader.kt`
  - Keep classloader and `ServiceLoader` logic out of `PipelinePlugin.kt`.
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
  - Remove `layout.aggregateEnumTranslation`.
  - Remove `generators.aggregate.artifacts.enumTranslation`.
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
  - Stop emitting `artifact.enumTranslation`.
  - Stop building `aggregateEnumTranslation` layout.

### cap4k Aggregate Generator and Sources

- Delete: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EnumTranslationArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
  - Remove enum translation delegate.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactSelection.kt`
  - Remove enum translation selection flag.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt`
  - Replace with the API-level catalog or delegate to it, then remove this internal duplicate.
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb`
- Modify: `cap4k-plugin-pipeline-source-enum-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProvider.kt`
  - Stop parsing `generateTranslation`.
  - Fail fast when a manifest still contains `generateTranslation`.

### cap4k Tests, Fixtures, and Docs

- Modify tests under:
  - `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/`
  - `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/`
  - `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/`
  - `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/`
  - `cap4k-plugin-pipeline-source-enum-manifest/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/`
  - `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/`
- Remove or rewrite functional fixtures:
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample`
- Modify public docs:
  - `docs/public/reference/generator-dsl.zh-CN.md`
  - docs that describe aggregate enum translation as core behavior

### only-engine Addon

- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\settings.gradle.kts`
  - Include `:engine-cap4k-addon`.
  - Add the cap4k Maven repository for cap4k plugin API dependencies.
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\gradle\libs.versions.toml`
  - Add cap4k plugin API dependency coordinates.
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\kotlin\com\only\engine\cap4k\addon\translation\OnlyEngineEnumTranslationAddonProvider.kt`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\resources\META-INF\services\com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\resources\cap4k\addons\only-engine-enum-translation\aggregate\enum_translation.kt.peb`
- Create tests under `engine-cap4k-addon/src/test/kotlin/com/only/engine/cap4k/addon/translation/`

---

### Task 1: Add cap4k API Contracts For Artifact Addons

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineContracts.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

- [ ] **Step 1: Add a failing API test for addon provider contracts**

Append this test to `PipelineModelsTest.kt`:

```kotlin
@Test
fun `artifact addon provider can create plan items from canonical context`() {
    val provider = object : ArtifactAddonProvider {
        override val id: String = "sample-addon"

        override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> =
            listOf(
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = "addons/sample-addon/sample.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/Sample.kt",
                    context = mapOf("basePackage" to context.config.basePackage),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            )
    }

    val config = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = mapOf("adapter" to "demo-adapter"),
        sources = emptyMap(),
        generators = emptyMap(),
        templates = TemplateConfig(
            preset = "ddd-default",
            overrideDirs = emptyList(),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )

    val items = provider.plan(
        ArtifactAddonContext(
            config = config,
            model = CanonicalModel(),
            options = emptyMap(),
        )
    )

    assertEquals("sample-addon", provider.id)
    assertEquals("sample-addon", items.single().generatorId)
    assertEquals("addons/sample-addon/sample.kt.peb", items.single().templateId)
    assertEquals("com.acme.demo", items.single().context["basePackage"])
}
```

- [ ] **Step 2: Run the API test and verify it fails**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest.artifact addon provider can create plan items from canonical context"
```

Expected: compilation fails because `ArtifactAddonProvider` and `ArtifactAddonContext` do not exist.

- [ ] **Step 3: Add addon provider API types**

Modify `PipelineContracts.kt`:

```kotlin
interface ArtifactAddonProvider {
    val id: String

    fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem>
}
```

Add this near `PipelineResult`-adjacent model declarations in `PipelineModels.kt`:

```kotlin
data class ArtifactAddonContext(
    val config: ProjectConfig,
    val model: CanonicalModel,
    val options: Map<String, Any?> = emptyMap(),
)
```

- [ ] **Step 4: Run the API test and verify it passes**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest.artifact addon provider can create plan items from canonical context"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Task 1**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineContracts.kt cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add artifact addon provider api"
```

### Task 2: Promote Canonical Enum Resolution To cap4k API

**Files:**
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/CanonicalEnumCatalog.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/CanonicalEnumCatalogTest.kt`
- Test: existing aggregate enum planning tests

- [ ] **Step 1: Write failing tests for shared and local enum descriptors**

Create `CanonicalEnumCatalogTest.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CanonicalEnumCatalogTest {

    @Test
    fun `catalog resolves shared and local enum descriptors`() {
        val statusItems = listOf(EnumItemModel(1, "OPEN", "open"))
        val priorityItems = listOf(EnumItemModel(1, "HIGH", "high"))
        val model = CanonicalModel(
            sharedEnums = listOf(
                SharedEnumDefinition(
                    typeName = "OrderStatus",
                    packageName = "shared",
                    items = statusItems,
                )
            ),
            entities = listOf(
                EntityModel(
                    packageName = "order",
                    typeName = "Order",
                    tableName = "order",
                    fields = listOf(
                        FieldModel(
                            name = "priority",
                            type = "Int",
                            typeBinding = "OrderPriority",
                            enumItems = priorityItems,
                        )
                    )
                )
            )
        )

        val catalog = CanonicalEnumCatalog.from(
            model = model,
            artifactLayout = ArtifactLayoutResolver("com.acme.demo", ArtifactLayoutConfig()),
            typeRegistry = emptyMap(),
        )

        assertEquals(
            CanonicalEnumDescriptor(
                scope = CanonicalEnumScope.SHARED,
                ownerPackageName = "",
                ownerTableName = "",
                ownerScope = "shared",
                typeName = "OrderStatus",
                typeFqn = "com.acme.demo.domain.shared.enums.OrderStatus",
                items = statusItems,
            ),
            catalog.shared.single(),
        )
        assertEquals(
            CanonicalEnumDescriptor(
                scope = CanonicalEnumScope.LOCAL,
                ownerPackageName = "order",
                ownerTableName = "order",
                ownerScope = "order",
                typeName = "OrderPriority",
                typeFqn = "com.acme.demo.domain.aggregates.order.enums.OrderPriority",
                items = priorityItems,
            ),
            catalog.local.single(),
        )
    }

    @Test
    fun `catalog fails when shared enum and type registry collide`() {
        val model = CanonicalModel(
            sharedEnums = listOf(
                SharedEnumDefinition(
                    typeName = "OrderStatus",
                    packageName = "shared",
                    items = listOf(EnumItemModel(1, "OPEN", "open")),
                )
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            CanonicalEnumCatalog.from(
                model = model,
                artifactLayout = ArtifactLayoutResolver("com.acme.demo", ArtifactLayoutConfig()),
                typeRegistry = mapOf("OrderStatus" to TypeRegistryEntry("com.acme.types.OrderStatus")),
            )
        }

        assertEquals(
            "ambiguous type binding for OrderStatus: matches both shared enum and general type registry",
            error.message,
        )
    }
}
```

- [ ] **Step 2: Run the new catalog tests and verify they fail**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalogTest"
```

Expected: compilation fails because `CanonicalEnumCatalog`, `CanonicalEnumDescriptor`, and `CanonicalEnumScope` do not exist, and `SharedEnumDefinition.generateTranslation` is still required.

- [ ] **Step 3: Add API-level canonical enum catalog**

Create `CanonicalEnumCatalog.kt` by moving the logic from `AggregateEnumPlanning.kt` into public API names:

```kotlin
package com.only4.cap4k.plugin.pipeline.api

enum class CanonicalEnumScope {
    SHARED,
    LOCAL,
}

data class CanonicalEnumDescriptor(
    val scope: CanonicalEnumScope,
    val ownerPackageName: String,
    val ownerTableName: String,
    val ownerScope: String,
    val typeName: String,
    val typeFqn: String,
    val items: List<EnumItemModel>,
)

class CanonicalEnumCatalog private constructor(
    val shared: List<CanonicalEnumDescriptor>,
    val local: List<CanonicalEnumDescriptor>,
    private val sharedEnumFqns: Map<String, String>,
    private val sharedEnumItems: Map<String, List<EnumItemModel>>,
    private val localEnumFqns: Map<LocalEnumOwnerKey, String>,
    private val localEnumItems: Map<LocalEnumOwnerKey, List<EnumItemModel>>,
    private val typeRegistry: Map<String, TypeRegistryEntry>,
) {
    val all: List<CanonicalEnumDescriptor> = shared + local

    fun resolveFieldType(typeName: String, enumItems: List<EnumItemModel>): String =
        resolveFieldType(ownerPackageName = null, typeName = typeName, enumItems = enumItems)

    fun resolveFieldType(ownerPackageName: String?, typeName: String, enumItems: List<EnumItemModel>): String =
        resolveKnownType(ownerPackageName, typeName) ?: typeName

    fun resolveFieldType(field: FieldModel): String =
        resolveFieldType(ownerPackageName = null, field = field)

    fun resolveFieldType(ownerPackageName: String?, field: FieldModel): String {
        val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() }
        if (typeBinding != null) {
            return resolveTypeBinding(ownerPackageName, typeBinding)
        }
        return resolveFieldType(ownerPackageName, field.type, field.enumItems)
    }

    fun resolveEnumItems(ownerPackageName: String?, field: FieldModel): List<EnumItemModel> {
        if (field.enumItems.isNotEmpty()) {
            return field.enumItems
        }
        val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (ownerPackageName != null) {
            localEnumItems[LocalEnumOwnerKey(ownerPackageName = ownerPackageName, typeBinding = typeBinding)]?.let {
                return it
            }
        }
        return sharedEnumItems[typeBinding].orEmpty()
    }

    private fun resolveTypeBinding(ownerPackageName: String?, typeName: String): String =
        resolveKnownType(ownerPackageName, typeName)
            ?: if (typeName in builtInTypeNames) {
                typeName
            } else {
                throw IllegalArgumentException(
                    "unresolved type binding for $typeName: expected enum manifest, type registry, FQN, or built-in type"
                )
            }

    private fun resolveKnownType(ownerPackageName: String?, typeName: String): String? {
        if ('.' in typeName) {
            return typeName
        }
        if (ownerPackageName != null) {
            localEnumFqns[LocalEnumOwnerKey(ownerPackageName = ownerPackageName, typeBinding = typeName)]?.let {
                return it
            }
        }
        sharedEnumFqns[typeName]?.let { return it }
        typeRegistry[typeName]?.fqn?.let { return it }
        return null
    }

    companion object {
        fun from(
            model: CanonicalModel,
            basePackage: String,
            typeRegistry: Map<String, TypeRegistryEntry>,
        ): CanonicalEnumCatalog =
            from(model, ArtifactLayoutResolver(basePackage, ArtifactLayoutConfig()), typeRegistry)

        fun from(
            model: CanonicalModel,
            artifactLayout: ArtifactLayoutResolver,
            typeRegistry: Map<String, TypeRegistryEntry>,
        ): CanonicalEnumCatalog {
            val shared = buildSharedEnumDescriptors(model.sharedEnums, artifactLayout)
            val local = buildLocalEnumDescriptors(model.entities, artifactLayout)
            val sharedEnumFqns = shared.associate { it.typeName to it.typeFqn }
            val sharedEnumItems = shared.associate { it.typeName to it.items }
            val localEnumFqns = local.associate { LocalEnumOwnerKey(it.ownerPackageName, it.typeName) to it.typeFqn }
            val localEnumItems = local.associate { LocalEnumOwnerKey(it.ownerPackageName, it.typeName) to it.items }

            sharedEnumFqns.keys.firstOrNull { it in typeRegistry }?.let { typeName ->
                throw IllegalArgumentException(
                    "ambiguous type binding for $typeName: matches both shared enum and general type registry"
                )
            }
            localEnumFqns.keys.firstOrNull { it.typeBinding in sharedEnumFqns }?.let { key ->
                throw IllegalArgumentException(
                    "ambiguous enum ownership for ${key.typeBinding}: matches both shared enum and local enum in ${key.ownerPackageName}"
                )
            }
            localEnumFqns.keys.firstOrNull { it.typeBinding in typeRegistry }?.let { key ->
                throw IllegalArgumentException(
                    "ambiguous enum ownership for ${key.typeBinding}: " +
                        "matches both local enum in ${key.ownerPackageName} and general type registry"
                )
            }

            return CanonicalEnumCatalog(
                shared = shared,
                local = local,
                sharedEnumFqns = sharedEnumFqns,
                sharedEnumItems = sharedEnumItems,
                localEnumFqns = localEnumFqns,
                localEnumItems = localEnumItems,
                typeRegistry = typeRegistry,
            )
        }

        private fun buildSharedEnumDescriptors(
            definitions: List<SharedEnumDefinition>,
            artifactLayout: ArtifactLayoutResolver,
        ): List<CanonicalEnumDescriptor> {
            val grouped = definitions.groupBy { it.typeName.trim() }
            grouped.entries.firstOrNull { it.value.size > 1 }?.key?.let { duplicated ->
                throw IllegalArgumentException("duplicate shared enum definition: $duplicated")
            }
            return grouped.values.map { values ->
                val definition = values.single()
                val packageName = resolveSharedEnumPackageName(definition.packageName, artifactLayout)
                val fqn = if (packageName.isBlank()) definition.typeName else "$packageName.${definition.typeName}"
                CanonicalEnumDescriptor(
                    scope = CanonicalEnumScope.SHARED,
                    ownerPackageName = "",
                    ownerTableName = "",
                    ownerScope = "shared",
                    typeName = definition.typeName,
                    typeFqn = fqn,
                    items = definition.items,
                )
            }
        }

        private fun buildLocalEnumDescriptors(
            entities: List<EntityModel>,
            artifactLayout: ArtifactLayoutResolver,
        ): List<CanonicalEnumDescriptor> {
            val grouped = entities
                .flatMap { entity ->
                    entity.fields.mapNotNull { field ->
                        val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        if (field.enumItems.isEmpty()) return@mapNotNull null
                        LocalEnumOwnerKey(entity.packageName, typeBinding) to CanonicalEnumDescriptor(
                            scope = CanonicalEnumScope.LOCAL,
                            ownerPackageName = entity.packageName,
                            ownerTableName = entity.tableName,
                            ownerScope = aggregateTableSegment(entity.tableName),
                            typeName = typeBinding,
                            typeFqn = "${artifactLayout.aggregateLocalEnumPackage(entity.packageName)}.$typeBinding",
                            items = field.enumItems,
                        )
                    }
                }
                .groupBy({ it.first }, { it.second })

            grouped.entries.firstOrNull { entry -> entry.value.map { it.items }.distinct().size > 1 }?.let { entry ->
                throw IllegalArgumentException("conflicting local enum definition for ${entry.value.first().typeFqn}")
            }
            return grouped.values.map { it.first() }
        }

        private fun resolveSharedEnumPackageName(packageName: String, artifactLayout: ArtifactLayoutResolver): String {
            val trimmed = packageName.trim()
            if ('.' in trimmed) {
                return trimmed
            }
            return artifactLayout.aggregateSharedEnumPackage(trimmed)
        }
    }
}

private data class LocalEnumOwnerKey(
    val ownerPackageName: String,
    val typeBinding: String,
)

fun aggregateTableSegment(tableName: String): String =
    tableName.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { tableName.trim() }

private val builtInTypeNames = setOf(
    "Any", "Array", "Boolean", "Byte", "Char", "Collection", "Double", "Float", "Int", "Iterable",
    "List", "Long", "Map", "MutableCollection", "MutableIterable", "MutableList", "MutableMap",
    "MutableSet", "Nothing", "Number", "Pair", "Sequence", "Set", "Short", "String", "Triple", "Unit",
)
```

- [ ] **Step 4: Remove `generateTranslation` from shared enum model**

In `PipelineModels.kt`, change `SharedEnumDefinition` to:

```kotlin
data class SharedEnumDefinition(
    val typeName: String,
    val packageName: String,
    val items: List<EnumItemModel>,
)
```

- [ ] **Step 5: Replace aggregate generator usage with `CanonicalEnumCatalog`**

In `AggregateEnumPlanning.kt`, either delete the file and update imports, or keep this compatibility wrapper during the same task:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalog
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry

internal typealias AggregateEnumPlanning = CanonicalEnumCatalog

internal fun aggregateEnumPlanning(
    model: CanonicalModel,
    artifactLayout: ArtifactLayoutResolver,
    typeRegistry: Map<String, TypeRegistryEntry>,
): CanonicalEnumCatalog =
    CanonicalEnumCatalog.from(model, artifactLayout, typeRegistry)
```

Then update aggregate planners that call the current `AggregateEnumPlanning.from` helper to call the new `CanonicalEnumCatalog.from` helper.

- [ ] **Step 6: Run API and aggregate enum tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalogTest"
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateEnumPlanningTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Task 2**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate
git commit -m "feat: expose canonical enum catalog"
```

### Task 3: Integrate Addon Planning Into DefaultPipelineRunner

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

- [ ] **Step 1: Add failing tests for addon planning and duplicate ids**

Append these tests to `DefaultPipelineRunnerTest.kt`:

```kotlin
@Test
fun `run appends addon plan items and resolves template conflict policies`() {
    val addon = object : ArtifactAddonProvider {
        override val id: String = "sample-addon"

        override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> =
            listOf(
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "adapter",
                    templateId = "addons/sample-addon/sample.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/Sample.kt",
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            )
    }
    val runner = runner(
        sources = emptyList(),
        generators = emptyList(),
        addonProviders = listOf(addon),
    )

    val result = runner.run(
        config(
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
                templateConflictPolicies = mapOf(
                    "addons/sample-addon/sample.kt.peb" to ConflictPolicy.OVERWRITE,
                ),
            )
        )
    )

    assertEquals("sample-addon", result.planItems.single().generatorId)
    assertEquals(ConflictPolicy.OVERWRITE, result.planItems.single().conflictPolicy)
}

@Test
fun `run fails when addon provider ids duplicate`() {
    val first = object : ArtifactAddonProvider {
        override val id: String = "duplicate-addon"
        override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> = emptyList()
    }
    val second = object : ArtifactAddonProvider {
        override val id: String = "duplicate-addon"
        override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> = emptyList()
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        runner(
            sources = emptyList(),
            generators = emptyList(),
            addonProviders = listOf(first, second),
        ).run(config())
    }

    assertEquals("duplicate artifact addon provider id: duplicate-addon", error.message)
}
```

If the local `runner` helper in the test file does not yet accept `addonProviders`, extend that helper in this test task.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest.run appends addon plan items and resolves template conflict policies" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest.run fails when addon provider ids duplicate"
```

Expected: compilation fails because `DefaultPipelineRunner` has no addon provider constructor parameter.

- [ ] **Step 3: Add addon planning to runner**

Modify `DefaultPipelineRunner` constructor:

```kotlin
class DefaultPipelineRunner(
    private val sources: List<SourceProvider>,
    private val generators: List<GeneratorProvider>,
    private val assembler: CanonicalAssembler,
    private val renderer: ArtifactRenderer,
    private val exporter: ArtifactExporter,
    private val transformPlanItem: (ArtifactPlanItem) -> ArtifactPlanItem = { it },
    private val includePlanItem: (ArtifactPlanItem) -> Boolean = { true },
    private val addonProviders: List<ArtifactAddonProvider> = emptyList(),
) : PipelineRunner {
```

Add this helper:

```kotlin
private fun validateAddonProviderIds() {
    val duplicate = addonProviders
        .groupingBy { it.id }
        .eachCount()
        .entries
        .firstOrNull { it.value > 1 }
        ?.key
    require(duplicate == null) {
        "duplicate artifact addon provider id: $duplicate"
    }
}
```

Call it at the start of `run(config)`.

Build plan items like this:

```kotlin
val builtInPlanItems = generators
    .filter { config.generators[it.id]?.enabled == true }
    .flatMap { it.plan(config, model) }

val addonPlanItems = addonProviders.flatMap { provider ->
    provider.plan(
        ArtifactAddonContext(
            config = config,
            model = model,
            options = emptyMap(),
        )
    )
}

val planItems = (builtInPlanItems + addonPlanItems)
    .map(transformPlanItem)
    .filter(includePlanItem)
    .map { resolveConflictPolicy(it, config) }
```

- [ ] **Step 4: Run core tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Task 3**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt
git commit -m "feat: include artifact addon plan items"
```

### Task 4: Add Addon-Aware Template Resolution

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolverTest.kt`

- [ ] **Step 1: Add failing resolver tests**

Append these tests to `PresetTemplateResolverTest.kt`:

```kotlin
@Test
fun `resolves addon template from project override before addon resource`() {
    val overrideDir = Files.createTempDirectory("cap4k-addon-overrides")
    val overrideFile = overrideDir.resolve("addons/sample-addon/sample.kt.peb")
    Files.createDirectories(overrideFile.parent)
    Files.writeString(overrideFile, "override-template")

    val resolver = PresetTemplateResolver(
        preset = "ddd-default",
        overrideDirs = listOf(overrideDir.toString()),
        addonTemplateClassLoaders = mapOf("sample-addon" to javaClass.classLoader),
    )

    assertEquals("override-template", resolver.resolve("addons/sample-addon/sample.kt.peb"))
}

@Test
fun `fails when addon template has no registered addon classloader`() {
    val resolver = PresetTemplateResolver(
        preset = "ddd-default",
        overrideDirs = emptyList(),
        addonTemplateClassLoaders = emptyMap(),
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        resolver.resolve("addons/missing-addon/sample.kt.peb")
    }

    assertEquals("Template references addon 'missing-addon' but no addon provider is loaded.", error.message)
}
```

Add imports if missing:

```kotlin
import org.junit.jupiter.api.Assertions.assertThrows
import java.nio.file.Files
```

- [ ] **Step 2: Run resolver tests and verify they fail**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolverTest"
```

Expected: compilation fails because the resolver constructor does not accept `addonTemplateClassLoaders`.

- [ ] **Step 3: Implement addon template resolution**

Modify the resolver constructor:

```kotlin
class PresetTemplateResolver(
    private val preset: String,
    private val overrideDirs: List<String>,
    private val addonTemplateClassLoaders: Map<String, ClassLoader> = emptyMap(),
) : TemplateResolver {
```

Add addon parsing:

```kotlin
private fun addonId(templateId: String): String? {
    val prefix = "addons/"
    if (!templateId.startsWith(prefix)) {
        return null
    }
    return templateId.removePrefix(prefix).substringBefore('/').takeIf { it.isNotBlank() }
}
```

In `resolve(templateId)`, after override dirs and before preset resources:

```kotlin
val addonId = addonId(templateId)
if (addonId != null) {
    val classLoader = addonTemplateClassLoaders[addonId]
        ?: throw IllegalArgumentException("Template references addon '$addonId' but no addon provider is loaded.")
    val resourcePath = "cap4k/$templateId"
    val resource = classLoader.getResource(resourcePath)
        ?: throw IllegalArgumentException("Addon template not found: $resourcePath")
    return resource.readText()
}
```

- [ ] **Step 4: Add test resource for addon jar resource resolution**

Create test resource:

`cap4k-plugin-pipeline-renderer-pebble/src/test/resources/cap4k/addons/sample-addon/sample.kt.peb`

Content:

```text
addon-resource-template
```

Add this test:

```kotlin
@Test
fun `resolves addon template from addon resource when no override exists`() {
    val resolver = PresetTemplateResolver(
        preset = "ddd-default",
        overrideDirs = emptyList(),
        addonTemplateClassLoaders = mapOf("sample-addon" to javaClass.classLoader),
    )

    assertEquals("addon-resource-template", resolver.resolve("addons/sample-addon/sample.kt.peb"))
}
```

- [ ] **Step 5: Run renderer tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolverTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 4**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolverTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/resources/cap4k/addons/sample-addon/sample.kt.peb
git commit -m "feat: resolve addon templates"
```

### Task 5: Add Gradle `cap4kAddon` Loading

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/ArtifactAddonLoader.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/ArtifactAddonLoaderTest.kt`

- [ ] **Step 1: Write failing loader tests**

Create `ArtifactAddonLoaderTest.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLClassLoader
import java.nio.file.Files

class ArtifactAddonLoaderTest {

    @Test
    fun `loads addon providers from service loader classpath`() {
        val serviceRoot = Files.createTempDirectory("cap4k-addon-services")
        val serviceFile = serviceRoot.resolve(
            "META-INF/services/com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider"
        )
        Files.createDirectories(serviceFile.parent)
        Files.writeString(serviceFile, TestAddonProvider::class.qualifiedName)

        val classLoader = URLClassLoader(arrayOf(serviceRoot.toUri().toURL()), javaClass.classLoader)

        val providers = ArtifactAddonLoader.load(classLoader)

        assertEquals(listOf("test-addon"), providers.map { it.id })
    }

    @Test
    fun `fails when service loader returns duplicate addon ids`() {
        val providers = listOf(TestAddonProvider(), DuplicateTestAddonProvider())

        val error = kotlin.runCatching {
            ArtifactAddonLoader.validateProviderIds(providers)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("duplicate artifact addon provider id: test-addon", error?.message)
    }
}

class TestAddonProvider : ArtifactAddonProvider {
    override val id: String = "test-addon"
    override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> = emptyList()
}

class DuplicateTestAddonProvider : ArtifactAddonProvider {
    override val id: String = "test-addon"
    override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> = emptyList()
}
```

- [ ] **Step 2: Run loader tests and verify they fail**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.ArtifactAddonLoaderTest"
```

Expected: compilation fails because `ArtifactAddonLoader` does not exist.

- [ ] **Step 3: Implement `ArtifactAddonLoader`**

Create `ArtifactAddonLoader.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

internal object ArtifactAddonLoader {
    fun classLoader(files: Collection<File>, parent: ClassLoader): URLClassLoader {
        val urls = files.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, parent)
    }

    fun load(classLoader: ClassLoader): List<ArtifactAddonProvider> {
        val providers = ServiceLoader.load(ArtifactAddonProvider::class.java, classLoader).toList()
        validateProviderIds(providers)
        return providers
    }

    fun validateProviderIds(providers: List<ArtifactAddonProvider>) {
        val duplicate = providers
            .groupingBy { it.id }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicate == null) {
            "duplicate artifact addon provider id: $duplicate"
        }
    }
}
```

- [ ] **Step 4: Wire Gradle configuration and runner constructor**

In `PipelinePlugin.kt`, create a configuration during plugin apply:

```kotlin
val cap4kAddon = project.configurations.create("cap4kAddon") {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Build-time cap4k artifact addon dependencies."
}
```

Where runners are built, resolve providers once:

```kotlin
val addonClassLoader = ArtifactAddonLoader.classLoader(
    cap4kAddon.resolve(),
    PipelinePlugin::class.java.classLoader,
)
val addonProviders = ArtifactAddonLoader.load(addonClassLoader)
val addonTemplateClassLoaders = addonProviders.associate { it.id to addonClassLoader as ClassLoader }
```

Pass `addonProviders` to `DefaultPipelineRunner`, and pass `addonTemplateClassLoaders` into `PresetTemplateResolver`.

- [ ] **Step 5: Run Gradle plugin tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.ArtifactAddonLoaderTest"
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 5**

```powershell
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle
git commit -m "feat: load artifact addons from gradle configuration"
```

### Task 6: Remove cap4k Core enumTranslation Surface

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Modify: `cap4k-plugin-pipeline-source-enum-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProvider.kt`
- Modify or delete tests and fixtures that mention `enumTranslation`, `aggregateEnumTranslation`, or `generateTranslation`

- [ ] **Step 1: Add failing tests that stale enum translation config is gone**

In `Cap4kProjectConfigFactoryTest.kt`, replace any test asserting `enumTranslation` exists with:

```kotlin
@Test
fun `aggregate generator options no longer include enum translation`() {
    val extension = extension()
    extension.project.basePackage.set("com.acme.demo")
    extension.project.domainModulePath.set("demo-domain")
    extension.project.applicationModulePath.set("demo-application")
    extension.project.adapterModulePath.set("demo-adapter")
    extension.generators.aggregate.enabled.set(true)

    val config = Cap4kProjectConfigFactory().build(project(), extension)

    assertFalse(config.generators.getValue("aggregate").options.containsKey("artifact.enumTranslation"))
}
```

In `EnumManifestSourceProviderTest.kt`, add:

```kotlin
@Test
fun `enum manifest rejects deprecated generateTranslation flag`() {
    val file = tempDir.resolve("shared-enums.json")
    Files.writeString(
        file,
        """
        [
          {
            "name": "OrderStatus",
            "package": "shared",
            "generateTranslation": true,
            "items": [
              { "value": 1, "name": "OPEN", "desc": "open" }
            ]
          }
        ]
        """.trimIndent()
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        EnumManifestSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "enum-manifest" to SourceConfig(
                        enabled = true,
                        options = mapOf("files" to listOf(file.toString())),
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )
    }

    assertEquals("enum manifest field generateTranslation is removed; install an enum translation addon instead.", error.message)
}
```

- [ ] **Step 2: Run targeted tests and verify they fail**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest.aggregate generator options no longer include enum translation"
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-enum-manifest:test --tests "com.only4.cap4k.plugin.pipeline.source.enummanifest.EnumManifestSourceProviderTest.enum manifest rejects deprecated generateTranslation flag"
```

Expected: tests fail while stale config still exists.

- [ ] **Step 3: Remove Gradle DSL fields**

In `Cap4kExtension.kt`:

- delete `val aggregateEnumTranslation`
- delete the `aggregateEnumTranslation` package resolver function
- delete `val enumTranslation` from `AggregateGeneratorArtifactsExtension`

In `Cap4kProjectConfigFactory.kt`:

- remove `"artifact.enumTranslation"` from aggregate generator options
- remove the `aggregateEnumTranslation` argument from `ArtifactLayoutConfig` construction

- [ ] **Step 4: Remove API layout and model fields**

In `ProjectConfig.kt`:

- remove `val aggregateEnumTranslation` from `ArtifactLayoutConfig`

In `ArtifactLayoutResolver.kt`:

- remove `aggregateEnumTranslationPackage`
- remove `aggregateEnumTranslation` from layout report maps

In `PipelineModels.kt`:

- ensure `SharedEnumDefinition` has no `generateTranslation`

- [ ] **Step 5: Reject stale enum manifest flags**

In `EnumManifestSourceProvider.kt`, before constructing `SharedEnumDefinition`, add:

```kotlin
require(!json.has("generateTranslation")) {
    "enum manifest field generateTranslation is removed; install an enum translation addon instead."
}
```

Construct the definition without `generateTranslation`.

- [ ] **Step 6: Delete core enum translation planner and template**

Delete:

```text
cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EnumTranslationArtifactPlanner.kt
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb
```

Modify `AggregateArtifactPlanner.kt`:

- remove `EnumTranslationArtifactPlanner()` from delegates
- remove special handling for `EnumTranslationArtifactPlanner`

Modify `AggregateArtifactSelection.kt`:

- remove `enumTranslationEnabled`

- [ ] **Step 7: Update tests and fixtures to compile after removal**

Use:

```powershell
rg -n "enumTranslation|aggregateEnumTranslation|generateTranslation|EnumTranslationArtifactPlanner|aggregate/enum_translation.kt.peb|only\\.engine\\.translation" cap4k-plugin-* docs/public
```

For each live code/test hit:

- remove assertions that core translation artifacts exist
- remove functional fixture `generateTranslation` flags
- remove only-engine translation stub classes from cap4k fixtures
- keep historical `docs/superpowers/**` hits unchanged unless a plan or spec in this issue explicitly supersedes them

- [ ] **Step 8: Run removal verification**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-enum-manifest:test
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-renderer-pebble:test
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit Task 6**

```powershell
git add cap4k-plugin-pipeline-api cap4k-plugin-pipeline-source-enum-manifest cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle
git add -u
git commit -m "refactor: remove core enum translation artifact"
```

### Task 7: Add only-engine `engine-cap4k-addon` Module

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\settings.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\gradle\libs.versions.toml`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\kotlin\com\only\engine\cap4k\addon\translation\OnlyEngineEnumTranslationAddonProvider.kt`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\resources\META-INF\services\com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\resources\cap4k\addons\only-engine-enum-translation\aggregate\enum_translation.kt.peb`
- Test: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\test\kotlin\com\only\engine\cap4k\addon\translation\OnlyEngineEnumTranslationAddonProviderTest.kt`

- [ ] **Step 1: Add cap4k repository and dependency coordinates**

In `only-engine/settings.gradle.kts`, add the cap4k AliYun Maven repository in `dependencyResolutionManagement.repositories`:

```kotlin
maven {
    credentials {
        username = providers.gradleProperty("aliyun.maven.username").orNull ?: "defaultUsername"
        password = providers.gradleProperty("aliyun.maven.password").orNull ?: "defaultPassword"
    }
    url = uri("https://packages.aliyun.com/67053c6149e9309ce56b9e9e/maven/cap4k")
}
```

At the bottom, include:

```kotlin
include(":engine-cap4k-addon")
```

In `gradle/libs.versions.toml`, add:

```toml
cap4k = "0.5.0-SNAPSHOT"
cap4k-plugin-pipeline-api = { module = "com.only4:cap4k-plugin-pipeline-api", version.ref = "cap4k" }
```

- [ ] **Step 2: Create module build file**

Create `engine-cap4k-addon/build.gradle.kts`:

```kotlin
plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(libs.cap4k.plugin.pipeline.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.junit.core)
    testRuntimeOnly(libs.bundles.junit.runtime)
}
```

- [ ] **Step 3: Write failing provider test**

Create `OnlyEngineEnumTranslationAddonProviderTest.kt`:

```kotlin
package com.only.engine.cap4k.addon.translation

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnlyEngineEnumTranslationAddonProviderTest {

    @Test
    fun `plans shared and local enum translation artifacts`() {
        val provider = OnlyEngineEnumTranslationAddonProvider()
        val items = provider.plan(
            ArtifactAddonContext(
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = mapOf("adapter" to "demo-adapter"),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
                    artifactLayout = ArtifactLayoutConfig(),
                ),
                model = CanonicalModel(
                    sharedEnums = listOf(
                        SharedEnumDefinition(
                            typeName = "OrderStatus",
                            packageName = "shared",
                            items = listOf(EnumItemModel(1, "OPEN", "open")),
                        )
                    ),
                    entities = listOf(
                        EntityModel(
                            packageName = "order",
                            typeName = "Order",
                            tableName = "order",
                            fields = listOf(
                                FieldModel(
                                    name = "priority",
                                    type = "Int",
                                    typeBinding = "OrderPriority",
                                    enumItems = listOf(EnumItemModel(1, "HIGH", "high")),
                                )
                            )
                        )
                    )
                ),
            )
        )

        assertEquals(listOf("only-engine-enum-translation", "only-engine-enum-translation"), items.map { it.generatorId })
        assertEquals(
            listOf(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/translation/shared/OrderStatusTranslation.kt",
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/translation/order/OrderPriorityTranslation.kt",
            ),
            items.map { it.outputPath },
        )
        assertEquals(
            "addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb",
            items.single { it.outputPath.endsWith("OrderStatusTranslation.kt") }.templateId,
        )
    }
}
```

- [ ] **Step 4: Run provider test and verify it fails**

Run from `only-engine`:

```powershell
.\gradlew.bat --no-daemon :engine-cap4k-addon:test --tests "com.only.engine.cap4k.addon.translation.OnlyEngineEnumTranslationAddonProviderTest"
```

Expected: compilation fails because the provider does not exist.

- [ ] **Step 5: Implement provider**

Create `OnlyEngineEnumTranslationAddonProvider.kt`:

```kotlin
package com.only.engine.cap4k.addon.translation

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalog
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumDescriptor
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class OnlyEngineEnumTranslationAddonProvider : ArtifactAddonProvider {
    override val id: String = "only-engine-enum-translation"

    override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(context.config.basePackage, context.config.artifactLayout)
        val enumCatalog = CanonicalEnumCatalog.from(
            model = context.model,
            artifactLayout = artifactLayout,
            typeRegistry = context.config.typeRegistry,
        )

        return enumCatalog.all.map { descriptor ->
            descriptor.toPlanItem(context.config, artifactLayout)
        }
    }

    private fun CanonicalEnumDescriptor.toPlanItem(
        config: ProjectConfig,
        artifactLayout: ArtifactLayoutResolver,
    ): ArtifactPlanItem {
        val moduleRoot = config.modules["adapter"]
            ?: throw IllegalArgumentException("project.adapterModulePath is required when only-engine enum translation addon is installed.")
        val packageName = ArtifactLayoutResolver.joinPackage(
            config.basePackage,
            "adapter.domain.translation",
            ownerScope,
        )
        val typeName = "${typeName}Translation"
        val typeKey = translationTypeKey(ownerScope, this.typeName)
        val translationTypeConst = "${typeKey.uppercase()}_CODE_TO_DESC"
        val translationTypeValue = "${typeKey}_code_to_desc"

        return ArtifactPlanItem(
            generatorId = id,
            moduleRole = "adapter",
            templateId = "addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb",
            outputPath = artifactLayout.kotlinSourcePath(moduleRoot, packageName, typeName),
            context = mapOf(
                "packageName" to packageName,
                "typeName" to typeName,
                "enumTypeName" to this.typeName,
                "enumTypeFqn" to typeFqn,
                "translationTypeConst" to translationTypeConst,
                "translationTypeValue" to translationTypeValue,
            ),
            conflictPolicy = config.templates.conflictPolicy,
            outputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
            resolvedOutputRoot = artifactLayout.kotlinSourceRoot(moduleRoot),
        )
    }
}

private fun translationTypeKey(ownerScope: String, typeName: String): String {
    val scopedTypeName = if (ownerScope.isBlank() || ownerScope == "shared") typeName else "${ownerScope}_$typeName"
    return scopedTypeName
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace("-", "_")
        .replace(".", "_")
        .lowercase()
}
```

- [ ] **Step 6: Add ServiceLoader file**

Create `META-INF/services/com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider` with:

```text
com.only.engine.cap4k.addon.translation.OnlyEngineEnumTranslationAddonProvider
```

- [ ] **Step 7: Add default only-engine template**

Create `cap4k/addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb` with the old cap4k template body, owned by only-engine:

```pebble
package {{ packageName }}

{{ use("com.only.engine.translation.annotation.TranslationType") -}}
{{ use("com.only.engine.translation.core.BatchTranslationInterface") -}}
{{ use("com.only.engine.translation.core.TranslationInterface") -}}
{{ use("org.springframework.stereotype.Component") -}}
{{ use(enumTypeFqn) -}}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}

@TranslationType(type = "{{ translationTypeValue }}")
@Component
class {{ typeName }} : TranslationInterface<String>, BatchTranslationInterface<String> {

    companion object {
        const val {{ translationTypeConst }} = "{{ translationTypeValue }}"
    }

    override fun translation(key: Any, other: String): String? {
        val code = when (key) {
            is Number -> key.toInt()
            is String -> key.toIntOrNull()
            else -> null
        } ?: return null
        return {{ enumTypeName }}.valueOfOrNull(code)?.description
    }

    override fun translationBatch(keys: Collection<Any>, other: String): Map<Any, String?> =
        keys.associateWith { translation(it, other) }
}
```

- [ ] **Step 8: Run only-engine addon tests**

Run from `only-engine`:

```powershell
.\gradlew.bat --no-daemon :engine-cap4k-addon:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit only-engine Task 7**

Run from `only-engine`:

```powershell
git add settings.gradle.kts gradle/libs.versions.toml engine-cap4k-addon
git commit -m "feat: add cap4k enum translation addon"
```

### Task 8: Add Cross-Repository Functional Verification

**Files:**
- Modify or add cap4k Gradle functional test fixture under `cap4k-plugin-pipeline-gradle/src/test/resources/functional/`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Use published or composite only-engine addon in a downstream verification project

- [ ] **Step 1: Add a functional test that no addon means no enum translation**

Create or update a functional fixture that has shared/local enums but no `cap4kAddon` dependency. The build file must not use `enumTranslation`.

Add a test assertion:

```kotlin
assertFalse(planFile.readText().contains("Translation.kt"))
assertFalse(planFile.readText().contains("only-engine-enum-translation"))
```

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "*no addon means no enum translation artifacts*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Add a functional test that an addon artifact uses normal plan semantics**

Use a test addon jar or the only-engine addon artifact after it is published locally. The sample build should include:

```kotlin
dependencies {
    cap4kAddon("com.only4:engine-cap4k-addon:0.1.12-SNAPSHOT")
}

cap4k {
    templates {
        templateConflictPolicies.put(
            "addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb",
            "OVERWRITE"
        )
    }
}
```

Expected plan assertions:

```kotlin
assertTrue(planFile.readText().contains("only-engine-enum-translation"))
assertTrue(planFile.readText().contains("addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb"))
assertTrue(planFile.readText().contains("\"conflictPolicy\": \"OVERWRITE\""))
```

- [ ] **Step 3: Verify project override wins over addon bundled template**

In the functional fixture, add:

```text
cap4k-templates/addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb
```

Content:

```pebble
package {{ packageName }}

class {{ typeName }}
```

Run generation and assert generated file contains:

```kotlin
class OrderStatusTranslation
```

and does not contain:

```kotlin
TranslationInterface
```

- [ ] **Step 4: Run functional verification**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "*addon*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Task 8 in cap4k**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test
git commit -m "test: verify artifact addon generation path"
```

### Task 9: Update Public Docs

**Files:**
- Modify: `docs/public/reference/generator-dsl.zh-CN.md`
- Modify: any public authoring docs found by the search command

- [ ] **Step 1: Scan public docs for stale enum translation claims**

Run:

```powershell
rg -n "enumTranslation|aggregateEnumTranslation|generateTranslation|only-engine|TranslationInterface|BatchTranslationInterface" docs/public
```

Expected: live public docs identify only stale enum translation claims.

- [ ] **Step 2: Update `generator-dsl.zh-CN.md`**

Remove the `artifacts.enumTranslation` DSL row and examples.

Add a short addon note:

```markdown
### Addon 产物

cap4k 原生产物和 addon 产物共用同一套模板覆盖、冲突策略、计划和生成语义。

项目如果安装构建期 addon，例如 only-engine 枚举翻译 addon，addon 贡献的产物会出现在 `cap4kPlan` 中，并通过 `cap4kGenerate` 写入文件。

addon 模板可通过 `templates.overrideDirs` 覆盖；addon 模板冲突策略可通过 `templates.templateConflictPolicies` 按 `templateId` 配置。
```

- [ ] **Step 3: Document the dependency split**

Add this wording in the same public doc section:

```markdown
运行时代码依赖和生成期 addon 依赖是两件事。运行时库由项目通过 `implementation` 等配置声明；生成期 addon 通过 `cap4kAddon` 声明。cap4k 不会扫描项目普通运行时 classpath 来自动发现 addon。
```

- [ ] **Step 4: Run docs scan**

Run:

```powershell
rg -n "enumTranslation|aggregateEnumTranslation|generateTranslation|com.only.engine.translation|TranslationInterface|BatchTranslationInterface" docs/public cap4k-plugin-pipeline-renderer-pebble/src/main/resources
```

Expected: no live public docs or cap4k core template hits remain.

- [ ] **Step 5: Commit Task 9**

```powershell
git add docs/public
git commit -m "docs: describe artifact addon generation"
```

### Task 10: Full Verification, Publish, and Issue Update

**Files:**
- No new source files unless verification exposes a failing case.
- Update GitHub issue `LDmoxeii/cap4k#33`.

- [ ] **Step 1: Run full cap4k verification**

Run from `cap4k`:

```powershell
.\gradlew.bat --no-daemon build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full only-engine verification**

Run from `only-engine`:

```powershell
.\gradlew.bat --no-daemon :engine-cap4k-addon:build :engine-translation:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run residue scan**

Run from `cap4k`:

```powershell
rg -n "enumTranslation|aggregateEnumTranslation|generateTranslation|EnumTranslationArtifactPlanner|com.only.engine.translation|TranslationInterface|BatchTranslationInterface" cap4k-plugin-* docs/public
```

Expected: no live cap4k core or public docs hits remain. Historical `docs/superpowers/**` may still contain old design records and should not be edited by this task.

- [ ] **Step 4: Publish cap4k**

Run from `cap4k` master after merging:

```powershell
.\gradlew.bat --no-daemon publish
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Publish only-engine addon**

Run from `only-engine` master after merging:

```powershell
.\gradlew.bat --no-daemon :engine-cap4k-addon:publish
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Downstream smoke verification**

Use a small consumer project or `cap4k-reference-content-studio` after updating its build to consume the published cap4k and only-engine addon. Verify:

```kotlin
dependencies {
    implementation("com.only4:engine-translation:0.1.12-SNAPSHOT")
    cap4kAddon("com.only4:engine-cap4k-addon:0.1.12-SNAPSHOT")
}
```

Run:

```powershell
.\gradlew.bat --no-daemon --refresh-dependencies cap4kPlan cap4kGenerate
```

Expected:

- `cap4kPlan` includes `only-engine-enum-translation`
- generated translation classes compile
- project template override works when `templates.overrideDirs` contains `addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb`
- `templateConflictPolicies` can set the addon template to `OVERWRITE`

- [ ] **Step 7: Update and close issue only after all lifecycle items are complete**

Update `LDmoxeii/cap4k#33`:

- check `plan written` after committing this plan
- check `implementation merged` after cap4k and only-engine implementation commits are merged
- check `released if required` after both publishes complete
- check `downstream verified if required` after consumer verification
- close only after all applicable items are complete

Commit final verification or issue update notes only if repository files changed.

---

## Self-Review Checklist

- Spec coverage: the plan covers the general artifact addon SPI, explicit build-time addon dependency, addon template resource resolution, project overrides, template conflict policy behavior, cap4k enumTranslation removal, only-engine same-cycle addon, docs, publishing, and downstream verification.
- Cross-repository coverage: cap4k and only-engine file paths are both named.
- Consumer consistency: addon artifacts are normal `ArtifactPlanItem`s and flow through existing plan, render, conflict policy, and exporter behavior.
- Risk boundary: addons cannot mutate `CanonicalModel`, collect sources, or replace renderer/exporter behavior.

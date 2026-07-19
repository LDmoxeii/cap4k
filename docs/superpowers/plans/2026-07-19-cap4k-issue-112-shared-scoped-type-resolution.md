# Cap4k Issue 112 Shared Scoped Type Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make design and value-object generators share scoped type symbol identity and aggregate-aware short-name candidate selection, then make `types-value-object` strictly resolve enum/value-object/Strong ID/registry imports instead of emitting unresolved Kotlin.

**Architecture:** Add `cap4k-plugin-pipeline-generator-common` as a sibling generator support module. Move only symbol identity, registry storage, canonical registry construction, and aggregate-context candidate selection into common; keep parser, render result, diagnostics, and template context construction inside each generator.

**Tech Stack:** Kotlin/JVM 17, Gradle Kotlin DSL, JUnit 5, cap4k pipeline generator modules.

## Global Constraints

- Work must not happen on `master`.
- Execute implementation after merging this documentation branch into `spec/issue-112-value-object-enum-imports`.
- Do not modify the older #112 spec or plan files.
- Do not run compile, test, Gradle, generation, or install commands from subagents.
- Subagents may commit their implementation task changes.
- Main agent performs exactly one final compile/test verification after all implementation tasks are complete.
- Do not preserve compatibility for unknown short value-object field types; fail planning instead.
- Do not make `cap4k-plugin-pipeline-generator-types` depend on `cap4k-plugin-pipeline-generator-design`.
- Do not move generator resolution policy into `cap4k-plugin-pipeline-api`.
- Do not change manifest JSON shape or value-object templates.
- Before editing the current implementation worktree, remove or replace the uncommitted diagnostic value-object patch deliberately; do not build on the BOM-contaminated diff.

---

## File Structure

Create:

- `cap4k-plugin-pipeline-generator-common/build.gradle.kts` - Gradle module for shared generator-internal type symbol utilities.
- `cap4k-plugin-pipeline-generator-common/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolIdentity.kt` - Shared symbol identity and source constants.
- `cap4k-plugin-pipeline-generator-common/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolRegistry.kt` - Multiple-candidate registry by simple name.
- `cap4k-plugin-pipeline-generator-common/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolSelector.kt` - Local-first candidate narrowing by aggregate context.
- `cap4k-plugin-pipeline-generator-common/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/CanonicalTypeSymbolRegistryFactory.kt` - Canonical model to type symbol registry construction.
- `cap4k-plugin-pipeline-generator-common/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolRegistryTest.kt`
- `cap4k-plugin-pipeline-generator-common/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolSelectorTest.kt`
- `cap4k-plugin-pipeline-generator-common/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/CanonicalTypeSymbolRegistryFactoryTest.kt`
- `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectTypeResolution.kt` - Value-object-specific parser and strict resolver using common symbols.

Modify:

- `settings.gradle.kts`
- `cap4k-plugin-pipeline-generator-design/build.gradle.kts`
- `cap4k-plugin-pipeline-generator-types/build.gradle.kts`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRegistryBindings.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolver.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignImportPlanner.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSagaRenderModels.kt`
- design planner files that call `config.designSymbolRegistry(model)`
- `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSymbolRegistryTest.kt`
- `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt`
- `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolverTest.kt`
- `cap4k-plugin-pipeline-generator-types/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlannerTest.kt`

Delete after migration:

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/SymbolIdentity.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignSymbolRegistry.kt`
- private `ValueObjectTypeBindings` and private lookup helpers from `ValueObjectArtifactPlanner.kt`

## Task 1: Add Common Module And Symbol Primitives

**Files:**
- Modify: `settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-generator-common/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-generator-common/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolIdentity.kt`
- Create: `cap4k-plugin-pipeline-generator-common/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolRegistry.kt`
- Create: `cap4k-plugin-pipeline-generator-common/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolSelector.kt`
- Test: `cap4k-plugin-pipeline-generator-common/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolRegistryTest.kt`
- Test: `cap4k-plugin-pipeline-generator-common/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/TypeSymbolSelectorTest.kt`

**Interfaces:**
- Produces: `TypeSymbolIdentity`
- Produces: source constants `PROJECT_TYPE_REGISTRY_SOURCE`, `STRONG_ID_SOURCE`, `MANIFEST_ENUM_SOURCE`, `MANIFEST_VALUE_OBJECT_SOURCE`, `EXPLICIT_FQCN_SOURCE`, `AGGREGATE_SOURCE`
- Produces: `TypeSymbolRegistry.register(symbol: TypeSymbolIdentity): Unit`
- Produces: `TypeSymbolRegistry.findBySimpleName(simpleName: String): List<TypeSymbolIdentity>`
- Produces: `TypeSymbolRegistry.allSymbols(): List<TypeSymbolIdentity>`
- Produces: `TypeSymbolSelector.selectShortNameCandidates(candidates: List<TypeSymbolIdentity>, aggregateContext: List<String>): List<TypeSymbolIdentity>`

- [ ] **Step 1: Add the Gradle module include**

In `settings.gradle.kts`, add `"cap4k-plugin-pipeline-generator-common"` inside the existing `include(...)` block with the other pipeline modules, before the generator modules that will consume it:

```kotlin
    "cap4k-plugin-pipeline-source-ir-analysis",
    "cap4k-plugin-pipeline-generator-common",
    "cap4k-plugin-pipeline-generator-design",
    "cap4k-plugin-pipeline-generator-types",
```

- [ ] **Step 2: Create the common module build file**

Create `cap4k-plugin-pipeline-generator-common/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":cap4k-plugin-pipeline-api"))

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

- [ ] **Step 3: Add `TypeSymbolIdentity`**

Create `TypeSymbolIdentity.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.common.types

const val PROJECT_TYPE_REGISTRY_SOURCE = "project-type-registry"
const val STRONG_ID_SOURCE = "strong-id"
const val MANIFEST_ENUM_SOURCE = "manifest-enum"
const val MANIFEST_VALUE_OBJECT_SOURCE = "manifest-value-object"
const val EXPLICIT_FQCN_SOURCE = "explicit-fqcn"
const val AGGREGATE_SOURCE = "aggregate"

data class TypeSymbolIdentity(
    val packageName: String,
    val typeName: String,
    val moduleRole: String? = null,
    val source: String? = null,
    val ownerAggregateName: String? = null,
    val manifestOwned: Boolean = false,
    val shared: Boolean = false,
) {
    val simpleName: String
        get() = typeName.substringAfterLast('.')

    val fqcn: String
        get() = if (packageName.isBlank()) typeName else "$packageName.$typeName"
}
```

- [ ] **Step 4: Add `TypeSymbolRegistry`**

Create `TypeSymbolRegistry.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.common.types

class TypeSymbolRegistry(symbols: Iterable<TypeSymbolIdentity> = emptyList()) {
    private val symbolsBySimpleName = linkedMapOf<String, LinkedHashSet<TypeSymbolIdentity>>()

    init {
        symbols.forEach(::register)
    }

    fun register(symbol: TypeSymbolIdentity) {
        symbolsBySimpleName.getOrPut(symbol.simpleName) { linkedSetOf() }.add(symbol)
    }

    fun findBySimpleName(simpleName: String): List<TypeSymbolIdentity> =
        symbolsBySimpleName[simpleName].orEmpty().toList()

    fun allSymbols(): List<TypeSymbolIdentity> = symbolsBySimpleName.values.flatten()
}
```

- [ ] **Step 5: Add `TypeSymbolSelector`**

Create `TypeSymbolSelector.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.common.types

object TypeSymbolSelector {
    fun selectShortNameCandidates(
        candidates: List<TypeSymbolIdentity>,
        aggregateContext: List<String>,
    ): List<TypeSymbolIdentity> {
        val uniqueCandidates = candidates.distinctBy { it.fqcn }
        val singleAggregate = singleAggregateContext(aggregateContext)
        if (singleAggregate != null) {
            val localManifestCandidates = uniqueCandidates.filter { candidate ->
                candidate.manifestOwned &&
                    !candidate.shared &&
                    candidate.ownerAggregateName == singleAggregate
            }
            if (localManifestCandidates.isNotEmpty()) {
                return localManifestCandidates
            }
        }
        return uniqueCandidates
    }

    private fun singleAggregateContext(aggregateContext: List<String>): String? {
        val names = aggregateContext
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return names.singleOrNull()
    }
}
```

- [ ] **Step 6: Write registry tests**

Create `TypeSymbolRegistryTest.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.common.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeSymbolRegistryTest {
    @Test
    fun `stores multiple candidates for the same simple name`() {
        val first = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val second = TypeSymbolIdentity(packageName = "com.bar", typeName = "Status")

        val registry = TypeSymbolRegistry(listOf(first, second))

        assertEquals(listOf(first, second), registry.findBySimpleName("Status"))
        assertEquals(listOf(first, second), registry.allSymbols())
    }

    @Test
    fun `deduplicates exact symbol identity while preserving distinct fqns`() {
        val first = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val second = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val third = TypeSymbolIdentity(packageName = "com.bar", typeName = "Status")

        val registry = TypeSymbolRegistry(listOf(first, second, third))

        assertEquals(listOf(first, third), registry.findBySimpleName("Status"))
    }
}
```

- [ ] **Step 7: Write selector tests**

Create `TypeSymbolSelectorTest.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.common.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeSymbolSelectorTest {
    @Test
    fun `single aggregate context prefers matching local manifest candidates`() {
        val shared = TypeSymbolIdentity(
            packageName = "com.acme.shared",
            typeName = "Status",
            source = MANIFEST_ENUM_SOURCE,
            manifestOwned = true,
            shared = true,
        )
        val orderLocal = TypeSymbolIdentity(
            packageName = "com.acme.order",
            typeName = "Status",
            source = MANIFEST_ENUM_SOURCE,
            ownerAggregateName = "Order",
            manifestOwned = true,
            shared = false,
        )
        val customerLocal = TypeSymbolIdentity(
            packageName = "com.acme.customer",
            typeName = "Status",
            source = MANIFEST_VALUE_OBJECT_SOURCE,
            ownerAggregateName = "Customer",
            manifestOwned = true,
            shared = false,
        )

        val selected = TypeSymbolSelector.selectShortNameCandidates(
            candidates = listOf(shared, orderLocal, customerLocal),
            aggregateContext = listOf("Order"),
        )

        assertEquals(listOf(orderLocal), selected)
    }

    @Test
    fun `no context returns all unique candidates`() {
        val first = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val duplicate = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val second = TypeSymbolIdentity(packageName = "com.bar", typeName = "Status")

        val selected = TypeSymbolSelector.selectShortNameCandidates(
            candidates = listOf(first, duplicate, second),
            aggregateContext = emptyList(),
        )

        assertEquals(listOf(first, second), selected)
    }

    @Test
    fun `multi aggregate context does not choose a local owner`() {
        val orderLocal = TypeSymbolIdentity(
            packageName = "com.acme.order",
            typeName = "Snapshot",
            ownerAggregateName = "Order",
            manifestOwned = true,
            shared = false,
        )
        val customerLocal = TypeSymbolIdentity(
            packageName = "com.acme.customer",
            typeName = "Snapshot",
            ownerAggregateName = "Customer",
            manifestOwned = true,
            shared = false,
        )

        val selected = TypeSymbolSelector.selectShortNameCandidates(
            candidates = listOf(orderLocal, customerLocal),
            aggregateContext = listOf("Order", "Customer"),
        )

        assertEquals(listOf(orderLocal, customerLocal), selected)
    }
}
```

- [ ] **Step 8: Static verification only**

Do not run Gradle or tests. Inspect the files just created and confirm:

- module include is present once;
- package names are `com.only4.cap4k.plugin.pipeline.generator.common.types`;
- there are no placeholder words;
- code is ASCII and has no UTF-8 BOM.

- [ ] **Step 9: Commit Task 1**

```powershell
git add settings.gradle.kts cap4k-plugin-pipeline-generator-common
git commit -m "feat: add shared generator type symbols"
```

## Task 2: Add Canonical Registry Construction In Common

**Files:**
- Create: `cap4k-plugin-pipeline-generator-common/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/CanonicalTypeSymbolRegistryFactory.kt`
- Test: `cap4k-plugin-pipeline-generator-common/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/common/types/CanonicalTypeSymbolRegistryFactoryTest.kt`

**Interfaces:**
- Consumes: `TypeSymbolIdentity`, `TypeSymbolRegistry`
- Produces: `CanonicalTypeSymbolRegistryFactory.from(config: ProjectConfig, model: CanonicalModel, artifactLayout: ArtifactLayoutResolver): TypeSymbolRegistry`

- [ ] **Step 1: Create the factory**

Create `CanonicalTypeSymbolRegistryFactory.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.common.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalog
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumDescriptor
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.ownerAggregate

object CanonicalTypeSymbolRegistryFactory {
    fun from(
        config: ProjectConfig,
        model: CanonicalModel,
        artifactLayout: ArtifactLayoutResolver,
    ): TypeSymbolRegistry =
        TypeSymbolRegistry().apply {
            config.typeRegistryFqns().forEach { (simpleName, fqn) ->
                register(
                    TypeSymbolIdentity(
                        packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
                        typeName = simpleName,
                        source = PROJECT_TYPE_REGISTRY_SOURCE,
                    )
                )
            }

            model.strongIds.forEach { strongId ->
                register(
                    TypeSymbolIdentity(
                        packageName = strongId.packageName,
                        typeName = strongId.typeName,
                        source = STRONG_ID_SOURCE,
                        ownerAggregateName = strongId.ownerAggregateName,
                    )
                )
            }

            manifestEnumSymbols(model, artifactLayout).forEach(::register)

            model.valueObjects.forEach { valueObject ->
                register(
                    TypeSymbolIdentity(
                        packageName = valueObject.packageName,
                        typeName = valueObject.name,
                        source = MANIFEST_VALUE_OBJECT_SOURCE,
                        ownerAggregateName = valueObject.ownerAggregate,
                        manifestOwned = true,
                        shared = valueObject.aggregates.isEmpty(),
                    )
                )
            }
        }

    private fun manifestEnumSymbols(
        model: CanonicalModel,
        artifactLayout: ArtifactLayoutResolver,
    ): List<TypeSymbolIdentity> {
        val sharedDefinitions = model.sharedEnums.filter { it.aggregates.isEmpty() }
        val localDefinitions = model.sharedEnums.filter { it.aggregates.isNotEmpty() }
        val sharedCatalog = CanonicalEnumCatalog.from(
            model.copy(sharedEnums = sharedDefinitions),
            artifactLayout,
            emptyMap(),
        )
        val localCatalog = CanonicalEnumCatalog.from(
            model.copy(sharedEnums = localDefinitions),
            artifactLayout,
            emptyMap(),
        )
        val selection = ManifestEnumCatalogSelection.from(
            model = model,
            sharedCatalog = sharedCatalog,
            localCatalog = localCatalog,
        )

        return model.sharedEnums.flatMap { definition ->
            val ownerAggregateName = definition.aggregates.singleOrNull()
            val shared = definition.aggregates.isEmpty()
            selection.descriptorsFor(definition).map { descriptor ->
                TypeSymbolIdentity(
                    packageName = descriptor.fqn.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = descriptor.typeName,
                    source = MANIFEST_ENUM_SOURCE,
                    ownerAggregateName = ownerAggregateName,
                    manifestOwned = true,
                    shared = shared,
                )
            }
        }
    }
}

private class ManifestEnumCatalogSelection(
    private val sharedByTypeName: Map<String, CanonicalEnumDescriptor>,
    private val localByKey: Map<ManifestLocalEnumKey, CanonicalEnumDescriptor>,
    private val entities: List<EntityModel>,
    private val aggregateRootNameByEntity: Map<ManifestEntityKey, String>,
) {
    fun descriptorsFor(definition: SharedEnumDefinition): List<CanonicalEnumDescriptor> =
        if (definition.aggregates.isEmpty()) {
            listOf(
                requireNotNull(sharedByTypeName[definition.typeName]) {
                    "missing shared enum catalog entry for ${definition.typeName}"
                }
            )
        } else {
            localOwnerKeys(definition).map { key ->
                requireNotNull(localByKey[key]) {
                    "missing local enum catalog entry for ${key.ownerPackageName}.${key.typeName}"
                }
            }
        }

    private fun localOwnerKeys(definition: SharedEnumDefinition): List<ManifestLocalEnumKey> {
        val ownerAggregateName = requireNotNull(definition.aggregates.singleOrNull()) {
            "enum ${definition.typeName} may declare at most one aggregate"
        }
        return entities
            .filter { entity -> aggregateRootNameByEntity[entity.key()] == ownerAggregateName }
            .map { entity -> ManifestLocalEnumKey(entity.packageName, definition.typeName) }
            .distinct()
            .ifEmpty { listOf(ManifestLocalEnumKey(ownerAggregateName, definition.typeName)) }
    }

    companion object {
        fun from(
            model: CanonicalModel,
            sharedCatalog: CanonicalEnumCatalog,
            localCatalog: CanonicalEnumCatalog,
        ): ManifestEnumCatalogSelection =
            ManifestEnumCatalogSelection(
                sharedByTypeName = sharedCatalog.sharedEnums.associateBy { it.typeName },
                localByKey = localCatalog.localEnums
                    .mapNotNull { descriptor ->
                        descriptor.ownerPackageName?.let { ownerPackageName ->
                            ManifestLocalEnumKey(ownerPackageName, descriptor.typeName) to descriptor
                        }
                    }
                    .toMap(),
                entities = model.entities,
                aggregateRootNameByEntity = buildAggregateRootNameByEntity(model.entities),
            )

        private fun buildAggregateRootNameByEntity(
            entities: List<EntityModel>,
        ): Map<ManifestEntityKey, String> {
            val entitiesByKey = entities.associateBy { it.key() }
            val entitiesByName = entities.groupBy { it.name }
            val resolving = mutableSetOf<ManifestEntityKey>()
            val resolved = linkedMapOf<ManifestEntityKey, String>()

            fun resolve(entity: EntityModel): String {
                val key = entity.key()
                resolved[key]?.let { return it }
                if (!resolving.add(key)) {
                    return entity.name
                }
                val parentEntityName = entity.parentEntityName?.takeIf { it.isNotBlank() }
                val rootName = when {
                    entity.aggregateRoot -> entity.name
                    parentEntityName == null -> entity.name
                    else -> {
                        val parent = entitiesByKey[ManifestEntityKey(entity.packageName, parentEntityName)]
                            ?: entitiesByName[parentEntityName]?.singleOrNull()
                        parent?.let { resolve(it) } ?: entity.name
                    }
                }
                resolving.remove(key)
                resolved[key] = rootName
                return rootName
            }

            entities.forEach { resolve(it) }
            return resolved
        }
    }
}

private data class ManifestLocalEnumKey(
    val ownerPackageName: String,
    val typeName: String,
)

private data class ManifestEntityKey(
    val packageName: String,
    val name: String,
)

private fun EntityModel.key(): ManifestEntityKey =
    ManifestEntityKey(packageName = packageName, name = name)
```

- [ ] **Step 2: Write canonical registry tests**

Create `CanonicalTypeSymbolRegistryFactoryTest.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.common.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CanonicalTypeSymbolRegistryFactoryTest {
    @Test
    fun `registers registry strong id shared enum local enum shared value object and local value object`() {
        val registry = CanonicalTypeSymbolRegistryFactory.from(
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                typeRegistry = TypeRegistryConfig(
                    entries = mapOf("ExternalStatus" to TypeRegistryEntry("com.acme.external.ExternalStatus")),
                ),
            ),
            model = CanonicalModel(
                entities = listOf(
                    entity(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                ),
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "OrderId",
                        packageName = "com.acme.demo.domain.aggregates.order",
                        kind = StrongIdKind.AGGREGATE_ROOT,
                        ownerAggregateName = "Order",
                    ),
                ),
                sharedEnums = listOf(
                    enumDefinition("TransportType", "shared"),
                    enumDefinition("OrderStatus", "order", aggregates = listOf("Order")),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Money",
                        packageName = "com.acme.demo.domain.shared.values",
                    ),
                    ValueObjectModel(
                        name = "OrderSnapshot",
                        packageName = "com.acme.demo.domain.aggregates.order.values",
                        aggregates = listOf("Order"),
                    ),
                ),
            ),
            artifactLayout = ArtifactLayoutResolver("com.acme.demo", ArtifactLayoutConfig()),
        )

        assertEquals(
            "com.acme.external.ExternalStatus",
            registry.findBySimpleName("ExternalStatus").single().fqcn,
        )
        assertEquals(STRONG_ID_SOURCE, registry.findBySimpleName("OrderId").single().source)
        assertEquals(MANIFEST_ENUM_SOURCE, registry.findBySimpleName("TransportType").single().source)
        assertEquals(
            "com.acme.demo.domain.aggregates.order.enums.OrderStatus",
            registry.findBySimpleName("OrderStatus").single().fqcn,
        )
        assertEquals(MANIFEST_VALUE_OBJECT_SOURCE, registry.findBySimpleName("Money").single().source)
        assertEquals("Order", registry.findBySimpleName("OrderSnapshot").single().ownerAggregateName)
    }

    @Test
    fun `local enum ownership resolves through child entity parent chain`() {
        val registry = CanonicalTypeSymbolRegistryFactory.from(
            config = ProjectConfig(basePackage = "com.acme.demo"),
            model = CanonicalModel(
                entities = listOf(
                    entity(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                    entity(
                        name = "OrderLine",
                        packageName = "com.acme.demo.domain.aggregates.order",
                        aggregateRoot = false,
                        parentEntityName = "Order",
                    ),
                ),
                sharedEnums = listOf(
                    enumDefinition("LineStatus", "order", aggregates = listOf("Order")),
                ),
            ),
            artifactLayout = ArtifactLayoutResolver("com.acme.demo", ArtifactLayoutConfig()),
        )

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.order.enums.LineStatus"),
            registry.findBySimpleName("LineStatus").map { it.fqcn },
        )
    }

    private fun entity(
        name: String,
        packageName: String,
        aggregateRoot: Boolean = true,
        parentEntityName: String? = null,
    ): EntityModel =
        EntityModel(
            name = name,
            tableName = name.lowercase(),
            packageName = packageName,
            aggregateRoot = aggregateRoot,
            parentEntityName = parentEntityName,
        )

    private fun enumDefinition(
        typeName: String,
        packageName: String,
        aggregates: List<String> = emptyList(),
    ): SharedEnumDefinition =
        SharedEnumDefinition(
            typeName = typeName,
            packageName = packageName,
            items = listOf(EnumItemModel(value = 1, name = "ACTIVE", description = "Active")),
            aggregates = aggregates,
        )
}
```

- [ ] **Step 3: Static verification only**

Do not run Gradle or tests. Inspect:

- `CanonicalTypeSymbolRegistryFactory.from` uses split shared/local enum catalogs with `emptyMap()`;
- aggregate root resolution is package-aware and does not simplify to name-only lookup;
- source fields match the constants from `TypeSymbolIdentity.kt`;
- no implementation references `generator-design`.

- [ ] **Step 4: Commit Task 2**

```powershell
git add cap4k-plugin-pipeline-generator-common/src/main/kotlin cap4k-plugin-pipeline-generator-common/src/test/kotlin
git commit -m "feat: build canonical type symbol registry"
```

## Task 3: Migrate Design Generator To Common Symbols

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRegistryBindings.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolver.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignImportPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSagaRenderModels.kt`
- Modify: design planner files that pass `symbolRegistry`
- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/SymbolIdentity.kt`
- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignSymbolRegistry.kt`
- Modify tests under `cap4k-plugin-pipeline-generator-design/src/test/kotlin`

**Interfaces:**
- Consumes: `TypeSymbolIdentity`, `TypeSymbolRegistry`, `TypeSymbolSelector`, `CanonicalTypeSymbolRegistryFactory`
- Produces: `ProjectConfig.designTypeSymbolRegistry(model: CanonicalModel): TypeSymbolRegistry`
- Preserves: `ImportResolver.UnknownShortTypeFailure`
- Preserves: `ImportResolver.AmbiguousShortTypeFailure`

- [ ] **Step 1: Add the common dependency**

In `cap4k-plugin-pipeline-generator-design/build.gradle.kts`, add:

```kotlin
implementation(project(":cap4k-plugin-pipeline-generator-common"))
```

after the existing API dependency.

- [ ] **Step 2: Replace design registry construction**

Rewrite `DesignTypeRegistryBindings.kt` so it delegates to the common factory:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.generator.common.types.CanonicalTypeSymbolRegistryFactory
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry

internal fun ProjectConfig.designTypeSymbolRegistry(model: CanonicalModel): TypeSymbolRegistry =
    CanonicalTypeSymbolRegistryFactory.from(
        config = this,
        model = model,
        artifactLayout = ArtifactLayoutResolver(basePackage, artifactLayout),
    )
```

- [ ] **Step 3: Update planner call sites**

Replace all calls to:

```kotlin
config.designSymbolRegistry(model)
```

with:

```kotlin
config.designTypeSymbolRegistry(model)
```

The affected main files are discoverable with:

```powershell
rg -n "designSymbolRegistry|DesignSymbolRegistry|SymbolIdentity" cap4k-plugin-pipeline-generator-design/src/main
```

Do not run this command as a test; it is a static search command and is allowed for review.

- [ ] **Step 4: Update `ImportResolver` imports and signatures**

In `ImportResolver.kt`, replace design-local imports with common imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.generator.common.types.EXPLICIT_FQCN_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.common.types.PROJECT_TYPE_REGISTRY_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolIdentity
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolSelector
```

Change parameters from `DesignSymbolRegistry` to `TypeSymbolRegistry`.

Change explicit symbol creation from `SymbolIdentity(...)` to:

```kotlin
TypeSymbolIdentity(
    packageName = type.rawText.substringBeforeLast('.', missingDelimiterValue = ""),
    typeName = type.simpleName,
    source = EXPLICIT_FQCN_SOURCE,
)
```

Replace the private `selectShortTypeCandidates` and `singleAggregateContext` functions with:

```kotlin
val selectedCandidates = TypeSymbolSelector.selectShortNameCandidates(
    candidates = symbolRegistry.findBySimpleName(type.simpleName),
    aggregateContext = aggregateContext,
)
```

Keep the existing failure handling:

```kotlin
0 -> throw UnknownShortTypeFailure(type.rawText)
1 -> ImportResolutionResult(
    renderedType = type.simpleName,
    imports = setOf(selectedCandidates.single().fqcn),
    qualifiedFallback = false,
)
else -> throw AmbiguousShortTypeFailure(
    shortType = type.rawText,
    candidates = selectedCandidates.map { it.fqcn },
)
```

- [ ] **Step 5: Update factory/helper signatures**

In `DesignImportPlanner.kt`, `DesignPayloadRenderModelFactory.kt`, and `DesignSagaRenderModels.kt`, replace the design-local registry type with:

```kotlin
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
```

and use:

```kotlin
symbolRegistry: TypeSymbolRegistry = TypeSymbolRegistry()
```

Where code creates explicit symbols for sibling generated units, use `TypeSymbolIdentity` and `EXPLICIT_FQCN_SOURCE` from common.

- [ ] **Step 6: Update design tests**

Update imports in design tests from:

```kotlin
import com.only4.cap4k.plugin.pipeline.generator.design.types.DesignSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.design.types.SymbolIdentity
```

to:

```kotlin
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolIdentity
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
```

Rename helper functions locally:

```kotlin
private fun registryOf(vararg symbols: TypeSymbolIdentity): TypeSymbolRegistry =
    TypeSymbolRegistry().apply {
        symbols.forEach(::register)
    }
```

Keep the same test behavior. Do not weaken #104 tests.

- [ ] **Step 7: Remove obsolete design-local files**

Delete:

```text
cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/SymbolIdentity.kt
cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignSymbolRegistry.kt
```

- [ ] **Step 8: Static verification only**

Do not run Gradle or tests. Run static searches:

```powershell
rg -n "DesignSymbolRegistry|SymbolIdentity|designSymbolRegistry|selectShortTypeCandidates\\(" cap4k-plugin-pipeline-generator-design/src/main cap4k-plugin-pipeline-generator-design/src/test
rg -n "generator\\.design" cap4k-plugin-pipeline-generator-common cap4k-plugin-pipeline-generator-types/src/main
```

Expected:

- first search has no production references to old design-local registry names;
- test references are either removed or intentionally renamed to common names;
- second search has no matches showing common/types depending on design.

- [ ] **Step 9: Commit Task 3**

```powershell
git add cap4k-plugin-pipeline-generator-design
git add -u cap4k-plugin-pipeline-generator-design
git commit -m "refactor: use shared type symbols in design generator"
```

## Task 4: Migrate Value-Object Generator To Strict Common Resolution

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-types/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectTypeResolution.kt`
- Modify: `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-types/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlannerTest.kt`

**Interfaces:**
- Consumes: `CanonicalTypeSymbolRegistryFactory.from(...)`
- Consumes: `TypeSymbolRegistry`
- Consumes: `TypeSymbolSelector.selectShortNameCandidates(...)`
- Produces: `ValueObjectTypeParser.parse(type: String): ParsedValueObjectType`
- Produces: `ValueObjectTypeResolver.resolve(type: ParsedValueObjectType, symbolRegistry: TypeSymbolRegistry, aggregateContext: List<String>): ResolvedValueObjectType`

- [ ] **Step 1: Add the common dependency**

In `cap4k-plugin-pipeline-generator-types/build.gradle.kts`, add:

```kotlin
implementation(project(":cap4k-plugin-pipeline-generator-common"))
```

after the existing API dependency.

- [ ] **Step 2: Create value-object type resolution file**

Create `ValueObjectTypeResolution.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.generator.common.types.EXPLICIT_FQCN_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.common.types.PROJECT_TYPE_REGISTRY_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolIdentity
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolSelector

internal data class ParsedValueObjectType(
    val tokenText: String,
    val nullable: Boolean = false,
    val arguments: List<ParsedValueObjectType> = emptyList(),
) {
    fun tokenTexts(): Set<String> = buildSet {
        add(tokenText)
        arguments.forEach { argument -> addAll(argument.tokenTexts()) }
    }
}

internal data class ResolvedValueObjectType(
    val renderedType: String,
    val imports: Set<String> = emptySet(),
) {
    fun withNullability(nullable: Boolean): ResolvedValueObjectType =
        if (nullable && !renderedType.endsWith("?")) {
            copy(renderedType = "$renderedType?")
        } else {
            this
        }
}

internal object ValueObjectTypeResolver {
    private val builtInTypeNames = setOf(
        "Any",
        "Array",
        "Boolean",
        "Byte",
        "Char",
        "Collection",
        "Double",
        "Float",
        "Int",
        "Iterable",
        "List",
        "Long",
        "Map",
        "MutableCollection",
        "MutableIterable",
        "MutableList",
        "MutableMap",
        "MutableSet",
        "Nothing",
        "Number",
        "Pair",
        "Sequence",
        "Set",
        "Short",
        "String",
        "Triple",
        "Unit",
    )

    fun resolve(
        type: ParsedValueObjectType,
        symbolRegistry: TypeSymbolRegistry,
        aggregateContext: List<String>,
    ): ResolvedValueObjectType {
        val registry = TypeSymbolRegistry(symbolRegistry.allSymbols()).also { merged ->
            collectExplicitSymbols(type).forEach(merged::register)
        }
        return render(type, registry, aggregateContext)
    }

    private fun collectExplicitSymbols(type: ParsedValueObjectType): List<TypeSymbolIdentity> {
        val own = if (type.tokenText.contains('.')) {
            listOf(
                TypeSymbolIdentity(
                    packageName = type.tokenText.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = type.tokenText.substringAfterLast('.'),
                    source = EXPLICIT_FQCN_SOURCE,
                )
            )
        } else {
            emptyList()
        }
        return own + type.arguments.flatMap(::collectExplicitSymbols)
    }

    private fun render(
        type: ParsedValueObjectType,
        symbolRegistry: TypeSymbolRegistry,
        aggregateContext: List<String>,
    ): ResolvedValueObjectType {
        val resolvedArguments = type.arguments.map { render(it, symbolRegistry, aggregateContext) }
        val base = resolveBase(type.tokenText, symbolRegistry, aggregateContext)
        val renderedWithArguments = if (resolvedArguments.isEmpty()) {
            base.renderedType
        } else {
            resolvedArguments.joinToString(
                separator = ", ",
                prefix = "${base.renderedType}<",
                postfix = ">",
            ) { it.renderedType }
        }
        val rendered = if (type.nullable && !renderedWithArguments.endsWith("?")) {
            "$renderedWithArguments?"
        } else {
            renderedWithArguments
        }
        return ResolvedValueObjectType(
            renderedType = rendered,
            imports = base.imports + resolvedArguments.flatMap { it.imports },
        )
    }

    private fun resolveBase(
        tokenText: String,
        symbolRegistry: TypeSymbolRegistry,
        aggregateContext: List<String>,
    ): ResolvedValueObjectType {
        if (tokenText in builtInTypeNames) {
            return ResolvedValueObjectType(tokenText)
        }

        if (tokenText.contains('.')) {
            val simpleName = tokenText.substringAfterLast('.')
            val conflictingCandidates = symbolRegistry.findBySimpleName(simpleName)
                .filterNot { it.source == PROJECT_TYPE_REGISTRY_SOURCE }
                .filterNot { it.fqcn == tokenText }
            return if (conflictingCandidates.isEmpty()) {
                ResolvedValueObjectType(
                    renderedType = simpleName,
                    imports = setOf(tokenText),
                )
            } else {
                ResolvedValueObjectType(renderedType = tokenText)
            }
        }

        val selectedCandidates = TypeSymbolSelector.selectShortNameCandidates(
            candidates = symbolRegistry.findBySimpleName(tokenText),
            aggregateContext = aggregateContext,
        )
        return when (selectedCandidates.size) {
            0 -> throw UnknownValueObjectFieldTypeFailure(tokenText)
            1 -> ResolvedValueObjectType(
                renderedType = tokenText,
                imports = setOf(selectedCandidates.single().fqcn),
            )
            else -> throw AmbiguousValueObjectFieldTypeFailure(
                shortType = tokenText,
                candidates = selectedCandidates.map { it.fqcn },
            )
        }
    }
}

internal sealed class ValueObjectFieldTypeResolutionFailure(
    message: String,
) : IllegalArgumentException(message)

internal class UnknownValueObjectFieldTypeFailure(
    val shortType: String,
) : ValueObjectFieldTypeResolutionFailure(
    "unknown value object field type: $shortType; use a fully qualified name, declare a cap4k enum/value-object/Strong ID manifest type, or register an external type in types.registryFile"
)

internal class AmbiguousValueObjectFieldTypeFailure(
    val shortType: String,
    val candidates: List<String>,
) : ValueObjectFieldTypeResolutionFailure(
    "ambiguous value object field type: $shortType -> ${candidates.joinToString()}"
)

internal object ValueObjectTypeParser {
    fun parse(type: String): ParsedValueObjectType {
        val input = type.trim()
        require(input.isNotEmpty()) { "type must not be blank" }

        val parser = Parser(input)
        val parsed = parser.parseType()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            parser.failMismatchedAngles()
        }
        return parsed
    }

    private class Parser(
        private val input: String,
    ) {
        private var index = 0

        fun parseType(): ParsedValueObjectType {
            skipWhitespace()
            val tokenText = parseTokenText()
            skipWhitespace()

            val arguments = if (peek() == '<') {
                index++
                parseArguments()
            } else {
                emptyList()
            }

            skipWhitespace()
            val nullable = if (peek() == '?') {
                index++
                true
            } else {
                false
            }

            return ParsedValueObjectType(
                tokenText = tokenText,
                nullable = nullable,
                arguments = arguments,
            )
        }

        private fun parseArguments(): List<ParsedValueObjectType> {
            val arguments = mutableListOf<ParsedValueObjectType>()
            skipWhitespace()
            if (peek() == '>') {
                failEmptyGenericArgument()
            }

            while (true) {
                skipWhitespace()
                if (peek() == ',' || peek() == '>') {
                    failEmptyGenericArgument()
                }
                arguments += parseType()
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        index++
                        skipWhitespace()
                        if (peek() == ',' || peek() == '>') {
                            failEmptyGenericArgument()
                        }
                    }
                    '>' -> {
                        index++
                        return arguments
                    }
                    null -> failMismatchedAngles()
                    else -> failMismatchedAngles()
                }
            }
        }

        private fun parseTokenText(): String {
            val start = index
            while (true) {
                val char = peek() ?: break
                if (char == '<' || char == '>' || char == ',' || char == '?' || char.isWhitespace()) {
                    break
                }
                index++
            }
            require(index > start) {
                "expected type token in type: $input"
            }
            return input.substring(start, index)
        }

        fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) {
                index++
            }
        }

        fun isAtEnd(): Boolean = index >= input.length

        private fun peek(): Char? = input.getOrNull(index)

        fun failMismatchedAngles(): Nothing {
            throw IllegalArgumentException("mismatched angle brackets in type: $input")
        }

        private fun failEmptyGenericArgument(): Nothing {
            throw IllegalArgumentException("empty generic argument in type: $input")
        }
    }
}
```

- [ ] **Step 3: Wire common registry into `ValueObjectArtifactPlanner`**

In `ValueObjectArtifactPlanner.kt`:

Add imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.generator.common.types.CanonicalTypeSymbolRegistryFactory
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
```

Replace:

```kotlin
val typeBindings = ValueObjectTypeBindings.from(config, model, artifactLayout)
```

with:

```kotlin
val typeRegistry = CanonicalTypeSymbolRegistryFactory.from(config, model, artifactLayout)
```

Replace:

```kotlin
typeBindings.registryFor(valueObject)
```

with:

```kotlin
typeRegistry
```

Change `ValueObjectRenderModelFactory.create` signature:

```kotlin
fun create(
    valueObject: ValueObjectModel,
    typeRegistry: TypeSymbolRegistry,
): ValueObjectRenderModel
```

Change field resolution:

```kotlin
val resolved = ValueObjectTypeResolver.resolve(
    type = type,
    symbolRegistry = typeRegistry,
    aggregateContext = valueObject.aggregates,
)
```

Delete these private sections from `ValueObjectArtifactPlanner.kt` after the new file exists:

- `ValueObjectTypeBindings`
- `EntityKey`
- `ParsedType`
- `ResolvedType`
- `ValueObjectTypeResolver`
- `ValueObjectTypeParser`
- `manifestValueObjectTypeLookup`
- `sharedManifestValueObjectTypeLookup`
- `aggregateLocalManifestValueObjectFqnsByAggregate`
- `strongIdTypeLookup`
- private `EntityModel.key()` helper used only by `ValueObjectTypeBindings`

- [ ] **Step 4: Update value-object tests for strict behavior**

In `ValueObjectArtifactPlannerTest.kt`, update tests that currently expect unresolved short names to pass through.

Replace `aggregate owned enum is not imported without value object owner context` with a strict global-unique case:

```kotlin
@Test
fun `globally unique aggregate owned enum imports without owner context`() {
    val item = ValueObjectArtifactPlanner().plan(
        config(),
        model = CanonicalModel(
            entities = listOf(
                aggregateRootEntity(
                    name = "CarrierResourceConfirmation",
                    packageName = "com.acme.demo.domain.aggregates.carrier_resource_confirmation",
                ),
            ),
            sharedEnums = listOf(
                sharedEnum(
                    typeName = "CarrierResourceType",
                    packageName = "carrier_resource_confirmation",
                    aggregates = listOf("CarrierResourceConfirmation"),
                ),
            ),
            valueObjects = listOf(
                ValueObjectModel(
                    name = "CarrierResourceIdentity",
                    packageName = "booking",
                    fields = listOf(FieldModel("resourceType", "CarrierResourceType")),
                ),
            ),
        ),
    ).single()

    val fields = item.context["fields"] as List<Map<*, *>>

    assertEquals(
        listOf("com.acme.demo.domain.aggregates.carrier_resource_confirmation.enums.CarrierResourceType"),
        item.context["imports"],
    )
    assertEquals("CarrierResourceType", fields.single()["type"])
}
```

Replace `owner local enum conflicts with explicit registry` with local-first behavior:

```kotlin
@Test
fun `owner local enum wins over explicit registry in matching owner context`() {
    val item = ValueObjectArtifactPlanner().plan(
        config(
            typeRegistry = TypeRegistryConfig(
                entries = mapOf("Status" to TypeRegistryEntry("com.acme.external.Status")),
            ),
        ),
        CanonicalModel(
            entities = listOf(
                aggregateRootEntity(
                    name = "Order",
                    packageName = "com.acme.demo.domain.aggregates.order",
                ),
            ),
            sharedEnums = listOf(
                sharedEnum(
                    typeName = "Status",
                    packageName = "order",
                    aggregates = listOf("Order"),
                ),
            ),
            valueObjects = listOf(
                ValueObjectModel(
                    name = "OrderSnapshot",
                    packageName = "booking",
                    aggregates = listOf("Order"),
                    fields = listOf(FieldModel("status", "Status")),
                ),
            ),
        ),
    ).single()

    assertEquals(
        listOf("com.acme.demo.domain.aggregates.order.enums.Status"),
        item.context["imports"],
    )
}
```

Replace `owner local enum conflicts with Strong ID` with a matching local-first expectation, using the same structure and asserting the local enum import.

Add unknown short type failure:

```kotlin
@Test
fun `unknown short field type fails planning`() {
    val error = assertThrows<UnknownValueObjectFieldTypeFailure> {
        ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Money",
                        packageName = "com.acme.demo.domain.shared.values",
                        fields = listOf(FieldModel("currency", "CurrencyCode")),
                    ),
                ),
            ),
        )
    }

    assertEquals(true, error.message?.contains("unknown value object field type: CurrencyCode"))
}
```

Add unrelated aggregate-local value object regression:

```kotlin
@Test
fun `owner local enum ignores unrelated aggregate local value object`() {
    val item = ValueObjectArtifactPlanner().plan(
        config(),
        CanonicalModel(
            entities = listOf(
                aggregateRootEntity(
                    name = "Order",
                    packageName = "com.acme.demo.domain.aggregates.order",
                ),
                aggregateRootEntity(
                    name = "Customer",
                    packageName = "com.acme.demo.domain.aggregates.customer",
                ),
            ),
            sharedEnums = listOf(
                sharedEnum(
                    typeName = "Status",
                    packageName = "order",
                    aggregates = listOf("Order"),
                ),
            ),
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Status",
                    packageName = "com.acme.demo.domain.aggregates.customer.values",
                    aggregates = listOf("Customer"),
                    fields = listOf(FieldModel("code", "String")),
                ),
                ValueObjectModel(
                    name = "OrderSnapshot",
                    packageName = "booking",
                    aggregates = listOf("Order"),
                    fields = listOf(FieldModel("status", "Status")),
                ),
            ),
        ),
    ).single { it.context["typeName"] == "OrderSnapshot" }

    assertEquals(
        listOf("com.acme.demo.domain.aggregates.order.enums.Status"),
        item.context["imports"],
    )
}
```

Add no-context duplicate ambiguity:

```kotlin
@Test
fun `no context duplicate local short type fails ambiguous`() {
    val error = assertThrows<AmbiguousValueObjectFieldTypeFailure> {
        ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.order.values",
                        aggregates = listOf("Order"),
                        fields = listOf(FieldModel("code", "String")),
                    ),
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.customer.values",
                        aggregates = listOf("Customer"),
                        fields = listOf(FieldModel("code", "String")),
                    ),
                    ValueObjectModel(
                        name = "AuditEntry",
                        packageName = "com.acme.demo.domain.shared.values",
                        fields = listOf(FieldModel("snapshot", "Snapshot")),
                    ),
                ),
            ),
        )
    }

    assertEquals(true, error.message?.contains("ambiguous value object field type: Snapshot"))
}
```

- [ ] **Step 5: Static verification only**

Do not run Gradle or tests. Run static searches:

```powershell
rg -n "ValueObjectTypeBindings|manifestValueObjectTypeLookup|strongIdTypeLookup|Map<String, String>" cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types
rg -n "return ResolvedValueObjectType\\(tokenText\\)|return ResolvedType\\(tokenText\\)" cap4k-plugin-pipeline-generator-types/src/main
```

Expected:

- no `ValueObjectTypeBindings` remains;
- no value-object manifest/Strong ID lookup duplication remains in generator-types;
- no unknown short type pass-through remains.

- [ ] **Step 6: Commit Task 4**

```powershell
git add cap4k-plugin-pipeline-generator-types
git commit -m "fix: resolve value object fields with shared type symbols"
```

## Task 5: Cleanup, Documentation Merge Readiness, And Final Main Verification

**Files:**
- Review: `docs/superpowers/specs/2026-07-19-cap4k-issue-112-shared-scoped-type-resolution-design.md`
- Review: `docs/superpowers/plans/2026-07-19-cap4k-issue-112-shared-scoped-type-resolution.md`
- Review: all files modified by Tasks 1-4

**Interfaces:**
- Consumes: all task commits
- Produces: final issue #112 implementation branch ready for review

- [ ] **Step 1: Confirm old docs were not modified**

Run static diff commands:

```powershell
git diff --name-only origin/master...HEAD -- docs/superpowers/specs docs/superpowers/plans
```

Expected document additions include only:

```text
docs/superpowers/specs/2026-07-19-cap4k-issue-112-shared-scoped-type-resolution-design.md
docs/superpowers/plans/2026-07-19-cap4k-issue-112-shared-scoped-type-resolution.md
```

If older #112 document paths appear as modified, stop and remove those modifications before continuing.

- [ ] **Step 2: Check module dependency direction**

Run static search:

```powershell
rg -n "cap4k-plugin-pipeline-generator-design|generator\\.design" cap4k-plugin-pipeline-generator-common cap4k-plugin-pipeline-generator-types
```

Expected:

- no common module dependency on design;
- no types module dependency on design;
- no code imports from design into common/types.

- [ ] **Step 3: Check removed duplicate logic**

Run static search:

```powershell
rg -n "DesignSymbolRegistry|SymbolIdentity|ValueObjectTypeBindings|manifestValueObjectTypeLookup|sharedManifestValueObjectTypeLookup|aggregateLocalManifestValueObjectFqns|strongIdTypeLookup" cap4k-plugin-pipeline-generator-design/src/main cap4k-plugin-pipeline-generator-types/src/main
```

Expected:

- no old design-local symbol registry implementation;
- no old value-object type binding helper;
- no old value-object-specific manifest/Strong ID lookup helpers.

- [ ] **Step 4: Check BOM and broad rewrite risk**

Run:

```powershell
git diff --check
```

Expected:

```text
```

The command should print no output and exit 0. If it reports whitespace errors, fix only those lines.

- [ ] **Step 5: Main agent final verification**

Only the main agent runs this command, after all implementation tasks and commits are complete:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-common:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-generator-types:test
```

Expected:

```text
BUILD SUCCESSFUL
```

If this fails, do not patch blindly. Use `superpowers:systematic-debugging`, inspect the first failing test and the corresponding implementation evidence, then apply the smallest fix consistent with this spec.

- [ ] **Step 6: Commit final cleanup if needed**

If final verification required any cleanup fixes, commit them:

```powershell
git add <only-files-changed-by-cleanup>
git commit -m "chore: clean up shared type resolution migration"
```

Do not create an empty commit if no cleanup was needed.

## Execution Notes

Run this plan only after the new spec/plan branch has been merged into `spec/issue-112-value-object-enum-imports`.

Before Task 1 in the implementation worktree, inspect and handle the current uncommitted diagnostic patch:

```powershell
git status --short --branch
git diff -- cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt cap4k-plugin-pipeline-generator-types/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlannerTest.kt
```

If the diff only contains the earlier failed local `ValueObjectTypeBindings` patch and BOM changes, remove or replace it before implementation. Do not run `git reset --hard`. Prefer explicit file restoration or a reviewed reverse patch for only those main-agent diagnostic edits.

## Plan Self-Review

Spec coverage:

- Shared common module is covered by Tasks 1 and 2.
- Design generator migration is covered by Task 3.
- Value-object strict resolution and #112 behavior are covered by Task 4.
- Worktree/document isolation and final verification policy are covered by Task 5.

Placeholder scan:

- No unfinished placeholder steps are intentionally present.
- Test command execution is intentionally centralized in Task 5 because user constraints prohibit subagent compile/test runs.

Type consistency:

- Common registry type is consistently named `TypeSymbolRegistry`.
- Common identity type is consistently named `TypeSymbolIdentity`.
- Common selector is consistently named `TypeSymbolSelector`.
- Canonical factory is consistently named `CanonicalTypeSymbolRegistryFactory`.

Plan complete and saved to `docs/superpowers/plans/2026-07-19-cap4k-issue-112-shared-scoped-type-resolution.md`. Execution should use Subagent-Driven mode after user review and after this docs branch is merged into the current implementation branch.

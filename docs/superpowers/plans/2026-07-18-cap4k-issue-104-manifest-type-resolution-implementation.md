# Cap4k Issue 104 Manifest Type Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix #104 so design-generator field type resolution consumes manifest-managed enum and value-object identities from `CanonicalModel` without requiring duplicate `types.registryFile` entries.

**Architecture:** Keep the repair inside `cap4k-plugin-pipeline-generator-design`. Replace the design-generator's map-only type binding path with a design-local `DesignSymbolRegistry` candidate pool that preserves source and owner metadata, then make `ImportResolver` select unresolved short names using the current design block aggregate context. Reuse `CanonicalEnumCatalog` plus `ArtifactLayoutResolver` for enum FQNs so enum package rules stay aligned with aggregate generation.

**Tech Stack:** Kotlin, JUnit 5, cap4k pipeline API, cap4k design-generator module, Gradle project layout.

## Global Constraints

- Work in the existing isolated worktree: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/spec-issue-104-manifest-type-resolution`.
- Do not implement normal work directly on `master`.
- Do not modify source providers.
- Do not modify manifest JSON contracts.
- Do not move type resolution into Pebble templates.
- Do not add downstream `booking-center` or other dogfood project registry workarounds.
- Do not restore source-code scanning.
- Do not restore class-name guessing.
- Do not support sibling design-entry short-name references.
- Preserve explicit FQN behavior.
- Preserve inner generated-unit type precedence.
- Preserve canonical Strong ID short-name resolution.
- Preserve explicit `types.registryFile` resolution for external handwritten types.
- Register manifest enum identities from `CanonicalModel.sharedEnums`.
- Register manifest value-object identities from `CanonicalModel.valueObjects`.
- Aggregate-owned manifest types are valid design type identities.
- Single aggregate context resolves matching aggregate-owned manifest candidates before shared candidates.
- No aggregate context and multiple aggregate contexts only accept globally unique short-name candidates.
- Ambiguous short names fail fast with candidate FQNs.
- Unknown short-name diagnostics mention `types.enumManifest`, `types.valueObjectManifest`, and `types.registryFile`.
- This workspace forbids active build, compile, run, test, install, and dependency-download commands. The plan still documents RED/GREEN commands for the user or a later permitted environment, but this session must not execute them.
- Do not modify the git index or create commits unless the user explicitly asks. Commit checkpoints below are written as manual commands for a permitted handoff.

---

## File Structure

- `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlannerTest.kt`
  - Owns planner-level regression coverage for command payload field imports.
  - This file is enough to prove the common payload factory path for manifest value objects, manifest enums, nested generic imports, explicit registry entries, Strong IDs, aggregate-local narrowing, ambiguity, and diagnostics.

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/SymbolIdentity.kt`
  - Owns the immutable metadata for one resolvable design type candidate.
  - Add source constants and owner metadata here so registry and resolver code use the same vocabulary.

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignSymbolRegistry.kt`
  - Owns in-memory grouping of symbol candidates by simple name.
  - Keep it as a small collection wrapper; selection rules stay in `ImportResolver`.

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRegistryBindings.kt`
  - Owns conversion from `ProjectConfig` plus `CanonicalModel` into design-generator symbol candidates.
  - This file replaces the map-only `designTypeRegistryFqns` path.

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolver.kt`
  - Owns rendering and short-name selection.
  - Add aggregate-context-aware candidate selection only for `DesignResolvedTypeKind.UNRESOLVED`.

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignImportPlanner.kt`
  - Thin wrapper over `ImportResolver.plan`.
  - Add `aggregateContext` pass-through.

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`
  - Owns field namespace preparation and shared payload render model creation.
  - Replace map parameters with `DesignSymbolRegistry`, pass each block's aggregate context through validation and import planning, and update unknown-type advisory text.

- Planner call sites to update from map binding to symbol binding:
  - `DesignCommandArtifactPlanner.kt`
  - `DesignQueryArtifactPlanner.kt`
  - `DesignClientArtifactPlanner.kt`
  - `DesignApiPayloadArtifactPlanner.kt`
  - `DesignDomainEventArtifactPlanner.kt`
  - `DesignIntegrationEventArtifactPlanner.kt`
  - `DesignSagaArtifactPlanner.kt`
  - `DesignSagaRenderModels.kt`

---

### Task 1: Write Planner-Level Failing Tests

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlannerTest.kt`

**Interfaces:**
- Consumes: `DesignCommandArtifactPlanner.plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem>`
- Consumes helper: `private fun commandBlock(...): DesignBlockModel`
- Consumes helper: `private fun projectConfig(...): ProjectConfig`
- Produces: failing tests that define #104 behavior before production code changes.

- [ ] **Step 1: Add imports required by the new tests**

Add these imports to `DesignCommandArtifactPlannerTest.kt` near the existing `cap4k-plugin-pipeline-api` imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
```

- [ ] **Step 2: Extend `commandBlock` helper**

Replace the existing `commandBlock` helper with this version so tests can express no-context, single-context, and multi-context blocks without duplicating `DesignBlockModel` construction:

```kotlin
private fun commandBlock(
    packageName: String = "order.submit",
    name: String = "SubmitOrder",
    aggregates: List<String> = listOf("Order"),
    fields: List<FieldModel> = emptyList(),
    artifacts: List<ArtifactSelectionModel> = listOf(ArtifactSelectionModel("command")),
) = DesignBlockModel(
    tag = "command",
    packageName = packageName,
    name = name,
    description = "submit order",
    aggregates = aggregates,
    artifacts = artifacts,
    fields = fields,
)
```

- [ ] **Step 3: Extend `projectConfig` helper**

Replace the existing `projectConfig` helper with this version so explicit registry preservation can be asserted in the same test file:

```kotlin
private fun projectConfig(
    modules: Map<String, String>,
    artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    typeRegistry: TypeRegistryConfig = TypeRegistryConfig(),
) = ProjectConfig(
    basePackage = "com.acme.demo",
    layout = ProjectLayout.MULTI_MODULE,
    modules = modules,
    typeRegistry = typeRegistry,
    sources = emptyMap(),
    generators = mapOf("command" to GeneratorConfig()),
    templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    artifactLayout = artifactLayout,
)
```

- [ ] **Step 4: Add shared manifest value-object test**

Insert this test after `designCommand resolves strong id field imports from canonical model`:

```kotlin
@Test
fun `designCommand resolves shared manifest value object field imports`() {
    val planner = DesignCommandArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            designBlocks = listOf(
                commandBlock(
                    packageName = "booking",
                    name = "CreateBooking",
                    fields = listOf(FieldModel("customerRef", "CustomerRef")),
                )
            ),
            valueObjects = listOf(
                ValueObjectModel(
                    name = "CustomerRef",
                    packageName = "com.acme.demo.domain.shared.values",
                )
            ),
        ),
    )

    val command = items.single()

    assertEquals(listOf("com.acme.demo.domain.shared.values.CustomerRef"), command.context["imports"])
    assertEquals(
        listOf(DesignRenderFieldModel(name = "customerRef", renderedType = "CustomerRef")),
        command.context["fields"],
    )
}
```

- [ ] **Step 5: Add shared manifest enum and nested generic test**

Insert this test after the shared value-object test:

```kotlin
@Test
fun `designCommand resolves shared manifest enum field imports including generic arguments`() {
    val planner = DesignCommandArtifactPlanner()
    val enumItems = listOf(EnumItemModel(1, "PASSPORT", "Passport"))

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            designBlocks = listOf(
                commandBlock(
                    packageName = "document",
                    name = "AttachDocument",
                    fields = listOf(
                        FieldModel("documentType", "DocumentType"),
                        FieldModel("documentTypes", "List<DocumentType>"),
                    ),
                )
            ),
            sharedEnums = listOf(
                SharedEnumDefinition(
                    typeName = "DocumentType",
                    packageName = "shared",
                    items = enumItems,
                )
            ),
        ),
    )

    val command = items.single()

    assertEquals(listOf("com.acme.demo.domain.shared.enums.DocumentType"), command.context["imports"])
    assertEquals(
        listOf(
            DesignRenderFieldModel(name = "documentType", renderedType = "DocumentType"),
            DesignRenderFieldModel(name = "documentTypes", renderedType = "List<DocumentType>"),
        ),
        command.context["fields"],
    )
}
```

- [ ] **Step 6: Add explicit registry preservation test**

Insert this test after the shared manifest enum test:

```kotlin
@Test
fun `designCommand still resolves explicit type registry field imports`() {
    val planner = DesignCommandArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(
            modules = mapOf("application" to "demo-application"),
            typeRegistry = TypeRegistryConfig(
                entries = mapOf("ExternalCustomerRef" to TypeRegistryEntry("com.acme.external.ExternalCustomerRef")),
            ),
        ),
        model = CanonicalModel(
            designBlocks = listOf(
                commandBlock(
                    packageName = "booking",
                    name = "CreateBooking",
                    fields = listOf(FieldModel("customerRef", "ExternalCustomerRef")),
                )
            ),
        ),
    )

    val command = items.single()

    assertEquals(listOf("com.acme.external.ExternalCustomerRef"), command.context["imports"])
    assertEquals(
        listOf(DesignRenderFieldModel(name = "customerRef", renderedType = "ExternalCustomerRef")),
        command.context["fields"],
    )
}
```

- [ ] **Step 7: Add no-context aggregate-owned value-object ambiguity test**

Insert this test after the explicit registry preservation test:

```kotlin
@Test
fun `designCommand fails ambiguous aggregate-owned value object without aggregate context`() {
    val planner = DesignCommandArtifactPlanner()

    val error = assertThrows(IllegalArgumentException::class.java) {
        planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "content",
                        name = "PublishContent",
                        aggregates = emptyList(),
                        fields = listOf(FieldModel("snapshot", "Snapshot")),
                    )
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.content.values",
                        aggregates = listOf("Content"),
                    ),
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.review.values",
                        aggregates = listOf("Review"),
                    ),
                ),
            ),
        )
    }

    val message = error.message.orEmpty()
    assertTrue(message.contains("ambiguous short type: Snapshot"))
    assertTrue(message.contains("com.acme.demo.domain.aggregates.content.values.Snapshot"))
    assertTrue(message.contains("com.acme.demo.domain.aggregates.review.values.Snapshot"))
}
```

- [ ] **Step 8: Add single aggregate value-object local resolution test**

Insert this test after the no-context ambiguity test:

```kotlin
@Test
fun `designCommand resolves matching aggregate-owned value object in single aggregate context`() {
    val planner = DesignCommandArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            designBlocks = listOf(
                commandBlock(
                    packageName = "content",
                    name = "PublishContent",
                    aggregates = listOf("Content"),
                    fields = listOf(FieldModel("snapshot", "Snapshot")),
                )
            ),
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Snapshot",
                    packageName = "com.acme.demo.domain.aggregates.content.values",
                    aggregates = listOf("Content"),
                ),
                ValueObjectModel(
                    name = "Snapshot",
                    packageName = "com.acme.demo.domain.aggregates.review.values",
                    aggregates = listOf("Review"),
                ),
            ),
        ),
    )

    val command = items.single()

    assertEquals(listOf("com.acme.demo.domain.aggregates.content.values.Snapshot"), command.context["imports"])
    assertEquals(
        listOf(DesignRenderFieldModel(name = "snapshot", renderedType = "Snapshot")),
        command.context["fields"],
    )
}
```

- [ ] **Step 9: Add local-over-shared value-object test**

Insert this test after the single aggregate value-object test:

```kotlin
@Test
fun `designCommand prefers matching aggregate-owned value object over shared value object`() {
    val planner = DesignCommandArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            designBlocks = listOf(
                commandBlock(
                    packageName = "content",
                    name = "PublishContent",
                    aggregates = listOf("Content"),
                    fields = listOf(FieldModel("snapshot", "Snapshot")),
                )
            ),
            valueObjects = listOf(
                ValueObjectModel(
                    name = "Snapshot",
                    packageName = "com.acme.demo.domain.aggregates.content.values",
                    aggregates = listOf("Content"),
                ),
                ValueObjectModel(
                    name = "Snapshot",
                    packageName = "com.acme.demo.domain.shared.values",
                ),
            ),
        ),
    )

    val command = items.single()

    assertEquals(listOf("com.acme.demo.domain.aggregates.content.values.Snapshot"), command.context["imports"])
    assertEquals(
        listOf(DesignRenderFieldModel(name = "snapshot", renderedType = "Snapshot")),
        command.context["fields"],
    )
}
```

- [ ] **Step 10: Add multi-aggregate value-object ambiguity test**

Insert this test after the local-over-shared value-object test:

```kotlin
@Test
fun `designCommand fails ambiguous aggregate-owned value object in multi aggregate context`() {
    val planner = DesignCommandArtifactPlanner()

    val error = assertThrows(IllegalArgumentException::class.java) {
        planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "content.review",
                        name = "ReviewContent",
                        aggregates = listOf("Content", "Review"),
                        fields = listOf(FieldModel("snapshot", "Snapshot")),
                    )
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.content.values",
                        aggregates = listOf("Content"),
                    ),
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.review.values",
                        aggregates = listOf("Review"),
                    ),
                ),
            ),
        )
    }

    val message = error.message.orEmpty()
    assertTrue(message.contains("ambiguous short type: Snapshot"))
    assertTrue(message.contains("com.acme.demo.domain.aggregates.content.values.Snapshot"))
    assertTrue(message.contains("com.acme.demo.domain.aggregates.review.values.Snapshot"))
}
```

- [ ] **Step 11: Add local-over-shared enum test**

Insert this test after the multi-aggregate value-object ambiguity test:

```kotlin
@Test
fun `designCommand prefers matching aggregate-owned enum over shared enum`() {
    val planner = DesignCommandArtifactPlanner()
    val enumItems = listOf(EnumItemModel(1, "OPEN", "Open"))

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            designBlocks = listOf(
                commandBlock(
                    packageName = "content",
                    name = "PublishContent",
                    aggregates = listOf("Content"),
                    fields = listOf(FieldModel("status", "Status")),
                )
            ),
            sharedEnums = listOf(
                SharedEnumDefinition(
                    typeName = "Status",
                    packageName = "content",
                    items = enumItems,
                    aggregates = listOf("Content"),
                ),
                SharedEnumDefinition(
                    typeName = "Status",
                    packageName = "shared",
                    items = enumItems,
                ),
            ),
        ),
    )

    val command = items.single()

    assertEquals(listOf("com.acme.demo.domain.aggregates.content.enums.Status"), command.context["imports"])
    assertEquals(
        listOf(DesignRenderFieldModel(name = "status", renderedType = "Status")),
        command.context["fields"],
    )
}
```

- [ ] **Step 12: Add multi-aggregate enum ambiguity test**

Insert this test after the local-over-shared enum test:

```kotlin
@Test
fun `designCommand fails ambiguous aggregate-owned enum in multi aggregate context`() {
    val planner = DesignCommandArtifactPlanner()
    val enumItems = listOf(EnumItemModel(1, "OPEN", "Open"))

    val error = assertThrows(IllegalArgumentException::class.java) {
        planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "content.review",
                        name = "ReviewContent",
                        aggregates = listOf("Content", "Review"),
                        fields = listOf(FieldModel("status", "Status")),
                    )
                ),
                sharedEnums = listOf(
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "content",
                        items = enumItems,
                        aggregates = listOf("Content"),
                    ),
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "review",
                        items = enumItems,
                        aggregates = listOf("Review"),
                    ),
                ),
            ),
        )
    }

    val message = error.message.orEmpty()
    assertTrue(message.contains("ambiguous short type: Status"))
    assertTrue(message.contains("com.acme.demo.domain.aggregates.content.enums.Status"))
    assertTrue(message.contains("com.acme.demo.domain.aggregates.review.enums.Status"))
}
```

- [ ] **Step 13: Add diagnostic wording test**

Insert this test after the existing sibling diagnostic tests:

```kotlin
@Test
fun `unresolved short type diagnostic mentions manifest type inputs and external registry`() {
    val planner = DesignCommandArtifactPlanner()

    val error = assertThrows(IllegalArgumentException::class.java) {
        planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        name = "CreateBooking",
                        fields = listOf(FieldModel("customerRef", "CustomerRef")),
                    ),
                ),
            ),
        )
    }

    val message = error.message.orEmpty()
    assertTrue(message.contains("types.enumManifest"))
    assertTrue(message.contains("types.valueObjectManifest"))
    assertTrue(message.contains("types.registryFile"))
}
```

- [ ] **Step 14: Document RED command without running it**

Do not execute this command in the current workspace. Record it in the task notes or final handoff as the user-run RED command:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignCommandArtifactPlannerTest"
```

Expected RED result before Task 2 production changes:

```text
The newly added manifest resolution tests fail because CustomerRef, DocumentType, Snapshot, and Status are unknown or ambiguous under the old map-only registry path.
The existing strong ID test remains expected to pass.
```

- [ ] **Step 15: Manual commit checkpoint**

Do not execute these commands unless the user explicitly asks for commits:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlannerTest.kt
git commit -m "test: cover manifest type resolution in design generator"
```

### Task 2: Replace Map Binding With Design Symbol Candidates

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/SymbolIdentity.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignSymbolRegistry.kt`
- Replace: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRegistryBindings.kt`

**Interfaces:**
- Produces: `internal fun ProjectConfig.designSymbolRegistry(model: CanonicalModel): DesignSymbolRegistry`
- Produces: `SymbolIdentity.ownerAggregateName: String?`
- Produces: `SymbolIdentity.manifestOwned: Boolean`
- Produces: `SymbolIdentity.shared: Boolean`
- Keeps: `DesignSymbolRegistry.register(symbol: SymbolIdentity)`
- Keeps: `DesignSymbolRegistry.findBySimpleName(simpleName: String): List<SymbolIdentity>`
- Keeps: `DesignSymbolRegistry.allSymbols(): List<SymbolIdentity>`

- [ ] **Step 1: Replace `SymbolIdentity.kt`**

Replace the entire file with:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design.types

internal const val PROJECT_TYPE_REGISTRY_SOURCE = "project-type-registry"
internal const val STRONG_ID_SOURCE = "strong-id"
internal const val MANIFEST_ENUM_SOURCE = "manifest-enum"
internal const val MANIFEST_VALUE_OBJECT_SOURCE = "manifest-value-object"
internal const val EXPLICIT_FQCN_SOURCE = "explicit-fqcn"
internal const val AGGREGATE_SOURCE = "aggregate"

internal data class SymbolIdentity(
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

- [ ] **Step 2: Keep `DesignSymbolRegistry.kt` unchanged except imports are not needed**

Read `DesignSymbolRegistry.kt`. The current implementation already preserves all candidates under one simple name:

```kotlin
internal class DesignSymbolRegistry {
    constructor(symbols: Iterable<SymbolIdentity> = emptyList()) {
        symbols.forEach(::register)
    }

    private val symbolsBySimpleName = linkedMapOf<String, LinkedHashSet<SymbolIdentity>>()

    fun register(symbol: SymbolIdentity) {
        symbolsBySimpleName.getOrPut(symbol.simpleName) { linkedSetOf() }.add(symbol)
    }

    fun findBySimpleName(simpleName: String): List<SymbolIdentity> {
        return symbolsBySimpleName[simpleName].orEmpty().toList()
    }

    fun allSymbols(): List<SymbolIdentity> = symbolsBySimpleName.values.flatten()
}
```

Do not add selection logic to this file.

- [ ] **Step 3: Replace `DesignTypeRegistryBindings.kt`**

Replace the entire file with:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalog
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumDescriptor
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.ownerAggregate
import com.only4.cap4k.plugin.pipeline.generator.design.types.DesignSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.design.types.MANIFEST_ENUM_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.design.types.MANIFEST_VALUE_OBJECT_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.design.types.PROJECT_TYPE_REGISTRY_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.design.types.STRONG_ID_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.design.types.SymbolIdentity

internal fun ProjectConfig.designSymbolRegistry(model: CanonicalModel): DesignSymbolRegistry =
    DesignSymbolRegistry().apply {
        typeRegistryFqns().forEach { (simpleName, fqn) ->
            register(
                SymbolIdentity(
                    packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = simpleName,
                    source = PROJECT_TYPE_REGISTRY_SOURCE,
                )
            )
        }

        model.strongIds.forEach { strongId ->
            register(
                SymbolIdentity(
                    packageName = strongId.packageName,
                    typeName = strongId.typeName,
                    source = STRONG_ID_SOURCE,
                    ownerAggregateName = strongId.ownerAggregateName,
                )
            )
        }

        manifestEnumSymbols(model).forEach(::register)

        model.valueObjects.forEach { valueObject ->
            register(
                SymbolIdentity(
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

private fun ProjectConfig.manifestEnumSymbols(model: CanonicalModel): List<SymbolIdentity> {
    val layoutResolver = ArtifactLayoutResolver(basePackage, artifactLayout)
    val sharedDefinitions = model.sharedEnums.filter { it.aggregates.isEmpty() }
    val localDefinitions = model.sharedEnums.filter { it.aggregates.isNotEmpty() }
    val sharedCatalog = CanonicalEnumCatalog.from(
        model.copy(sharedEnums = sharedDefinitions),
        layoutResolver,
        emptyMap(),
    )
    val localCatalog = CanonicalEnumCatalog.from(
        model.copy(sharedEnums = localDefinitions),
        layoutResolver,
        emptyMap(),
    )
    val selection = DesignManifestEnumCatalogSelection.from(
        model = model,
        sharedCatalog = sharedCatalog,
        localCatalog = localCatalog,
    )

    return model.sharedEnums.flatMap { definition ->
        val ownerAggregateName = definition.aggregates.singleOrNull()
        val shared = definition.aggregates.isEmpty()
        selection.descriptorsFor(definition).map { descriptor ->
            SymbolIdentity(
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

private class DesignManifestEnumCatalogSelection(
    private val sharedByTypeName: Map<String, CanonicalEnumDescriptor>,
    private val localByKey: Map<DesignManifestLocalEnumKey, CanonicalEnumDescriptor>,
    private val entities: List<EntityModel>,
    private val aggregateRootNameByEntity: Map<DesignManifestEntityKey, String>,
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

    private fun localOwnerKeys(definition: SharedEnumDefinition): List<DesignManifestLocalEnumKey> {
        val ownerAggregateName = requireNotNull(definition.aggregates.singleOrNull()) {
            "enum ${definition.typeName} may declare at most one aggregate"
        }
        return entities
            .filter { entity -> aggregateRootNameByEntity[entity.key()] == ownerAggregateName }
            .map { entity -> DesignManifestLocalEnumKey(entity.packageName, definition.typeName) }
            .distinct()
            .ifEmpty { listOf(DesignManifestLocalEnumKey(ownerAggregateName, definition.typeName)) }
    }

    companion object {
        fun from(
            model: CanonicalModel,
            sharedCatalog: CanonicalEnumCatalog,
            localCatalog: CanonicalEnumCatalog,
        ): DesignManifestEnumCatalogSelection =
            DesignManifestEnumCatalogSelection(
                sharedByTypeName = sharedCatalog.sharedEnums.associateBy { it.typeName },
                localByKey = localCatalog.localEnums
                    .mapNotNull { descriptor ->
                        descriptor.ownerPackageName?.let { ownerPackageName ->
                            DesignManifestLocalEnumKey(ownerPackageName, descriptor.typeName) to descriptor
                        }
                    }
                    .toMap(),
                entities = model.entities,
                aggregateRootNameByEntity = buildAggregateRootNameByEntity(model.entities),
            )

        private fun buildAggregateRootNameByEntity(
            entities: List<EntityModel>,
        ): Map<DesignManifestEntityKey, String> {
            val entitiesByKey = entities.associateBy { it.key() }
            val entitiesByName = entities.groupBy { it.name }
            val resolving = mutableSetOf<DesignManifestEntityKey>()
            val resolved = linkedMapOf<DesignManifestEntityKey, String>()

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
                        val parent = entitiesByKey[DesignManifestEntityKey(entity.packageName, parentEntityName)]
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

private data class DesignManifestLocalEnumKey(
    val ownerPackageName: String,
    val typeName: String,
)

private data class DesignManifestEntityKey(
    val packageName: String,
    val name: String,
)

private fun EntityModel.key(): DesignManifestEntityKey =
    DesignManifestEntityKey(packageName = packageName, name = name)
```

The split `sharedCatalog` and `localCatalog` construction is required. A single combined `CanonicalEnumCatalog.from(model, ..., typeRegistry.entries)` call inherits DB `@T` binding ambiguity rules and rejects shared/local enum same-name cases before the design resolver can apply the spec's single-aggregate local-first rule. Passing `emptyMap()` keeps registry collisions in the design candidate pool so `ImportResolver` can report them as short-name ambiguity.

- [ ] **Step 4: Document RED command without running it**

Do not execute this command in the current workspace. Record it in the task notes or final handoff as the user-run RED command after Task 1 and before Task 3:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignCommandArtifactPlannerTest"
```

Expected result after Task 2 only:

```text
Compilation still fails because planner and factory call sites still pass typeRegistry maps while the new registry helper has not been threaded through.
```

- [ ] **Step 5: Manual commit checkpoint**

Do not execute these commands unless the user explicitly asks for commits:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/SymbolIdentity.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRegistryBindings.kt
git commit -m "feat: build design symbol candidates from canonical model"
```

### Task 3: Thread Symbol Registry And Aggregate Context Through Design Payload Planning

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignImportPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSagaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSagaRenderModels.kt`

**Interfaces:**
- Consumes: `ProjectConfig.designSymbolRegistry(model: CanonicalModel): DesignSymbolRegistry`
- Consumes: `DesignBlockModel.aggregates: List<String>`
- Produces: `DesignImportPlanner.plan(..., aggregateContext: List<String> = emptyList())`
- Produces: factory functions that accept `symbolRegistry: DesignSymbolRegistry` instead of `typeRegistry: Map<String, String>`.

- [ ] **Step 1: Update `DesignImportPlanner.plan` signature**

Replace the body of `DesignImportPlanner.kt` with:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.generator.design.types.DesignSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.design.types.ImportResolver

internal object DesignImportPlanner {

    fun plan(
        types: List<DesignResolvedTypeModel>,
        innerTypeNames: Set<String> = emptySet(),
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
        aggregateContext: List<String> = emptyList(),
    ): DesignImportPlan {
        return ImportResolver.plan(
            types = types,
            innerTypeNames = innerTypeNames,
            symbolRegistry = symbolRegistry,
            aggregateContext = aggregateContext,
        )
    }
}
```

- [ ] **Step 2: Change `DesignPayloadRenderModelFactory` public function parameters**

In `DesignPayloadRenderModelFactory.kt`, replace each public factory parameter named `typeRegistry: Map<String, String> = emptyMap()` with `symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry()`.

For `createForCommandBlock`, the exact signature should be:

```kotlin
fun createForCommandBlock(
    packageName: String,
    block: DesignBlockModel,
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    siblingTypeNames: Set<String> = emptySet(),
): DesignRenderModel = createForBlock(
    packageName = packageName,
    typeName = block.commandTypeName(),
    description = block.description,
    fields = block.fields,
    resultFields = block.resultFields,
    symbolRegistry = symbolRegistry,
    aggregateContext = block.aggregates,
    siblingRequestTypeNames = siblingTypeNames,
)
```

For `createForQueryBlock`, the exact signature should be:

```kotlin
fun createForQueryBlock(
    packageName: String,
    block: DesignBlockModel,
    pageRequest: Boolean,
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    siblingTypeNames: Set<String> = emptySet(),
): DesignRenderModel = createForBlock(
    packageName = packageName,
    typeName = block.queryTypeName(),
    description = block.description,
    fields = block.fields,
    resultFields = block.resultFields,
    symbolRegistry = symbolRegistry,
    aggregateContext = block.aggregates,
    siblingRequestTypeNames = siblingTypeNames,
    pageRequest = pageRequest,
)
```

For `createForClientBlock`, the exact signature should be:

```kotlin
fun createForClientBlock(
    packageName: String,
    block: DesignBlockModel,
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    siblingTypeNames: Set<String> = emptySet(),
): DesignRenderModel = createForBlock(
    packageName = packageName,
    typeName = block.clientTypeName(),
    description = block.description,
    fields = block.fields,
    resultFields = block.resultFields,
    symbolRegistry = symbolRegistry,
    aggregateContext = block.aggregates,
    siblingRequestTypeNames = siblingTypeNames,
)
```

For `createForApiPayloadBlock`, the exact signature should be:

```kotlin
fun createForApiPayloadBlock(
    packageName: String,
    block: DesignBlockModel,
    pageRequest: Boolean,
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
): DesignRenderModel = createForBlock(
    packageName = packageName,
    typeName = block.apiPayloadTypeName(),
    description = block.description,
    fields = block.fields,
    resultFields = block.resultFields,
    symbolRegistry = symbolRegistry,
    aggregateContext = block.aggregates,
    pageRequest = pageRequest,
)
```

For `createForDomainEventBlock`, the exact signature should be:

```kotlin
fun createForDomainEventBlock(
    packageName: String,
    block: DesignBlockModel,
    aggregate: EntityModel,
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
): DesignRenderModel = createForBlock(
    packageName = packageName,
    typeName = block.domainEventTypeName(),
    description = block.description,
    aggregateName = aggregate.name,
    aggregatePackageName = aggregate.packageName,
    fields = block.fields,
    resultFields = emptyList(),
    symbolRegistry = symbolRegistry,
    aggregateContext = block.aggregates,
)
```

For `createForIntegrationEventBlock`, the exact signature should be:

```kotlin
fun createForIntegrationEventBlock(
    packageName: String,
    block: DesignBlockModel,
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
): DesignRenderModel = createForBlock(
    packageName = packageName,
    typeName = block.integrationEventTypeName(),
    description = block.description,
    fields = block.fields,
    resultFields = emptyList(),
    symbolRegistry = symbolRegistry,
    aggregateContext = block.aggregates,
)
```

For `createForSagaBlock`, the exact signature should be:

```kotlin
fun createForSagaBlock(
    packageName: String,
    block: DesignBlockModel,
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
): DesignRenderModel = createForBlock(
    packageName = packageName,
    typeName = block.name,
    description = block.description,
    fields = block.fields,
    resultFields = block.resultFields,
    symbolRegistry = symbolRegistry,
    aggregateContext = block.aggregates,
)
```

- [ ] **Step 3: Change private factory signatures**

In `DesignPayloadRenderModelFactory.kt`, replace the private `createForBlock` and `createRenderModel` parameters so they accept symbol registry and aggregate context:

```kotlin
private fun createForBlock(
    packageName: String,
    typeName: String,
    description: String,
    fields: List<FieldModel>,
    resultFields: List<FieldModel>,
    symbolRegistry: DesignSymbolRegistry,
    aggregateContext: List<String>,
    siblingRequestTypeNames: Set<String> = emptySet(),
    pageRequest: Boolean = false,
    aggregateName: String? = null,
    aggregatePackageName: String? = null,
): DesignRenderModel
```

```kotlin
private fun createRenderModel(
    packageName: String,
    typeName: String,
    description: String,
    aggregateName: String?,
    aggregatePackageName: String?,
    requestNamespace: NamespaceModel,
    responseNamespace: NamespaceModel,
    symbolRegistry: DesignSymbolRegistry,
    aggregateContext: List<String>,
    siblingRequestTypeNames: Set<String> = emptySet(),
    pageRequest: Boolean = false,
): DesignRenderModel
```

Inside `createForBlock`, call `createRenderModel` with:

```kotlin
symbolRegistry = symbolRegistry,
aggregateContext = aggregateContext,
```

- [ ] **Step 4: Replace `buildSymbolRegistry` implementation**

In `DesignPayloadRenderModelFactory.kt`, replace `buildSymbolRegistry` with:

```kotlin
private fun buildSymbolRegistry(
    aggregateName: String?,
    aggregatePackageName: String?,
    requestNamespace: NamespaceModel,
    responseNamespace: NamespaceModel,
    symbolRegistry: DesignSymbolRegistry,
): DesignSymbolRegistry {
    val registry = DesignSymbolRegistry(symbolRegistry.allSymbols())
    val resolvedAggregateName = aggregateName?.takeIf { it.isNotBlank() }
    val resolvedAggregatePackageName = aggregatePackageName?.takeIf { it.isNotBlank() }
    if (resolvedAggregateName != null && resolvedAggregatePackageName != null) {
        registry.register(
            SymbolIdentity(
                packageName = resolvedAggregatePackageName,
                typeName = resolvedAggregateName,
                moduleRole = "domain",
                source = AGGREGATE_SOURCE,
            )
        )
    }

    (requestNamespace.resolvedTypes + responseNamespace.resolvedTypes)
        .flatMap(::collectExplicitSymbols)
        .forEach(registry::register)

    return registry
}
```

Add these imports near existing design type imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.generator.design.types.AGGREGATE_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.design.types.EXPLICIT_FQCN_SOURCE
```

In `collectExplicitSymbols`, replace the literal source with the constant:

```kotlin
source = EXPLICIT_FQCN_SOURCE,
```

- [ ] **Step 5: Pass aggregate context through validation and import planning**

In `createRenderModel`, update both validation calls:

```kotlin
validateNamespaceTypes(
    "request",
    requestNamespace,
    symbolRegistry,
    requestNamespace.nestedTypeNames,
    aggregateContext,
    siblingRequestTypeNames,
)
validateNamespaceTypes(
    "response",
    responseNamespace,
    symbolRegistry,
    responseNamespace.nestedTypeNames,
    aggregateContext,
    siblingRequestTypeNames,
)
```

Update both import planning calls:

```kotlin
val requestImportPlan = DesignImportPlanner.plan(
    types = requestNamespace.resolvedTypes,
    innerTypeNames = requestNamespace.nestedTypeNames,
    symbolRegistry = symbolRegistry,
    aggregateContext = aggregateContext,
)
val responseImportPlan = DesignImportPlanner.plan(
    types = responseNamespace.resolvedTypes,
    innerTypeNames = responseNamespace.nestedTypeNames,
    symbolRegistry = symbolRegistry,
    aggregateContext = aggregateContext,
)
```

Change validation helper signatures:

```kotlin
private fun validateNamespaceTypes(
    namespace: String,
    model: NamespaceModel,
    symbolRegistry: DesignSymbolRegistry,
    innerTypeNames: Set<String>,
    aggregateContext: List<String>,
    siblingRequestTypeNames: Set<String> = emptySet(),
)
```

```kotlin
private fun validateFieldType(
    field: PreparedFieldModel,
    symbolRegistry: DesignSymbolRegistry,
    innerTypeNames: Set<String>,
    aggregateContext: List<String>,
    siblingRequestTypeNames: Set<String>,
)
```

Inside `validateFieldType`, update the resolver call:

```kotlin
ImportResolver.resolve(
    type = field.resolvedType,
    innerTypeNames = innerTypeNames,
    symbolRegistry = symbolRegistry,
    aggregateContext = aggregateContext,
)
```

- [ ] **Step 6: Update planner call sites**

For each planner file listed in this task, replace `typeRegistry = config.designTypeRegistryFqns(model)` with `symbolRegistry = config.designSymbolRegistry(model)`.

In `DesignCommandArtifactPlanner.kt`, the factory call should be:

```kotlin
context = DesignPayloadRenderModelFactory.createForCommandBlock(
    packageName = packageName,
    block = block,
    symbolRegistry = config.designSymbolRegistry(model),
    siblingTypeNames = siblingTypeNames,
).toContextMap() + mapOf("buildingBlock" to block.buildingBlockContext(id)),
```

In `DesignSagaRenderModels.kt`, replace the factory signature and call with:

```kotlin
fun create(
    packageName: String,
    block: DesignBlockModel,
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
): DesignSagaRenderModel {
    val renderModel = DesignPayloadRenderModelFactory.createForSagaBlock(
        packageName = packageName,
        block = block,
        symbolRegistry = symbolRegistry,
    )
```

Add this import to `DesignSagaRenderModels.kt`:

```kotlin
import com.only4.cap4k.plugin.pipeline.generator.design.types.DesignSymbolRegistry
```

In `DesignSagaArtifactPlanner.kt`, the render model call should be:

```kotlin
val renderModel = DesignSagaRenderModelFactory.create(
    packageName = packageName,
    block = block,
    symbolRegistry = config.designSymbolRegistry(model),
)
```

- [ ] **Step 7: Remove map helper usage**

After all call sites are updated, delete the old `designTypeRegistryFqns` function. Run this static search:

```powershell
rg -n "designTypeRegistryFqns|typeRegistry\\s*=" cap4k-plugin-pipeline-generator-design/src/main/kotlin cap4k-plugin-pipeline-generator-design/src/test/kotlin
```

Expected static result:

```text
No designTypeRegistryFqns matches.
Only ProjectConfig constructor arguments named typeRegistry may remain in tests.
```

- [ ] **Step 8: Manual commit checkpoint**

Do not execute these commands unless the user explicitly asks for commits:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design
git commit -m "refactor: thread design symbol registry through payload planners"
```

### Task 4: Implement Aggregate-Aware Short-Name Resolution

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolver.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`

**Interfaces:**
- Consumes: `aggregateContext: List<String>`
- Consumes: `SymbolIdentity.manifestOwned`
- Consumes: `SymbolIdentity.shared`
- Consumes: `SymbolIdentity.ownerAggregateName`
- Produces: local-first selection for single aggregate context.
- Produces: global uniqueness selection for no aggregate context and multiple aggregate context.

- [ ] **Step 1: Use source constants in `ImportResolver.kt`**

`ImportResolver.kt` is already in package `com.only4.cap4k.plugin.pipeline.generator.design.types`, so do not add imports for the constants created in `SymbolIdentity.kt`. Use `EXPLICIT_FQCN_SOURCE` and `PROJECT_TYPE_REGISTRY_SOURCE` directly in this file.

- [ ] **Step 2: Update `ImportResolver.plan` signature and body**

Replace the `plan` function with:

```kotlin
fun plan(
    types: List<DesignResolvedTypeModel>,
    innerTypeNames: Set<String> = emptySet(),
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    aggregateContext: List<String> = emptyList(),
): DesignImportPlan {
    val registry = DesignSymbolRegistry(symbolRegistry.allSymbols()).also { merged ->
        types.flatMap(::collectExplicitSymbols).forEach(merged::register)
    }

    val rendered = types.map { render(it, registry, innerTypeNames, aggregateContext) }

    return DesignImportPlan(
        renderedTypes = rendered.map { result ->
            DesignRenderedTypeModel(
                renderedText = result.renderedType,
                qualifiedFallback = result.qualifiedFallback,
            )
        },
        imports = rendered.flatMap { it.imports }.distinct().sorted(),
    )
}
```

- [ ] **Step 3: Update `ImportResolver.resolve` signature and body**

Replace the `resolve` function with:

```kotlin
internal fun resolve(
    type: DesignResolvedTypeModel,
    innerTypeNames: Set<String> = emptySet(),
    symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    aggregateContext: List<String> = emptyList(),
): ImportResolutionResult {
    val registry = DesignSymbolRegistry(symbolRegistry.allSymbols()).also { merged ->
        collectExplicitSymbols(type).forEach(merged::register)
    }
    return render(type, registry, innerTypeNames, aggregateContext)
}
```

- [ ] **Step 4: Replace explicit symbol source literal**

In `collectExplicitSymbols`, replace:

```kotlin
source = "explicit-fqcn",
```

with:

```kotlin
source = EXPLICIT_FQCN_SOURCE,
```

- [ ] **Step 5: Update recursive render signatures**

Replace the private `render` signature with:

```kotlin
private fun render(
    type: DesignResolvedTypeModel,
    symbolRegistry: DesignSymbolRegistry,
    innerTypeNames: Set<String>,
    aggregateContext: List<String>,
): ImportResolutionResult {
    val renderedArguments = type.arguments.map { render(it, symbolRegistry, innerTypeNames, aggregateContext) }
    val base = resolveBase(type, symbolRegistry, innerTypeNames, aggregateContext)
    val withArguments = if (renderedArguments.isEmpty()) {
        base.renderedType
    } else {
        base.renderedType + renderedArguments.joinToString(
            separator = ", ",
            prefix = "<",
            postfix = ">",
        ) { it.renderedType }
    }
    val renderedText = if (type.nullable && !withArguments.endsWith("?")) {
        "$withArguments?"
    } else {
        withArguments
    }

    return ImportResolutionResult(
        renderedType = renderedText,
        imports = buildSet {
            addAll(base.imports)
            renderedArguments.forEach { addAll(it.imports) }
        },
        qualifiedFallback = base.qualifiedFallback || renderedArguments.any { it.qualifiedFallback },
    )
}
```

Replace the private `resolveBase` signature with:

```kotlin
private fun resolveBase(
    type: DesignResolvedTypeModel,
    symbolRegistry: DesignSymbolRegistry,
    innerTypeNames: Set<String>,
    aggregateContext: List<String>,
): ImportResolutionResult
```

- [ ] **Step 6: Update explicit FQCN branch source comparison**

Inside `DesignResolvedTypeKind.EXPLICIT_FQCN`, replace:

```kotlin
val conflictingCandidates = candidates.filterNot { it.source == "project-type-registry" }
```

with:

```kotlin
val conflictingCandidates = candidates.filterNot { it.source == PROJECT_TYPE_REGISTRY_SOURCE }
```

Keep the rest of the explicit-FQCN branch unchanged.

- [ ] **Step 7: Replace unresolved short-name branch**

Inside `DesignResolvedTypeKind.UNRESOLVED`, replace the current `nonRegistryCandidates` and `registryCandidates` block with:

```kotlin
val selectedCandidates = selectShortTypeCandidates(
    candidates = symbolRegistry.findBySimpleName(type.simpleName),
    aggregateContext = aggregateContext,
)

when (selectedCandidates.size) {
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
}
```

- [ ] **Step 8: Add short-name selection helpers**

Add these private helper functions inside `ImportResolver`, above `internal sealed class ShortTypeResolutionFailure`:

```kotlin
private fun selectShortTypeCandidates(
    candidates: List<SymbolIdentity>,
    aggregateContext: List<String>,
): List<SymbolIdentity> {
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
```

This deliberately makes manifest, Strong ID, and explicit registry collisions ambiguous outside the single-aggregate local-first exception. It avoids recreating the old `Map` overwrite behavior.

- [ ] **Step 9: Update unknown diagnostic advisory**

In `DesignPayloadRenderModelFactory.validateFieldType`, replace the unknown advisory text:

```kotlin
"; use a fully qualified name or register it in type-registry.json$siblingAdvisory"
```

with:

```kotlin
"; use a fully qualified name, declare cap4k-owned enum/value-object types in " +
    "types.enumManifest or types.valueObjectManifest, or register external handwritten types in " +
    "types.registryFile$siblingAdvisory"
```

Keep the sibling advisory calculation unchanged.

- [ ] **Step 10: Document GREEN command without running it**

Do not execute this command in the current workspace. Record it in the task notes or final handoff as the user-run GREEN command:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignCommandArtifactPlannerTest" --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeResolverTest" --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignSymbolRegistryTest"
```

Expected GREEN result after Task 4:

```text
The focused design-generator tests pass, including the existing Strong ID and registry tests plus the new manifest enum/value-object resolution tests.
```

- [ ] **Step 11: Manual commit checkpoint**

Do not execute these commands unless the user explicitly asks for commits:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolver.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignImportPlanner.kt
git commit -m "feat: resolve manifest design types by aggregate context"
```

### Task 5: Static Verification And Issue Handoff

**Files:**
- Review: `docs/superpowers/specs/2026-07-18-cap4k-issue-104-manifest-type-resolution-design.md`
- Review: `docs/superpowers/plans/2026-07-18-cap4k-issue-104-manifest-type-resolution-implementation.md`
- Review: all modified `cap4k-plugin-pipeline-generator-design` files.

**Interfaces:**
- Produces: final handoff summary with exact static evidence.
- Produces: user-run test command list.
- Produces: issue #104 lifecycle recommendation.

- [ ] **Step 1: Run static search for forbidden plan markers**

Run this static command from the worktree root:

```powershell
$markerPattern = ('TO' + 'DO') + '|' + ('TB' + 'D') + '|' + ('FIX' + 'ME') + '|' + ('PLACE' + 'HOLDER')
rg -n $markerPattern docs/superpowers/plans/2026-07-18-cap4k-issue-104-manifest-type-resolution-implementation.md
```

Expected output:

```text
No matches.
```

- [ ] **Step 2: Run static code searches**

Run these static commands from the worktree root:

```powershell
rg -n "designTypeRegistryFqns" cap4k-plugin-pipeline-generator-design/src/main/kotlin cap4k-plugin-pipeline-generator-design/src/test/kotlin
rg -n "type-registry\\.json" cap4k-plugin-pipeline-generator-design/src/main/kotlin cap4k-plugin-pipeline-generator-design/src/test/kotlin
rg -n "types.enumManifest|types.valueObjectManifest|types.registryFile" cap4k-plugin-pipeline-generator-design/src/main/kotlin cap4k-plugin-pipeline-generator-design/src/test/kotlin
```

Expected output:

```text
No designTypeRegistryFqns matches.
The old type-registry.json-only diagnostic is absent from production code.
The manifest-aware diagnostic appears in production code and its focused test.
```

- [ ] **Step 3: Run whitespace and diff checks**

Run these static commands from the worktree root:

```powershell
git diff --check
git status --short --branch
git diff --stat
```

Expected output:

```text
git diff --check exits 0.
git status shows only the spec, implementation plan, and issue #104 implementation files changed.
git diff --stat shows changes limited to docs/superpowers and cap4k-plugin-pipeline-generator-design.
```

- [ ] **Step 4: Read changed production files**

Run these read-only commands from the worktree root:

```powershell
Get-Content -Path "cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRegistryBindings.kt" -Raw
Get-Content -Path "cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolver.kt" -Raw
Get-Content -Path "cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt" -Raw
```

Manual checks:

```text
DesignTypeRegistryBindings uses CanonicalEnumCatalog for enum FQNs.
Manifest enum ownerAggregateName comes from SharedEnumDefinition.aggregates.singleOrNull().
Value-object ownerAggregateName comes from ValueObjectModel.ownerAggregate.
ImportResolver only changes the UNRESOLVED short-name branch.
No source scanning appears.
No package guessing appears.
No Pebble template code owns type resolution.
```

- [ ] **Step 5: Compare against acceptance criteria**

Check each line manually:

```text
Manifest enum identities from CanonicalModel.sharedEnums are registered.
Manifest value-object identities from CanonicalModel.valueObjects are registered.
Aggregate-owned manifest types are not excluded.
No-context design blocks accept only globally unique short names.
Multi-context design blocks accept only globally unique short names.
Single-context design blocks prefer matching aggregate-owned manifest candidates.
Strong ID resolution still has an existing test.
Explicit type registry resolution has a focused test.
Nested generic manifest enum resolution has a focused test.
Ambiguous short names fail with candidate FQNs.
Unknown short-name diagnostics mention manifest inputs and registry.
The booking-center CustomerRef case is covered by the shared value-object test shape.
Issue #104 remains open until plan, implementation, release, and downstream verification lifecycle steps are complete.
```

- [ ] **Step 6: User-run verification commands**

Do not execute these commands in this workspace. Include them in final handoff:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignCommandArtifactPlannerTest" --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeResolverTest" --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignSymbolRegistryTest"
./gradlew :cap4k-plugin-pipeline-generator-design:test
```

Expected output in a permitted environment:

```text
The focused tests pass first.
The design-generator module test suite passes or reports unrelated pre-existing fixture debt separately.
```

- [ ] **Step 7: Issue lifecycle handoff**

Do not close #104 after implementation. Prepare this lifecycle note for the issue or PR:

```text
Spec written: docs/superpowers/specs/2026-07-18-cap4k-issue-104-manifest-type-resolution-design.md
Plan written: docs/superpowers/plans/2026-07-18-cap4k-issue-104-manifest-type-resolution-implementation.md
Implementation status: pending merge
Release required: yes, if downstream booking-center consumes published cap4k artifacts
Downstream verification required: booking-center cap4kPlan must pass the CustomerRef point without a type-registry workaround
```

- [ ] **Step 8: Manual final commit checkpoint**

Do not execute these commands unless the user explicitly asks for commits:

```powershell
git add docs/superpowers/specs/2026-07-18-cap4k-issue-104-manifest-type-resolution-design.md docs/superpowers/plans/2026-07-18-cap4k-issue-104-manifest-type-resolution-implementation.md cap4k-plugin-pipeline-generator-design
git commit -m "fix: resolve manifest types in design generator"
```

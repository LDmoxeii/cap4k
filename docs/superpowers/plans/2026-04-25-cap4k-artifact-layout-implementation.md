# Cap4k Artifact Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement one artifact-family layout contract for Kotlin generated source packages and project-resource output roots, covering aggregate, design, flow, and drawing-board artifacts.

**Architecture:** Add immutable layout config and a shared resolver in `cap4k-plugin-pipeline-api` so core, Gradle, and all generator modules use the same placement rules. Gradle DSL owns user-facing defaults; canonical assembly and planners consume resolved package/output roots instead of hardcoded paths or generator-specific `outputDir` options.

**Tech Stack:** Kotlin 2.x, Gradle plugin DSL, JUnit 5, Gradle TestKit, existing cap4k pipeline modules.

---

## Spec Reference

Implement the approved design in:

- `docs/superpowers/specs/2026-04-25-cap4k-artifact-layout-design.md`

Hard constraints:

- `layout` owns generated artifact locations only.
- `sources.irAnalysis.inputDirs` stays under `sources`.
- `flow` and `drawingBoard` stay under `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`.
- Do not support per-table, per-entity, or per-file placement.
- Do not add module override.
- Do not leave generator-specific location defaults outside layout.

## File Map

### API Layout Contract

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt`

### Gradle DSL And Config Factory

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

### Canonical Aggregate Assembly

- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

### Aggregate Generators

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/*.kt`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

### Design Generators

- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/*.kt`
- Test: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/*Test.kt`

### Analysis Generators

- Modify: `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- Test: `cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt`

### Documentation And Functional Verification

- Modify: `cap4k-plugin-pipeline-gradle/README.md`
- Modify or add functional fixtures under `cap4k-plugin-pipeline-gradle/src/test/resources/functional`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/*FunctionalTest.kt`
- Verify: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmaku-next`

---

## Task 1: API Layout Config And Resolver

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
- Create: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt`

- [ ] **Step 1: Add failing resolver tests for default packages and output roots**

Create `ArtifactLayoutResolverTest.kt` with tests that assert default behavior:

```kotlin
package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ArtifactLayoutResolverTest {

    @Test
    fun `default layout resolves all source package families`() {
        val resolver = ArtifactLayoutResolver(basePackage = "com.acme.demo")

        assertEquals("com.acme.demo.domain.aggregates.user_message", resolver.aggregateEntityPackage("user_message"))
        assertEquals("com.acme.demo.domain._share.meta.user_message", resolver.aggregateSchemaPackage("user_message"))
        assertEquals("com.acme.demo.domain._share.meta", resolver.aggregateSchemaBasePackage())
        assertEquals("com.acme.demo.adapter.domain.repositories", resolver.aggregateRepositoryPackage())
        assertEquals("com.acme.demo.domain.shared.enums", resolver.aggregateSharedEnumPackage(""))
        assertEquals("com.acme.demo.domain.quality.enums", resolver.aggregateSharedEnumPackage("quality"))
        assertEquals("com.acme.demo.domain.translation.shared", resolver.aggregateEnumTranslationPackage("shared"))
        assertEquals("com.acme.demo.application.queries.user_message.unique", resolver.aggregateUniqueQueryPackage("user_message"))
        assertEquals("com.acme.demo.adapter.queries.user_message.unique", resolver.aggregateUniqueQueryHandlerPackage("user_message"))
        assertEquals("com.acme.demo.application.validators.user_message.unique", resolver.aggregateUniqueValidatorPackage("user_message"))
    }

    @Test
    fun `default layout resolves all design package families`() {
        val resolver = ArtifactLayoutResolver(basePackage = "com.acme.demo")

        assertEquals("com.acme.demo.application.commands.message.create", resolver.designCommandPackage("message.create"))
        assertEquals("com.acme.demo.application.queries.message.read", resolver.designQueryPackage("message.read"))
        assertEquals("com.acme.demo.application.distributed.clients.message.delivery", resolver.designClientPackage("message.delivery"))
        assertEquals("com.acme.demo.adapter.queries.message.read", resolver.designQueryHandlerPackage("message.read"))
        assertEquals("com.acme.demo.adapter.application.distributed.clients.message.delivery", resolver.designClientHandlerPackage("message.delivery"))
        assertEquals("com.acme.demo.application.validators.message", resolver.designValidatorPackage("message"))
        assertEquals("com.acme.demo.adapter.portal.api.payload.message", resolver.designApiPayloadPackage("message"))
        assertEquals("com.acme.demo.domain.aggregates.message.events", resolver.designDomainEventPackage("message"))
        assertEquals("com.acme.demo.application.message.events", resolver.designDomainEventHandlerPackage("message"))
    }

    @Test
    fun `default layout resolves analysis output roots`() {
        val resolver = ArtifactLayoutResolver(basePackage = "com.acme.demo")

        assertEquals("flows", resolver.flowOutputRoot())
        assertEquals("design", resolver.drawingBoardOutputRoot())
    }
}
```

- [ ] **Step 2: Run API tests and verify the new tests fail**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-api:test --tests "*ArtifactLayoutResolverTest"
```

Expected: compilation fails because `ArtifactLayoutResolver` does not exist.

- [ ] **Step 3: Add immutable layout config models to `ProjectConfig.kt`**

Add these data classes and add `artifactLayout` to `ProjectConfig` with a default value:

```kotlin
data class ProjectConfig(
    val basePackage: String,
    val layout: ProjectLayout,
    val modules: Map<String, String>,
    val typeRegistry: Map<String, String> = emptyMap(),
    val sources: Map<String, SourceConfig>,
    val generators: Map<String, GeneratorConfig>,
    val templates: TemplateConfig,
    val artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
)

data class ArtifactLayoutConfig(
    val aggregate: PackageLayout = PackageLayout("domain.aggregates"),
    val aggregateSchema: PackageLayout = PackageLayout("domain._share.meta"),
    val aggregateRepository: PackageLayout = PackageLayout("adapter.domain.repositories"),
    val aggregateSharedEnum: PackageLayout = PackageLayout(
        packageRoot = "domain",
        defaultPackage = "shared",
        packageSuffix = "enums",
    ),
    val aggregateEnumTranslation: PackageLayout = PackageLayout("domain.translation"),
    val aggregateUniqueQuery: PackageLayout = PackageLayout(
        packageRoot = "application.queries",
        packageSuffix = "unique",
    ),
    val aggregateUniqueQueryHandler: PackageLayout = PackageLayout(
        packageRoot = "adapter.queries",
        packageSuffix = "unique",
    ),
    val aggregateUniqueValidator: PackageLayout = PackageLayout(
        packageRoot = "application.validators",
        packageSuffix = "unique",
    ),
    val flow: OutputRootLayout = OutputRootLayout("flows"),
    val drawingBoard: OutputRootLayout = OutputRootLayout("design"),
    val designCommand: PackageLayout = PackageLayout("application.commands"),
    val designQuery: PackageLayout = PackageLayout("application.queries"),
    val designClient: PackageLayout = PackageLayout("application.distributed.clients"),
    val designQueryHandler: PackageLayout = PackageLayout("adapter.queries"),
    val designClientHandler: PackageLayout = PackageLayout("adapter.application.distributed.clients"),
    val designValidator: PackageLayout = PackageLayout("application.validators"),
    val designApiPayload: PackageLayout = PackageLayout("adapter.portal.api.payload"),
    val designDomainEvent: PackageLayout = PackageLayout(
        packageRoot = "domain.aggregates",
        packageSuffix = "events",
    ),
    val designDomainEventHandler: PackageLayout = PackageLayout(
        packageRoot = "application",
        packageSuffix = "events",
    ),
)

data class PackageLayout(
    val packageRoot: String,
    val packageSuffix: String = "",
    val defaultPackage: String = "",
)

data class OutputRootLayout(
    val outputRoot: String,
)
```

Keep `artifactLayout` as the final constructor argument with a default value so existing positional `ProjectConfig(...)` call sites continue compiling.

- [ ] **Step 4: Implement `ArtifactLayoutResolver.kt`**

Create the resolver in the API module because all planner modules depend on API and must not depend on core:

```kotlin
package com.only4.cap4k.plugin.pipeline.api

import java.nio.file.InvalidPathException
import java.nio.file.Path

class ArtifactLayoutResolver(
    private val basePackage: String,
    private val layout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
) {
    fun aggregateEntityPackage(tableSegment: String): String =
        packageName(layout.aggregate, semanticPackage = tableSegment)

    fun aggregateSchemaPackage(tableSegment: String): String =
        packageName(layout.aggregateSchema, semanticPackage = tableSegment)

    fun aggregateSchemaBasePackage(): String =
        packageName(layout.aggregateSchema)

    fun aggregateRepositoryPackage(): String =
        packageName(layout.aggregateRepository)

    fun aggregateSharedEnumPackage(enumPackage: String): String =
        packageName(layout.aggregateSharedEnum, semanticPackage = enumPackage)

    fun aggregateEnumTranslationPackage(scope: String): String =
        packageName(layout.aggregateEnumTranslation, semanticPackage = scope)

    fun aggregateUniqueQueryPackage(tableSegment: String): String =
        packageName(layout.aggregateUniqueQuery, semanticPackage = tableSegment)

    fun aggregateUniqueQueryHandlerPackage(tableSegment: String): String =
        packageName(layout.aggregateUniqueQueryHandler, semanticPackage = tableSegment)

    fun aggregateUniqueValidatorPackage(tableSegment: String): String =
        packageName(layout.aggregateUniqueValidator, semanticPackage = tableSegment)

    fun aggregateWrapperPackage(entityPackage: String): String = entityPackage

    fun aggregateFactoryPackage(entityPackage: String): String =
        joinPackage(entityPackage, "factory")

    fun aggregateSpecificationPackage(entityPackage: String): String =
        joinPackage(entityPackage, "specification")

    fun aggregateLocalEnumPackage(entityPackage: String): String =
        joinPackage(entityPackage, "enums")

    fun designCommandPackage(designPackage: String): String =
        packageName(layout.designCommand, semanticPackage = designPackage)

    fun designQueryPackage(designPackage: String): String =
        packageName(layout.designQuery, semanticPackage = designPackage)

    fun designClientPackage(designPackage: String): String =
        packageName(layout.designClient, semanticPackage = designPackage)

    fun designQueryHandlerPackage(designPackage: String): String =
        packageName(layout.designQueryHandler, semanticPackage = designPackage)

    fun designClientHandlerPackage(designPackage: String): String =
        packageName(layout.designClientHandler, semanticPackage = designPackage)

    fun designValidatorPackage(designPackage: String): String =
        packageName(layout.designValidator, semanticPackage = designPackage)

    fun designApiPayloadPackage(designPackage: String): String =
        packageName(layout.designApiPayload, semanticPackage = designPackage)

    fun designDomainEventPackage(designPackage: String): String =
        packageName(layout.designDomainEvent, semanticPackage = designPackage)

    fun designDomainEventHandlerPackage(designPackage: String): String =
        packageName(layout.designDomainEventHandler, semanticPackage = designPackage)

    fun flowOutputRoot(): String = outputRoot(layout.flow, "flow")

    fun drawingBoardOutputRoot(): String = outputRoot(layout.drawingBoard, "drawing-board")

    fun kotlinSourcePath(moduleRoot: String, packageName: String, typeName: String): String =
        joinPath(moduleRoot, "src/main/kotlin", packageName.replace('.', '/'), "$typeName.kt")

    fun projectResourcePath(outputRoot: String, relativeFileName: String): String =
        joinPath(outputRoot, relativeFileName)

    private fun packageName(packageLayout: PackageLayout, semanticPackage: String = ""): String {
        val semantic = semanticPackage.trim().ifBlank { packageLayout.defaultPackage }
        return joinPackage(basePackage, packageLayout.packageRoot, semantic, packageLayout.packageSuffix)
    }

    private fun outputRoot(outputLayout: OutputRootLayout, familyName: String): String =
        normalizeOutputRoot(outputLayout.outputRoot, familyName)

    companion object {
        fun joinPackage(vararg parts: String): String =
            parts.map { it.trim().trim('.') }
                .filter { it.isNotBlank() }
                .joinToString(".")

        fun joinPath(vararg parts: String): String =
            parts.map { it.trim().trim('/', '\\') }
                .filter { it.isNotBlank() }
                .joinToString("/")

        fun validatePackageFragment(value: String, label: String) {
            val trimmed = value.trim()
            require(!trimmed.contains('/')) { "$label must be a valid relative Kotlin package fragment: $value" }
            require(!trimmed.contains('\\')) { "$label must be a valid relative Kotlin package fragment: $value" }
            require(!trimmed.startsWith(".")) { "$label must be a valid relative Kotlin package fragment: $value" }
            require(!trimmed.endsWith(".")) { "$label must be a valid relative Kotlin package fragment: $value" }
            require(!trimmed.contains("..")) { "$label must be a valid relative Kotlin package fragment: $value" }
            require(!trimmed.contains("*")) { "$label must be a valid relative Kotlin package fragment: $value" }
        }

        fun normalizeOutputRoot(value: String, familyName: String): String {
            val rawValue = value.trim()
            if (rawValue.isBlank()) {
                throw IllegalArgumentException("$familyName outputRoot must be a valid relative filesystem path: $value")
            }
            val path = try {
                Path.of(rawValue)
            } catch (ex: InvalidPathException) {
                throw IllegalArgumentException("$familyName outputRoot must be a valid relative filesystem path: $value", ex)
            }
            if (path.isAbsolute || path.root != null || path.any { it.toString() == ".." }) {
                throw IllegalArgumentException("$familyName outputRoot must be a valid relative filesystem path: $value")
            }
            val normalized = path.normalize()
                .toString()
                .replace('\\', '/')
                .trimEnd('/')
            if (normalized.isBlank() || normalized == ".") {
                throw IllegalArgumentException("$familyName outputRoot must be a valid relative filesystem path: $value")
            }
            return normalized
        }
    }
}
```

- [ ] **Step 5: Add validation tests and implement validation helper**

Extend `ArtifactLayoutResolverTest` with:

```kotlin
@Test
fun `rejects invalid package fragments`() {
    val invalid = listOf(
        "domain/aggregates",
        "domain\\aggregates",
        ".domain",
        "domain.",
        "domain..aggregates",
        "domain.*",
    )

    invalid.forEach { value ->
        val error = assertThrows(IllegalArgumentException::class.java) {
            ArtifactLayoutResolver.validatePackageFragment(value, "layout.aggregate.packageRoot")
        }
        assertEquals(
            "layout.aggregate.packageRoot must be a valid relative Kotlin package fragment: $value",
            error.message,
        )
    }
}

@Test
fun `rejects invalid output roots`() {
    val absolutePath = java.nio.file.Path.of("flows").toAbsolutePath().toString()
    val invalid = listOf("", " ", "../flows", "flows/..", absolutePath)

    invalid.forEach { value ->
        val error = assertThrows(IllegalArgumentException::class.java) {
            ArtifactLayoutResolver.normalizeOutputRoot(value, "flow")
        }
        assertEquals(
            "flow outputRoot must be a valid relative filesystem path: $value",
            error.message,
        )
    }
}
```

If Step 4 already includes the helper, this test should pass after Step 5.

- [ ] **Step 6: Update `ProjectConfigTest` for default artifact layout**

Add assertions to the existing `enabled ids and template conflict policy are exposed` test:

```kotlin
assertEquals("domain.aggregates", config.artifactLayout.aggregate.packageRoot)
assertEquals("flows", config.artifactLayout.flow.outputRoot)
assertEquals("design", config.artifactLayout.drawingBoard.outputRoot)
```

- [ ] **Step 7: Run API module tests**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-api:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit API contract**

Commit only API changes:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt `
        cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolverTest.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt
git commit -m "feat: add artifact layout resolver"
```

## Task 2: Gradle DSL Layout Block And Config Factory

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing Gradle DSL tests for default and custom layout values**

Add tests to `Cap4kProjectConfigFactoryTest.kt`:

```kotlin
@Test
fun `nested cap4k extension exposes artifact layout defaults`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    assertEquals("domain.aggregates", extension.layout.aggregate.packageRoot.get())
    assertEquals("domain._share.meta", extension.layout.aggregateSchema.packageRoot.get())
    assertEquals("adapter.domain.repositories", extension.layout.aggregateRepository.packageRoot.get())
    assertEquals("flows", extension.layout.flow.outputRoot.get())
    assertEquals("design", extension.layout.drawingBoard.outputRoot.get())
    assertEquals("domain.aggregates", extension.layout.designDomainEvent.packageRoot.get())
    assertEquals("events", extension.layout.designDomainEvent.packageSuffix.get())
}

@Test
fun `factory copies custom artifact layout into project config`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
    }
    extension.layout {
        aggregate { packageRoot.set("domain.model") }
        aggregateSchema { packageRoot.set("domain.meta") }
        aggregateRepository { packageRoot.set("adapter.persistence.repositories") }
        flow { outputRoot.set("build/cap4k/flows") }
        drawingBoard { outputRoot.set("build/cap4k/design") }
        designDomainEvent {
            packageRoot.set("domain.model")
            packageSuffix.set("events")
        }
    }

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertEquals("domain.model", config.artifactLayout.aggregate.packageRoot)
    assertEquals("domain.meta", config.artifactLayout.aggregateSchema.packageRoot)
    assertEquals("adapter.persistence.repositories", config.artifactLayout.aggregateRepository.packageRoot)
    assertEquals("build/cap4k/flows", config.artifactLayout.flow.outputRoot)
    assertEquals("build/cap4k/design", config.artifactLayout.drawingBoard.outputRoot)
    assertEquals("domain.model", config.artifactLayout.designDomainEvent.packageRoot)
    assertEquals("events", config.artifactLayout.designDomainEvent.packageSuffix)
}
```

- [ ] **Step 2: Run Gradle config tests and verify they fail**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest"
```

Expected: compilation fails because `Cap4kExtension.layout` does not exist.

- [ ] **Step 3: Add `layout` extension classes**

In `Cap4kExtension.kt`, add imports for the API layout models:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.OutputRootLayout
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
```

Add a top-level property and block:

```kotlin
val layout: Cap4kLayoutExtension = objects.newInstance(Cap4kLayoutExtension::class.java)

fun layout(block: Cap4kLayoutExtension.() -> Unit) {
    layout.block()
}
```

Add focused extension classes:

```kotlin
open class Cap4kLayoutExtension @Inject constructor(objects: ObjectFactory) {
    val aggregate: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("domain.aggregates")
    val aggregateSchema: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("domain._share.meta")
    val aggregateRepository: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("adapter.domain.repositories")
    val aggregateSharedEnum: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "domain", defaultPackage = "shared", packageSuffix = "enums")
    val aggregateEnumTranslation: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("domain.translation")
    val aggregateUniqueQuery: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "application.queries", packageSuffix = "unique")
    val aggregateUniqueQueryHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "adapter.queries", packageSuffix = "unique")
    val aggregateUniqueValidator: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "application.validators", packageSuffix = "unique")
    val flow: OutputRootLayoutExtension = objects.newInstance(OutputRootLayoutExtension::class.java).convention("flows")
    val drawingBoard: OutputRootLayoutExtension = objects.newInstance(OutputRootLayoutExtension::class.java).convention("design")
    val designCommand: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("application.commands")
    val designQuery: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("application.queries")
    val designClient: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("application.distributed.clients")
    val designQueryHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("adapter.queries")
    val designClientHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("adapter.application.distributed.clients")
    val designValidator: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("application.validators")
    val designApiPayload: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java).convention("adapter.portal.api.payload")
    val designDomainEvent: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "domain.aggregates", packageSuffix = "events")
    val designDomainEventHandler: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
        .convention(packageRoot = "application", packageSuffix = "events")

    fun aggregate(block: PackageLayoutExtension.() -> Unit) = aggregate.block()
    fun aggregateSchema(block: PackageLayoutExtension.() -> Unit) = aggregateSchema.block()
    fun aggregateRepository(block: PackageLayoutExtension.() -> Unit) = aggregateRepository.block()
    fun aggregateSharedEnum(block: PackageLayoutExtension.() -> Unit) = aggregateSharedEnum.block()
    fun aggregateEnumTranslation(block: PackageLayoutExtension.() -> Unit) = aggregateEnumTranslation.block()
    fun aggregateUniqueQuery(block: PackageLayoutExtension.() -> Unit) = aggregateUniqueQuery.block()
    fun aggregateUniqueQueryHandler(block: PackageLayoutExtension.() -> Unit) = aggregateUniqueQueryHandler.block()
    fun aggregateUniqueValidator(block: PackageLayoutExtension.() -> Unit) = aggregateUniqueValidator.block()
    fun flow(block: OutputRootLayoutExtension.() -> Unit) = flow.block()
    fun drawingBoard(block: OutputRootLayoutExtension.() -> Unit) = drawingBoard.block()
    fun designCommand(block: PackageLayoutExtension.() -> Unit) = designCommand.block()
    fun designQuery(block: PackageLayoutExtension.() -> Unit) = designQuery.block()
    fun designClient(block: PackageLayoutExtension.() -> Unit) = designClient.block()
    fun designQueryHandler(block: PackageLayoutExtension.() -> Unit) = designQueryHandler.block()
    fun designClientHandler(block: PackageLayoutExtension.() -> Unit) = designClientHandler.block()
    fun designValidator(block: PackageLayoutExtension.() -> Unit) = designValidator.block()
    fun designApiPayload(block: PackageLayoutExtension.() -> Unit) = designApiPayload.block()
    fun designDomainEvent(block: PackageLayoutExtension.() -> Unit) = designDomainEvent.block()
    fun designDomainEventHandler(block: PackageLayoutExtension.() -> Unit) = designDomainEventHandler.block()
}

open class PackageLayoutExtension @Inject constructor(objects: ObjectFactory) {
    val packageRoot: Property<String> = objects.property(String::class.java)
    val packageSuffix: Property<String> = objects.property(String::class.java).convention("")
    val defaultPackage: Property<String> = objects.property(String::class.java).convention("")

    fun convention(
        packageRoot: String,
        packageSuffix: String = "",
        defaultPackage: String = "",
    ): PackageLayoutExtension {
        this.packageRoot.convention(packageRoot)
        this.packageSuffix.convention(packageSuffix)
        this.defaultPackage.convention(defaultPackage)
        return this
    }
}

open class OutputRootLayoutExtension @Inject constructor(objects: ObjectFactory) {
    val outputRoot: Property<String> = objects.property(String::class.java)

    fun convention(outputRoot: String): OutputRootLayoutExtension {
        this.outputRoot.convention(outputRoot)
        return this
    }
}
```

Remove `outputDir` from `FlowGeneratorExtension` and `DrawingBoardGeneratorExtension` after config factory has moved to `layout`.

- [ ] **Step 4: Build `ArtifactLayoutConfig` in `Cap4kProjectConfigFactory`**

Add imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.OutputRootLayout
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
```

In `build`, compute:

```kotlin
val artifactLayout = buildArtifactLayout(extension)
```

Pass it to `ProjectConfig`:

```kotlin
artifactLayout = artifactLayout,
```

Add helper functions:

```kotlin
private fun buildArtifactLayout(extension: Cap4kExtension): ArtifactLayoutConfig =
    ArtifactLayoutConfig(
        aggregate = extension.layout.aggregate.toPackageLayout("layout.aggregate"),
        aggregateSchema = extension.layout.aggregateSchema.toPackageLayout("layout.aggregateSchema"),
        aggregateRepository = extension.layout.aggregateRepository.toPackageLayout("layout.aggregateRepository"),
        aggregateSharedEnum = extension.layout.aggregateSharedEnum.toPackageLayout("layout.aggregateSharedEnum"),
        aggregateEnumTranslation = extension.layout.aggregateEnumTranslation.toPackageLayout("layout.aggregateEnumTranslation"),
        aggregateUniqueQuery = extension.layout.aggregateUniqueQuery.toPackageLayout("layout.aggregateUniqueQuery"),
        aggregateUniqueQueryHandler = extension.layout.aggregateUniqueQueryHandler.toPackageLayout("layout.aggregateUniqueQueryHandler"),
        aggregateUniqueValidator = extension.layout.aggregateUniqueValidator.toPackageLayout("layout.aggregateUniqueValidator"),
        flow = extension.layout.flow.toOutputRootLayout("layout.flow"),
        drawingBoard = extension.layout.drawingBoard.toOutputRootLayout("layout.drawingBoard"),
        designCommand = extension.layout.designCommand.toPackageLayout("layout.designCommand"),
        designQuery = extension.layout.designQuery.toPackageLayout("layout.designQuery"),
        designClient = extension.layout.designClient.toPackageLayout("layout.designClient"),
        designQueryHandler = extension.layout.designQueryHandler.toPackageLayout("layout.designQueryHandler"),
        designClientHandler = extension.layout.designClientHandler.toPackageLayout("layout.designClientHandler"),
        designValidator = extension.layout.designValidator.toPackageLayout("layout.designValidator"),
        designApiPayload = extension.layout.designApiPayload.toPackageLayout("layout.designApiPayload"),
        designDomainEvent = extension.layout.designDomainEvent.toPackageLayout("layout.designDomainEvent"),
        designDomainEventHandler = extension.layout.designDomainEventHandler.toPackageLayout("layout.designDomainEventHandler"),
    )

private fun PackageLayoutExtension.toPackageLayout(path: String): PackageLayout {
    val packageRoot = packageRoot.normalized()
    val packageSuffix = packageSuffix.normalized()
    val defaultPackage = defaultPackage.normalized()
    ArtifactLayoutResolver.validatePackageFragment(packageRoot, "$path.packageRoot")
    ArtifactLayoutResolver.validatePackageFragment(packageSuffix, "$path.packageSuffix")
    ArtifactLayoutResolver.validatePackageFragment(defaultPackage, "$path.defaultPackage")
    return PackageLayout(
        packageRoot = packageRoot,
        packageSuffix = packageSuffix,
        defaultPackage = defaultPackage,
    )
}

private fun OutputRootLayoutExtension.toOutputRootLayout(path: String): OutputRootLayout =
    OutputRootLayout(
        outputRoot = ArtifactLayoutResolver.normalizeOutputRoot(outputRoot.normalized(), path.substringAfter("layout.")),
    )
```

- [ ] **Step 5: Move flow and drawing-board generator config away from `outputDir`**

In `buildGenerators`, change the flow and drawing-board entries to no location options:

```kotlin
if (states.flowEnabled) {
    put("flow", GeneratorConfig(enabled = true))
}
if (states.drawingBoardEnabled) {
    put("drawing-board", GeneratorConfig(enabled = true))
}
```

Do not move `sources.irAnalysis.inputDirs`; leave `buildSources` unchanged for `ir-analysis`.

- [ ] **Step 6: Add validation tests for invalid DSL values**

Add to `Cap4kProjectConfigFactoryTest.kt`:

```kotlin
@Test
fun `factory rejects invalid artifact layout package roots`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project.basePackage.set("com.acme.demo")
    extension.layout.aggregate.packageRoot.set("domain/aggregates")

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals(
        "layout.aggregate.packageRoot must be a valid relative Kotlin package fragment: domain/aggregates",
        error.message,
    )
}

@Test
fun `factory rejects invalid artifact layout output roots`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.project.basePackage.set("com.acme.demo")
    extension.layout.flow.outputRoot.set("../flows")

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals(
        "flow outputRoot must be a valid relative filesystem path: ../flows",
        error.message,
    )
}
```

- [ ] **Step 7: Run Gradle plugin unit tests**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit Gradle DSL and config factory**

Commit only Gradle DSL/config changes:

```powershell
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: expose artifact layout gradle dsl"
```

## Task 3: Canonical Aggregate Assembly Uses Layout Resolver

**Files:**

- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Add failing canonical assembler test for custom aggregate layout**

In `DefaultCanonicalAssemblerTest.kt`, add a test that assembles one DB table with custom layout and verifies all canonical package names:

```kotlin
@Test
fun `db aggregate canonical packages use artifact layout`() {
    val config = projectConfig(
        artifactLayout = ArtifactLayoutConfig(
            aggregate = PackageLayout("domain.model"),
            aggregateSchema = PackageLayout("domain.meta"),
            aggregateRepository = PackageLayout("adapter.persistence.repositories"),
            aggregateSharedEnum = PackageLayout(
                packageRoot = "domain",
                defaultPackage = "shared",
                packageSuffix = "enums",
            ),
        ),
    )
    val result = DefaultCanonicalAssembler().assemble(
        config,
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "user_message",
                        comment = "user message",
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                        columns = listOf(
                            DbColumnSnapshot(
                                name = "id",
                                dbType = "bigint",
                                kotlinType = "Long",
                                nullable = false,
                                isPrimaryKey = true,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    assertEquals("com.acme.demo.domain.model.user_message", result.model.entities.single().packageName)
    assertEquals("com.acme.demo.domain.meta.user_message", result.model.schemas.single().packageName)
    assertEquals("com.acme.demo.adapter.persistence.repositories", result.model.repositories.single().packageName)
    assertEquals("com.acme.demo.domain.model.user_message", result.model.aggregateRelations.firstOrNull()?.ownerEntityPackageName ?: "com.acme.demo.domain.model.user_message")
}
```

If the existing helper `projectConfig(...)` does not accept `artifactLayout`, update it to:

```kotlin
private fun projectConfig(
    artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
): ProjectConfig =
    ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = emptyMap(),
        sources = emptyMap(),
        generators = emptyMap(),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )
```

- [ ] **Step 2: Run core test and verify it fails**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest"
```

Expected: the new assertions fail because canonical assembly still uses hardcoded package roots.

- [ ] **Step 3: Use resolver in `DefaultCanonicalAssembler`**

Add import:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
```

Create a resolver near the top of `assemble`:

```kotlin
val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
```

Replace DB-derived package construction:

```kotlin
SchemaModel(
    name = schemaName,
    packageName = artifactLayout.aggregateSchemaPackage(segment),
    entityName = entityName,
    comment = table.comment,
    fields = fields,
)
EntityModel(
    name = entityName,
    packageName = artifactLayout.aggregateEntityPackage(segment),
    ...
)
RepositoryModel(
    name = repositoryName,
    packageName = artifactLayout.aggregateRepositoryPackage(),
    entityName = entityName,
    idType = idField.type,
)
```

- [ ] **Step 4: Update aggregate relation inference to use resolver**

Change the function signature:

```kotlin
fun fromTables(
    artifactLayout: ArtifactLayoutResolver,
    tables: List<DbTableSnapshot>,
    skippedTableNames: Set<String> = emptySet(),
    outOfScopeTableNames: Set<String> = emptySet(),
): List<AggregateRelationModel>
```

Replace endpoint package construction:

```kotlin
packageName = artifactLayout.aggregateEntityPackage(AggregateNaming.tableSegment(table.tableName))
```

Update the call site in `DefaultCanonicalAssembler`:

```kotlin
val aggregateRelations = AggregateRelationInference.fromTables(
    artifactLayout = artifactLayout,
    tables = supportedTables,
    skippedTableNames = skippedTableNames,
    outOfScopeTableNames = outOfScopeTableNames,
)
```

- [ ] **Step 5: Update shared enum FQN inference in `AggregateJpaControlInference`**

Change `fromModel` signature:

```kotlin
fun fromModel(
    entities: List<EntityModel>,
    schema: DbSchemaSnapshot?,
    sharedEnums: List<SharedEnumDefinition>,
    artifactLayout: ArtifactLayoutResolver,
): List<AggregateEntityJpaModel>
```

Change shared enum package resolution:

```kotlin
private fun buildSharedEnumFqns(
    definitions: List<SharedEnumDefinition>,
    artifactLayout: ArtifactLayoutResolver,
): Map<String, String> =
    definitions.associate { definition ->
        val packageName = resolveSharedEnumPackageName(definition.packageName, artifactLayout)
        val fqn = if (packageName.isBlank()) {
            definition.typeName
        } else {
            "$packageName.${definition.typeName}"
        }
        definition.typeName to fqn
    }

private fun resolveSharedEnumPackageName(
    packageName: String,
    artifactLayout: ArtifactLayoutResolver,
): String {
    val trimmed = packageName.trim()
    if ('.' in trimmed) {
        return trimmed
    }
    return artifactLayout.aggregateSharedEnumPackage(trimmed)
}
```

Update the call site in `DefaultCanonicalAssembler` to pass `artifactLayout`.

- [ ] **Step 6: Run core tests**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-core:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit canonical layout routing**

Commit only core changes:

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: route aggregate canonical packages through layout"
```

## Task 4: Aggregate Planners Use Layout Resolver And Path Helpers

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaBaseArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SpecificationArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateWrapperArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SharedEnumArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/LocalEnumArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EnumTranslationArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueValidatorArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing aggregate planner test for custom layout**

Add a test to `AggregateArtifactPlannerTest.kt` that builds a canonical model whose entity/schema/repository packages already came from custom canonical layout, then verifies planner output paths and attached packages:

```kotlin
@Test
fun `aggregate planner resolves output paths from custom artifact layout`() {
    val entity = EntityModel(
        name = "UserMessage",
        packageName = "com.acme.demo.domain.model.user_message",
        tableName = "user_message",
        comment = "user message",
        fields = listOf(FieldModel("id", "Long", columnName = "id")),
        idField = FieldModel("id", "Long", columnName = "id"),
        uniqueConstraints = listOf(listOf("id")),
    )
    val schema = SchemaModel(
        name = "SUserMessage",
        packageName = "com.acme.demo.domain.meta.user_message",
        entityName = "UserMessage",
        comment = "user message",
        fields = entity.fields,
    )
    val repository = RepositoryModel(
        name = "UserMessageRepository",
        packageName = "com.acme.demo.adapter.persistence.repositories",
        entityName = "UserMessage",
        idType = "Long",
    )
    val config = aggregateConfig(
        artifactLayout = ArtifactLayoutConfig(
            aggregate = PackageLayout("domain.model"),
            aggregateSchema = PackageLayout("domain.meta"),
            aggregateRepository = PackageLayout("adapter.persistence.repositories"),
            aggregateUniqueQuery = PackageLayout("application.readmodels", packageSuffix = "unique"),
            aggregateUniqueQueryHandler = PackageLayout("adapter.readmodels", packageSuffix = "unique"),
            aggregateUniqueValidator = PackageLayout("application.rules", packageSuffix = "unique"),
        ),
    )

    val plan = AggregateArtifactPlanner().plan(
        config,
        CanonicalModel(
            entities = listOf(entity),
            schemas = listOf(schema),
            repositories = listOf(repository),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
        ),
    )

    assertEquals(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/meta/Schema.kt",
        plan.single { it.templateId == "aggregate/schema_base.kt.peb" }.outputPath,
    )
    assertEquals(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/model/user_message/UserMessage.kt",
        plan.single { it.templateId == "aggregate/entity.kt.peb" }.outputPath,
    )
    assertEquals(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/model/user_message/factory/UserMessageFactory.kt",
        plan.single { it.templateId == "aggregate/factory.kt.peb" }.outputPath,
    )
    assertEquals(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/model/user_message/specification/UserMessageSpecification.kt",
        plan.single { it.templateId == "aggregate/specification.kt.peb" }.outputPath,
    )
    assertEquals(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/persistence/repositories/UserMessageRepository.kt",
        plan.single { it.templateId == "aggregate/repository.kt.peb" }.outputPath,
    )
    assertEquals(
        "com.acme.demo.domain.meta",
        plan.single { it.templateId == "aggregate/schema.kt.peb" }.context["schemaBasePackage"],
    )
}
```

Update `aggregateConfig(...)` helper to accept `artifactLayout`:

```kotlin
private fun aggregateConfig(
    artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
): ProjectConfig =
    ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = mapOf(
            "domain" to "demo-domain",
            "application" to "demo-application",
            "adapter" to "demo-adapter",
        ),
        sources = emptyMap(),
        generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )
```

- [ ] **Step 2: Run aggregate planner test and verify it fails**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest"
```

Expected: custom layout assertions fail because planners still hardcode schema base, unique paths, shared enum roots, or raw output path concatenation.

- [ ] **Step 3: Add resolver helper usage to aggregate planners**

In each aggregate planner, create:

```kotlin
val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
```

Use the shared helper for Kotlin paths:

```kotlin
outputPath = artifactLayout.kotlinSourcePath(domainRoot, packageName, typeName)
```

For entity/repository/schema where canonical models already carry final package names, keep context package from the model and only centralize output path:

```kotlin
outputPath = artifactLayout.kotlinSourcePath(domainRoot, entity.packageName, entity.name)
```

- [ ] **Step 4: Route schema base through layout**

In `SchemaBaseArtifactPlanner.kt`, replace:

```kotlin
val packageName = "${config.basePackage}.domain._share.meta"
```

with:

```kotlin
val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
val packageName = artifactLayout.aggregateSchemaBasePackage()
```

Set:

```kotlin
outputPath = artifactLayout.kotlinSourcePath(domainRoot, packageName, "Schema")
```

In `SchemaArtifactPlanner.kt`, set:

```kotlin
"schemaBasePackage" to artifactLayout.aggregateSchemaBasePackage()
```

- [ ] **Step 5: Route entity-attached families through resolver**

Use these package helpers:

```kotlin
val wrapperPackage = artifactLayout.aggregateWrapperPackage(entity.packageName)
val factoryPackage = artifactLayout.aggregateFactoryPackage(entity.packageName)
val specificationPackage = artifactLayout.aggregateSpecificationPackage(entity.packageName)
```

Expected output path usage:

```kotlin
artifactLayout.kotlinSourcePath(domainRoot, factoryPackage, "${entity.name}Factory")
artifactLayout.kotlinSourcePath(domainRoot, specificationPackage, "${entity.name}Specification")
artifactLayout.kotlinSourcePath(domainRoot, wrapperPackage, "Agg${entity.name}")
```

Update contexts to use the same packages:

```kotlin
"packageName" to factoryPackage
"packageName" to specificationPackage
"packageName" to wrapperPackage
```

- [ ] **Step 6: Route aggregate enum planning through resolver**

Change `AggregateEnumPlanning.from` signature:

```kotlin
fun from(
    model: CanonicalModel,
    artifactLayout: ArtifactLayoutResolver,
    typeRegistry: Map<String, String>,
): AggregateEnumPlanning
```

Resolve shared enum packages with:

```kotlin
private fun resolveSharedEnumPackageName(
    packageName: String,
    artifactLayout: ArtifactLayoutResolver,
): String {
    val trimmed = packageName.trim()
    if ('.' in trimmed) {
        return trimmed
    }
    return artifactLayout.aggregateSharedEnumPackage(trimmed)
}
```

Resolve local enum FQNs with:

```kotlin
private fun buildLocalEnumFqn(
    artifactLayout: ArtifactLayoutResolver,
    entity: EntityModel,
    typeName: String,
): String =
    "${artifactLayout.aggregateLocalEnumPackage(entity.packageName)}.$typeName"
```

Update callers in `EntityArtifactPlanner`, `SchemaArtifactPlanner`, `SharedEnumArtifactPlanner`, `LocalEnumArtifactPlanner`, and `EnumTranslationArtifactPlanner`.

- [ ] **Step 7: Route unique query, handler, and validator through layout**

In unique planners, replace hardcoded package and output path construction with:

```kotlin
val packageName = artifactLayout.aggregateUniqueQueryPackage(tableSegment)
outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, selection.queryTypeName)
```

```kotlin
val packageName = artifactLayout.aggregateUniqueQueryHandlerPackage(tableSegment)
outputPath = artifactLayout.kotlinSourcePath(adapterRoot, packageName, selection.queryHandlerTypeName)
```

```kotlin
val packageName = artifactLayout.aggregateUniqueValidatorPackage(tableSegment)
outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, selection.validatorTypeName)
```

Set context `"packageName"` from the same local variable.

- [ ] **Step 8: Route enum translation without parsing `.domain.aggregates.`**

Replace marker-based package parsing in `EnumTranslationArtifactPlanner.kt` with resolver-based scope:

```kotlin
private fun localOwnerScope(entity: EntityModel): String =
    aggregateTableSegment(entity.tableName)

private fun localTranslationPackage(
    artifactLayout: ArtifactLayoutResolver,
    entity: EntityModel,
): String =
    artifactLayout.aggregateEnumTranslationPackage(aggregateTableSegment(entity.tableName))
```

For shared enum translation:

```kotlin
packageName = artifactLayout.aggregateEnumTranslationPackage("shared")
```

This removes assumptions that the aggregate root contains `.domain.aggregates.`.

- [ ] **Step 9: Run aggregate generator tests**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-aggregate:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit aggregate planner migration**

Commit only aggregate generator changes:

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate `
        cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: route aggregate artifacts through layout"
```

## Task 5: Design Planners Use Layout Resolver

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerRenderModels.kt`
- Modify tests under `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design`

- [ ] **Step 1: Add failing domain event route assertion**

Update `DesignDomainEventArtifactPlannerTest.kt` expected default path and package:

```kotlin
assertEquals(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt",
    event.outputPath,
)
assertEquals("com.acme.demo.domain.aggregates.order.events", event.context["packageName"])
```

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test --tests "*DesignDomainEventArtifactPlannerTest"
```

Expected: failure because the current planner still emits `domain/order/events`.

- [ ] **Step 2: Add custom layout test for one design family**

Add this test to `DesignCommandArtifactPlannerTest.kt`:

```kotlin
@Test
fun `command planner uses custom artifact layout package root`() {
    val planner = DesignCommandArtifactPlanner()
    val command = CommandModel(
        packageName = "message.create",
        typeName = "CreateUserMessageCmd",
        description = "create user message",
        aggregateRef = null,
        requestFields = listOf(FieldModel("messageKey", "String")),
        responseFields = emptyList(),
        variant = CommandVariant.DEFAULT,
    )
    val config = projectConfig(
        modules = mapOf("application" to "demo-application"),
        artifactLayout = ArtifactLayoutConfig(
            designCommand = PackageLayout("application.usecases.commands"),
        ),
    )

    val item = planner.plan(config, CanonicalModel(commands = listOf(command))).single()

    assertEquals(
        "demo-application/src/main/kotlin/com/acme/demo/application/usecases/commands/message/create/CreateUserMessageCmd.kt",
        item.outputPath,
    )
    assertEquals("com.acme.demo.application.usecases.commands.message.create", item.context["packageName"])
}
```

Update that test helper to accept `artifactLayout`:

```kotlin
private fun projectConfig(
    modules: Map<String, String>,
    artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
) = ProjectConfig(
    basePackage = "com.acme.demo",
    layout = ProjectLayout.MULTI_MODULE,
    modules = modules,
    sources = emptyMap(),
    generators = mapOf("design-command" to GeneratorConfig(enabled = true)),
    templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    artifactLayout = artifactLayout,
)
```

- [ ] **Step 3: Add custom handler import test**

Update `DesignDomainEventHandlerArtifactPlannerTest.kt` to assert the default event import after the route fix:

```kotlin
assertEquals(
    listOf("com.acme.demo.domain.aggregates.order.events.OrderCreatedDomainEvent"),
    item.context["imports"],
)
assertEquals(
    "com.acme.demo.domain.aggregates.order.events.OrderCreatedDomainEvent",
    item.context["domainEventType"],
)
```

Expected before implementation: failure because render model still imports from `domain.order.events`.

- [ ] **Step 4: Route command/query/client planner packages through resolver**

In each request-family planner:

```kotlin
val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
val packageName = artifactLayout.designCommandPackage(command.packageName)
val outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, command.typeName)
```

Use the appropriate resolver method per planner:

```text
DesignCommandArtifactPlanner      -> designCommandPackage
DesignQueryArtifactPlanner        -> designQueryPackage
DesignClientArtifactPlanner       -> designClientPackage
DesignQueryHandlerArtifactPlanner -> designQueryHandlerPackage
DesignClientHandlerArtifactPlanner -> designClientHandlerPackage
```

Pass the same `packageName` into `DesignPayloadRenderModelFactory` or handler render-model factories.

- [ ] **Step 5: Route validator and API payload planner packages through resolver**

Use:

```kotlin
val packageName = artifactLayout.designValidatorPackage(validator.packageName)
```

and:

```kotlin
val packageName = artifactLayout.designApiPayloadPackage(payload.packageName)
```

Set output paths through:

```kotlin
artifactLayout.kotlinSourcePath(applicationRoot, packageName, validator.typeName)
artifactLayout.kotlinSourcePath(adapterRoot, packageName, payload.typeName)
```

- [ ] **Step 6: Route domain event planner through resolver**

In `DesignDomainEventArtifactPlanner.kt`, use:

```kotlin
val packageName = artifactLayout.designDomainEventPackage(event.packageName)
val renderModel = DesignPayloadRenderModelFactory.createForDomainEvent(
    packageName = packageName,
    event = event,
    typeRegistry = config.typeRegistry,
)
```

Set:

```kotlin
outputPath = artifactLayout.kotlinSourcePath(domainRoot, packageName, event.typeName)
```

This is the specific fix for `only-danmaku-next`:

```text
edu.only4.danmaku.domain.aggregates.message.events.UserMessageCreatedDomainEvent
```

- [ ] **Step 7: Route domain event handler package and event import through resolver**

Change `DesignDomainEventHandlerRenderModelFactory.create` signature:

```kotlin
fun create(
    eventHandlerPackageName: String,
    domainEventType: String,
    event: DomainEventModel,
): DesignDomainEventHandlerRenderModel
```

Create values in `DesignDomainEventHandlerArtifactPlanner.kt`:

```kotlin
val packageName = artifactLayout.designDomainEventHandlerPackage(event.packageName)
val domainEventType = "${artifactLayout.designDomainEventPackage(event.packageName)}.${event.typeName}"
```

Use:

```kotlin
outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, "${event.typeName}Subscriber")
```

And pass the resolved values into the render model factory. Do not reconstruct the event package inside the render-model factory.

- [ ] **Step 8: Update all design tests for default path expectations**

Update tests to assert resolver defaults:

```text
DesignCommandArtifactPlannerTest       -> application/commands/<pkg>
DesignQueryArtifactPlannerTest         -> application/queries/<pkg>
DesignClientArtifactPlannerTest        -> application/distributed/clients/<pkg>
DesignQueryHandlerArtifactPlannerTest  -> adapter/queries/<pkg>
DesignClientHandlerArtifactPlannerTest -> adapter/application/distributed/clients/<pkg>
DesignValidatorArtifactPlannerTest     -> application/validators/<pkg>
DesignApiPayloadArtifactPlannerTest    -> adapter/portal/api/payload/<pkg>
DesignDomainEventArtifactPlannerTest   -> domain/aggregates/<pkg>/events
DesignDomainEventHandlerArtifactPlannerTest -> application/<pkg>/events
```

Do not add separate path-construction logic in tests; assert final output paths.

- [ ] **Step 9: Run design generator tests**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit design planner migration**

Commit only design generator changes:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design `
        cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design
git commit -m "feat: route design artifacts through layout"
```

## Task 6: Flow And Drawing Board Use OutputRoot Layout

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt`

- [ ] **Step 1: Update flow tests to use `artifactLayout.flow.outputRoot`**

In `FlowArtifactPlannerTest.kt`, change helper to:

```kotlin
private fun config(outputRoot: String = "flows"): ProjectConfig =
    ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = emptyMap(),
        sources = emptyMap(),
        generators = mapOf("flow" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = ArtifactLayoutConfig(
            flow = OutputRootLayout(outputRoot),
        ),
    )
```

Rename tests from `output dir` to `output root` and update expected errors:

```kotlin
assertEquals(
    "flow outputRoot must be a valid relative filesystem path: $absolutePath",
    absoluteEx.message,
)
```

Expected before implementation: tests fail because planner still reads `config.generators["flow"].options["outputDir"]`.

- [ ] **Step 2: Update drawing-board tests to use `artifactLayout.drawingBoard.outputRoot`**

In `DrawingBoardArtifactPlannerTest.kt`, change helper to:

```kotlin
private fun config(outputRoot: String = "design"): ProjectConfig =
    ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = emptyMap(),
        sources = emptyMap(),
        generators = mapOf("drawing-board" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = ArtifactLayoutConfig(
            drawingBoard = OutputRootLayout(outputRoot),
        ),
    )
```

Remove `includeOutputDir`; missing output-root behavior is covered by default `ArtifactLayoutConfig`.

Update expected errors:

```kotlin
assertEquals(
    "drawing-board outputRoot must be a valid relative filesystem path: $absolutePath",
    absoluteEx.message,
)
```

- [ ] **Step 3: Remove local output-dir validation from `FlowArtifactPlanner`**

In `FlowArtifactPlanner.kt`, remove:

```kotlin
private fun requireRelativeOutputDir(config: ProjectConfig): String
private fun invalidOutputDir(value: String, cause: Throwable? = null): IllegalArgumentException
```

Remove unused imports:

```kotlin
import java.nio.file.InvalidPathException
import java.nio.file.Path
```

Use resolver:

```kotlin
val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
val outputRoot = artifactLayout.flowOutputRoot()
```

Build paths with:

```kotlin
outputPath = artifactLayout.projectResourcePath(outputRoot, "${flow.slug}.json")
outputPath = artifactLayout.projectResourcePath(outputRoot, "${flow.slug}.mmd")
outputPath = artifactLayout.projectResourcePath(outputRoot, "index.json")
```

- [ ] **Step 4: Remove local output-dir validation from `DrawingBoardArtifactPlanner`**

In `DrawingBoardArtifactPlanner.kt`, remove:

```kotlin
private fun requireRelativeOutputDir(config: ProjectConfig): String
private fun invalidOutputDir(value: String, cause: Throwable? = null): IllegalArgumentException
```

Remove unused imports:

```kotlin
import java.nio.file.InvalidPathException
import java.nio.file.Path
```

Use resolver:

```kotlin
val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
val outputRoot = artifactLayout.drawingBoardOutputRoot()
```

Build paths with:

```kotlin
outputPath = artifactLayout.projectResourcePath(outputRoot, "drawing_board_$tag.json")
```

- [ ] **Step 5: Run analysis generator tests**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-flow:test :cap4k-plugin-pipeline-generator-drawing-board:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit analysis output-root migration**

Commit only analysis generator changes:

```powershell
git add cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt `
        cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt `
        cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt `
        cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt
git commit -m "feat: route analysis artifacts through layout"
```

## Task 7: Gradle Functional Tests And README

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/build.gradle.kts`
- Modify other functional fixtures that use `generators.flow.outputDir` or `generators.drawingBoard.outputDir`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/README.md`

- [ ] **Step 1: Update analysis functional fixtures to use layout output roots**

Change `flow-sample/build.gradle.kts` from:

```kotlin
generators {
    flow {
        enabled.set(true)
        outputDir.set("flows")
    }
}
```

to:

```kotlin
layout {
    flow {
        outputRoot.set("flows")
    }
}
generators {
    flow {
        enabled.set(true)
    }
}
```

Change `drawing-board-sample/build.gradle.kts` from:

```kotlin
generators {
    drawingBoard {
        enabled.set(true)
        outputDir.set("design")
    }
}
```

to:

```kotlin
layout {
    drawingBoard {
        outputRoot.set("design")
    }
}
generators {
    drawingBoard {
        enabled.set(true)
    }
}
```

Search for remaining old usage:

```powershell
rg -n "outputDir\\.set|generators\\.flow\\.outputDir|generators\\.drawingBoard\\.outputDir" cap4k-plugin-pipeline-gradle/src/test/resources cap4k-plugin-pipeline-gradle/src/test/kotlin
```

Expected after fixture updates: no old `outputDir.set(...)` use for flow/drawingBoard remains.

- [ ] **Step 2: Update functional test that edits flow fixture**

In `PipelinePluginFunctionalTest.kt`, update the replacement block in `cap4kPlan and cap4kGenerate ignore flow and drawing board generators`.

Use this replacement target:

```kotlin
"""
layout {
    flow {
        outputRoot.set("flows")
    }
}
generators {
    flow {
        enabled.set(true)
    }
}
""".trimIndent()
```

Use this replacement value:

```kotlin
"""
layout {
    flow {
        outputRoot.set("flows")
    }
    drawingBoard {
        outputRoot.set("design")
    }
}
generators {
    flow {
        enabled.set(true)
    }
    drawingBoard {
        enabled.set(true)
    }
}
""".trimIndent()
```

Keep assertions that `cap4kPlan` and `cap4kGenerate` do not write analysis artifacts.

- [ ] **Step 3: Add functional custom layout test for analysis outputs**

Add a test to `PipelinePluginFunctionalTest.kt`:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kAnalysisPlan and cap4kAnalysisGenerate use layout output roots`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-analysis-layout")
    copyFixture(projectDir, "flow-sample")
    val buildFile = projectDir.resolve("build.gradle.kts")
    buildFile.writeText(
        buildFile.readText()
            .replace("\r\n", "\n")
            .replace("outputRoot.set(\"flows\")", "outputRoot.set(\"build/cap4k/flows\")")
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kAnalysisPlan", "cap4kAnalysisGenerate")
        .build()

    val analysisPlan = projectDir.resolve("build/cap4k/analysis-plan.json").readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(analysisPlan.contains("\"outputPath\": \"build/cap4k/flows/OrderController_submit.json\""))
    assertTrue(projectDir.resolve("build/cap4k/flows/OrderController_submit.json").toFile().exists())
    assertTrue(projectDir.resolve("build/cap4k/flows/OrderController_submit.mmd").toFile().exists())
    assertTrue(projectDir.resolve("build/cap4k/flows/index.json").toFile().exists())
    assertFalse(projectDir.resolve("flows/index.json").toFile().exists())
}
```

- [ ] **Step 4: Add functional custom layout test for Kotlin package roots**

Add a focused domain-event test to `PipelinePluginFunctionalTest.kt` using the existing `design-domain-event-sample` fixture:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate domain event uses artifact layout package root`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-domain-event-layout")
    copyFixture(projectDir, "design-domain-event-sample")
    val buildFile = projectDir.resolve("build.gradle.kts")
    buildFile.writeText(
        buildFile.readText().replace(
            "cap4k {",
            """
            cap4k {
                layout {
                    designDomainEvent {
                        packageRoot.set("domain.model")
                        packageSuffix.set("events")
                    }
                }
            """.trimIndent(),
        )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kPlan", "cap4kGenerate")
        .build()

    val generatedFile = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/model/order/events/OrderCreatedDomainEvent.kt"
    )

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generatedFile.toFile().exists())
    assertTrue(generatedFile.readText().contains("package com.acme.demo.domain.model.order.events"))
}
```

If the fixture's root block shape makes the string replacement unsafe, edit the fixture directly or create a separate `design-domain-event-layout-sample` fixture. Do not use fragile replacement if it changes unrelated DSL.

- [ ] **Step 5: Update README**

In `cap4k-plugin-pipeline-gradle/README.md`, add an `artifact layout` section with this exact content shape:

````markdown
### Artifact Layout

Generated artifact locations are configured under `layout`.

`layout` controls generated output only. Source inputs stay under `sources`; for example `sources.irAnalysis.inputDirs` remains source configuration.

Kotlin source artifacts use package roots:

```kotlin
cap4k {
    layout {
        aggregate {
            packageRoot.set("domain.aggregates")
        }
        designDomainEvent {
            packageRoot.set("domain.aggregates")
            packageSuffix.set("events")
        }
    }
}
```

Project resource artifacts use output roots:

```kotlin
cap4k {
    layout {
        flow {
            outputRoot.set("flows")
        }
        drawingBoard {
            outputRoot.set("design")
        }
    }
}
```

`flow` and `drawingBoard` still run through `cap4kAnalysisPlan` and `cap4kAnalysisGenerate`.
````

Remove or update any README examples that still show:

```kotlin
generators {
    flow {
        outputDir.set("flows")
    }
}
```

- [ ] **Step 6: Run Gradle functional tests touched by layout**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest" --tests "*PipelinePluginFunctionalTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Gradle functional and README updates**

Commit only Gradle fixtures/tests/docs:

```powershell
git add cap4k-plugin-pipeline-gradle/README.md `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "test: verify artifact layout functional flows"
```

## Task 8: Full Verification, Publish, And Downstream Generation Check

**Files:**

- Verify: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k`
- Verify: `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmaku-next`
- Potential generated output: `only-danmaku-next/only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/message/events/UserMessageCreatedDomainEvent.kt`

- [ ] **Step 1: Run full targeted cap4k pipeline test suite**

From `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k`, run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache `
    :cap4k-plugin-pipeline-api:test `
    :cap4k-plugin-pipeline-core:test `
    :cap4k-plugin-pipeline-generator-aggregate:test `
    :cap4k-plugin-pipeline-generator-design:test `
    :cap4k-plugin-pipeline-generator-flow:test `
    :cap4k-plugin-pipeline-generator-drawing-board:test `
    :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL`.

The Gradle plugin test task already has a 25 minute timeout configured in `cap4k-plugin-pipeline-gradle/build.gradle.kts`; do not reduce it in this iteration.

- [ ] **Step 2: Run cap4k build**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Publish cap4k to the configured Maven repository**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache publish
```

Expected: `BUILD SUCCESSFUL`.

Warnings about Gradle plugin marker publications overlapping Maven publications are acceptable only if the build still succeeds. Do not change publication coordinates in this task unless the warning becomes a failure.

- [ ] **Step 4: Prepare only-danmaku-next for clean generation verification**

From `C:/Users/LD_moxeii/Documents/code/only-workspace/only-danmaku-next`, inspect existing generated event files:

```powershell
git status --short
Test-Path 'only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\message\events\UserMessageCreatedDomainEvent.kt'
Test-Path 'only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\message\events\UserMessageCreatedDomainEvent.kt'
```

Expected before cleanup: the command may report either path as present depending on prior generated output.

If the old wrong file exists and `git diff -- only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/message/events/UserMessageCreatedDomainEvent.kt` shows it is generated output, remove exactly that stale file before rerunning generation:

```powershell
Remove-Item -LiteralPath 'only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\message\events\UserMessageCreatedDomainEvent.kt' -Force
```

Do not remove user-authored files outside that exact generated path.

- [ ] **Step 5: Regenerate only-danmaku-next with the published plugin**

Run:

```powershell
.\gradlew.bat --refresh-dependencies --no-configuration-cache --no-build-cache cap4kPlan cap4kGenerate
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify only-danmaku-next generated package layout**

Run:

```powershell
Test-Path 'only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\message\events\UserMessageCreatedDomainEvent.kt'
Test-Path 'only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\message\events\UserMessageCreatedDomainEvent.kt'
Select-String -Path 'only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\message\events\UserMessageCreatedDomainEvent.kt' -Pattern '^package edu\.only4\.danmaku\.domain\.aggregates\.message\.events$'
```

Expected:

```text
True
False
package edu.only4.danmaku.domain.aggregates.message.events
```

If the second `Test-Path` is `True`, the old wrong path is still present and must be explained before claiming the iteration is complete.

- [ ] **Step 7: Verify only-danmaku-next compiles after generation**

Run:

```powershell
.\gradlew.bat --refresh-dependencies --no-configuration-cache --no-build-cache build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Review downstream diff and commit expected generated output**

Run:

```powershell
git status --short
git diff --name-only
```

Expected changed files should be generated output or deliberate fixture/config updates caused by the new layout. Commit only expected downstream changes:

```powershell
git add only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/message/events/UserMessageCreatedDomainEvent.kt
git commit -m "chore: refresh artifact layout generated output"
```

If no downstream files changed after generation, do not create an empty commit.

- [ ] **Step 9: Final cap4k repository status check**

Return to `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k` and run:

```powershell
git status --short
git log --oneline -8
```

Expected: no uncommitted cap4k changes remain. The recent commits should include the API resolver, Gradle DSL, canonical migration, aggregate migration, design migration, analysis migration, functional tests, and README updates.

## Execution Checkpoints

- After Task 1, stop if the API module does not compile. Every later task depends on `ArtifactLayoutConfig`, `PackageLayout`, `OutputRootLayout`, and `ArtifactLayoutResolver`.
- After Task 2, stop if Gradle DSL defaults do not round-trip into `ProjectConfig.artifactLayout`. Generator migrations must not read layout defaults from local constants.
- After Task 3, stop if canonical aggregate packages still contain hardcoded `domain.aggregates`, `domain._share.meta`, or `adapter.domain.repositories` outside resolver-backed defaults.
- After Task 4, stop if aggregate generated paths are assembled manually with `replace('.', '/')` in planners instead of `ArtifactLayoutResolver.kotlinSourcePath`.
- After Task 5, stop if `UserMessageCreatedDomainEvent` would still generate under `domain.message.events`; the new default must be `domain.aggregates.message.events`.
- After Task 6, stop if `flow` or `drawingBoard` still accepts `generators.*.outputDir`; output roots must live under `layout`.
- After Task 7, stop if README or fixtures still document `flow.outputDir` or `drawingBoard.outputDir`.
- After Task 8, stop if `only-danmaku-next` compiles only with stale generated files. The verification must prove the new plugin creates the right path from a clean state.

## Self-Review

Spec coverage:

- The plan implements a single artifact-family layout contract in Task 1 and exposes it through Gradle DSL in Task 2.
- Kotlin source artifact package roots are covered by Tasks 3, 4, and 5.
- Project-resource output roots for analysis artifacts are covered by Task 6.
- `sources.irAnalysis.inputDirs` remains under `sources`; Task 2 and Task 6 explicitly avoid moving input configuration into `layout`.
- `flow` and `drawingBoard` remain outside `cap4kPlan` and `cap4kGenerate`; Task 7 keeps the functional assertion that normal generation ignores analysis artifacts.
- The domain event default correction to `domain.aggregates.<package>.events` is covered by Task 5 and verified in Task 8 against `only-danmaku-next`.
- The plan does not add per-table, per-entity, per-file placement, or module override.

Placeholder scan:

- No unresolved placeholder or deferred implementation step is intentionally left in the plan.
- Steps that change code include concrete Kotlin or PowerShell snippets.
- Steps that verify behavior include exact commands and expected outcomes.

Type consistency:

- `ArtifactLayoutConfig`, `PackageLayout`, and `OutputRootLayout` are defined in Task 1 and reused consistently in Gradle DSL, canonical assembly, planners, and tests.
- Resolver methods use the same naming pattern across aggregate, design, flow, and drawing-board families.
- Analysis input configuration remains `sources.irAnalysis.inputDirs`; generated output configuration becomes `artifactLayout.flow.outputRoot` and `artifactLayout.drawingBoard.outputRoot`.

Risk review:

- Moving defaults into `layout` is not destructive to the framework model because it removes scattered output-location defaults instead of adding a second routing path.
- The only intentional default behavior change is `designDomainEvent`, which fixes the known wrong package for aggregate domain events.
- Removing `generators.flow.outputDir` and `generators.drawingBoard.outputDir` is acceptable because this branch has no external compatibility requirement.
- The plan keeps import rendering, template override behavior, source parsing, and task entrypoints out of scope to prevent this iteration from expanding beyond artifact placement.

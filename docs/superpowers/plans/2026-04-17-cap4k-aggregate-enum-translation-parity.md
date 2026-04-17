# Cap4k Aggregate Enum / Enum-Translation Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded shared-enum and aggregate-local enum parity to the existing `aggregate` generator, including enum-translation generation, deterministic `@T` binding, and same-run shared-enum reference support.

**Architecture:** This slice introduces one new source module for shared enum manifests, extends the DB source just enough to surface `@T` / `@E` metadata, and keeps all enum / enum-translation planning inside the current `aggregate` generator. Binding is resolved before planning through a read-only aggregate enum planning layer, and the final verification uses dedicated functional and compile fixtures, including local translation stubs because the repository does not ship the old translation runtime.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, H2 metadata fixtures, existing aggregate compile harness

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-source-enum-manifest/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-source-enum-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProvider.kt`
- Create: `cap4k-plugin-pipeline-source-enum-manifest/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProviderTest.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationMetadata.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SharedEnumArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/LocalEnumArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EnumTranslationArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanningTest.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/enums/shared-enums.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/enums/shared-enums.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateEnumCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/SharedEnumTranslationCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/video_post/LocalEnumTranslationCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/only/engine/translation/annotation/TranslationType.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/only/engine/translation/core/TranslationInterface.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/only/engine/translation/core/BatchTranslationInterface.kt`

### Existing files to modify

- Modify: `settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

### Responsibilities

- `PipelineModels.kt`
  - introduce shared-enum snapshot/model types and the minimum field metadata needed to carry `@T` / `@E` information without reopening a global mutable type map

- `EnumManifestSourceProvider.kt`
  - ingest shared enum definitions as a bounded source contract before planning

- `DbColumnAnnotationParser.kt`
  - parse old `@T` and `@E` syntax from column comments into deterministic source metadata

- `DbSchemaSourceProvider.kt`
  - surface parsed type-binding and enum-definition metadata on DB columns

- `DefaultCanonicalAssembler.kt`
  - carry shared enum definitions into canonical/enrich-time data and preserve DB field metadata for aggregate planners

- `AggregateEnumPlanning.kt`
  - own resolution order, conflict detection, shared/local ownership separation, translation ownership, and field-type enrichment for aggregate planners

- `SharedEnumArtifactPlanner.kt`
  - emit domain-side shared enum artifacts from manifest-owned definitions

- `LocalEnumArtifactPlanner.kt`
  - emit domain-side aggregate-local enum artifacts only for `@E + @T`

- `EnumTranslationArtifactPlanner.kt`
  - emit adapter-side translation artifacts that follow enum ownership

- `EntityArtifactPlanner.kt` / `SchemaArtifactPlanner.kt`
  - consume binding-aware field types so same-run shared enum references actually land in aggregate outputs

- aggregate enum fixtures
  - prove same-run shared manifest + aggregate reference, local enum generation, duplicate prevention, and bounded compile viability

## Task 1: Add the Shared Enum Source Contract and Gradle Wiring

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k-plugin-pipeline-source-enum-manifest/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-source-enum-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProvider.kt`
- Create: `cap4k-plugin-pipeline-source-enum-manifest/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/enummanifest/EnumManifestSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Write the failing source-provider tests for the shared enum manifest**

Create `EnumManifestSourceProviderTest.kt` with:

```kotlin
package com.only4.cap4k.plugin.pipeline.source.enummanifest

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.writeText

class EnumManifestSourceProviderTest {

    @Test
    fun `collects shared enum manifest definitions`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source")
        val manifest = projectDir.resolve("shared-enums.json")
        manifest.writeText(
            """
            [
              {
                "name": "Status",
                "package": "shared",
                "generateTranslation": true,
                "items": [
                  { "value": 0, "name": "DRAFT", "desc": "Draft" },
                  { "value": 1, "name": "PUBLISHED", "desc": "Published" }
                ]
              }
            ]
            """.trimIndent()
        )

        val snapshot = EnumManifestSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                typeRegistry = emptyMap(),
                sources = mapOf(
                    "enum-manifest" to SourceConfig(
                        enabled = true,
                        options = mapOf("files" to listOf(manifest.toAbsolutePath().toString()))
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        assertEquals("enum-manifest", snapshot.id)
        assertEquals(listOf("Status"), snapshot.definitions.map { it.typeName })
        assertEquals(true, snapshot.definitions.single().generateTranslation)
        assertEquals(listOf("DRAFT", "PUBLISHED"), snapshot.definitions.single().items.map { it.name })
    }

    @Test
    fun `duplicate shared enum type names fail fast`() {
        val projectDir = Files.createTempDirectory("enum-manifest-source-duplicate")
        val manifest = projectDir.resolve("shared-enums.json")
        manifest.writeText(
            """
            [
              { "name": "Status", "package": "shared", "items": [ { "value": 0, "name": "DRAFT", "desc": "Draft" } ] },
              { "name": "Status", "package": "shared", "items": [ { "value": 1, "name": "PUBLISHED", "desc": "Published" } ] }
            ]
            """.trimIndent()
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            EnumManifestSourceProvider().collect(
                ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    typeRegistry = emptyMap(),
                    sources = mapOf(
                        "enum-manifest" to SourceConfig(
                            enabled = true,
                            options = mapOf("files" to listOf(manifest.toAbsolutePath().toString()))
                        )
                    ),
                    generators = emptyMap(),
                    templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
                )
            )
        }

        assertEquals("duplicate shared enum definition: Status", error.message)
    }
}
```

- [ ] **Step 2: Write the failing Gradle config test for the new source block**

In `Cap4kProjectConfigFactoryTest.kt`, add:

```kotlin
@Test
fun `factory includes enum manifest source when enabled`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    val manifest = project.file("shared-enums.json")
    manifest.writeText(
        """
        [
          { "name": "Status", "package": "shared", "items": [ { "value": 0, "name": "DRAFT", "desc": "Draft" } ] }
        ]
        """.trimIndent()
    )

    extension.project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    extension.sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:mem:test")
            username.set("sa")
            password.set("secret")
        }
        enumManifest {
            enabled.set(true)
            files.from(manifest)
        }
    }
    extension.generators {
        aggregate { enabled.set(true) }
    }

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertEquals(setOf("db", "enum-manifest"), config.enabledSourceIds())
    assertEquals(
        listOf(manifest.absolutePath),
        config.sources.getValue("enum-manifest").options["files"]
    )
}
```

- [ ] **Step 3: Run the focused source and config tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-enum-manifest:test :cap4k-plugin-pipeline-gradle:test --tests "*factory includes enum manifest source when enabled"
```

Expected:

- FAIL because the enum-manifest module does not exist yet
- FAIL because `sources.enumManifest` is not part of the Gradle DSL or config factory

- [ ] **Step 4: Implement the bounded shared enum source contract and wiring**

In `PipelineModels.kt`, add:

```kotlin
data class EnumItemModel(
    val value: Int,
    val name: String,
    val description: String,
)

data class SharedEnumDefinition(
    val typeName: String,
    val packageName: String,
    val generateTranslation: Boolean,
    val items: List<EnumItemModel>,
)

data class EnumManifestSnapshot(
    override val id: String = "enum-manifest",
    val definitions: List<SharedEnumDefinition>,
) : SourceSnapshot
```

In `Cap4kExtension.kt`, add a bounded source block:

```kotlin
open class EnumManifestSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val files: ConfigurableFileCollection = objects.fileCollection()
}
```

and register it:

```kotlin
val enumManifest: EnumManifestSourceExtension =
    objects.newInstance(EnumManifestSourceExtension::class.java)
```

plus:

```kotlin
fun enumManifest(block: EnumManifestSourceExtension.() -> Unit) {
    enumManifest.block()
}
```

In `Cap4kProjectConfigFactory.kt`, extend source states and `buildSources(...)`:

```kotlin
if (states.enumManifestEnabled) {
    val files = extension.sources.enumManifest.files.files.map(File::getAbsolutePath).sorted()
    if (files.isEmpty()) {
        throw IllegalArgumentException("sources.enumManifest.files must not be empty when enumManifest is enabled.")
    }
    put("enum-manifest", SourceConfig(enabled = true, options = mapOf("files" to files)))
}
```

In `PipelinePlugin.kt`, add the provider and module dependency:

```kotlin
sources = listOf(
    DbSchemaSourceProvider(),
    EnumManifestSourceProvider(),
    DesignJsonSourceProvider(),
    KspMetadataSourceProvider(),
    IrAnalysisSourceProvider(),
)
```

In `settings.gradle.kts`, include:

```kotlin
include("cap4k-plugin-pipeline-source-enum-manifest")
```

- [ ] **Step 5: Re-run the focused source and config tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-enum-manifest:test :cap4k-plugin-pipeline-gradle:test --tests "*factory includes enum manifest source when enabled"
```

Expected:

- PASS
- `enum-manifest` is a real source id in `ProjectConfig`
- the Gradle plugin runner now knows about the new source provider

- [ ] **Step 6: Commit the shared enum source slice**

```powershell
git add settings.gradle.kts
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-source-enum-manifest
git add cap4k-plugin-pipeline-gradle/build.gradle.kts
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add aggregate enum manifest source"
```

## Task 2: Parse `@E` / `@T` Metadata and Carry It into Aggregate Binding Resolution

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationMetadata.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanningTest.kt`

- [ ] **Step 1: Write the failing parser, DB-source, canonical, and resolution-order tests**

Create `DbColumnAnnotationParserTest.kt` with:

```kotlin
package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbColumnAnnotationParserTest {

    @Test
    fun `parser extracts type binding and enum items from comment`() {
        val metadata = DbColumnAnnotationParser.parse(
            "status field @T=VideoPostVisibility;@E=0:HIDDEN:Hidden|1:PUBLIC:Public;"
        )

        assertEquals("VideoPostVisibility", metadata.typeBinding)
        assertEquals(listOf("HIDDEN", "PUBLIC"), metadata.enumItems.map { it.name })
        assertEquals(listOf(0, 1), metadata.enumItems.map { it.value })
    }

    @Test
    fun `parser keeps type-only binding without enum generation`() {
        val metadata = DbColumnAnnotationParser.parse("shared status @T=Status;")

        assertEquals("Status", metadata.typeBinding)
        assertEquals(emptyList<Any>(), metadata.enumItems)
    }

    @Test
    fun `enum definition without type binding fails`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@E=0:DRAFT:Draft|1:PUBLISHED:Published;")
        }

        assertEquals("@E requires @T on the same column comment.", error.message)
    }
}
```

In `DbSchemaSourceProviderTest.kt`, add:

```kotlin
@Test
fun `db source records parsed type binding and enum items from column comments`() {
    val url = "jdbc:h2:mem:cap4k-db-source-enum-comment;MODE=MySQL;DB_CLOSE_DELAY=-1"
    DriverManager.getConnection(url, "sa", "").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table video_post (
                    id bigint primary key comment 'pk',
                    status int not null comment 'shared status @T=Status;',
                    visibility int not null comment '@T=VideoPostVisibility;@E=0:HIDDEN:Hidden|1:PUBLIC:Public;'
                )
                """.trimIndent()
            )
        }
    }

    val snapshot = DbSchemaSourceProvider().collect(
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            typeRegistry = emptyMap(),
            sources = mapOf(
                "db" to SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "url" to url,
                        "username" to "sa",
                        "password" to "",
                        "schema" to "PUBLIC",
                        "includeTables" to listOf("video_post"),
                        "excludeTables" to emptyList<String>(),
                    )
                )
            ),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    ) as DbSchemaSnapshot
    val table = snapshot.tables.single()
    val status = table.columns.first { it.name.equals("STATUS", true) }
    val visibility = table.columns.first { it.name.equals("VISIBILITY", true) }

    assertEquals("Status", status.typeBinding)
    assertEquals(emptyList<Any>(), status.enumItems)
    assertEquals("VideoPostVisibility", visibility.typeBinding)
    assertEquals(listOf("HIDDEN", "PUBLIC"), visibility.enumItems.map { it.name })
}
```

In `DefaultCanonicalAssemblerTest.kt`, add:

```kotlin
@Test
fun `assembly carries shared enum definitions and db field enum metadata`() {
    val assembler = DefaultCanonicalAssembler()

    val result = assembler.assemble(
        config = baseConfig(),
        snapshots = listOf(
            EnumManifestSnapshot(
                definitions = listOf(
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "shared",
                        generateTranslation = true,
                        items = listOf(
                            EnumItemModel(0, "DRAFT", "Draft"),
                            EnumItemModel(1, "PUBLISHED", "Published"),
                        ),
                    )
                )
            ),
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "",
                        columns = listOf(
                            DbColumnSnapshot(
                                name = "status",
                                dbType = "INT",
                                kotlinType = "Int",
                                nullable = false,
                                typeBinding = "Status",
                            ),
                            DbColumnSnapshot(
                                name = "visibility",
                                dbType = "INT",
                                kotlinType = "Int",
                                nullable = false,
                                typeBinding = "VideoPostVisibility",
                                enumItems = listOf(
                                    EnumItemModel(0, "HIDDEN", "Hidden"),
                                    EnumItemModel(1, "PUBLIC", "Public"),
                                ),
                            ),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    )
                )
            )
        )
    ).model

    assertEquals(listOf("Status"), result.sharedEnums.map { it.typeName })
    val entity = result.entities.single()
    assertEquals("Status", entity.fields.first { it.name == "status" }.typeBinding)
    assertEquals("VideoPostVisibility", entity.fields.first { it.name == "visibility" }.typeBinding)
}
```

Create `AggregateEnumPlanningTest.kt` with:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregateEnumPlanningTest {

    @Test
    fun `type binding prefers local enum definition over shared enum lookup only when E and T coexist`() {
        val planning = AggregateEnumPlanning.from(
            CanonicalModel(
                sharedEnums = listOf(
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "shared",
                        generateTranslation = true,
                        items = listOf(EnumItemModel(0, "DRAFT", "Draft"))
                    )
                ),
                entities = listOf(
                    EntityModel(
                        name = "VideoPost",
                        packageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        comment = "",
                        fields = listOf(
                            FieldModel(name = "status", type = "Int", typeBinding = "Status"),
                            FieldModel(
                                name = "visibility",
                                type = "Int",
                                typeBinding = "VideoPostVisibility",
                                enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
                            ),
                        ),
                        idField = FieldModel(name = "id", type = "Long"),
                    )
                )
            ),
            typeRegistry = emptyMap(),
        )

        assertEquals("com.acme.demo.domain.shared.enums.Status", planning.resolveFieldType("Status", emptyList()))
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.enums.VideoPostVisibility",
            planning.resolveFieldType(
                "VideoPostVisibility",
                listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
            )
        )
    }

    @Test
    fun `ambiguous simple type between shared enum and general registry fails`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEnumPlanning.from(
                CanonicalModel(
                    sharedEnums = listOf(
                        SharedEnumDefinition(
                            typeName = "Status",
                            packageName = "shared",
                            generateTranslation = true,
                            items = listOf(EnumItemModel(0, "DRAFT", "Draft"))
                        )
                    ),
                    entities = emptyList(),
                ),
                typeRegistry = mapOf("Status" to "com.acme.shared.Status"),
            )
        }

        assertEquals("ambiguous type binding for Status: matches both shared enum and general type registry", error.message)
    }
}
```

- [ ] **Step 2: Run the focused parser, source, canonical, and planning tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest" --tests "*DbSchemaSourceProviderTest" :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateEnumPlanningTest"
```

Expected:

- FAIL because DB columns do not yet surface parsed `@T` / `@E` metadata
- FAIL because canonical model does not yet carry `sharedEnums`
- FAIL because the aggregate binding helper does not exist yet

- [ ] **Step 3: Implement bounded metadata carry-over and pre-planning binding resolution**

In `PipelineModels.kt`, extend the shared models just enough:

```kotlin
data class FieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
)

data class DbColumnSnapshot(
    val name: String,
    val dbType: String,
    val kotlinType: String,
    val nullable: Boolean,
    val defaultValue: String? = null,
    val comment: String = "",
    val isPrimaryKey: Boolean = false,
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
)

data class CanonicalModel(
    val requests: List<RequestModel> = emptyList(),
    val validators: List<ValidatorModel> = emptyList(),
    val domainEvents: List<DomainEventModel> = emptyList(),
    val schemas: List<SchemaModel> = emptyList(),
    val entities: List<EntityModel> = emptyList(),
    val repositories: List<RepositoryModel> = emptyList(),
    val analysisGraph: AnalysisGraphModel? = null,
    val drawingBoard: DrawingBoardModel? = null,
    val apiPayloads: List<ApiPayloadModel> = emptyList(),
    val sharedEnums: List<SharedEnumDefinition> = emptyList(),
)
```

Create `DbColumnAnnotationParser.kt` with the bounded old-syntax parser:

```kotlin
internal object DbColumnAnnotationParser {
    private val annotationPattern = Regex("@([A-Za-z]+)(=[^;]+)?;?")

    fun parse(comment: String): DbColumnAnnotationMetadata {
        val annotations = annotationPattern.findAll(comment).associate { match ->
            match.groupValues[1] to match.groupValues[2].removePrefix("=").trim()
        }
        val typeBinding = annotations["Type"] ?: annotations["T"]
        val enumConfig = annotations["Enum"] ?: annotations["E"]

        if (enumConfig != null && typeBinding.isNullOrBlank()) {
            throw IllegalArgumentException("@E requires @T on the same column comment.")
        }

        return DbColumnAnnotationMetadata(
            typeBinding = typeBinding?.takeIf { it.isNotBlank() },
            enumItems = parseEnumItems(enumConfig),
        )
    }
}
```

In `DbSchemaSourceProvider.kt`, enrich columns:

```kotlin
val annotationMetadata = DbColumnAnnotationParser.parse(rows.getString("REMARKS") ?: "")
DbColumnSnapshot(
    name = name,
    dbType = rows.getString("TYPE_NAME"),
    kotlinType = JdbcTypeMapper.toKotlinType(rows.getInt("DATA_TYPE")),
    nullable = rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
    defaultValue = rows.getString("COLUMN_DEF"),
    comment = rows.getString("REMARKS") ?: "",
    isPrimaryKey = name in primaryKeySet,
    typeBinding = annotationMetadata.typeBinding,
    enumItems = annotationMetadata.enumItems,
)
```

In `DefaultCanonicalAssembler.kt`, carry shared enums and field metadata:

```kotlin
val sharedEnums = snapshots.filterIsInstance<EnumManifestSnapshot>().flatMap { it.definitions }

FieldModel(
    name = column.name,
    type = column.kotlinType,
    nullable = column.nullable,
    defaultValue = column.defaultValue,
    typeBinding = column.typeBinding,
    enumItems = column.enumItems,
)
```

and return:

```kotlin
CanonicalModel(
    schemas = schemas,
    entities = entities,
    repositories = repositories,
    sharedEnums = sharedEnums,
)
```

Create `AggregateEnumPlanning.kt` as a read-only helper that owns:

- shared enum registry assembly
- local enum ownership detection from `enumItems.isNotEmpty() && typeBinding != null`
- `@T` resolution order
- ambiguity fail-fast
- field type enrichment to final FQNs used by aggregate planners

- [ ] **Step 4: Re-run the focused parser, source, canonical, and planning tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest" --tests "*DbSchemaSourceProviderTest" :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateEnumPlanningTest"
```

Expected:

- PASS
- same-run shared enum visibility is now part of canonical/enrich-time data
- `@T`-only fields stay as references
- `@E + @T` fields become local enum candidates

- [ ] **Step 5: Commit the metadata and binding slice**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationMetadata.kt
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanningTest.kt
git commit -m "feat: add aggregate enum binding metadata"
```

## Task 3: Add Aggregate Enum Planners, Templates, and Field-Type Consumption

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SharedEnumArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/LocalEnumArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EnumTranslationArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing aggregate planner assertions for shared enum, local enum, translation, and field binding**

In `AggregateArtifactPlannerTest.kt`, add:

```kotlin
@Test
fun `aggregate planner emits shared enum local enum and translation artifacts with stable ownership`() {
    val planner = AggregateArtifactPlanner()
    val config = aggregateConfig()
    val model = CanonicalModel(
        sharedEnums = listOf(
            SharedEnumDefinition(
                typeName = "Status",
                packageName = "shared",
                generateTranslation = true,
                items = listOf(
                    EnumItemModel(0, "DRAFT", "Draft"),
                    EnumItemModel(1, "PUBLISHED", "Published"),
                ),
            )
        ),
        schemas = listOf(
            SchemaModel(
                name = "VideoPostSchema",
                packageName = "com.acme.demo.domain.aggregates.video_post",
                entityName = "VideoPost",
                comment = "video post schema",
                fields = listOf(
                    FieldModel(name = "status", type = "Int", typeBinding = "Status"),
                    FieldModel(
                        name = "visibility",
                        type = "Int",
                        typeBinding = "VideoPostVisibility",
                        enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                    ),
                ),
            )
        ),
        entities = listOf(
            EntityModel(
                name = "VideoPost",
                packageName = "com.acme.demo.domain.aggregates.video_post",
                tableName = "video_post",
                comment = "video post",
                fields = listOf(
                    FieldModel(name = "status", type = "Int", typeBinding = "Status"),
                    FieldModel(
                        name = "visibility",
                        type = "Int",
                        typeBinding = "VideoPostVisibility",
                        enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                    ),
                ),
                idField = FieldModel(name = "id", type = "Long"),
            )
        ),
        repositories = listOf(
            RepositoryModel(
                name = "VideoPostRepository",
                packageName = "com.acme.demo.domain.aggregates.video_post.repo",
                entityName = "VideoPost",
                idType = "Long",
            )
        )
    )

    val items = planner.plan(config, model)

    assertTrue(items.any { it.templateId == "aggregate/enum.kt.peb" && it.outputPath.endsWith("/domain/shared/enums/Status.kt") })
    assertTrue(items.any { it.templateId == "aggregate/enum.kt.peb" && it.outputPath.endsWith("/domain/aggregates/video_post/enums/VideoPostVisibility.kt") })
    assertTrue(items.any { it.templateId == "aggregate/enum_translation.kt.peb" && it.outputPath.endsWith("/domain/translation/shared/StatusTranslation.kt") })
    assertTrue(items.any { it.templateId == "aggregate/enum_translation.kt.peb" && it.outputPath.endsWith("/domain/translation/video_post/VideoPostVisibilityTranslation.kt") })

    val entityPlan = items.single { it.templateId == "aggregate/entity.kt.peb" }
    val entityFields = entityPlan.context.getValue("fields") as List<Map<String, Any?>>
    assertEquals("com.acme.demo.domain.shared.enums.Status", entityFields.single { it["name"] == "status" }["type"])
    assertEquals(
        "com.acme.demo.domain.aggregates.video_post.enums.VideoPostVisibility",
        entityFields.single { it["name"] == "visibility" }["type"]
    )
}
```

- [ ] **Step 2: Run the aggregate planner tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest" --tests "*AggregateEnumPlanningTest"
```

Expected:

- FAIL because no enum planners exist
- FAIL because `SchemaArtifactPlanner` and `EntityArtifactPlanner` still pass raw DB types through unchanged

- [ ] **Step 3: Implement the bounded enum planners and consume binding-aware field types in schema/entity**

Create `SharedEnumArtifactPlanner.kt` to emit domain-module shared enums:

```kotlin
internal class SharedEnumArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val planning = AggregateEnumPlanning.from(model, config.typeRegistry)
        val domainRoot = requireRelativeModule(config, "domain")
        return planning.sharedEnums().map { shared ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/enum.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${shared.packageName.replace(".", "/")}/${shared.typeName}.kt",
                context = mapOf(
                    "packageName" to shared.packageName,
                    "typeName" to shared.typeName,
                    "items" to shared.items.map { item ->
                        mapOf(
                            "value" to item.value,
                            "name" to item.name,
                            "description" to item.description,
                        )
                    },
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

Create `LocalEnumArtifactPlanner.kt` with the same template id but aggregate-local package routing:

```kotlin
internal class LocalEnumArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val planning = AggregateEnumPlanning.from(model, config.typeRegistry)
        val domainRoot = requireRelativeModule(config, "domain")
        return planning.localEnums().map { local ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/enum.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${local.packageName.replace(".", "/")}/${local.typeName}.kt",
                context = mapOf(
                    "packageName" to local.packageName,
                    "typeName" to local.typeName,
                    "items" to local.items.map { item ->
                        mapOf(
                            "value" to item.value,
                            "name" to item.name,
                            "description" to item.description,
                        )
                    },
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

Create `EnumTranslationArtifactPlanner.kt`:

```kotlin
internal class EnumTranslationArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val planning = AggregateEnumPlanning.from(model, config.typeRegistry)
        val adapterRoot = requireRelativeModule(config, "adapter")
        return planning.translations().map { translation ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "adapter",
                templateId = "aggregate/enum_translation.kt.peb",
                outputPath = "$adapterRoot/src/main/kotlin/${translation.packageName.replace(".", "/")}/${translation.typeName}.kt",
                context = mapOf(
                    "packageName" to translation.packageName,
                    "typeName" to translation.typeName,
                    "enumTypeName" to translation.enumTypeName,
                    "enumTypeFqn" to translation.enumTypeFqn,
                    "translationTypeConst" to translation.translationTypeConst,
                    "translationTypeValue" to translation.translationTypeValue,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

Register them in `AggregateArtifactPlanner.kt`:

```kotlin
private val delegates: List<AggregateArtifactFamilyPlanner> = listOf(
    SchemaArtifactPlanner(),
    EntityArtifactPlanner(),
    RepositoryArtifactPlanner(),
    FactoryArtifactPlanner(),
    SpecificationArtifactPlanner(),
    AggregateWrapperArtifactPlanner(),
    UniqueQueryArtifactPlanner(),
    UniqueQueryHandlerArtifactPlanner(),
    UniqueValidatorArtifactPlanner(),
    SharedEnumArtifactPlanner(),
    LocalEnumArtifactPlanner(),
    EnumTranslationArtifactPlanner(),
)
```

In both `SchemaArtifactPlanner.kt` and `EntityArtifactPlanner.kt`, replace raw `fields` with the helper’s enriched render fields:

```kotlin
val planning = AggregateEnumPlanning.from(model, config.typeRegistry)
val fields = planning.enrichedFieldsFor(schema.name)
```

and:

```kotlin
val fields = planning.enrichedFieldsFor(entity.name)
```

- [ ] **Step 4: Add failing renderer assertions for enum and translation preset fallback**

In `PebbleArtifactRendererTest.kt`, extend the aggregate fallback coverage with:

```kotlin
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "domain",
    templateId = "aggregate/enum.kt.peb",
    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt",
    context = mapOf(
        "packageName" to "com.acme.demo.domain.shared.enums",
        "typeName" to "Status",
        "aggregateName" to null,
        "items" to listOf(
            mapOf("value" to 0, "name" to "DRAFT", "description" to "Draft"),
            mapOf("value" to 1, "name" to "PUBLISHED", "description" to "Published"),
        ),
        "translationTypeName" to "StatusTranslation",
        "translationEnabled" to true,
    ),
    conflictPolicy = ConflictPolicy.SKIP,
),
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "adapter",
    templateId = "aggregate/enum_translation.kt.peb",
    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/StatusTranslation.kt",
    context = mapOf(
        "packageName" to "com.acme.demo.domain.translation.shared",
        "typeName" to "StatusTranslation",
        "enumTypeName" to "Status",
        "enumTypeFqn" to "com.acme.demo.domain.shared.enums.Status",
        "translationTypeConst" to "STATUS_CODE_TO_DESC",
        "translationTypeValue" to "status_code_to_desc",
        "enumNameField" to "description",
    ),
    conflictPolicy = ConflictPolicy.SKIP,
),
```

with assertions:

```kotlin
assertTrue(enumContent.contains("enum class Status("))
assertTrue(enumContent.contains("DRAFT(0, \"Draft\")"))
assertTrue(translationContent.contains("class StatusTranslation"))
assertTrue(translationContent.contains("import com.acme.demo.domain.shared.enums.Status"))
assertTrue(translationContent.contains("@TranslationType(type = STATUS_CODE_TO_DESC)"))
```

- [ ] **Step 5: Add the two aggregate preset templates**

Create `aggregate/enum.kt.peb`:

```pebble
package {{ packageName }}

enum class {{ typeName }}(
    val value: Int,
    val description: String
) {
{% for item in items %}
    {{ item.name }}({{ item.value }}, "{{ item.description }}"){% if not loop.last %},{% else %};{% endif %}
{% endfor %}

    companion object {
        private val enumMap: Map<Int, {{ typeName }}> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): {{ typeName }}? = enumMap[value]
    }
}
```

Create `aggregate/enum_translation.kt.peb`:

```pebble
package {{ packageName }}

import com.only.engine.translation.annotation.TranslationType
import com.only.engine.translation.core.BatchTranslationInterface
import com.only.engine.translation.core.TranslationInterface
import org.springframework.stereotype.Component
import {{ enumTypeFqn }}

@TranslationType(type = {{ translationTypeConst }})
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

- [ ] **Step 6: Re-run planner and renderer tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest" --tests "*AggregateEnumPlanningTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"
```

Expected:

- PASS
- aggregate planners now emit shared enums, local enums, and translations
- entity/schema outputs now consume resolved enum FQNs
- renderer fallback proves both new aggregate templates

- [ ] **Step 7: Commit the planner and template slice**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SharedEnumArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/LocalEnumArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EnumTranslationArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add aggregate enum planners"
```

## Task 4: Add Functional and Compile Verification for Shared and Local Enum Ownership

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/enums/shared-enums.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/schema.sql`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/enums/shared-enums.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateEnumCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/SharedEnumTranslationCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/video_post/LocalEnumTranslationCompileSmoke.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/only/engine/translation/annotation/TranslationType.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/only/engine/translation/core/TranslationInterface.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample/demo-adapter/src/main/kotlin/com/only/engine/translation/core/BatchTranslationInterface.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Add failing functional assertions for shared enum, local enum, and translation generation**

In `PipelinePluginFunctionalTest.kt`, add a new test:

```kotlin
@Test
fun `cap4kPlan and cap4kGenerate produce shared and local aggregate enum artifacts`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum")
    FunctionalFixtureSupport.copyFixture(projectDir, "aggregate-enum-sample")

    val planResult = FunctionalFixtureSupport.runner(projectDir, "cap4kPlan").build()
    val generateResult = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerate").build()
    val planFile = projectDir.resolve("build/reports/cap4k/plan.json")

    assertTrue(planResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/enum.kt.peb\""))
    assertTrue(planFile.readText().contains("\"templateId\": \"aggregate/enum_translation.kt.peb\""))
    assertTrue(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt").toFile().exists())
    assertTrue(projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/enums/VideoPostVisibility.kt").toFile().exists())
    assertTrue(projectDir.resolve("demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/StatusTranslation.kt").toFile().exists())
    assertTrue(projectDir.resolve("demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/video_post/VideoPostVisibilityTranslation.kt").toFile().exists())
}
```

In the same file, add a duplicate-prevention assertion:

```kotlin
val generatedEntity = projectDir.resolve(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
).readText()
assertTrue(generatedEntity.contains("val status: com.acme.demo.domain.shared.enums.Status"))
assertFalse(generatedEntity.contains("class Status("))
```

- [ ] **Step 2: Add failing compile tests for same-run shared binding and translation stubs**

In `PipelinePluginCompileFunctionalTest.kt`, add:

```kotlin
@Test
fun `aggregate enum generation participates in domain compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum-domain-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-enum-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .buildAndFail()
    assertTrue(beforeGenerateCompileResult.output.contains("Status"))
    assertTrue(beforeGenerateCompileResult.output.contains("VideoPostVisibility"))

    val generateResult = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerate").build()
    val compileResult = FunctionalFixtureSupport.runner(projectDir, ":demo-domain:compileKotlin").build()

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}

@Test
fun `aggregate enum translation generation participates in adapter compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum-adapter-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-enum-compile-sample")

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-adapter:compileKotlin", "-x", ":demo-domain:compileKotlin")
        .buildAndFail()
    assertTrue(beforeGenerateCompileResult.output.contains("StatusTranslation"))
    assertTrue(beforeGenerateCompileResult.output.contains("VideoPostVisibilityTranslation"))

    val generateResult = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerate").build()
    val compileResult = FunctionalFixtureSupport.runner(projectDir, ":demo-adapter:compileKotlin").build()

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

- [ ] **Step 3: Run the targeted functional and compile tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce shared and local aggregate enum artifacts" --tests "*aggregate enum generation participates in domain compileKotlin" --tests "*aggregate enum translation generation participates in adapter compileKotlin"
```

Expected:

- FAIL because no dedicated aggregate-enum fixtures exist yet
- FAIL because compile smoke files and translation stubs do not exist yet

- [ ] **Step 4: Create the dedicated enum fixtures and bounded compile stubs**

Create `aggregate-enum-sample/build.gradle.kts`:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:file:${"$"}projectDir/build/demo;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
            username.set("sa")
            password.set("secret")
        }
        enumManifest {
            enabled.set(true)
            files.from("enums/shared-enums.json")
        }
    }
    generators {
        aggregate {
            enabled.set(true)
        }
    }
}
```

Create `aggregate-enum-sample/enums/shared-enums.json`:

```json
[
  {
    "name": "Status",
    "package": "com.acme.demo.domain.shared.enums",
    "generateTranslation": true,
    "items": [
      { "value": 0, "name": "DRAFT", "desc": "Draft" },
      { "value": 1, "name": "PUBLISHED", "desc": "Published" }
    ]
  }
]
```

Create `aggregate-enum-sample/schema.sql`:

```sql
create table video_post (
    id bigint primary key,
    status int not null comment 'shared status @T=Status;',
    visibility int not null comment '@T=VideoPostVisibility;@E=0:HIDDEN:Hidden|1:PUBLIC:Public;',
    title varchar(255) not null
);
```

Create `AggregateEnumCompileSmoke.kt`:

```kotlin
package com.acme.demo.domain.aggregates.video_post

import com.acme.demo.domain.aggregates.video_post.enums.VideoPostVisibility
import com.acme.demo.domain.shared.enums.Status

class AggregateEnumCompileSmoke(
    private val entity: VideoPost,
    private val status: Status,
    private val visibility: VideoPostVisibility,
) {
    fun wire(): Triple<VideoPost, Status, VideoPostVisibility> = Triple(entity, status, visibility)
}
```

Create translation runtime stubs in the adapter compile fixture:

```kotlin
package com.only.engine.translation.annotation

annotation class TranslationType(val type: String)
```

```kotlin
package com.only.engine.translation.core

interface TranslationInterface<T> {
    fun translation(key: Any, other: String): T?
}
```

```kotlin
package com.only.engine.translation.core

interface BatchTranslationInterface<T> {
    fun translationBatch(keys: Collection<Any>, other: String): Map<Any, T?>
}
```

Create `SharedEnumTranslationCompileSmoke.kt`:

```kotlin
package com.acme.demo.domain.translation.shared

class SharedEnumTranslationCompileSmoke(
    private val translation: StatusTranslation,
) {
    fun wire(): StatusTranslation = translation
}
```

Create `LocalEnumTranslationCompileSmoke.kt`:

```kotlin
package com.acme.demo.domain.translation.video_post

class LocalEnumTranslationCompileSmoke(
    private val translation: VideoPostVisibilityTranslation,
) {
    fun wire(): VideoPostVisibilityTranslation = translation
}
```

- [ ] **Step 5: Re-run the targeted functional and compile tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan and cap4kGenerate produce shared and local aggregate enum artifacts" --tests "*aggregate enum generation participates in domain compileKotlin" --tests "*aggregate enum translation generation participates in adapter compileKotlin"
```

Expected:

- PASS
- same-run manifest-owned shared enum and DB-owned local enum are both generated
- domain compile proves aggregate outputs bind to the right types
- adapter compile proves translation files compile with bounded local stubs

- [ ] **Step 6: Commit the functional and compile slice**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-sample
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-enum-compile-sample
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover aggregate enum parity"
```

## Task 5: Run Aggregate-Focused Regression and Final Verification

**Files:**
- Modify only if a regression exposes an enum-family-specific issue:
  - `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
  - `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt`
  - `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SharedEnumArtifactPlanner.kt`
  - `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/LocalEnumArtifactPlanner.kt`
  - `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EnumTranslationArtifactPlanner.kt`
  - `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb`
  - `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb`
  - `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  - `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Run the enum-focused regression set**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-enum-manifest:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected:

- PASS
- no regression in source ingestion, canonical carry-over, aggregate planners, renderer fallback, or Gradle functional/compile coverage

- [ ] **Step 2: If a regression exposes an enum-family-only issue, fix it minimally and re-run the exact failing command**

Acceptable examples:

```kotlin
private fun resolveSimpleType(typeName: String): String =
    if ('.' in typeName) typeName else sharedEnumFqns[typeName] ?: typeRegistry[typeName] ?: typeName
```

```pebble
return {{ enumTypeName }}.valueOfOrNull(code)?.description
```

Do not widen the fix into relation parity, JPA parity, or general source-semantic recovery.

- [ ] **Step 3: Run the final verification command**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-enum-manifest:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 4: Verify git state before declaring completion**

Run:

```powershell
git status --short --branch
git log --oneline -8
```

Expected:

- only enum / enum-translation parity work is present on the branch
- no unexpected modified or untracked files remain

- [ ] **Step 5: Commit any final enum-family-only regression fix**

If Step 2 required code or template changes, commit them separately:

```powershell
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SharedEnumArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/LocalEnumArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EnumTranslationArtifactPlanner.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "fix: harden aggregate enum parity"
```

If no extra fix was required, mark this step complete without creating another commit.

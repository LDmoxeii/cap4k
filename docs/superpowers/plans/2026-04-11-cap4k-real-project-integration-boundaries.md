# Cap4k Real Project Integration Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make real-project pipeline integration safer by adding manifest-driven design input selection and explicit aggregate unsupported-table handling with diagnostics.

**Architecture:** Keep the current pipeline stages unchanged. Add narrow DSL/config support in `pipeline-gradle`, manifest loading in `pipeline-source-design-json`, source-filter metadata plus unsupported-table policy in `pipeline-core`, and expose aggregate diagnostics through `cap4kPlan` output without widening aggregate capability to composite keys.

**Tech Stack:** Kotlin, Gradle plugin/TestKit, Gson, JDBC metadata, JUnit 5

---

### Task 1: Extend DSL And Config Surface

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`

- [ ] **Step 1: Write the failing config-factory tests**

```kotlin
@Test
fun `maps aggregate unsupported table policy into generator config`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
    val extension = project.extensions.getByType(Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        adapterModulePath.set("demo-adapter")
    }
    extension.sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:mem:test")
            username.set("sa")
            password.set("secret")
        }
    }
    extension.generators {
        aggregate {
            enabled.set(true)
            unsupportedTablePolicy.set("SKIP")
        }
    }

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertEquals("SKIP", config.generators.getValue("aggregate").options.getValue("unsupportedTablePolicy"))
}

@Test
fun `prefers design manifest when manifest file is configured`() {
    val tempDir = Files.createTempDirectory("cap4k-config-manifest")
    val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
    val extension = project.extensions.getByType(Cap4kExtension::class.java)
    val manifest = tempDir.resolve("iterate/design-manifest.json").toFile().apply {
        parentFile.mkdirs()
        writeText("""["iterate/active/order_gen.json"]""")
    }

    extension.project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
    }
    extension.sources {
        designJson {
            enabled.set(true)
            manifestFile.set(manifest.absolutePath)
            files.from(tempDir.resolve("ignored.json").toFile())
        }
    }
    extension.generators {
        design { enabled.set(true) }
    }

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertEquals(manifest.absolutePath, config.sources.getValue("design-json").options.getValue("manifestFile"))
    assertEquals(project.projectDir.absolutePath, config.sources.getValue("design-json").options.getValue("projectDir"))
    assertFalse(config.sources.getValue("design-json").options.containsKey("files"))
}
```

- [ ] **Step 2: Run the focused Gradle test task and verify failure**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: FAIL because `manifestFile` and `unsupportedTablePolicy` do not exist yet.

- [ ] **Step 3: Add the new extension properties and config mapping**

```kotlin
open class DesignJsonSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val files: ConfigurableFileCollection = objects.fileCollection()
    val manifestFile: Property<String> = objects.property(String::class.java)
}

open class AggregateGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val unsupportedTablePolicy: Property<String> =
        objects.property(String::class.java).convention("FAIL")
}
```

```kotlin
if (states.designJsonEnabled) {
    extension.sources.designJson.manifestFile.optionalValue()?.let { manifestPath ->
        put(
            "design-json",
            SourceConfig(
                enabled = true,
                options = mapOf(
                    "manifestFile" to project.file(manifestPath).absolutePath,
                    "projectDir" to project.projectDir.absolutePath,
                ),
            )
        )
    } ?: run {
        val files = extension.sources.designJson.files.files.map(File::getAbsolutePath).sorted()
        if (files.isEmpty()) {
            throw IllegalArgumentException("sources.designJson.files must not be empty when designJson is enabled.")
        }
        put("design-json", SourceConfig(enabled = true, options = mapOf("files" to files)))
    }
}

if (states.aggregateEnabled) {
    put(
        "aggregate",
        GeneratorConfig(
            enabled = true,
            options = mapOf(
                "unsupportedTablePolicy" to extension.generators.aggregate.unsupportedTablePolicy.normalized().ifEmpty { "FAIL" }
            ),
        )
    )
}
```

- [ ] **Step 4: Add the new API enum used by later tasks**

```kotlin
enum class UnsupportedTablePolicy {
    FAIL,
    SKIP,
}
```

- [ ] **Step 5: Re-run the config-factory tests and verify success**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt \
        cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git commit -m "feat: add pipeline integration boundary config"
```

### Task 2: Implement Manifest-Driven Design Source Loading

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`

- [ ] **Step 1: Write the failing source-provider tests**

```kotlin
@Test
fun `collects design entries from manifest file`() {
    val root = Files.createTempDirectory("design-manifest-source")
    val manifest = root.resolve("iterate/design-manifest.json")
    val designFile = root.resolve("iterate/active/video_encrypt/video_encrypt_gen.json")
    Files.createDirectories(designFile.parent)
    Files.writeString(
        designFile,
        """
        [
          {
            "tag": "cmd",
            "package": "video.encrypt",
            "name": "GenerateVideoHlsKey",
            "desc": "generate key",
            "aggregates": ["VideoPost"]
          }
        ]
        """.trimIndent()
    )
    Files.createDirectories(manifest.parent)
    Files.writeString(manifest, """["iterate/active/video_encrypt/video_encrypt_gen.json"]""")

    val snapshot = DesignJsonSourceProvider().collect(
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "manifestFile" to manifest.toFile().absolutePath,
                        "projectDir" to root.toFile().absolutePath,
                    ),
                )
            ),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    ) as DesignSpecSnapshot

    assertEquals(listOf("GenerateVideoHlsKey"), snapshot.entries.map { it.name })
}

@Test
fun `fails when manifest contains duplicate file entries`() {
    val root = Files.createTempDirectory("design-manifest-duplicate")
    val manifest = root.resolve("iterate/design-manifest.json")
    val designFile = root.resolve("iterate/active/video_encrypt/video_encrypt_gen.json")
    Files.createDirectories(designFile.parent)
    Files.writeString(designFile, """[]""")
    Files.createDirectories(manifest.parent)
    Files.writeString(
        manifest,
        """["iterate/active/video_encrypt/video_encrypt_gen.json","iterate/active/video_encrypt/video_encrypt_gen.json"]"""
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        DesignJsonSourceProvider().collect(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = mapOf(
                    "design-json" to SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "manifestFile" to manifest.toFile().absolutePath,
                            "projectDir" to root.toFile().absolutePath,
                        ),
                    )
                ),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )
    }
    assertTrue(error.message!!.contains("duplicate design manifest entry"))
}
```

- [ ] **Step 2: Run the source-provider test task and verify failure**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest"
```

Expected: FAIL because manifest loading does not exist yet.

- [ ] **Step 3: Implement manifest-first file resolution**

```kotlin
override fun collect(config: ProjectConfig): DesignSpecSnapshot {
    val source = config.sources[id] ?: error("Missing design-json source config")
    val manifestFile = source.options["manifestFile"] as? String
    val projectDir = source.options["projectDir"] as? String
    val files = if (manifestFile != null) {
        resolveManifestFiles(
            manifestFile = File(manifestFile),
            projectDir = requireNotNull(projectDir) { "design-json manifest resolution requires projectDir" },
        )
    } else {
        (source.options["files"] as? List<*>).orEmpty().map { File(it.toString()) }
    }

    val entries = files.flatMap { parseFile(it) }
    return DesignSpecSnapshot(entries = entries)
}

private fun resolveManifestFiles(manifestFile: File, projectDir: String): List<File> {
    require(manifestFile.exists()) { "design manifest file does not exist: ${manifestFile.absolutePath}" }
    val rootDir = File(projectDir)
    val paths = JsonParser.parseReader(manifestFile.reader(Charsets.UTF_8))
        .asJsonArray
        .map { it.asString.trim() }
    require(paths.isNotEmpty()) { "design manifest must not be empty: ${manifestFile.absolutePath}" }
    require(paths.size == paths.toSet().size) { "duplicate design manifest entry found in ${manifestFile.absolutePath}" }
    return paths.map { relativePath ->
        rootDir.resolve(relativePath).canonicalFile.also { file ->
            require(file.exists()) { "design manifest entry does not exist: $relativePath" }
        }
    }
}
```

- [ ] **Step 4: Re-run the source-provider tests and verify success**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt \
        cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt
git commit -m "feat: add manifest driven design source loading"
```

### Task 3: Add Aggregate Unsupported-Table Policy And Source Diagnostics

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing DB source and assembler tests**

```kotlin
@Test
fun `db source records discovered included and excluded table diagnostics`() {
    val dbFile = Files.createTempDirectory("db-source-diagnostics").resolve("demo").toAbsolutePath().toString()
    DriverManager.getConnection("jdbc:h2:file:$dbFile;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "secret").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("create table video_post (id bigint primary key, title varchar(128) not null)")
            statement.execute("create table audit_log (tenant_id bigint not null, event_id varchar(64) not null, primary key (tenant_id, event_id))")
        }
    }

    val snapshot = DbSchemaSourceProvider().collect(
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "db" to SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "url" to "jdbc:h2:file:$dbFile;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                        "username" to "sa",
                        "password" to "secret",
                        "schema" to "PUBLIC",
                        "includeTables" to listOf("video_post", "audit_log"),
                        "excludeTables" to listOf("audit_log"),
                    ),
                )
            ),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    ) as DbSchemaSnapshot

    assertEquals(listOf("audit_log", "video_post"), snapshot.discoveredTables)
    assertEquals(listOf("video_post"), snapshot.includedTables)
    assertEquals(listOf("audit_log"), snapshot.excludedTables)
}

@Test
fun `skips composite key table when unsupported policy is skip`() {
    val assembly = DefaultCanonicalAssembler().assemble(
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf("domain" to "demo-domain", "adapter" to "demo-adapter"),
            sources = emptyMap(),
            generators = mapOf(
                "aggregate" to GeneratorConfig(
                    enabled = true,
                    options = mapOf("unsupportedTablePolicy" to "SKIP"),
                )
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        ),
        snapshots = listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        "audit_log",
                        "",
                        columns = listOf(
                            DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, null, "", true),
                            DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", true),
                        ),
                        primaryKey = listOf("tenant_id", "event_id"),
                        uniqueConstraints = emptyList(),
                    ),
                    DbTableSnapshot(
                        "video_post",
                        "",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                            DbColumnSnapshot("title", "VARCHAR", "String", false, null, "", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    ),
                ),
                discoveredTables = listOf("audit_log", "video_post"),
                includedTables = listOf("audit_log", "video_post"),
                excludedTables = emptyList(),
            )
        )
    )
    val model = assembly.model

    assertEquals(listOf("VideoPost"), model.entities.map { it.name })
    assertEquals(listOf("video_post"), assembly.diagnostics!!.aggregate!!.supportedTables)
    assertEquals("audit_log", assembly.diagnostics!!.aggregate!!.unsupportedTables.single().tableName)
}
```

- [ ] **Step 2: Run the DB source and assembler tests and verify failure**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest" \
          :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: FAIL because snapshot diagnostics fields and skip policy behavior do not exist yet.

- [ ] **Step 3: Extend snapshot and result models with diagnostics structures**

```kotlin
data class CanonicalAssemblyResult(
    val model: CanonicalModel,
    val diagnostics: PipelineDiagnostics? = null,
)

data class DbSchemaSnapshot(
    override val id: String = "db",
    val tables: List<DbTableSnapshot>,
    val discoveredTables: List<String> = tables.map { it.tableName },
    val includedTables: List<String> = tables.map { it.tableName },
    val excludedTables: List<String> = emptyList(),
) : SourceSnapshot

data class UnsupportedAggregateTable(
    val tableName: String,
    val reason: String,
)

data class AggregateDiagnostics(
    val discoveredTables: List<String>,
    val includedTables: List<String>,
    val excludedTables: List<String>,
    val supportedTables: List<String>,
    val unsupportedTables: List<UnsupportedAggregateTable>,
)

data class PipelineDiagnostics(
    val aggregate: AggregateDiagnostics? = null,
)
```

- [ ] **Step 4: Preserve filter metadata in the DB source**

```kotlin
val selectedTables = when {
    includeTableRequests.isNotEmpty() && includeTables.isEmpty() -> emptyList()
    includeTableRequests.isEmpty() -> discoveredTables
    else -> discoveredTables.filter { it in includeTables }
}
val filteredTables = selectedTables.filterNot { it in excludeTables }

return DbSchemaSnapshot(
    tables = filteredTables.map { readTable(metadata, schema, it) }.sortedBy { it.tableName },
    discoveredTables = discoveredTables.sorted(),
    includedTables = filteredTables.sorted(),
    excludedTables = excludeTables.sorted(),
)
```

- [ ] **Step 5: Apply unsupported-table policy in canonical assembly**

```kotlin
interface CanonicalAssembler {
    fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult
}

val supportedTables = mutableListOf<DbTableSnapshot>()

val aggregatePolicy = UnsupportedTablePolicy.valueOf(
    config.generators["aggregate"]
        ?.options
        ?.get("unsupportedTablePolicy")
        ?.toString()
        ?.uppercase(Locale.ROOT)
        ?: "FAIL"
)

val aggregateDiagnostics = dbSnapshots.firstOrNull()?.let { snapshot ->
    val unsupported = mutableListOf<UnsupportedAggregateTable>()

    snapshot.tables.forEach { table ->
        val reason = when {
            table.primaryKey.isEmpty() -> "missing_primary_key"
            table.primaryKey.size != 1 -> "composite_primary_key"
            else -> null
        }
        if (reason == null) {
            supportedTables += table
        } else if (aggregatePolicy == UnsupportedTablePolicy.FAIL) {
            error("db table ${table.tableName} is unsupported for aggregate generation: $reason")
        } else {
            unsupported += UnsupportedAggregateTable(table.tableName, reason)
        }
    }

    AggregateDiagnostics(
        discoveredTables = snapshot.discoveredTables,
        includedTables = snapshot.includedTables,
        excludedTables = snapshot.excludedTables,
        supportedTables = supportedTables.map { it.tableName },
        unsupportedTables = unsupported,
    )
}

val aggregateModels = supportedTables.map { table ->
    val entityName = AggregateNaming.entityName(table.tableName)
    val schemaName = AggregateNaming.schemaName(table.tableName)
    val repositoryName = AggregateNaming.repositoryName(table.tableName)
    val segment = AggregateNaming.tableSegment(table.tableName)
    val fields = table.columns.map {
        FieldModel(name = it.name, type = it.kotlinType, nullable = it.nullable, defaultValue = it.defaultValue)
    }
    val idField = fields.first { it.name == table.primaryKey.first() }

    Triple(
        SchemaModel(schemaName, "${config.basePackage}.domain._share.meta.$segment", entityName, table.comment, fields),
        EntityModel(entityName, "${config.basePackage}.domain.aggregates.$segment", table.tableName, table.comment, fields, idField),
        RepositoryModel(repositoryName, "${config.basePackage}.adapter.domain.repositories", entityName, idField.type),
    )
}

return CanonicalAssemblyResult(
    model = CanonicalModel(
        requests = requests,
        schemas = aggregateModels.map { it.first },
        entities = aggregateModels.map { it.second },
        repositories = aggregateModels.map { it.third },
        analysisGraph = analysisGraph,
        drawingBoard = drawingBoard,
    ),
    diagnostics = PipelineDiagnostics(aggregate = aggregateDiagnostics),
)
```

- [ ] **Step 6: Re-run the DB source and assembler tests and verify success**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest" \
          :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt \
        cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt \
        cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt \
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt \
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: add aggregate unsupported table diagnostics"
```

### Task 4: Propagate Diagnostics Through Runner And Plan Output

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

- [ ] **Step 1: Write the failing runner and plan-output tests**

```kotlin
@Test
fun `pipeline result carries aggregate diagnostics`() {
    val result = DefaultPipelineRunner(
        sources = listOf(
            object : SourceProvider {
                override val id: String = "db"
                override fun collect(config: ProjectConfig): SourceSnapshot =
                    DbSchemaSnapshot(
                        tables = listOf(
                            DbTableSnapshot(
                                "audit_log",
                                "",
                                columns = listOf(
                                    DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, null, "", true),
                                    DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", true),
                                ),
                                primaryKey = listOf("tenant_id", "event_id"),
                                uniqueConstraints = emptyList(),
                            ),
                            DbTableSnapshot(
                                "video_post",
                                "",
                                columns = listOf(
                                    DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                ),
                                primaryKey = listOf("id"),
                                uniqueConstraints = emptyList(),
                            ),
                        ),
                        discoveredTables = listOf("audit_log", "video_post"),
                        includedTables = listOf("audit_log", "video_post"),
                        excludedTables = emptyList(),
                    )
            }
        ),
        generators = listOf(
            object : GeneratorProvider {
                override val id: String = "aggregate"
                override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> = emptyList()
            }
        ),
        assembler = DefaultCanonicalAssembler(),
        renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> = emptyList()
        },
        exporter = NoopArtifactExporter(),
    ).run(
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf("domain" to "demo-domain", "adapter" to "demo-adapter"),
            sources = mapOf("db" to SourceConfig(enabled = true)),
            generators = mapOf(
                "aggregate" to GeneratorConfig(
                    enabled = true,
                    options = mapOf("unsupportedTablePolicy" to "SKIP"),
                )
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    )

    assertEquals(listOf("video_post"), result.diagnostics!!.aggregate!!.supportedTables)
    assertEquals("composite_primary_key", result.diagnostics!!.aggregate!!.unsupportedTables.single().reason)
}
```

```kotlin
@Test
fun `cap4kPlan writes items and diagnostics envelope`() {
    val planJson = projectDir.resolve("build/cap4k/plan.json").readText()
    assertTrue(planJson.contains("\"items\""))
    assertTrue(planJson.contains("\"diagnostics\""))
    assertTrue(planJson.contains("\"aggregate\""))
}
```

- [ ] **Step 2: Run the runner and Gradle tests and verify failure**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest" \
          :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: FAIL because `PipelineResult` and `plan.json` still expose plan items only.

- [ ] **Step 3: Add a plan-report envelope and propagate diagnostics**

```kotlin
data class PlanReport(
    val items: List<ArtifactPlanItem>,
    val diagnostics: PipelineDiagnostics? = null,
)

data class PipelineResult(
    val planItems: List<ArtifactPlanItem> = emptyList(),
    val renderedArtifacts: List<RenderedArtifact> = emptyList(),
    val writtenPaths: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val diagnostics: PipelineDiagnostics? = null,
)
```

```kotlin
val assembly = assembler.assemble(config, snapshots)
val model = assembly.model
val planItems = generators
    .filter { config.generators[it.id]?.enabled == true }
    .flatMap { it.plan(config, model) }
val renderedArtifacts = renderer.render(planItems, config)
val writtenPaths = exporter.export(renderedArtifacts)
return PipelineResult(
    planItems = planItems,
    renderedArtifacts = renderedArtifacts,
    writtenPaths = writtenPaths,
    warnings = emptyList(),
    diagnostics = assembly.diagnostics,
)
```

```kotlin
outputFile.writeText(
    GsonBuilder()
        .setPrettyPrinting()
        .create()
        .toJson(
            PlanReport(
                items = result.planItems,
                diagnostics = result.diagnostics,
            )
        )
)
```

- [ ] **Step 4: Re-run the runner and Gradle tests and verify success**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest" \
          :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt \
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt \
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt
git commit -m "feat: surface pipeline diagnostics in plan output"
```

### Task 5: Add Functional Fixtures For Manifest And Aggregate Skip Policy

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-manifest-sample/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-manifest-sample/iterate/design-manifest.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-manifest-sample/iterate/active/video_encrypt/video_encrypt_gen.json`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-policy-sample/build.gradle.kts`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-policy-sample/schema.sql`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Write the failing functional tests**

```kotlin
@Test
fun `cap4kPlan and cap4kGenerate support manifest driven design inputs`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-manifest")
    copyFixture(projectDir, "design-manifest-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kPlan", "cap4kGenerate")
        .build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(projectDir.resolve("build/cap4k/plan.json").readText().contains("\"diagnostics\""))
    assertTrue(
        projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/video/encrypt/GenerateVideoHlsKeyCmd.kt"
        ).toFile().exists()
    )
}

@Test
fun `cap4kPlan skips unsupported tables when aggregate policy is skip`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-skip")
    copyFixture(projectDir, "aggregate-policy-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kPlan", "cap4kGenerate")
        .build()

    val planJson = projectDir.resolve("build/cap4k/plan.json").readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(planJson.contains("\"unsupportedTables\""))
    assertTrue(planJson.contains("\"tableName\": \"audit_log\""))
    assertTrue(planJson.contains("\"reason\": \"composite_primary_key\""))
    assertTrue(
        projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).toFile().exists()
    )
}
```

- [ ] **Step 2: Run the functional test task and verify failure**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: FAIL because the new fixtures and behaviors are not wired yet.

- [ ] **Step 3: Add the manifest and aggregate-policy fixtures**

```kotlin
// design-manifest-sample/build.gradle.kts
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            manifestFile.set("iterate/design-manifest.json")
        }
    }
    generators {
        design { enabled.set(true) }
    }
}
```

```kotlin
// aggregate-policy-sample/build.gradle.kts
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val schemaScriptPath = layout.projectDirectory.file("schema.sql").asFile.absolutePath.replace("\\", "/")
val dbFilePath = layout.buildDirectory.file("h2/demo").get().asFile.absolutePath.replace("\\", "/")

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:file:$dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '$schemaScriptPath'")
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
        }
    }
    generators {
        aggregate {
            enabled.set(true)
            unsupportedTablePolicy.set("SKIP")
        }
    }
}
```

```sql
create table video_post (
  id bigint primary key,
  title varchar(128) not null
);

create table audit_log (
  tenant_id bigint not null,
  event_id varchar(64) not null,
  payload varchar(255),
  constraint pk_audit_log primary key (tenant_id, event_id)
);
```

- [ ] **Step 4: Re-run the functional tests and verify success**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: PASS.

- [ ] **Step 5: Run the full verification slice**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-api:test \
          :cap4k-plugin-pipeline-core:test \
          :cap4k-plugin-pipeline-source-db:test \
          :cap4k-plugin-pipeline-source-design-json:test \
          :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-manifest-sample \
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-policy-sample \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover real project integration boundaries"
```

## Self-Review

- Spec coverage:
  - aggregate unsupported-table policy: covered in Tasks 1, 3, 4, 5
  - source-level filtering semantics: covered in Task 3
  - diagnostics in result/plan: covered in Tasks 3 and 4
  - manifest-driven design source: covered in Tasks 1, 2, and 5
  - Gradle DSL additions: covered in Task 1
- Placeholder scan:
  - no `TODO`, `TBD`, or “similar to Task N” references remain
  - every code-changing step includes concrete code snippets
- Type consistency:
  - `UnsupportedTablePolicy`, `PipelineDiagnostics`, `AggregateDiagnostics`, and `PlanReport` are introduced before later tasks reference them
  - `manifestFile` and `unsupportedTablePolicy` names are consistent across DSL, config mapping, source, and functional tests

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-11-cap4k-real-project-integration-boundaries.md`.

Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

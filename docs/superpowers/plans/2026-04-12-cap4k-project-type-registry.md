# Cap4k Project Type Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a project-level `type-registry.json` fallback for design generation so short project-local type names can resolve safely without class-name guessing.

**Architecture:** Keep the feature strictly project-scoped. Gradle reads and validates the registry file, normalizes it into `ProjectConfig`, and the design generator merges those entries into `DesignSymbolRegistry` as a fallback only. No source scanning, no sibling design-entry reuse, and no changes to pipeline stage ordering.

**Tech Stack:** Kotlin, Gradle plugin DSL, JUnit 5, Gradle TestKit, Gson JSON parsing, existing cap4k pipeline modules

---

## File Structure

### Files to Modify

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
  - Add normalized project type-registry data to `ProjectConfig`
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
  - Add `types { registryFile }` DSL block
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
  - Read `registryFile`, validate it, and pass normalized registry entries into `ProjectConfig`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
  - Merge project type-registry entries into `DesignSymbolRegistry`
- `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt`
  - Add registry-backed resolution tests and unsupported sibling-reference tests
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
  - Add DSL/config mapping tests for `types.registryFile`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  - Add functional success/failure coverage for registry usage

### Files to Create

- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/build.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/settings.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/iterate/design-manifest.json`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/iterate/type-registry.json`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/iterate/design/registry_design.json`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/demo-application/build.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/demo-domain/build.gradle.kts`
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/demo-adapter/build.gradle.kts`

---

### Task 1: Extend API and DSL for Project Type Registry

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Write the failing config-mapping tests**

```kotlin
@Test
fun `build maps project type registry entries into project config`() {
    val project = ProjectBuilder.builder().build()
    val registry = project.file("iterate/type-registry.json")
    registry.parentFile.mkdirs()
    registry.writeText(
        """
        {
          "VideoStatus": "edu.only4.danmuku.domain.aggregates.video_post.enums.VideoStatus"
        }
        """.trimIndent()
    )

    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    extension.project.basePackage.set("com.acme.demo")
    extension.project.applicationModulePath.set("demo-application")
    extension.sources.designJson.enabled.set(true)
    extension.sources.designJson.files.from(project.file("iterate/design.json"))
    extension.generators.design.enabled.set(true)
    extension.types.registryFile.set("iterate/type-registry.json")

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertEquals(
        mapOf("VideoStatus" to "edu.only4.danmuku.domain.aggregates.video_post.enums.VideoStatus"),
        config.typeRegistry
    )
}

@Test
fun `build fails when type registry overrides built in type`() {
    val project = ProjectBuilder.builder().build()
    val registry = project.file("iterate/type-registry.json")
    registry.parentFile.mkdirs()
    registry.writeText("""{ "String": "com.foo.String" }""")

    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    extension.project.basePackage.set("com.acme.demo")
    extension.project.applicationModulePath.set("demo-application")
    extension.sources.designJson.enabled.set(true)
    extension.sources.designJson.files.from(project.file("iterate/design.json"))
    extension.generators.design.enabled.set(true)
    extension.types.registryFile.set("iterate/type-registry.json")

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertTrue(error.message!!.contains("must not override built-in type"))
}
```

- [ ] **Step 2: Run the focused Gradle test to verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks
```

Expected: FAIL because `Cap4kExtension` has no `types` block and `ProjectConfig` has no `typeRegistry`.

- [ ] **Step 3: Add minimal API and DSL support**

```kotlin
data class ProjectConfig(
    val basePackage: String,
    val layout: ProjectLayout,
    val modules: Map<String, String>,
    val sources: Map<String, SourceConfig>,
    val generators: Map<String, GeneratorConfig>,
    val templates: TemplateConfig,
    val typeRegistry: Map<String, String> = emptyMap(),
)
```

```kotlin
open class Cap4kExtension @Inject constructor(objects: ObjectFactory) {
    val project: Cap4kProjectExtension = objects.newInstance(Cap4kProjectExtension::class.java)
    val sources: Cap4kSourcesExtension = objects.newInstance(Cap4kSourcesExtension::class.java)
    val generators: Cap4kGeneratorsExtension = objects.newInstance(Cap4kGeneratorsExtension::class.java)
    val templates: Cap4kTemplatesExtension = objects.newInstance(Cap4kTemplatesExtension::class.java)
    val types: Cap4kTypesExtension = objects.newInstance(Cap4kTypesExtension::class.java)

    fun types(block: Cap4kTypesExtension.() -> Unit) {
        types.block()
    }
}

open class Cap4kTypesExtension @Inject constructor(objects: ObjectFactory) {
    val registryFile: Property<String> = objects.property(String::class.java)
}
```

- [ ] **Step 4: Re-run the focused test to verify it still fails on missing parsing**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks
```

Expected: FAIL because the config factory still ignores `types.registryFile`.

- [ ] **Step 5: Commit the API/DSL foundation**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add project type registry config model"
```

### Task 2: Read and Validate Registry File in Gradle Config Factory

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing tests for registry-file validation**

```kotlin
@Test
fun `build fails when type registry file is missing`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    extension.project.basePackage.set("com.acme.demo")
    extension.project.applicationModulePath.set("demo-application")
    extension.sources.designJson.enabled.set(true)
    extension.sources.designJson.files.from(project.file("iterate/design.json"))
    extension.generators.design.enabled.set(true)
    extension.types.registryFile.set("iterate/missing-type-registry.json")

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertTrue(error.message!!.contains("type registry file does not exist"))
}

@Test
fun `build fails when type registry entry value is not fqcn`() {
    val project = ProjectBuilder.builder().build()
    val registry = project.file("iterate/type-registry.json")
    registry.parentFile.mkdirs()
    registry.writeText("""{ "VideoStatus": "VideoStatus" }""")

    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    extension.project.basePackage.set("com.acme.demo")
    extension.project.applicationModulePath.set("demo-application")
    extension.sources.designJson.enabled.set(true)
    extension.sources.designJson.files.from(project.file("iterate/design.json"))
    extension.generators.design.enabled.set(true)
    extension.types.registryFile.set("iterate/type-registry.json")

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertTrue(error.message!!.contains("must be a fully qualified type name"))
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks
```

Expected: FAIL because `Cap4kProjectConfigFactory` does not yet parse or validate the registry file.

- [ ] **Step 3: Implement registry loading and validation in Gradle**

```kotlin
private fun buildTypeRegistry(project: Project, extension: Cap4kExtension): Map<String, String> {
    val registryPath = extension.types.registryFile.optionalValue() ?: return emptyMap()
    val file = project.file(registryPath).canonicalFile
    require(file.exists()) { "type registry file does not exist: ${file.path}" }

    val root = file.reader(Charsets.UTF_8).use { reader -> JsonParser.parseReader(reader) }
    require(root.isJsonObject) { "type registry file is not a valid JSON object: ${file.path}" }

    return root.asJsonObject.entrySet()
        .associate { (rawKey, value) ->
            val key = rawKey.trim()
            require(key.isNotEmpty() && !key.contains('.')) {
                "type registry entry key must be a simple type name: $rawKey"
            }
            val fqcn = value.asString.trim()
            require(fqcn.contains('.')) {
                "type registry entry value must be a fully qualified type name: $fqcn"
            }
            require(key !in setOf("String", "List", "Set", "Map")) {
                "type registry entry must not override built-in type: $key"
            }
            key to fqcn
        }
}
```

```kotlin
return ProjectConfig(
    basePackage = basePackage,
    layout = ProjectLayout.MULTI_MODULE,
    modules = modules,
    sources = sources,
    generators = generators,
    templates = ...,
    typeRegistry = buildTypeRegistry(project, extension),
)
```

- [ ] **Step 4: Run the config-factory test again to verify it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks
```

Expected: PASS

- [ ] **Step 5: Commit the Gradle registry parsing layer**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: load project type registry from gradle config"
```

### Task 3: Merge Project Registry into Design Symbol Resolution

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSymbolRegistryTest.kt`

- [ ] **Step 1: Add failing generator tests for project-registry fallback and sibling-reference failure**

```kotlin
@Test
fun `render model factory resolves short type from project config registry`() {
    val request = RequestModel(
        kind = RequestKind.COMMAND,
        packageName = "video_post",
        typeName = "SyncVideoPostProcessStatusCmd",
        description = "",
        requestFields = listOf(FieldModel(name = "targetStatus", type = "VideoStatus")),
    )

    val model = DesignRenderModelFactory.create(
        packageName = "edu.only4.danmuku.application.commands.video_post",
        request = request,
        typeRegistry = mapOf(
            "VideoStatus" to "edu.only4.danmuku.domain.aggregates.video_post.enums.VideoStatus"
        ),
    )

    assertEquals(
        listOf("edu.only4.danmuku.domain.aggregates.video_post.enums.VideoStatus"),
        model.imports
    )
    assertEquals("VideoStatus", model.requestFields.single().renderedType)
}

@Test
fun `render model factory keeps failing on sibling design entry short type`() {
    val request = RequestModel(
        kind = RequestKind.COMMAND,
        packageName = "video_post_processing",
        typeName = "StartVideoPostProcessingCmd",
        description = "",
        requestFields = listOf(FieldModel(name = "fileList", type = "List<VideoPostProcessingFileSpec>")),
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        DesignRenderModelFactory.create(
            packageName = "edu.only4.danmuku.application.commands.video_post_processing",
            request = request,
            typeRegistry = emptyMap(),
        )
    }

    assertTrue(error.message!!.contains("VideoPostProcessingFileSpec"))
    assertTrue(error.message!!.contains("sibling design-entry references are not supported"))
}
```

- [ ] **Step 2: Run the generator-design tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeResolverTest" --rerun-tasks
```

Expected: FAIL because `DesignRenderModelFactory.create(...)` does not yet accept project type-registry entries.

- [ ] **Step 3: Implement project-registry merge and explicit sibling-reference diagnostics**

```kotlin
internal object DesignRenderModelFactory {
    fun create(
        packageName: String,
        request: RequestModel,
        typeRegistry: Map<String, String> = emptyMap(),
    ): DesignRenderModel {
        ...
        val symbolRegistry = buildSymbolRegistry(request, requestNamespace, responseNamespace, typeRegistry)
        ...
    }

    private fun buildSymbolRegistry(
        request: RequestModel,
        requestNamespace: NamespaceModel,
        responseNamespace: NamespaceModel,
        typeRegistry: Map<String, String>,
    ): DesignSymbolRegistry {
        val registry = DesignSymbolRegistry()
        ...
        typeRegistry.forEach { (simpleName, fqcn) ->
            registry.register(
                SymbolIdentity(
                    packageName = fqcn.substringBeforeLast('.'),
                    typeName = simpleName,
                    source = "project-type-registry",
                )
            )
        }
        return registry
    }
}
```

```kotlin
private fun validateFieldType(
    field: PreparedFieldModel,
    symbolRegistry: DesignSymbolRegistry,
    innerTypeNames: Set<String>,
) {
    try {
        ImportResolver.resolve(
            type = field.resolvedType,
            innerTypeNames = innerTypeNames,
            symbolRegistry = symbolRegistry,
        )
    } catch (ex: IllegalArgumentException) {
        val message = "failed to resolve type for field ${field.sourceName}: ${field.resolvedType.rawText} " +
            "(${ex.message}; use a fully qualified name or register it in type-registry.json; " +
            "sibling design-entry references are not supported)"
        throw IllegalArgumentException(message, ex)
    }
}
```

- [ ] **Step 4: Run the generator-design test suite to verify it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --rerun-tasks
```

Expected: PASS

- [ ] **Step 5: Commit the design-generator integration**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSymbolRegistryTest.kt
git commit -m "feat: resolve design short types from project registry"
```

### Task 4: Add Functional Fixtures for End-to-End Registry Behavior

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/iterate/design-manifest.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/iterate/type-registry.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/iterate/design/registry_design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/demo-adapter/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing functional tests for registry success and unsupported sibling reference**

```kotlin
@Test
fun `cap4kGenerate resolves short type from project registry file`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-type-registry")
    copyFixture(projectDir, "design-type-registry-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val commandFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/commands/video/sync/SyncVideoStatusCmd.kt"
    )
    val content = commandFile.readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(content.contains("import edu.only4.danmuku.domain.aggregates.video_post.enums.VideoStatus"))
    assertTrue(content.contains("val targetStatus: VideoStatus"))
}

@Test
fun `cap4kGenerate fails when design references sibling payload entry short type`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-sibling-short-type")
    copyFixture(projectDir, "design-type-registry-sample")

    projectDir.resolve("iterate/design/registry_design.json").writeText(
        """
        [
          {
            "tag": "payload",
            "package": "video_post_processing",
            "name": "VideoPostProcessingFileSpec",
            "desc": "",
            "requestFields": [],
            "responseFields": []
          },
          {
            "tag": "cmd",
            "package": "video_post_processing",
            "name": "StartVideoPostProcessing",
            "desc": "",
            "requestFields": [
              { "name": "fileList", "type": "List<VideoPostProcessingFileSpec>" }
            ],
            "responseFields": []
          }
        ]
        """.trimIndent()
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .buildAndFail()

    assertTrue(result.output.contains("VideoPostProcessingFileSpec"))
    assertTrue(result.output.contains("sibling design-entry references are not supported"))
}
```

- [ ] **Step 2: Run the functional test target to verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks
```

Expected: FAIL because the fixture and new DSL path are not wired yet.

- [ ] **Step 3: Create the fixture and wire the functional test**

```kotlin
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
        design {
            enabled.set(true)
        }
    }
    types {
        registryFile.set("iterate/type-registry.json")
    }
}
```

```json
{
  "VideoStatus": "edu.only4.danmuku.domain.aggregates.video_post.enums.VideoStatus"
}
```

```json
[
  {
    "tag": "cmd",
    "package": "video.sync",
    "name": "SyncVideoStatus",
    "desc": "sync video status",
    "requestFields": [
      { "name": "videoPostId", "type": "Long" },
      { "name": "targetStatus", "type": "VideoStatus" }
    ],
    "responseFields": [
      { "name": "success", "type": "Boolean" }
    ]
  }
]
```

- [ ] **Step 4: Run the focused functional tests, then the full touched-module verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks
./gradlew :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: PASS

- [ ] **Step 5: Commit the fixture and end-to-end coverage**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: add project type registry functional coverage"
```

## Self-Review

### Spec Coverage

- Project-level `types.registryFile` is covered in Task 1 and Task 2
- JSON object registry format and validation are covered in Task 2
- Fallback-only resolution order is preserved in Task 3
- Sibling design-entry references remain unsupported in Task 3 and Task 4
- Functional end-to-end coverage is covered in Task 4

No spec gaps remain.

### Placeholder Scan

- No `TODO`, `TBD`, or "implement later" placeholders remain
- Each code-changing step includes concrete code snippets
- Each verification step has exact commands and expected outcomes

### Type Consistency

- The plan consistently uses `typeRegistry` as the normalized `ProjectConfig` field name
- The DSL consistently uses `types.registryFile`
- The generator integration consistently merges registry entries into `DesignSymbolRegistry`

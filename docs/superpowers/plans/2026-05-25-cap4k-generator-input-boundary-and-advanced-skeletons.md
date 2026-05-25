# Cap4k Generator Input Boundary And Advanced Skeletons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement #84 as one generator-boundary iteration across `cap4k` and `only-engine`: design input becomes generation intent, enum/value-object inputs move into `types`, core validator generation moves to addon SPI, and domain-service/Saga/value-object skeleton generation is added without generating business logic.

**Architecture:** Keep the existing pipeline shape and avoid the broader internal source/input naming cleanup tracked by #88. Extend canonical models and planners only where they express auditable skeletons or type wiring; addon providers remain post-canonical artifact contributors with provider-scoped options and no mutation rights.

**Tech Stack:** Kotlin, Gradle plugin DSL, Pebble templates, JUnit/Kotest-style project tests, cap4k pipeline modules, only-engine `engine-cap4k-addon`.

---

## Scope Check

This plan intentionally covers `cap4k` and `only-engine` in one pass because the issue owner explicitly chose a single large iteration with no compatibility period and no phased rollout. The implementation is still ordered as reviewable commits so each task leaves the repository in a testable state.

Do all cap4k work in:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\issue-84-generator-boundary
```

When the plan reaches only-engine work, create or reuse an only-engine worktree, then verify it against this cap4k worktree with:

```powershell
.\gradlew.bat --no-daemon "-PonlyEngine.cap4kCompositePath=C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/issue-84-generator-boundary" :engine-cap4k-addon:test
```

## File Structure

### cap4k API and config

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`: add value-object, domain-service, Saga, addon-provider option models; remove validator from canonical core.
- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`: add types value-object manifest config, addon provider config, layout entries for value objects/domain services/Sagas; remove public design-validator layout entry.
- Modify tests under `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api`: lock model defaults and layout ids.

### cap4k Gradle DSL

- Modify `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`: move enum manifest under `types`, add value-object manifest and addon provider options, remove public design-family generator extension blocks.
- Modify `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`: map new DSL into `ProjectConfig`, internally enable design planners, defer module-path validation to non-empty planners.
- Modify `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`: register new source/planner providers and preserve addon loading.
- Modify Gradle DSL tests under `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle`.

### cap4k sources and canonical assembly

- Modify `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`: remove `validator`, add `domain_service` and `saga`.
- Create `cap4k-plugin-pipeline-source-value-object-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProvider.kt`: parse `types.valueObjectManifest`.
- Modify `settings.gradle.kts` and the new source module build file.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`: assemble value objects, domain services, and Sagas; reject duplicate type simple names across enum manifest, value-object manifest, and registry.
- Modify tests under source/core modules.

### cap4k generators and templates

- Modify files under `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design`: remove validator planner/render model; add domain-service and Saga planners/render models.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb`: delete it during validator removal.
- Create `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_service.kt.peb`, `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/saga_param.kt.peb`, `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/saga_result.kt.peb`, and `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/saga_handler.kt.peb`.
- Create `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`: generate checked-in JSON value-object skeletons with nested JPA converters.
- Create `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/types/value_object.kt.peb`.
- Modify `settings.gradle.kts` and plugin registration for the new generator module.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`: bind DB `@T` fields to manifest-managed value objects and set nested converter refs.
- Modify aggregate/design/types generator tests.

### cap4k addon SPI and docs

- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`: pass provider-scoped options, fail on unloaded configured providers, validate addon template namespace, wrap provider failures with provider id.
- Modify addon SPI tests under `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core`.
- Update docs under `docs/public` and shared workspace skills under `C:\Users\LD_moxeii\Documents\code\only-workspace\.agents\skills` that mention design generator switches, validator generation, enum manifest source ownership, value-object generation, addon behavior, or checked-in source policy.

### only-engine addon reference

- Modify `only-engine/engine-cap4k-addon/src/main/kotlin/com/only/engine/cap4k/addon/translation/OnlyEngineEnumTranslationAddonProvider.kt` only if SPI signature changes.
- Create `only-engine/engine-cap4k-addon/src/main/kotlin/com/only/engine/cap4k/addon/validation/OnlyEngineValidatorAddonProvider.kt`.
- Create `only-engine/engine-cap4k-addon/src/main/resources/cap4k/addons/only-engine-validator/validator.kt.peb`.
- Modify addon service registration in `only-engine/engine-cap4k-addon/src/main/resources/META-INF/services/com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider`.
- Modify or add tests under `only-engine/engine-cap4k-addon/src/test/kotlin/com/only/engine/cap4k/addon`.
- Update `only-engine/engine-cap4k-addon/README.md`.

## Tasks

### Task 1: Lock Public API Model Shape

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Test: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt`

- [ ] **Step 1: Write failing API model tests**

Add tests that instantiate the new target model shape and assert old validator shape is gone from canonical construction:

```kotlin
@Test
fun `canonical model carries value objects domain services and sagas`() {
    val model = CanonicalModel(
        project = ProjectModel(group = "com.acme", name = "demo"),
        aggregates = emptyList(),
        commands = emptyList(),
        queries = emptyList(),
        clients = emptyList(),
        apiPayloads = emptyList(),
        domainEvents = emptyList(),
        integrationEvents = emptyList(),
        strongIds = emptyList(),
        sharedEnums = emptyList(),
        valueObjects = listOf(
            ValueObjectModel(
                name = "Money",
                packageName = "shared.values",
                scope = ValueObjectScope.SHARED,
                aggregate = null,
                storage = ValueObjectStorage.JSON,
                fields = listOf(FieldModel(name = "amount", type = "BigDecimal"))
            )
        ),
        domainServices = listOf(
            DomainServiceModel(
                name = "ContentPublicationPolicy",
                packageName = "content.domain",
                description = "publication policy",
                aggregates = listOf("Content")
            )
        ),
        sagas = listOf(
            SagaModel(
                name = "PublishContentSaga",
                packageName = "content.workflow",
                description = "publish content",
                requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
                responseFields = listOf(FieldModel(name = "accepted", type = "Boolean"))
            )
        ),
        typeRegistry = TypeRegistryModel.empty()
    )

    assertEquals(1, model.valueObjects.size)
    assertEquals(ValueObjectScope.SHARED, model.valueObjects.single().scope)
    assertEquals("ContentPublicationPolicy", model.domainServices.single().name)
    assertEquals("PublishContentSaga", model.sagas.single().name)
}
```

Add a config test:

```kotlin
@Test
fun `project config stores type manifests and addon provider options`() {
    val config = ProjectConfig(
        typeRegistry = TypeRegistryConfig(
            registryFile = "design/type-registry.json",
            enumManifestFiles = listOf("design/enums.json"),
            valueObjectManifestFiles = listOf("design/value-objects.json")
        ),
        addons = mapOf(
            "only-engine-validator" to AddonProviderConfig(
                id = "only-engine-validator",
                options = mapOf("manifestFile" to "validation/validators.json")
            )
        )
    )

    assertEquals(listOf("design/enums.json"), config.typeRegistry.enumManifestFiles)
    assertEquals(listOf("design/value-objects.json"), config.typeRegistry.valueObjectManifestFiles)
    assertEquals("validation/validators.json", config.addons.getValue("only-engine-validator").options["manifestFile"])
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest*" --tests "*ProjectConfigTest*"
```

Expected: compile failure for `ValueObjectModel`, `ValueObjectScope`, `ValueObjectStorage`, `DomainServiceModel`, `SagaModel`, `AddonProviderConfig`, or `valueObjectManifestFiles`.

- [ ] **Step 3: Implement API models**

In `PipelineModels.kt`, add:

```kotlin
enum class ValueObjectScope {
    SHARED,
    AGGREGATE,
}

enum class ValueObjectStorage {
    JSON,
}

data class ValueObjectModel(
    val name: String,
    val packageName: String,
    val scope: ValueObjectScope,
    val aggregate: String? = null,
    val storage: ValueObjectStorage = ValueObjectStorage.JSON,
    val fields: List<FieldModel> = emptyList(),
    val description: String? = null,
)

data class DomainServiceModel(
    val name: String,
    val packageName: String,
    val description: String? = null,
    val aggregates: List<String> = emptyList(),
)

data class SagaModel(
    val name: String,
    val packageName: String,
    val description: String? = null,
    val requestFields: List<FieldModel> = emptyList(),
    val responseFields: List<FieldModel> = emptyList(),
)
```

Update `CanonicalModel` constructor defaults to include:

```kotlin
val valueObjects: List<ValueObjectModel> = emptyList(),
val domainServices: List<DomainServiceModel> = emptyList(),
val sagas: List<SagaModel> = emptyList(),
```

Remove `val validators: List<ValidatorModel>` from `CanonicalModel`. Remove `ValidatorModel` and `ValidatorParameterModel` from core pipeline API unless a compile reference from only-engine migration still requires a private copy; if so, only-engine owns that model in its addon module, not cap4k API.

In `ProjectConfig.kt`, add:

```kotlin
data class AddonProviderConfig(
    val id: String,
    val options: Map<String, String> = emptyMap(),
)
```

Extend `ProjectConfig`:

```kotlin
val addons: Map<String, AddonProviderConfig> = emptyMap(),
```

Extend `TypeRegistryConfig`:

```kotlin
val enumManifestFiles: List<String> = emptyList(),
val valueObjectManifestFiles: List<String> = emptyList(),
```

Extend artifact layout config with:

```kotlin
val designDomainService: ArtifactLayoutRule = ArtifactLayoutRule("design/domain-service"),
val designSagaParam: ArtifactLayoutRule = ArtifactLayoutRule("design/saga-param"),
val designSagaResult: ArtifactLayoutRule = ArtifactLayoutRule("design/saga-result"),
val designSagaHandler: ArtifactLayoutRule = ArtifactLayoutRule("design/saga-handler"),
val valueObject: ArtifactLayoutRule = ArtifactLayoutRule("types/value-object"),
```

Remove `designValidator` from the public layout config.

- [ ] **Step 4: Run API tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test
```

Expected: PASS for the API module. If downstream modules do not compile yet, keep this task scoped to the API module.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin cap4k-plugin-pipeline-api/src/test/kotlin
git commit -m "feat: add generator boundary API models"
```

### Task 2: Replace Public Gradle Generator Switches With Input-Driven Design Planning

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Write failing Gradle config tests**

Add tests covering the target DSL mapping:

```kotlin
@Test
fun `types block owns enum and value object manifests`() {
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java, project)

    extension.types {
        registryFile.set("design/type-registry.json")
        enumManifest {
            files.from("design/enums.json")
        }
        valueObjectManifest {
            files.from("design/value-objects.json")
        }
    }

    val config = Cap4kProjectConfigFactory(project, extension).create()

    assertEquals("design/type-registry.json", config.typeRegistry.registryFile)
    assertEquals(listOf("design/enums.json"), config.typeRegistry.enumManifestFiles)
    assertEquals(listOf("design/value-objects.json"), config.typeRegistry.valueObjectManifestFiles)
}
```

Add addon option mapping:

```kotlin
@Test
fun `addons block maps provider scoped options`() {
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java, project)

    extension.addons {
        provider("only-engine-validator") {
            option("manifestFile", "validation/validators.json")
            option("strict", "true")
        }
    }

    val config = Cap4kProjectConfigFactory(project, extension).create()

    assertEquals(
        mapOf("manifestFile" to "validation/validators.json", "strict" to "true"),
        config.addons.getValue("only-engine-validator").options
    )
}
```

Add no-public-design-switch assertion by using reflection on `Cap4kGeneratorsExtension`:

```kotlin
@Test
fun `generators extension exposes only explicit non design switches`() {
    val methodNames = Cap4kGeneratorsExtension::class.java.methods.map { it.name }.toSet()

    assertTrue("aggregate" in methodNames)
    assertTrue("flow" in methodNames)
    assertTrue("drawingBoard" in methodNames)
    assertFalse("designCommand" in methodNames)
    assertFalse("designValidator" in methodNames)
    assertFalse("designDomainEvent" in methodNames)
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest*"
```

Expected: compile failure for `valueObjectManifest`, `addons`, or removed design-family DSL methods still present.

- [ ] **Step 3: Implement DSL shape**

In `Cap4kExtension.kt`:

- add `types.enumManifest { files.from("design/enums.json") }`;
- add `types.valueObjectManifest { files.from("design/value-objects.json") }`;
- add `addons { provider("id") { option("key", "value") } }`;
- remove these public generator blocks: `designCommand`, `designQuery`, `designQueryHandler`, `designClient`, `designClientHandler`, `designValidator`, `designApiPayload`, `designDomainEvent`, `designDomainEventHandler`, `designIntegrationEvent`, `designIntegrationEventSubscriber`.

Use a provider config extension shaped like:

```kotlin
abstract class Cap4kAddonProviderExtension @Inject constructor(
    val id: String,
    objects: ObjectFactory,
) {
    val options: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

    fun option(key: String, value: String) {
        options.put(key, value)
    }
}
```

In `Cap4kProjectConfigFactory.kt`:

- read enum files from `extension.types.enumManifest.files`;
- read value-object files from `extension.types.valueObjectManifest.files`;
- map addon providers into `ProjectConfig.addons`;
- always register design planner generator ids internally when design source exists;
- keep explicit `aggregate`, `flow`, and `drawingBoard` generator states.

- [ ] **Step 4: Run Gradle config tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest*"
```

Expected: PASS for the targeted tests.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/src/main/kotlin cap4k-plugin-pipeline-gradle/src/test/kotlin
git commit -m "feat: align generator gradle dsl with input contracts"
```

### Task 3: Add Value-Object Manifest Source Provider

**Files:**
- Create: `cap4k-plugin-pipeline-source-value-object-manifest/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-source-value-object-manifest/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProvider.kt`
- Create: `cap4k-plugin-pipeline-source-value-object-manifest/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/valueobject/ValueObjectManifestSourceProviderTest.kt`
- Modify: `settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`

- [ ] **Step 1: Write failing provider tests**

Test successful parse:

```kotlin
@Test
fun `parses shared and aggregate json value objects`() {
    val file = tempDir.resolve("value-objects.json").toFile()
    file.writeText(
        """
        [
          {
            "name": "Money",
            "scope": "shared",
            "package": "shared.values",
            "storage": "json",
            "fields": [
              { "name": "amount", "type": "BigDecimal" },
              { "name": "currency", "type": "String" }
            ]
          },
          {
            "name": "PublishWindow",
            "scope": "aggregate",
            "aggregate": "Content",
            "package": "content.values",
            "storage": "json",
            "fields": [
              { "name": "startAt", "type": "Instant", "nullable": true }
            ]
          }
        ]
        """.trimIndent()
    )

    val snapshot = ValueObjectManifestSourceProvider().load(listOf(file.toPath()))

    assertEquals(2, snapshot.valueObjects.size)
    assertEquals(ValueObjectScope.SHARED, snapshot.valueObjects[0].scope)
    assertEquals(ValueObjectScope.AGGREGATE, snapshot.valueObjects[1].scope)
    assertEquals("Content", snapshot.valueObjects[1].aggregate)
}
```

Test validation:

```kotlin
@Test
fun `aggregate scope requires aggregate name`() {
    val file = tempDir.resolve("value-objects.json").toFile()
    file.writeText(
        """
        [
          {
            "name": "PublishWindow",
            "scope": "aggregate",
            "package": "content.values",
            "storage": "json",
            "fields": []
          }
        ]
        """.trimIndent()
    )

    val error = assertFailsWith<IllegalArgumentException> {
        ValueObjectManifestSourceProvider().load(listOf(file.toPath()))
    }

    assertTrue(error.message!!.contains("aggregate scope requires aggregate"))
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-value-object-manifest:test
```

Expected: Gradle cannot find the module or tests fail because the provider does not exist.

- [ ] **Step 3: Implement source module**

Add the module to `settings.gradle.kts`:

```kotlin
include("cap4k-plugin-pipeline-source-value-object-manifest")
```

Create `build.gradle.kts` mirroring the enum manifest source module dependencies, with dependencies on pipeline API and JSON parsing libraries already used by `source-design-json` or `source-enum-manifest`.

Implement `ValueObjectManifestSourceProvider` with:

```kotlin
class ValueObjectManifestSourceProvider {
    fun load(files: List<Path>): ValueObjectManifestSnapshot {
        require(files.isNotEmpty()) { "types.valueObjectManifest.files must not be empty when valueObjectManifest is configured" }
        val valueObjects = files.flatMap { file -> parseFile(file) }
        validateDuplicateNames(valueObjects)
        return ValueObjectManifestSnapshot(valueObjects)
    }
}
```

Validation rules:

- `scope` accepts only `shared` and `aggregate`;
- `storage` accepts only `json`;
- aggregate scope requires `aggregate`;
- shared scope must not set aggregate;
- fields require `name` and `type`;
- shared simple names are globally unique;
- aggregate-local names are unique within `(aggregate, name)`.

- [ ] **Step 4: Register provider in plugin**

In `PipelinePlugin.kt`, instantiate the provider when `config.typeRegistry.valueObjectManifestFiles` is non-empty and feed its snapshot into the runner source snapshots using the same pattern as enum manifest.

- [ ] **Step 5: Run provider tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-value-object-manifest:test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add settings.gradle.kts cap4k-plugin-pipeline-source-value-object-manifest cap4k-plugin-pipeline-gradle/src/main/kotlin
git commit -m "feat: add value object manifest source"
```

### Task 4: Normalize Design JSON Tags

**Files:**
- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Test: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`

- [ ] **Step 1: Write failing design parser tests**

Add:

```kotlin
@Test
fun `parses domain service and saga entries`() {
    val file = tempDir.resolve("design.json").toFile()
    file.writeText(
        """
        [
          {
            "tag": "domain_service",
            "package": "content.domain",
            "name": "ContentPublicationPolicy",
            "desc": "publication policy",
            "aggregates": ["Content"]
          },
          {
            "tag": "saga",
            "package": "content.workflow",
            "name": "PublishContentSaga",
            "desc": "publish content",
            "requestFields": [{ "name": "contentId", "type": "ContentId" }],
            "responseFields": [{ "name": "accepted", "type": "Boolean" }]
          }
        ]
        """.trimIndent()
    )

    val snapshot = DesignJsonSourceProvider().load(listOf(file.toPath()))

    assertEquals(listOf("domain_service", "saga"), snapshot.entries.map { it.tag })
    assertEquals(listOf("Content"), snapshot.entries[0].aggregates)
    assertEquals("contentId", snapshot.entries[1].requestFields.single().name)
}
```

Add validator rejection without migration wording:

```kotlin
@Test
fun `validator tag is unsupported as a normal design tag`() {
    val file = tempDir.resolve("design.json").toFile()
    file.writeText("""[{ "tag": "validator", "package": "content.validation", "name": "ValidAuthor" }]""")

    val error = assertFailsWith<IllegalArgumentException> {
        DesignJsonSourceProvider().load(listOf(file.toPath()))
    }

    assertTrue(error.message!!.contains("Unsupported design tag: validator"))
    assertFalse(error.message!!.contains("migration"))
    assertFalse(error.message!!.contains("deprecated"))
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-design-json:test --tests "*DesignJsonSourceProviderTest*"
```

Expected: `domain_service` and `saga` unsupported, or `validator` still accepted.

- [ ] **Step 3: Implement parser cleanup**

In `DesignJsonSourceProvider.kt`:

- set supported tags to `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `domain_service`, `saga`;
- remove validator-specific parsing helpers;
- keep `requestFields` and `responseFields` for Saga;
- keep `aggregates` for domain service;
- use the normal unsupported-tag error:

```kotlin
throw IllegalArgumentException("Unsupported design tag: $tag")
```

- [ ] **Step 4: Run parser tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-source-design-json:test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-source-design-json/src/main/kotlin cap4k-plugin-pipeline-source-design-json/src/test/kotlin
git commit -m "feat: normalize design json tags"
```

### Task 5: Assemble Canonical Types, Domain Services, And Sagas

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write failing canonical assembler tests**

Add:

```kotlin
@Test
fun `assembles domain services sagas and value objects`() {
    val design = DesignElementSnapshot(
        entries = listOf(
            DesignSpecEntry(
                tag = "domain_service",
                packageName = "content.domain",
                name = "ContentPublicationPolicy",
                desc = "publication policy",
                aggregates = listOf("Content")
            ),
            DesignSpecEntry(
                tag = "saga",
                packageName = "content.workflow",
                name = "PublishContentSaga",
                desc = "publish content",
                requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
                responseFields = listOf(FieldModel(name = "accepted", type = "Boolean"))
            )
        )
    )
    val valueObjects = ValueObjectManifestSnapshot(
        valueObjects = listOf(
            ValueObjectModel(
                name = "Money",
                packageName = "shared.values",
                scope = ValueObjectScope.SHARED,
                storage = ValueObjectStorage.JSON,
                fields = listOf(FieldModel(name = "amount", type = "BigDecimal"))
            )
        )
    )

    val model = assemble(design = design, valueObjects = valueObjects)

    assertEquals("ContentPublicationPolicy", model.domainServices.single().name)
    assertEquals("PublishContentSaga", model.sagas.single().name)
    assertEquals("Money", model.valueObjects.single().name)
}
```

Add duplicate type simple-name test:

```kotlin
@Test
fun `fails on duplicate simple type names across enum value object and registry`() {
    val valueObjects = ValueObjectManifestSnapshot(
        valueObjects = listOf(
            ValueObjectModel(
                name = "Status",
                packageName = "shared.values",
                scope = ValueObjectScope.SHARED,
                storage = ValueObjectStorage.JSON
            )
        )
    )
    val typeRegistry = TypeRegistryModel(
        types = listOf(TypeRegistryEntry(simpleName = "Status", qualifiedName = "com.acme.Status"))
    )

    val error = assertFailsWith<IllegalArgumentException> {
        assemble(valueObjects = valueObjects, typeRegistry = typeRegistry)
    }

    assertTrue(error.message!!.contains("Duplicate type simple name: Status"))
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"
```

Expected: compile failure or assertion failure because canonical assembler does not populate new model lists.

- [ ] **Step 3: Implement canonical assembly**

In `DefaultCanonicalAssembler.kt`:

- map `domain_service` entries to `DomainServiceModel`;
- map `saga` entries to `SagaModel`;
- include `ValueObjectManifestSnapshot.valueObjects` in `CanonicalModel.valueObjects`;
- remove validator assembly;
- validate duplicate simple names across:
  - shared enum manifest entries;
  - aggregate-local enum manifest entries where simple-name ambiguity affects unqualified `@T`;
  - value-object manifest entries;
  - type registry entries.

Use an error message beginning with:

```text
Duplicate type simple name: <Name>
```

- [ ] **Step 4: Run core tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest*"
```

Expected: PASS for targeted canonical tests.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin cap4k-plugin-pipeline-core/src/test/kotlin
git commit -m "feat: assemble advanced design and type inputs"
```

### Task 6: Generate Domain-Service And Saga Skeletons

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainServiceArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainServiceRenderModels.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSagaArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSagaRenderModels.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/resources/META-INF/services/com.only4.cap4k.plugin.pipeline.api.GeneratorProvider`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_service.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/saga_param.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/saga_result.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/saga_handler.kt.peb`
- Test: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainServiceArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignSagaArtifactPlannerTest.kt`

- [ ] **Step 1: Write failing planner tests**

Domain service test:

```kotlin
@Test
fun `plans checked in domain service skeleton`() {
    val model = canonicalModel(
        domainServices = listOf(
            DomainServiceModel(
                name = "ContentPublicationPolicy",
                packageName = "content.domain",
                description = "publication policy",
                aggregates = listOf("Content")
            )
        )
    )

    val items = DesignDomainServiceArtifactPlanner().plan(configWithDomainModule(), model)

    assertEquals("design/domain-service", items.single().templateId)
    assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, items.single().outputKind)
    assertTrue(items.single().path.endsWith("ContentPublicationPolicy.kt"))
}
```

Saga test:

```kotlin
@Test
fun `plans saga param result and handler skeletons`() {
    val model = canonicalModel(
        sagas = listOf(
            SagaModel(
                name = "PublishContentSaga",
                packageName = "content.workflow",
                requestFields = listOf(FieldModel(name = "contentId", type = "ContentId")),
                responseFields = listOf(FieldModel(name = "accepted", type = "Boolean"))
            )
        )
    )

    val items = DesignSagaArtifactPlanner().plan(configWithApplicationModule(), model)

    assertEquals(
        setOf("design/saga-param", "design/saga-result", "design/saga-handler"),
        items.map { it.templateId }.toSet()
    )
    assertTrue(items.all { it.outputKind == ArtifactOutputKind.CHECKED_IN_SOURCE })
}
```

Add empty-slice tests:

```kotlin
@Test
fun `empty domain service slice does not require domain module`() {
    val items = DesignDomainServiceArtifactPlanner().plan(configWithoutDomainModule(), canonicalModel(domainServices = emptyList()))
    assertTrue(items.isEmpty())
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-design:test --tests "*DomainService*" --tests "*Saga*"
```

Expected: planner classes or template ids do not exist.

- [ ] **Step 3: Implement planners**

Add `DesignDomainServiceArtifactPlanner`:

- returns empty when `model.domainServices` is empty;
- requires domain module only when non-empty;
- emits checked-in source plan items;
- template id `design/domain-service`.

Add `DesignSagaArtifactPlanner`:

- returns empty when `model.sagas` is empty;
- requires application module only when non-empty;
- emits checked-in source plan items;
- template ids `design/saga-param`, `design/saga-result`, `design/saga-handler`.

Register both in the design generator provider list.

- [ ] **Step 4: Add templates**

Domain-service template output shape:

```kotlin
package {{ packageName }}

import com.only4.cap4k.ddd.domain.distributed.annotation.DomainService

@DomainService
class {{ name }}
```

Saga handler template output shape:

```kotlin
package {{ packageName }}

import com.only4.cap4k.ddd.application.saga.SagaHandler

class {{ name }}Handler : SagaHandler<{{ name }}Param, {{ name }}Result> {
    override fun exec(param: {{ name }}Param): {{ name }}Result {
        throw UnsupportedOperationException("Author implements saga process.")
    }
}
```

Use the exact Saga runtime interface names present in the repository. If the method is not `exec`, inspect existing Saga runtime code and use that signature consistently.

- [ ] **Step 5: Run design generator tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-design:test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin cap4k-plugin-pipeline-generator-design/src/main/resources cap4k-plugin-pipeline-generator-design/src/test/kotlin
git commit -m "feat: generate domain service and saga skeletons"
```

### Task 7: Generate JSON Value Objects As Checked-In Source

**Files:**
- Create: `cap4k-plugin-pipeline-generator-types/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/types/value_object.kt.peb`
- Create: `cap4k-plugin-pipeline-generator-types/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlannerTest.kt`
- Modify: `settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`

- [ ] **Step 1: Write failing value-object planner tests**

Add:

```kotlin
@Test
fun `plans checked in json value object with nested converter`() {
    val model = canonicalModel(
        valueObjects = listOf(
            ValueObjectModel(
                name = "Money",
                packageName = "shared.values",
                scope = ValueObjectScope.SHARED,
                storage = ValueObjectStorage.JSON,
                fields = listOf(
                    FieldModel(name = "amount", type = "BigDecimal"),
                    FieldModel(name = "currency", type = "String")
                )
            )
        )
    )

    val items = ValueObjectArtifactPlanner().plan(configWithDomainModule(), model)

    val item = items.single()
    assertEquals("types/value-object", item.templateId)
    assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, item.outputKind)
    assertTrue(item.path.endsWith("Money.kt"))
    assertEquals(ConflictPolicy.SKIP, item.conflictPolicy)
}
```

Add rendered template assertion:

```kotlin
@Test
fun `renders nested converter fqn shape`() {
    val source = renderValueObject(
        ValueObjectModel(
            name = "Money",
            packageName = "shared.values",
            scope = ValueObjectScope.SHARED,
            storage = ValueObjectStorage.JSON,
            fields = listOf(FieldModel(name = "amount", type = "BigDecimal"))
        )
    )

    assertTrue(source.contains("data class Money("))
    assertTrue(source.contains("class Converter : AttributeConverter<Money, String>"))
    assertTrue(source.contains("ObjectMapper().findAndRegisterModules()"))
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-types:test
```

Expected: module missing.

- [ ] **Step 3: Implement generator-types module**

Add module include:

```kotlin
include("cap4k-plugin-pipeline-generator-types")
```

Planner behavior:

- empty `model.valueObjects` returns empty;
- non-empty value objects require domain module path;
- only `ValueObjectStorage.JSON` supported;
- output kind is `CHECKED_IN_SOURCE`;
- conflict policy comes from template defaults and defaults to `SKIP`;
- shared and aggregate-local value objects both generate under domain module using their declared package.

- [ ] **Step 4: Add value-object template**

Template output shape:

```kotlin
package {{ packageName }}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

data class {{ name }}(
{% for field in fields %}
    val {{ field.name }}: {{ field.typeRef }}{{ "?" if field.nullable else "" }},
{% endfor %}
) {
    @Converter(autoApply = false)
    class Converter : AttributeConverter<{{ name }}, String> {
        override fun convertToDatabaseColumn(attribute: {{ name }}?): String? {
            return attribute?.let { objectMapper.writeValueAsString(it) }
        }

        override fun convertToEntityAttribute(dbData: String?): {{ name }}? {
            return dbData
                ?.takeIf { it.isNotBlank() }
                ?.let { objectMapper.readValue<{{ name }}>(it) }
        }

        companion object {
            private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()
        }
    }
}
```

Import field types using the same type-reference/import helper used by design generator templates.

- [ ] **Step 5: Register planner**

In `PipelinePlugin.kt`, include the new types planner provider so value-object artifacts appear whenever `CanonicalModel.valueObjects` is non-empty. Do not add a public generator switch.

- [ ] **Step 6: Run generator-types tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-types:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add settings.gradle.kts cap4k-plugin-pipeline-generator-types cap4k-plugin-pipeline-gradle/src/main/kotlin
git commit -m "feat: generate json value object skeletons"
```

### Task 8: Bind DB Fields To Manifest-Managed Value Objects

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInferenceTest.kt`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlannerTest.kt`

- [ ] **Step 1: Write failing type-resolution tests**

Add:

```kotlin
@Test
fun `aggregate local value object wins before shared value object`() {
    val controls = AggregateJpaControlInference.fromModel(
        aggregateName = "Content",
        fields = listOf(fieldWithTypeOverride("publish_window", "PublishWindow")),
        sharedEnums = emptyList(),
        valueObjects = listOf(
            ValueObjectModel(
                name = "PublishWindow",
                packageName = "shared.values",
                scope = ValueObjectScope.SHARED,
                storage = ValueObjectStorage.JSON
            ),
            ValueObjectModel(
                name = "PublishWindow",
                packageName = "content.values",
                scope = ValueObjectScope.AGGREGATE,
                aggregate = "Content",
                storage = ValueObjectStorage.JSON
            )
        ),
        typeRegistry = TypeRegistryModel.empty()
    )

    assertEquals("content.values.PublishWindow.Converter", controls.single().converterFqn)
}
```

Add no-registry binding:

```kotlin
@Test
fun `shared value object binds without registry entry`() {
    val controls = AggregateJpaControlInference.fromModel(
        aggregateName = "Order",
        fields = listOf(fieldWithTypeOverride("total", "Money")),
        sharedEnums = emptyList(),
        valueObjects = listOf(
            ValueObjectModel(
                name = "Money",
                packageName = "shared.values",
                scope = ValueObjectScope.SHARED,
                storage = ValueObjectStorage.JSON
            )
        ),
        typeRegistry = TypeRegistryModel.empty()
    )

    assertEquals("shared.values.Money.Converter", controls.single().converterFqn)
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "*AggregateJpaControlInferenceTest*"
```

Expected: compile failure because `valueObjects` is not accepted or assertion failure because registry is required.

- [ ] **Step 3: Implement value-object converter resolution**

In `AggregateJpaControlInference.fromModel`, add `valueObjects: List<ValueObjectModel>` and resolution order:

1. aggregate-local value object where `scope == AGGREGATE`, `aggregate == current aggregate`, and `name == @T`;
2. shared value object where `scope == SHARED` and `name == @T`;
3. existing enum resolution;
4. type registry;
5. fully qualified type fallback;
6. built-in scalar fallback.

For a value object, converter FQN is:

```kotlin
"${valueObject.packageName}.${valueObject.name}.Converter"
```

Fail fast when multiple candidates exist at the same resolution level:

```text
Ambiguous value object type override: <TypeName>
```

- [ ] **Step 4: Update aggregate planner call sites**

Pass `model.valueObjects` into JPA inference. Ensure entity scalar field render models still set:

```kotlin
converterTypeRef = "PublishWindow.Converter"
converterClassRef = "PublishWindow.Converter::class"
```

with imports for the value-object type.

- [ ] **Step 5: Run aggregate tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "*AggregateJpaControlInferenceTest*"
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test --tests "*EntityArtifactPlannerTest*"
```

Expected: PASS targeted tests.

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin cap4k-plugin-pipeline-core/src/test/kotlin cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin
git commit -m "feat: bind aggregate fields to value objects"
```

### Task 9: Remove Core Design Validator Generation

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlanner.kt`
- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorRenderModels.kt`
- Delete: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb`
- Modify: tests and fixtures that reference `designValidator`, `ValidatorModel`, or `tag = "validator"`

- [ ] **Step 1: Find all validator references**

Run:

```powershell
rg -n "designValidator|ValidatorModel|ValidatorParameterModel|tag\\s*=\\s*\"validator\"|\"validator\"" cap4k-plugin-* docs .agents
```

Expected: references exist in API, design source, canonical assembler, design generator, tests, docs, and skills.

- [ ] **Step 2: Write regression test for absence from core plan**

Add a pipeline-level test that configures design JSON without validator and asserts no core validator family is planned:

```kotlin
@Test
fun `core pipeline has no design validator planner`() {
    val templateIds = defaultBuiltInPlannerIds()

    assertFalse(templateIds.contains("design/validator"))
}
```

Place this test in `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt` and assert the generated `cap4kPlan` JSON for `design-integrated-compile-sample` does not contain `"templateId":"design/validator"`.

- [ ] **Step 3: Remove core validator code**

Delete or remove:

- `ValidatorModel`;
- `ValidatorParameterModel`;
- canonical validator assembly;
- `DesignValidatorArtifactPlanner`;
- validator render model;
- validator template;
- design validator Gradle config;
- docs that present validator as a cap4k core design tag.

Keep aggregate unique validation adapter under `aggregate.artifacts.unique`. Rename docs wording to "aggregate unique helper" where needed.

- [ ] **Step 4: Run focused validator cleanup tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-design:test
```

Expected: PASS for these modules.

- [ ] **Step 5: Commit**

```powershell
git add -A cap4k-plugin-pipeline-api cap4k-plugin-pipeline-source-design-json cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-design docs .agents
git commit -m "refactor: remove core design validator generation"
```

### Task 10: Harden Addon Provider Options And Namespace Rules

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

- [ ] **Step 1: Write failing addon SPI tests**

Options pass-through:

```kotlin
@Test
fun `passes provider scoped options to matching addon`() {
    val provider = RecordingAddonProvider(id = "sample-addon")
    val runner = runnerWithAddons(
        providers = listOf(provider),
        config = configWithAddonOptions("sample-addon", mapOf("manifestFile" to "validation/validators.json"))
    )

    runner.plan()

    assertEquals(mapOf("manifestFile" to "validation/validators.json"), provider.recordedOptions)
}
```

Unloaded provider:

```kotlin
@Test
fun `fails when addon options reference unloaded provider`() {
    val runner = runnerWithAddons(
        providers = emptyList(),
        config = configWithAddonOptions("missing-addon", mapOf("enabled" to "true"))
    )

    val error = assertFailsWith<IllegalArgumentException> {
        runner.plan()
    }

    assertTrue(error.message!!.contains("Configured addon provider is not loaded: missing-addon"))
}
```

Template namespace:

```kotlin
@Test
fun `rejects addon template id outside provider namespace`() {
    val provider = StaticAddonProvider(
        id = "sample-addon",
        items = listOf(addonPlanItem(templateId = "design/command"))
    )
    val runner = runnerWithAddons(providers = listOf(provider))

    val error = assertFailsWith<IllegalArgumentException> {
        runner.plan()
    }

    assertTrue(error.message!!.contains("Addon sample-addon produced template id outside addons/sample-addon/"))
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "*DefaultPipelineRunnerTest*"
```

Expected: options are empty or invalid template id is accepted.

- [ ] **Step 3: Implement addon option and namespace enforcement**

In `DefaultPipelineRunner.kt`:

- build `loadedProviderIds`;
- fail if `config.addons.keys - loadedProviderIds` is non-empty;
- fail on duplicate loaded provider ids;
- pass `config.addons[provider.id]?.options.orEmpty()` into `ArtifactAddonContext`;
- wrap provider exceptions:

```kotlin
throw IllegalStateException("Addon provider ${provider.id} failed while planning artifacts", cause)
```

- validate every addon item:

```kotlin
require(item.templateId.startsWith("addons/${provider.id}/")) {
    "Addon ${provider.id} produced template id outside addons/${provider.id}/: ${item.templateId}"
}
```

- [ ] **Step 4: Run addon SPI tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "*DefaultPipelineRunnerTest*"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin cap4k-plugin-pipeline-core/src/main/kotlin cap4k-plugin-pipeline-core/src/test/kotlin
git commit -m "feat: add provider scoped addon options"
```

### Task 11: Update Integrated Fixtures And Plan Output

**Files:**
- Modify: integrated fixture projects under `cap4k-plugin-pipeline-gradle/src/test/resources/functional`
- Modify: generated expected snapshots under test resources
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Update fixture DSL**

Change fixture DSL from:

```kotlin
generators {
    designCommand {
        enabled.set(true)
    }
    designValidator {
        enabled.set(true)
    }
}
sources {
    enumManifest {
        enabled.set(true)
        files.from("design/enums.json")
    }
}
```

to:

```kotlin
sources {
    designJson {
        enabled.set(true)
        files.from("design/design.json")
    }
}
types {
    enumManifest {
        files.from("design/enums.json")
    }
    valueObjectManifest {
        files.from("design/value-objects.json")
    }
}
generators {
    aggregate {
        enabled.set(true)
        artifacts {
            factory.set(true)
            specification.set(false)
            unique.set(true)
        }
    }
    flow {
        enabled.set(true)
    }
    drawingBoard {
        enabled.set(true)
    }
}
```

- [ ] **Step 2: Add value-object fixture files**

Add `design/value-objects.json` to at least one aggregate fixture:

```json
[
  {
    "name": "PublishWindow",
    "scope": "aggregate",
    "aggregate": "Content",
    "package": "content.values",
    "storage": "json",
    "fields": [
      { "name": "startAt", "type": "Instant", "nullable": true },
      { "name": "endAt", "type": "Instant", "nullable": true }
    ]
  }
]
```

Ensure the DB fixture field uses the matching `@T=PublishWindow` override.

- [ ] **Step 3: Add domain-service and saga design entries**

Add to fixture `design/design.json`:

```json
[
  {
    "tag": "domain_service",
    "package": "content.domain",
    "name": "ContentPublicationPolicy",
    "desc": "publication policy",
    "aggregates": ["Content"]
  },
  {
    "tag": "saga",
    "package": "content.workflow",
    "name": "PublishContentSaga",
    "desc": "publish content",
    "requestFields": [
      { "name": "contentId", "type": "ContentId" }
    ],
    "responseFields": [
      { "name": "accepted", "type": "Boolean" }
    ]
  }
]
```

Merge these objects into the existing JSON array instead of replacing command/query/event entries.

- [ ] **Step 4: Run integrated plan/generate tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "*Functional*" --tests "*Integration*"
```

Expected: failures identify outdated snapshots or compile issues in generated fixtures.

- [ ] **Step 5: Update expected snapshots**

Update expected plan output to include:

- `types/value-object`;
- `design/domain-service`;
- `design/saga-param`;
- `design/saga-result`;
- `design/saga-handler`;
- addon template ids under `addons/<providerId>/<artifact>` where fixtures install test addons.

Remove `design/validator`.

- [ ] **Step 6: Run integrated tests again**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS for Gradle plugin test suite.

- [ ] **Step 7: Commit**

```powershell
git add -A cap4k-plugin-pipeline-gradle/src/test
git commit -m "test: update pipeline fixtures for input driven generation"
```

### Task 12: Implement only-engine Validator Addon

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\kotlin\com\only\engine\cap4k\addon\translation\OnlyEngineEnumTranslationAddonProvider.kt`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\kotlin\com\only\engine\cap4k\addon\validation\OnlyEngineValidatorAddonProvider.kt`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\resources\cap4k\addons\only-engine-validator\validator.kt.peb`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\main\resources\META-INF\services\com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\src\test\kotlin\com\only\engine\cap4k\addon\validation\OnlyEngineValidatorAddonProviderTest.kt`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\README.md`

- [ ] **Step 1: Create or enter only-engine worktree**

If there is no dedicated worktree, create one from `only-engine`:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine
git worktree add .worktrees\issue-84-cap4k-addon-validator -b issue-84-cap4k-addon-validator
cd .worktrees\issue-84-cap4k-addon-validator
```

If a clean worktree already exists, use it. Do not edit `only-engine/master` directly.

- [ ] **Step 2: Write failing only-engine addon tests**

Add enum-translation compatibility test if not present:

```kotlin
@Test
fun `enum translation addon still reports provider id`() {
    assertEquals("only-engine-enum-translation", OnlyEngineEnumTranslationAddonProvider().id)
}
```

Add validator manifest test:

```kotlin
@Test
fun `validator addon reads manifest from provider options`() {
    val manifest = tempDir.resolve("validators.json").toFile()
    manifest.writeText(
        """
        [
          {
            "package": "content.validation",
            "name": "ValidAuthor",
            "desc": "valid author",
            "message": "invalid author",
            "targets": ["FIELD", "VALUE_PARAMETER"],
            "valueType": "AuthorId",
            "parameters": [
              { "name": "allowDeleted", "type": "Boolean", "defaultValue": "false" }
            ]
          }
        ]
        """.trimIndent()
    )
    val context = addonContext(
        options = mapOf("manifestFile" to manifest.absolutePath),
        model = canonicalModelWithStrongId("AuthorId")
    )

    val items = OnlyEngineValidatorAddonProvider().plan(context)

    assertEquals("addons/only-engine-validator/validator", items.single().templateId)
    assertTrue(items.single().path.endsWith("ValidAuthor.kt"))
}
```

- [ ] **Step 3: Run only-engine tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon "-PonlyEngine.cap4kCompositePath=C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/issue-84-generator-boundary" :engine-cap4k-addon:test
```

Expected: validator provider missing or enum provider does not compile against updated SPI.

- [ ] **Step 4: Implement validator addon**

Provider behavior:

- id is `only-engine-validator`;
- reads `context.options["manifestFile"]`;
- missing manifest option fails with `only-engine-validator requires option manifestFile`;
- manifest entries contain `package`, `name`, `desc`, `message`, `targets`, `valueType`, `parameters`;
- template id is `addons/only-engine-validator/validator`;
- output is checked-in source in application module or configured addon output path following existing enum-translation addon conventions;
- resolve strong ID and type registry references using `context.model` and existing type helpers.

Template resource path:

```text
cap4k/addons/only-engine-validator/validator.kt.peb
```

- [ ] **Step 5: Register provider**

Add provider FQN to the existing Java service loader file for cap4k addon providers. Keep enum-translation provider registered.

- [ ] **Step 6: Run only-engine tests**

Run:

```powershell
.\gradlew.bat --no-daemon "-PonlyEngine.cap4kCompositePath=C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/issue-84-generator-boundary" :engine-cap4k-addon:test
```

Expected: PASS.

- [ ] **Step 7: Commit only-engine work**

```powershell
git add engine-cap4k-addon
git commit -m "feat: add cap4k validator addon"
```

### Task 13: Update Documentation And Skills

**Files:**
- Modify: `docs/public/reference/generator-dsl.md`
- Modify: `docs/public/authoring/generator/input-sources.md`
- Modify: `docs/public/authoring/generator/code-generation.md`
- Modify: `docs/public/authoring/generator/addons-and-spi.md`
- Modify: `docs/public/authoring/advanced/value-object.md`
- Modify: `docs/public/authoring/advanced/domain-service.md`
- Modify: `docs/public/authoring/advanced/saga.md`
- Modify: `.agents/skills/cap4k-generation/SKILL.md`
- Modify: `.agents/skills/cap4k-generated-output-review/SKILL.md`
- Modify: `.agents/skills/cap4k-verification/SKILL.md`
- Modify: `.agents/skills/cap4k-modeling/SKILL.md` if it lists design tags or value-object generation guidance
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\engine-cap4k-addon\README.md`

- [ ] **Step 1: Find stale docs and skills**

Run:

```powershell
rg -n "designValidator|designCommand|designQuery|designDomainEvent|sources\\.enumManifest|enumManifest|validator|value object|domain_service|saga|checked-in source|CHECKED_IN_SOURCE|addon" docs .agents
```

Expected: stale docs and skills are listed.

- [ ] **Step 2: Update cap4k docs**

Docs must state:

- design entries are generation intent and do not need design-family generator switches;
- supported design tags are `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `domain_service`, `saga`;
- `validator` is not a cap4k core design tag;
- `types.enumManifest` and `types.valueObjectManifest` are types input contracts;
- enum and value-object manifest entries do not need `types.registryFile` entries;
- value-object manifest uses explicit `scope = shared | aggregate`;
- generated JSON value objects are checked-in source with default `SKIP`;
- addon SPI is general artifact contribution and can use provider-scoped options;
- addon cannot mutate canonical model or built-in render contexts;
- aggregate unique validation adapter is part of aggregate unique helper generation.

- [ ] **Step 3: Update cap4k skills**

Skills must guide future agents to:

- put VO manifest under `types`, not `design.json`;
- avoid reintroducing public design-family switches;
- treat no-op checked-in source generation as expected when `SKIP` protects author-owned skeletons;
- review addon artifacts under `addons/<providerId>/<artifact>`;
- verify value-object converter binding when DB `@T` references VO simple names.

- [ ] **Step 4: Update only-engine README**

Add usage example:

```kotlin
cap4k {
    addons {
        provider("only-engine-validator") {
            option("manifestFile", "validation/validators.json")
        }
    }
}
```

Document that `cap4kAddon` dependency still installs the provider and `addons.provider("only-engine-validator")` only configures options.

- [ ] **Step 5: Run stale-reference scan**

Run:

```powershell
rg -n "designValidator|sources\\.enumManifest|tag\\s*[:=]\\s*[\"']validator[\"']|ValidatorModel|ValidatorParameterModel" docs .agents cap4k-plugin-*
```

Expected: no stale core validator/design switch references remain, except references inside only-engine validator addon docs/tests or historical specs where they are explicitly described as removed.

- [ ] **Step 6: Commit docs and skills**

In cap4k worktree:

```powershell
git add docs .agents
git commit -m "docs: describe generator input boundary"
```

In only-engine worktree, if README changed in Task 12 commit was not amended:

```powershell
git add engine-cap4k-addon/README.md
git commit -m "docs: document cap4k validator addon"
```

### Task 14: Run Full Verification

**Files:**
- No source files should be edited in this task unless verification finds a concrete defect. If a defect is found, add a focused test before the fix and commit it as a fixup commit.

- [ ] **Step 1: Run cap4k focused test suite**

Run:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\issue-84-generator-boundary
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-source-enum-manifest:test :cap4k-plugin-pipeline-source-value-object-manifest:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-generator-types:test :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS.

- [ ] **Step 2: Run cap4k plugin help smoke**

Run:

```powershell
.\gradlew.bat --no-daemon help
```

Expected: PASS.

- [ ] **Step 3: Run only-engine composite addon tests**

Run:

```powershell
cd C:\Users\LD_moxeii\Documents\code\only-workspace\only-engine\.worktrees\issue-84-cap4k-addon-validator
.\gradlew.bat --no-daemon "-PonlyEngine.cap4kCompositePath=C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/issue-84-generator-boundary" :engine-cap4k-addon:test
```

Expected: PASS.

- [ ] **Step 4: Inspect git status**

Run in both worktrees:

```powershell
git status --short
git log --oneline -5
```

Expected: no uncommitted source changes except intentional generated snapshots not yet committed. If uncommitted changes remain, either commit them with a specific message or explain why they are intentionally left out.

- [ ] **Step 5: Update GitHub issue #84**

Post a concise issue comment summarizing:

- cap4k branch/commit range;
- only-engine branch/commit range;
- verification commands and PASS/FAIL result;
- any residual risk or skipped check.

Do not close #84 until downstream verification confirms the new DSL and only-engine addon path in a real project fixture.

## Final Acceptance Checklist

- [ ] Public design-family generator switches are removed.
- [ ] `types.enumManifest` replaces public `sources.enumManifest`.
- [ ] `types.valueObjectManifest` is implemented and does not require registry duplication.
- [ ] Duplicate type simple names across enum manifest, value-object manifest, and registry fail fast.
- [ ] `design.json` supports `domain_service` and `saga`.
- [ ] `design.json` rejects `validator` as a normal unsupported tag.
- [ ] Core validator planner/model/template are removed.
- [ ] Aggregate unique validation adapter remains under aggregate unique helper generation.
- [ ] Domain-service skeletons contain class shells only.
- [ ] Saga skeletons contain param/result/handler shells only and no workflow logic.
- [ ] JSON value objects generate checked-in data classes with nested converters.
- [ ] DB `@T` fields resolve manifest-managed value objects and apply nested converters.
- [ ] Addon provider options are passed to matching providers.
- [ ] Addon options for unloaded providers fail fast.
- [ ] Addon template ids are constrained to `addons/<providerId>/<artifact>`.
- [ ] only-engine enum-translation addon still works.
- [ ] only-engine validator addon reads its own manifest through provider options.
- [ ] Docs and skills describe the new boundary and do not instruct users to use removed DSL.
- [ ] #88 remains the follow-up for historical internal naming cleanup.

# Cap4k Artifact Template Import Contract Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify non-bootstrap `cap4kGenerate` artifact templates around `use(...)` declaration plus one final `imports(imports)` emission path, with deterministic import ordering.

**Architecture:** Promote the current design-only two-pass import collection model into the default non-bootstrap artifact render protocol inside `PebbleArtifactRenderer`. Keep bootstrap isolated, keep planner-provided `imports` contracts intact, and migrate all in-scope default Kotlin templates in one pass by replacing direct `import ...` lines with `use(...)` while preserving template-local conditions.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, Pebble templates

---

## File Map

**Modify:**
- `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`
- `cap4k-plugin-pipeline-gradle/README.md`

**Create:**
- `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/ArtifactTemplateImportContractTest.kt`

**Verify Without Planned Source Changes:**
- `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

**Explicitly Out Of Scope:**
- `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRenderer.kt`
- any bootstrap preset template under `presets/ddd-default-bootstrap`
- bootstrap plan / bootstrap task / bootstrap root merge code

## Task 1: Generalize Non-Bootstrap Artifact Rendering To A Two-Pass Import Protocol

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write failing renderer tests for regular-template `use(...)` support and deterministic ordering**

```kotlin
@Test
fun `regular aggregate template override can use use helper and emits deterministically sorted imports`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-aggregate-use")
    val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
    overrideAggregateDir.resolve("factory.kt.peb").writeText(
        """
        package {{ packageName }}
        {{ use("org.springframework.stereotype.Service") -}}
        {{ use("com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory") -}}
        {{ use(entityTypeFqn) -}}
        {% for import in imports(imports) %}
        import {{ import }}
        {% endfor %}

        @Service
        class {{ typeName }} : AggregateFactory<Any, {{ entityName }}>
        """.trimIndent()
    )

    val renderer = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = listOf(overrideDir.toString()),
        )
    )

    val rendered = renderer.render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/factory.kt.peb",
                outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
                    "typeName" to "VideoPostFactory",
                    "entityName" to "VideoPost",
                    "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                    "imports" to listOf(
                        "java.util.UUID",
                        "com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload",
                        "java.util.UUID",
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            )
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString()),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        ),
    )

    val importLines = rendered.single().content.lineSequence()
        .filter { it.startsWith("import ") }
        .toList()

    assertEquals(
        listOf(
            "import com.acme.demo.domain.aggregates.video_post.VideoPost",
            "import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory",
            "import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload",
            "import java.util.UUID",
            "import org.springframework.stereotype.Service",
        ),
        importLines,
    )
}

@Test
fun `regular aggregate template use helper still fails fast on simple name conflicts`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-aggregate-use-conflict")
    val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
    overrideAggregateDir.resolve("factory.kt.peb").writeText(
        """
        package {{ packageName }}
        {{ use("com.acme.first.Order") -}}
        {{ use("com.acme.second.Order") -}}
        {% for import in imports(imports) %}
        import {{ import }}
        {% endfor %}
        class {{ typeName }}
        """.trimIndent()
    )

    val exception = assertThrows<Exception> {
        PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString()),
            )
        ).render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/factory.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
                        "typeName" to "VideoPostFactory",
                        "imports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP,
                ),
            ),
        )
    }

    val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
        .filterIsInstance<IllegalArgumentException>()
        .firstOrNull()

    assertEquals(
        "use() import conflict: Order is already bound to com.acme.first.Order, cannot also import com.acme.second.Order",
        illegalArgument?.message,
    )
}
```

- [ ] **Step 2: Run the renderer test class and confirm it fails for the right reason**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected:
- `BUILD FAILED`
- at least one failure mentions `Unknown function [use]`
- the ordering assertion also fails if `use()` is temporarily made visible without deterministic sorting

- [ ] **Step 3: Implement one renderer protocol for all non-bootstrap artifact templates and centralize deterministic sorting**

```kotlin
class PebbleArtifactRenderer(
    private val templateResolver: TemplateResolver,
) : ArtifactRenderer {
    private val sessionState = ThreadLocal<PebbleRenderSession?>()
    private val artifactEngine = newEngine()

    override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> =
        planItems.map { item ->
            val templateText = templateResolver.resolve(item.templateId)
            renderArtifact(item, templateText)
        }

    private fun renderArtifact(
        item: ArtifactPlanItem,
        templateText: String,
    ): RenderedArtifact {
        val session = PebbleRenderSession()
        sessionState.set(session)

        try {
            val template = artifactEngine.getLiteralTemplate(templateText)
            template.evaluate(StringWriter(), item.context)

            session.phase = RenderPhase.RENDERING
            val mergedImports = session.explicitImportCollector.mergedWith(readImports(item.context))
            val writer = StringWriter()
            template.evaluate(writer, item.context + mapOf("imports" to mergedImports))
            return RenderedArtifact(
                outputPath = item.outputPath,
                content = writer.toString(),
                conflictPolicy = item.conflictPolicy,
            )
        } finally {
            sessionState.remove()
        }
    }

    private fun newEngine(): PebbleEngine = PebbleEngine.Builder()
        .loader(StringLoader())
        .extension(PipelinePebbleExtension({ sessionState.get() }, enableUseHelper = true))
        .newLineTrimming(false)
        .build()
}
```

```kotlin
internal class ExplicitImportCollector {
    private val explicitImports = LinkedHashSet<String>()
    private val explicitImportsBySimpleName = LinkedHashMap<String, String>()

    fun register(rawImport: String) {
        val normalizedImport = rawImport.trim()
        validateExplicitImport(normalizedImport)

        val simpleName = normalizedImport.substringAfterLast('.')
        val existingImport = explicitImportsBySimpleName.putIfAbsent(simpleName, normalizedImport)
        if (existingImport != null && existingImport != normalizedImport) {
            throw IllegalArgumentException(
                "use() import conflict: $simpleName is already bound to $existingImport, cannot also import $normalizedImport"
            )
        }

        explicitImports.add(normalizedImport)
    }

    fun mergedWith(baseImports: List<String>): List<String> {
        val normalizedBaseImports = normalizeImports(baseImports)
        val mergedImports = LinkedHashSet<String>(normalizedBaseImports)
        val simpleNameToImport = LinkedHashMap<String, String>()

        for (baseImport in normalizedBaseImports) {
            simpleNameToImport.putIfAbsent(baseImport.substringAfterLast('.'), baseImport)
        }

        for (explicitImport in explicitImports) {
            val simpleName = explicitImport.substringAfterLast('.')
            val existingImport = simpleNameToImport[simpleName]
            if (existingImport != null && existingImport != explicitImport) {
                throw IllegalArgumentException(
                    "use() import conflict: $simpleName is already bound to $existingImport, cannot also import $explicitImport"
                )
            }
            simpleNameToImport.putIfAbsent(simpleName, explicitImport)
            mergedImports.add(explicitImport)
        }

        return sortImportsDeterministically(mergedImports)
    }
}

private fun normalizeImports(imports: List<String>): List<String> =
    sortImportsDeterministically(imports)

private fun sortImportsDeterministically(imports: Collection<String>): List<String> =
    imports.asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .toList()
```

- [ ] **Step 4: Re-run the renderer test class and confirm the generalized contract is green**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected:
- `BUILD SUCCESSFUL`
- regular aggregate overrides can call `use(...)`
- merge order is deterministic
- conflict behavior is unchanged

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: generalize artifact use helper contract"
```

## Task 2: Migrate The Simple Aggregate Kotlin Templates To `use(...) + imports(imports)`

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/ArtifactTemplateImportContractTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing contract and regression tests for the simple aggregate Kotlin templates**

```kotlin
class ArtifactTemplateImportContractTest {

    @Test
    fun `simple aggregate kotlin templates emit imports only through imports imports`() {
        val presetRoot = Path.of(
            "src",
            "main",
            "resources",
            "presets",
            "ddd-default",
            "aggregate",
        )

        val templates = listOf(
            "enum.kt.peb",
            "enum_translation.kt.peb",
            "factory.kt.peb",
            "specification.kt.peb",
            "unique_query.kt.peb",
            "unique_query_handler.kt.peb",
            "unique_validator.kt.peb",
            "wrapper.kt.peb",
        )

        val offenders = templates.flatMap { fileName ->
            Files.readAllLines(presetRoot.resolve(fileName)).mapIndexedNotNull { index, line ->
                if (line.startsWith("import ") && line.trim() != "import {{ import }}") {
                    "$fileName:${index + 1}:$line"
                } else {
                    null
                }
            }
        }

        assertEquals(emptyList<String>(), offenders)
    }
}

@Test
fun `aggregate factory preset renders one sorted import block after use migration`() {
    val content = renderSingle(
        templateId = "aggregate/factory.kt.peb",
        context = mapOf(
            "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
            "typeName" to "VideoPostFactory",
            "payloadTypeName" to "Payload",
            "aggregateName" to "VideoPost",
            "entityName" to "VideoPost",
            "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
            "imports" to emptyList<String>(),
        ),
    )

    assertEquals(
        listOf(
            "import com.acme.demo.domain.aggregates.video_post.VideoPost",
            "import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory",
            "import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload",
            "import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
            "import org.springframework.stereotype.Service",
        ),
        content.lineSequence().filter { it.startsWith("import ") }.toList(),
    )
}
```

- [ ] **Step 2: Run the focused renderer tests and confirm they fail on direct import lines**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.ArtifactTemplateImportContractTest" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected:
- `BUILD FAILED`
- the new contract test reports direct static imports in the listed aggregate templates
- the factory render assertion still reflects the pre-migration import ordering

- [ ] **Step 3: Rewrite the simple aggregate Kotlin templates so imports are declared through `use(...)` and emitted once**

```pebble
package {{ packageName }}
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory") -}}
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload") -}}
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate") -}}
{{ use("org.springframework.stereotype.Service") -}}
{{ use(entityTypeFqn) -}}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

@Service
@Aggregate(
    aggregate = "{{ aggregateName }}",
    name = "{{ typeName }}",
    type = Aggregate.TYPE_FACTORY,
    description = ""
)
class {{ typeName }} : AggregateFactory<{{ typeName }}.{{ payloadTypeName }}, {{ entityName }}> {
```

```pebble
package {{ packageName }}
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.Aggregate") -}}
{{ use(entityTypeFqn) -}}
{{ use(factoryTypeFqn) -}}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

/**
 * {{ entityName }} aggregate wrapper
 * {{ comment }}
 */
class {{ typeName }}(
```

```pebble
package {{ packageName }}
{{ use("jakarta.validation.Constraint") -}}
{{ use("jakarta.validation.ConstraintValidator") -}}
{{ use("jakarta.validation.ConstraintValidatorContext") -}}
{{ use("jakarta.validation.Payload") -}}
{{ use("kotlin.reflect.KClass") -}}
{{ use(queryTypeFqn) -}}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [{{ typeName }}.Validator::class])
annotation class {{ typeName }}(
```

Apply the same migration pattern to:
- `enum.kt.peb`
- `enum_translation.kt.peb`
- `factory.kt.peb`
- `specification.kt.peb`
- `unique_query.kt.peb`
- `unique_query_handler.kt.peb`
- `unique_validator.kt.peb`
- `wrapper.kt.peb`

Do not add wildcard imports.

Do not add no-arg `imports()`.

Do not add a second final import loop.
```

- [ ] **Step 4: Re-run the focused renderer tests and confirm the simple aggregate templates are green**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.ArtifactTemplateImportContractTest" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected:
- `BUILD SUCCESSFUL`
- the source-contract test passes for the simple aggregate templates
- factory, wrapper, enum, unique-query, unique-validator, and translation templates render with one sorted import block

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/ArtifactTemplateImportContractTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb
git commit -m "refactor: migrate simple aggregate templates to use contract"
```

## Task 3: Migrate `aggregate/entity.kt.peb` Without Rewriting The Aggregate Planners

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/ArtifactTemplateImportContractTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Extend the contract scan and add a failing entity-render regression**

```kotlin
@Test
fun `all default non bootstrap kotlin templates emit imports only through imports imports`() {
    val presetRoot = Path.of(
        "src",
        "main",
        "resources",
        "presets",
        "ddd-default",
    )

    val templates = listOf(
        "aggregate/entity.kt.peb",
        "aggregate/enum.kt.peb",
        "aggregate/enum_translation.kt.peb",
        "aggregate/factory.kt.peb",
        "aggregate/repository.kt.peb",
        "aggregate/schema.kt.peb",
        "aggregate/specification.kt.peb",
        "aggregate/unique_query.kt.peb",
        "aggregate/unique_query_handler.kt.peb",
        "aggregate/unique_validator.kt.peb",
        "aggregate/wrapper.kt.peb",
        "design/api_payload.kt.peb",
        "design/client.kt.peb",
        "design/client_handler.kt.peb",
        "design/command.kt.peb",
        "design/domain_event.kt.peb",
        "design/domain_event_handler.kt.peb",
        "design/query.kt.peb",
        "design/query_handler.kt.peb",
        "design/query_list.kt.peb",
        "design/query_list_handler.kt.peb",
        "design/query_page.kt.peb",
        "design/query_page_handler.kt.peb",
        "design/validator.kt.peb",
    )

    val directImportOffenders = templates.flatMap { relativePath ->
        Files.readAllLines(presetRoot.resolve(relativePath)).mapIndexedNotNull { index, line ->
            if (line.startsWith("import ") && line.trim() != "import {{ import }}") {
                "$relativePath:${index + 1}:$line"
            } else {
                null
            }
        }
    }

    val extraLoopOffenders = templates.filter { relativePath ->
        val content = Files.readString(presetRoot.resolve(relativePath))
        Regex("""imports\(imports\)""").findAll(content).count() != 1 ||
            content.contains("imports(jpaImports)")
    }

    assertEquals(emptyList<String>(), directImportOffenders)
    assertEquals(emptyList<String>(), extraLoopOffenders)
}

@Test
fun `aggregate entity preset emits one sorted import block after use migration`() {
    val content = renderSingle(
        templateId = "aggregate/entity.kt.peb",
        context = mapOf(
            "packageName" to "com.acme.demo.domain.aggregates.video_post",
            "typeName" to "VideoPost",
            "entityName" to "VideoPost",
            "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "video_post"),
            "hasGeneratedValueFields" to true,
            "hasGenericGeneratorFields" to false,
            "hasVersionFields" to true,
            "hasConverterFields" to true,
            "dynamicInsert" to true,
            "dynamicUpdate" to false,
            "softDeleteSql" to "",
            "softDeleteWhereClause" to "",
            "jpaImports" to listOf(
                "jakarta.persistence.FetchType",
                "jakarta.persistence.ManyToOne",
                "jakarta.persistence.JoinColumn",
            ),
            "imports" to listOf("com.acme.demo.domain.identity.user.UserProfile"),
            "scalarFields" to listOf(
                mapOf(
                    "isId" to true,
                    "generatedValueStrategy" to "IDENTITY",
                    "generatedValueGenerator" to null,
                    "genericGeneratorName" to null,
                    "genericGeneratorStrategy" to null,
                    "isVersion" to false,
                    "insertable" to true,
                    "updatable" to true,
                    "columnName" to "id",
                    "converterTypeRef" to null,
                    "name" to "id",
                    "type" to "Long",
                    "nullable" to false,
                ),
                mapOf(
                    "isId" to false,
                    "generatedValueStrategy" to null,
                    "generatedValueGenerator" to null,
                    "genericGeneratorName" to null,
                    "genericGeneratorStrategy" to null,
                    "isVersion" to true,
                    "insertable" to true,
                    "updatable" to true,
                    "columnName" to "version",
                    "converterTypeRef" to null,
                    "name" to "version",
                    "type" to "Long",
                    "nullable" to false,
                ),
                mapOf(
                    "isId" to false,
                    "generatedValueStrategy" to null,
                    "generatedValueGenerator" to null,
                    "genericGeneratorName" to null,
                    "genericGeneratorStrategy" to null,
                    "isVersion" to false,
                    "insertable" to true,
                    "updatable" to true,
                    "columnName" to "status",
                    "converterTypeRef" to "com.acme.demo.domain.shared.enums.Status",
                    "name" to "status",
                    "type" to "Int",
                    "nullable" to false,
                ),
            ),
            "relationFields" to listOf(
                mapOf(
                    "relationType" to "MANY_TO_ONE",
                    "fetchType" to "LAZY",
                    "joinColumn" to "author_id",
                    "joinColumnNullable" to false,
                    "nullable" to false,
                    "insertable" to true,
                    "updatable" to true,
                    "name" to "author",
                    "targetTypeRef" to "UserProfile",
                ),
            ),
        ),
    )

    assertEquals(
        listOf(
            "import com.acme.demo.domain.identity.user.UserProfile",
            "import com.acme.demo.domain.shared.enums.Status",
            "import jakarta.persistence.Column",
            "import jakarta.persistence.Convert",
            "import jakarta.persistence.Entity",
            "import jakarta.persistence.FetchType",
            "import jakarta.persistence.GeneratedValue",
            "import jakarta.persistence.GenerationType",
            "import jakarta.persistence.Id",
            "import jakarta.persistence.JoinColumn",
            "import jakarta.persistence.ManyToOne",
            "import jakarta.persistence.Table",
            "import jakarta.persistence.Version",
            "import org.hibernate.annotations.DynamicInsert",
        ),
        content.lineSequence().filter { it.startsWith("import ") }.toList(),
    )
}
```

- [ ] **Step 2: Run the contract and renderer tests and confirm entity is still the blocker**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.ArtifactTemplateImportContractTest" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected:
- `BUILD FAILED`
- the contract scan still reports `aggregate/entity.kt.peb`
- the entity render assertion still shows non-unified import output

- [ ] **Step 3: Convert entity import declaration to `use(...)` and keep planner inputs unchanged**

```pebble
package {{ packageName }}
{% if entityJpa.entityEnabled -%}
{{ use("jakarta.persistence.Column") -}}
{{ use("jakarta.persistence.Entity") -}}
{% if hasGeneratedValueFields or hasGenericGeneratorFields -%}
{{ use("jakarta.persistence.GeneratedValue") -}}
{% endif -%}
{% if hasGeneratedValueFields -%}
{{ use("jakarta.persistence.GenerationType") -}}
{% endif -%}
{{ use("jakarta.persistence.Id") -}}
{{ use("jakarta.persistence.Table") -}}
{% if hasVersionFields -%}
{{ use("jakarta.persistence.Version") -}}
{% endif -%}
{% endif -%}
{% if hasConverterFields -%}
{{ use("jakarta.persistence.Convert") -}}
{% endif -%}
{% if dynamicInsert -%}
{{ use("org.hibernate.annotations.DynamicInsert") -}}
{% endif -%}
{% if dynamicUpdate -%}
{{ use("org.hibernate.annotations.DynamicUpdate") -}}
{% endif -%}
{% if softDeleteSql -%}
{{ use("org.hibernate.annotations.SQLDelete") -}}
{% endif -%}
{% if softDeleteWhereClause -%}
{{ use("org.hibernate.annotations.Where") -}}
{% endif -%}
{% if hasGenericGeneratorFields -%}
{{ use("org.hibernate.annotations.GenericGenerator") -}}
{% endif -%}
{% for import in jpaImports -%}
{{ use(import) -}}
{% endfor -%}
{% for field in scalarFields if field.converterTypeRef -%}
{{ use(field.converterTypeRef) -}}
{% endfor -%}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}
{% if entityJpa.entityEnabled %}@Entity
@Table(name = "{{ entityJpa.tableName }}")
```

Keep the rest of the entity template structure unchanged:
- keep `scalarFields` and `relationFields` rendering in the template
- keep `jpaImports` coming from planners
- do not create a new aggregate import planner in this slice

- [ ] **Step 4: Re-run the contract and renderer tests and confirm the full non-bootstrap Kotlin template set is compliant**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.ArtifactTemplateImportContractTest" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected:
- `BUILD SUCCESSFUL`
- all in-scope design and aggregate Kotlin templates now satisfy the static contract test
- entity import output is one deterministically sorted block

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/ArtifactTemplateImportContractTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "refactor: unify aggregate entity import contract"
```

## Task 4: Align README With The New Artifact Import Contract And Prove Generator Regressions Stay Green

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/README.md`
- Verify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Verify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Verify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Capture the README lines that must change**

Run:

```powershell
rg -n "designEngine|regularEngine|仅 design 模板可用|非 design 模板|use\\(\\)|imports\\(list\\)" cap4k-plugin-pipeline-gradle/README.md
```

Expected:
- hits around section `11.3 Pebble 引擎`
- text still says `use()` is design-only
- helper table still describes `imports(list)` as a design-special merge path

- [ ] **Step 2: Rewrite the README contract section so it matches the new behavior exactly**

```markdown
### 11.3 Pebble 引擎

`cap4kGenerate` 的 artifact 渲染现在统一走非 bootstrap 的两段式协议：

- **collecting pass**：模板中的 `use(...)` 收集显式 import
- **rendering pass**：将显式 import 与 context 中的 `imports` 合并，并通过 `imports(imports)` 输出

Bootstrap 不在这里面，仍然走独立的 `PebbleBootstrapRenderer`。

| Helper | 类型 | 说明 |
| --- | --- | --- |
| `type(value)` | function | 读取 `renderedType` 或直接透传字符串 |
| `imports(imports)` | function | 合并 planner/context imports 与模板 `use(...)` imports，去重后做稳定排序 |
| `use(fqn)` | function | 对所有非 bootstrap artifact 模板可用。仅接受 FQCN，收集 import，不直接输出文本 |

新约束：

- 非 bootstrap 默认 Kotlin 模板最终只允许一处 `imports(imports)` 输出 import
- 默认模板不再直接写静态 `import ...`
- 排序目标是稳定、确定性，不承诺适配每个人本地 IDE 自定义 import layout
```

```markdown
旧说法：
- `designEngine`：`design/` 前缀 templateId 才能用 `use()`
- `regularEngine`：aggregate/flow/drawing-board/bootstrap

新说法：
- `PebbleArtifactRenderer`：所有非 bootstrap artifact 模板统一支持 `use()`
- `PebbleBootstrapRenderer`：bootstrap 单独保持简单，不参与这次 import contract 统一
```

- [ ] **Step 3: Run the generator regression stack that proves design and aggregate generation still work**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected:
- `BUILD SUCCESSFUL`
- aggregate planner tests still pass with unchanged planner contracts
- functional and compile tests still pass with the new renderer contract

- [ ] **Step 4: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/README.md
git commit -m "docs: describe unified artifact import contract"
```

## Task 5: Run Full Slice Verification And Lock The Final Green State

**Files:**
- Modify if any regression appears: any file touched in Tasks 1-4

- [ ] **Step 1: Run the full targeted verification suite for this slice**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.ArtifactTemplateImportContractTest" :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected:
- `BUILD SUCCESSFUL`
- renderer contract tests, aggregate planner regression, functional generation, and compile smoke all green together

- [ ] **Step 2: If any regression appears, fix it at the smallest responsible layer**

```text
If the failure is:
- use helper missing or conflict behavior wrong: fix PebbleArtifactRenderer or PipelinePebbleExtension
- import order drifting: fix sortImportsDeterministically only, do not add template-local reordering hacks
- static contract scan failing: fix the offending template, do not weaken the scan
- aggregate compile/function output failing: fix the concrete aggregate template, do not reopen bootstrap or planner architecture
```

- [ ] **Step 3: Re-run the exact failing command, then re-run the full slice suite**

Run the smallest failing command first, then:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.ArtifactTemplateImportContractTest"
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected:
- the previously failing command passes
- the combined suite still passes

- [ ] **Step 4: Commit the final green state**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/ArtifactTemplateImportContractTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/enum_translation.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/specification.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/wrapper.kt.peb cap4k-plugin-pipeline-gradle/README.md
git commit -m "feat: unify artifact template import contract"
```

- [ ] **Step 5: Record handoff notes for the next slice**

```markdown
- bootstrap is still intentionally isolated and still does not expose `use(...)`
- planner-provided `imports` contracts remain valid; this slice only unified the template import contract
- wildcard import support and no-arg `imports()` remain intentionally unsupported
- if future work wants richer import grouping, it should build on deterministic sorted output instead of restoring ad hoc template-local ordering
```

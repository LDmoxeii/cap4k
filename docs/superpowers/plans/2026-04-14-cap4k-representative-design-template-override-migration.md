# Cap4k Representative Design Template / Override Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded query template variants to the pipeline design generator, migrate the representative query template family onto helper-first presets and overrides, and prove the migration contract with planner, renderer, and functional coverage.

**Architecture:** Keep the public contract unchanged: users still override bounded template ids through `overrideDirs`, and template selection remains Kotlin-owned. Implement query-family routing conservatively in `DesignArtifactPlanner` using `typeName` suffix inference, then add helper-first `query`, `query_list`, and `query_page` preset and override templates that mirror the old design family without restoring regex or pattern DSL.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble templates, existing `cap4k` pipeline generator/renderer/Gradle functional fixtures

---

## File Structure

- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
  Responsibility: bounded query template routing by conservative `typeName` suffix

- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`
  Responsibility: planner regression coverage for default/list/page query template selection

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
  Responsibility: helper-first default query template that restores `RequestParam<Response>` semantics

- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb`
  Responsibility: helper-first list query preset using `ListQueryParam<Response>`

- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb`
  Responsibility: helper-first page query preset using `PageQueryParam<Response>()`

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  Responsibility: preset-level rendering coverage for default/list/page query contracts

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
  Responsibility: representative design fixture with default/list/page query entries

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb`
  Responsibility: representative default query override template using `RequestParam<Response>`

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list.kt.peb`
  Responsibility: representative list query override template using `ListQueryParam<Response>`

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page.kt.peb`
  Responsibility: representative page query override template using `PageQueryParam<Response>()`

- Verify only: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb`
  Responsibility: pre-existing command override fixture carried forward unchanged by this slice

- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  Responsibility: plan json assertions plus end-to-end preset and override coverage for query variants

- Verify only: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  Responsibility: current `entry.name -> ${entry.name}Qry` conversion that makes suffix-based planner routing deterministic

### Task 1: Route Query Variants in the Design Planner

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`
- Verify only: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Test: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing planner tests for bounded query template routing**

Add these tests to `DesignArtifactPlannerTest.kt`:

```kotlin
@Test
fun `plans bounded query template variants from conservative suffixes`() {
    val planner = DesignArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            requests = listOf(
                RequestModel(
                    kind = RequestKind.QUERY,
                    packageName = "order.read",
                    typeName = "FindOrderQry",
                    description = "find order",
                    aggregateName = "Order",
                    aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                ),
                RequestModel(
                    kind = RequestKind.QUERY,
                    packageName = "order.read",
                    typeName = "FindOrderListQry",
                    description = "find order list",
                    aggregateName = "Order",
                    aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                ),
                RequestModel(
                    kind = RequestKind.QUERY,
                    packageName = "order.read",
                    typeName = "FindOrderPageQry",
                    description = "find order page",
                    aggregateName = "Order",
                    aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                ),
            ),
        ),
    )

    assertEquals(
        listOf(
            "design/query.kt.peb",
            "design/query_list.kt.peb",
            "design/query_page.kt.peb",
        ),
        items.map { it.templateId },
    )
    assertEquals(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt",
        items[1].outputPath,
    )
    assertEquals(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt",
        items[2].outputPath,
    )
}

@Test
fun `keeps default query template when page or list are not suffix variants`() {
    val planner = DesignArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            requests = listOf(
                RequestModel(
                    kind = RequestKind.QUERY,
                    packageName = "order.read",
                    typeName = "FindOrderPageableQry",
                    description = "find pageable order",
                ),
                RequestModel(
                    kind = RequestKind.QUERY,
                    packageName = "order.read",
                    typeName = "FindOrderListingQry",
                    description = "find listing order",
                ),
            ),
        ),
    )

    assertEquals(
        listOf("design/query.kt.peb", "design/query.kt.peb"),
        items.map { it.templateId },
    )
}
```

- [ ] **Step 2: Run the focused planner tests and confirm current routing fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*plans bounded query template variants from conservative suffixes" --tests "*keeps default query template when page or list are not suffix variants" --rerun-tasks
```

Expected: FAIL because `DesignArtifactPlanner` still emits `design/query.kt.peb` for every query request.

- [ ] **Step 3: Implement bounded query variant routing in `DesignArtifactPlanner.kt`**

Replace the current template selection block with this implementation:

```kotlin
class DesignArtifactPlanner : GeneratorProvider {
    override val id: String = "design"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireApplicationModuleRoot(config)
        val basePath = config.basePackage.replace(".", "/")

        return model.requests.mapIndexed { index, request ->
            val siblingRequestTypeNames = model.requests.withIndex()
                .asSequence()
                .filter { it.index != index && it.value.packageName == request.packageName }
                .map { it.value.typeName }
                .toSet()
            val packagePath = request.packageName.replace(".", "/")
            val subdir = if (request.kind == RequestKind.COMMAND) "commands" else "queries"
            val templateId = resolveTemplateId(request)

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = templateId,
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/$subdir/$packagePath/${request.typeName}.kt",
                context = DesignRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.$subdir.${request.packageName}",
                    request = request,
                    typeRegistry = config.typeRegistry,
                    siblingRequestTypeNames = siblingRequestTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }

    private fun resolveTemplateId(request: RequestModel): String {
        return when (request.kind) {
            RequestKind.COMMAND -> "design/command.kt.peb"
            RequestKind.QUERY -> when {
                request.typeName.endsWith("PageQry") -> "design/query_page.kt.peb"
                request.typeName.endsWith("ListQry") -> "design/query_list.kt.peb"
                else -> "design/query.kt.peb"
            }
        }
    }
}
```

- [ ] **Step 4: Re-run the focused planner tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*plans bounded query template variants from conservative suffixes" --tests "*keeps default query template when page or list are not suffix variants" --rerun-tasks
```

Expected: PASS with `FindOrderQry -> design/query.kt.peb`, `FindOrderListQry -> design/query_list.kt.peb`, `FindOrderPageQry -> design/query_page.kt.peb`, and conservative non-suffix names still using `design/query.kt.peb`.

- [ ] **Step 5: Commit the planner routing change**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt
git commit -m "feat: route bounded design query templates"
```

### Task 2: Add Helper-First Query Variant Presets and Renderer Coverage

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing renderer tests for default, list, and page query preset contracts**

Add these tests to `PebbleArtifactRendererTest.kt`:

```kotlin
@Test
fun `default query preset uses request param contract`() {
    val renderer = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = emptyList(),
        ),
    )

    val rendered = renderer.render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "design",
                moduleRole = "application",
                templateId = "design/query.kt.peb",
                outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.application.queries.order.read",
                    "typeName" to "FindOrderQry",
                    "imports" to listOf("java.time.LocalDateTime", "java.util.UUID"),
                    "requestFields" to listOf(
                        mapOf("name" to "lookupId", "renderedType" to "UUID", "nullable" to false),
                        mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                    ),
                    "responseFields" to listOf(
                        mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                        mapOf("name" to "snapshot", "renderedType" to "Snapshot?", "nullable" to true),
                    ),
                    "requestNestedTypes" to emptyList<Map<String, Any>>(),
                    "responseNestedTypes" to listOf(
                        mapOf(
                            "name" to "Snapshot",
                            "fields" to listOf(
                                mapOf("name" to "updatedAt", "renderedType" to "LocalDateTime", "nullable" to false),
                                mapOf("name" to "snapshotId", "renderedType" to "UUID", "nullable" to false),
                            ),
                        ),
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        ),
    )

    val content = rendered.single().content
    assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
    assertTrue(content.contains("import java.time.LocalDateTime"))
    assertTrue(content.contains("import java.util.UUID"))
    assertFalse(content.contains("import com.foo.Status"))
    assertFalse(content.contains("import com.bar.Status"))
    assertTrue(content.contains("object FindOrderQry"))
    assertTrue(content.contains(") : RequestParam<Response>"))
    assertTrue(content.contains("val lookupId: UUID"))
    assertTrue(content.contains("val requestStatus: com.foo.Status"))
    assertTrue(content.contains("val responseStatus: com.bar.Status"))
}

@Test
fun `bounded query presets render list and page request contracts`() {
    val renderer = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = emptyList(),
        ),
    )

    val rendered = renderer.render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "design",
                moduleRole = "application",
                templateId = "design/query_list.kt.peb",
                outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.application.queries.order.read",
                    "typeName" to "FindOrderListQry",
                    "imports" to listOf("java.time.LocalDateTime", "java.util.UUID"),
                    "requestFields" to listOf(
                        mapOf("name" to "listCursorId", "renderedType" to "UUID", "nullable" to false),
                        mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                    ),
                    "responseFields" to listOf(
                        mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                        mapOf("name" to "summary", "renderedType" to "Summary?", "nullable" to true),
                    ),
                    "requestNestedTypes" to emptyList<Map<String, Any>>(),
                    "responseNestedTypes" to listOf(
                        mapOf(
                            "name" to "Summary",
                            "fields" to listOf(
                                mapOf("name" to "updatedAt", "renderedType" to "LocalDateTime", "nullable" to false),
                                mapOf("name" to "summaryId", "renderedType" to "UUID", "nullable" to false),
                            ),
                        ),
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
            ArtifactPlanItem(
                generatorId = "design",
                moduleRole = "application",
                templateId = "design/query_page.kt.peb",
                outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.application.queries.order.read",
                    "typeName" to "FindOrderPageQry",
                    "imports" to listOf("java.time.LocalDateTime", "java.util.UUID"),
                    "requestFields" to listOf(
                        mapOf("name" to "createdAfter", "renderedType" to "LocalDateTime", "nullable" to false),
                        mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                    ),
                    "responseFields" to listOf(
                        mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                        mapOf("name" to "snapshot", "renderedType" to "Snapshot?", "nullable" to true),
                    ),
                    "requestNestedTypes" to emptyList<Map<String, Any>>(),
                    "responseNestedTypes" to listOf(
                        mapOf(
                            "name" to "Snapshot",
                            "fields" to listOf(
                                mapOf("name" to "publishedAt", "renderedType" to "LocalDateTime", "nullable" to false),
                                mapOf("name" to "snapshotId", "renderedType" to "UUID", "nullable" to false),
                            ),
                        ),
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        ),
    )

    val listContent = rendered.single { it.outputPath.endsWith("FindOrderListQry.kt") }.content
    val pageContent = rendered.single { it.outputPath.endsWith("FindOrderPageQry.kt") }.content

    assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQueryParam"))
    assertTrue(listContent.contains(") : ListQueryParam<Response>"))
    assertTrue(listContent.contains("val listCursorId: UUID"))
    assertFalse(listContent.contains("import com.foo.Status"))
    assertFalse(listContent.contains("import com.bar.Status"))

    assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQueryParam"))
    assertTrue(pageContent.contains(") : PageQueryParam<Response>()"))
    assertTrue(pageContent.contains("val createdAfter: LocalDateTime"))
    assertFalse(pageContent.contains("import com.foo.Status"))
    assertFalse(pageContent.contains("import com.bar.Status"))
}
```

- [ ] **Step 2: Run the focused renderer tests and confirm they fail before the preset family is added**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*default query preset uses request param contract" --tests "*bounded query presets render list and page request contracts" --rerun-tasks
```

Expected: FAIL because `design/query.kt.peb` does not yet import or implement `RequestParam<Response>`, and `design/query_list.kt.peb` / `design/query_page.kt.peb` do not exist.

- [ ] **Step 3: Replace the default query preset with the helper-first `RequestParam` contract**

Replace `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb` with:

```pebble
{{ use("com.only4.cap4k.ddd.core.application.RequestParam") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
{% if requestFields.size > 0 %}
{% if requestNestedTypes.size > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : RequestParam<Response> {
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
{% else %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : RequestParam<Response>
{% endif %}
{% else %}
    class Request : RequestParam<Response>
{% endif %}

{% if responseFields.size > 0 %}
{% if responseNestedTypes.size > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) {
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
{% else %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    )
{% endif %}
{% else %}
    data object Response
{% endif %}
}
```

- [ ] **Step 4: Add the helper-first list query preset**

Create `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb` with:

```pebble
{{ use("com.only4.cap4k.ddd.core.application.query.ListQueryParam") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
{% if requestFields.size > 0 %}
{% if requestNestedTypes.size > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : ListQueryParam<Response> {
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
{% else %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : ListQueryParam<Response>
{% endif %}
{% else %}
    class Request : ListQueryParam<Response>
{% endif %}

{% if responseFields.size > 0 %}
{% if responseNestedTypes.size > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) {
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
{% else %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    )
{% endif %}
{% else %}
    data object Response
{% endif %}
}
```

- [ ] **Step 5: Add the helper-first page query preset**

Create `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb` with:

```pebble
{{ use("com.only4.cap4k.ddd.core.application.query.PageQueryParam") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
{% if requestFields.size > 0 %}
{% if requestNestedTypes.size > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : PageQueryParam<Response>() {
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
{% else %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : PageQueryParam<Response>()
{% endif %}
{% else %}
    class Request : PageQueryParam<Response>()
{% endif %}

{% if responseFields.size > 0 %}
{% if responseNestedTypes.size > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) {
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
{% else %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    )
{% endif %}
{% else %}
    data object Response
{% endif %}
}
```

- [ ] **Step 6: Re-run the focused renderer tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*default query preset uses request param contract" --tests "*bounded query presets render list and page request contracts" --rerun-tasks
```

Expected: PASS with `query.kt.peb` importing `RequestParam`, `query_list.kt.peb` importing `ListQueryParam`, `query_page.kt.peb` importing `PageQueryParam`, and all three templates still honoring computed imports, nested types, and collision-safe fully qualified statuses.

- [ ] **Step 7: Commit the preset and renderer coverage changes**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add helper-first query variant presets"
```

### Task 3: Expand the Functional Design Fixture and Override Template Family

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Verify only: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing functional assertions for plan output, preset rendering, and override rendering of query variants**

Update `PipelinePluginFunctionalTest.kt` with these changes:

1. Extend `cap4kPlan writes pretty printed plan json` with:

```kotlin
assertTrue(planFile.readText().contains("\"templateId\": \"design/query_list.kt.peb\""))
assertTrue(planFile.readText().contains("\"templateId\": \"design/query_page.kt.peb\""))
```

2. Extend `cap4kGenerate renders command and query files from repository config` with default-query contract assertions:

```kotlin
assertTrue(queryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
assertTrue(queryContent.contains(") : RequestParam<Response>"))
```

3. Add this new test:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate renders list and page query variants from repository config`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-query-variants")
    copyFixture(projectDir)

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val listFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"
    )
    val pageFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"
    )
    val listContent = listFile.readText()
    val pageContent = pageFile.readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(listFile.toFile().exists())
    assertTrue(pageFile.toFile().exists())

    assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQueryParam"))
    assertTrue(listContent.contains(") : ListQueryParam<Response>"))
    assertTrue(listContent.contains("import java.time.LocalDateTime"))
    assertTrue(listContent.contains("import java.util.UUID"))
    assertFalse(listContent.contains("import com.foo.Status"))
    assertFalse(listContent.contains("import com.bar.Status"))
    assertTrue(listContent.contains("val listCursorId: UUID"))
    assertTrue(listContent.contains("val requestStatus: com.foo.Status"))
    assertTrue(listContent.contains("val responseStatus: com.bar.Status"))
    assertTrue(listContent.contains("data class Summary("))
    assertTrue(listContent.contains("val updatedAt: LocalDateTime"))
    assertTrue(listContent.contains("val summaryId: UUID"))

    assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQueryParam"))
    assertTrue(pageContent.contains(") : PageQueryParam<Response>()"))
    assertTrue(pageContent.contains("import java.time.LocalDateTime"))
    assertTrue(pageContent.contains("import java.util.UUID"))
    assertFalse(pageContent.contains("import com.foo.Status"))
    assertFalse(pageContent.contains("import com.bar.Status"))
    assertTrue(pageContent.contains("val createdAfter: LocalDateTime"))
    assertTrue(pageContent.contains("val requestStatus: com.foo.Status"))
    assertTrue(pageContent.contains("val responseStatus: com.bar.Status"))
    assertTrue(pageContent.contains("data class Snapshot("))
    assertTrue(pageContent.contains("val publishedAt: LocalDateTime"))
    assertTrue(pageContent.contains("val snapshotId: UUID"))
}
```

4. Add this new override test:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate supports override list and page query templates`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-query-variant-override")
    copyFixture(projectDir)

    val buildFile = projectDir.resolve("build.gradle.kts")
    val buildFileContent = buildFile.readText().replace("\r\n", "\n")
    buildFile.writeText(
        buildFileContent.replace(
            """
            |        design {
            |            enabled.set(true)
            |        }
            """.trimMargin(),
            """
            |        design {
            |            enabled.set(true)
            |        }
            |        templates {
            |            overrideDirs.from("codegen/templates")
            |        }
            """.trimMargin()
        )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val listFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"
    )
    val pageFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"
    )
    val listContent = listFile.readText()
    val pageContent = pageFile.readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(listFile.toFile().exists())
    assertTrue(pageFile.toFile().exists())

    assertTrue(listContent.contains("// override: representative list query migration template"))
    assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.ListQueryParam"))
    assertTrue(listContent.contains(") : ListQueryParam<Response>"))
    assertFalse(listContent.contains("import com.foo.Status"))
    assertFalse(listContent.contains("import com.bar.Status"))
    assertTrue(listContent.contains("val responseStatus: com.bar.Status"))

    assertTrue(pageContent.contains("// override: representative page query migration template"))
    assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageQueryParam"))
    assertTrue(pageContent.contains(") : PageQueryParam<Response>()"))
    assertFalse(pageContent.contains("import com.foo.Status"))
    assertFalse(pageContent.contains("import com.bar.Status"))
    assertTrue(pageContent.contains("val responseStatus: com.bar.Status"))
}
```

- [ ] **Step 2: Run the focused functional tests and confirm they fail before the fixture is expanded**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan writes pretty printed plan json" --tests "*cap4kGenerate renders list and page query variants from repository config" --tests "*cap4kGenerate supports override list and page query templates" --rerun-tasks
```

Expected: FAIL because the design sample currently only defines `SubmitOrder` and `FindOrder`, and the override directory does not yet contain `query_list.kt.peb` or `query_page.kt.peb`.

- [ ] **Step 3: Expand the design sample with representative list and page query entries**

Replace `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json` with:

```json
[
  {
    "tag": "cmd",
    "package": "order.submit",
    "name": "SubmitOrder",
    "desc": "submit order command",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long", "defaultValue": "1" },
      { "name": "submittedAt", "type": "java.time.LocalDateTime" },
      { "name": "mirroredSubmittedAt", "type": "LocalDateTime" },
      { "name": "externalId", "type": "java.util.UUID" },
      { "name": "trackingId", "type": "UUID" },
      { "name": "requestStatus", "type": "com.foo.Status" },
      { "name": "title", "type": "String", "defaultValue": "demo" },
      { "name": "enabled", "type": "Boolean", "defaultValue": "true" },
      { "name": "tags", "type": "List<String>", "defaultValue": "emptyList()" },
      { "name": "address", "type": "Address", "nullable": true },
      { "name": "address.city", "type": "String" },
      { "name": "address.addressId", "type": "java.util.UUID" },
      { "name": "createdAt", "type": "java.time.LocalDateTime", "defaultValue": "java.time.LocalDateTime.MIN" }
    ],
    "responseFields": [
      { "name": "accepted", "type": "Boolean" },
      { "name": "responseStatus", "type": "com.bar.Status" },
      { "name": "result", "type": "Result", "nullable": true },
      { "name": "result.receiptId", "type": "java.util.UUID" }
    ]
  },
  {
    "tag": "qry",
    "package": "order.read",
    "name": "FindOrder",
    "desc": "find order query",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long" },
      { "name": "lookupId", "type": "java.util.UUID" },
      { "name": "lookupMirrorId", "type": "UUID" },
      { "name": "requestStatus", "type": "com.foo.Status" }
    ],
    "responseFields": [
      { "name": "responseStatus", "type": "com.bar.Status" },
      { "name": "snapshot", "type": "Snapshot", "nullable": true },
      { "name": "snapshot.updatedAt", "type": "java.time.LocalDateTime" },
      { "name": "snapshot.publishedAt", "type": "LocalDateTime" },
      { "name": "snapshot.snapshotId", "type": "java.util.UUID" }
    ]
  },
  {
    "tag": "qry",
    "package": "order.read",
    "name": "FindOrderList",
    "desc": "find order list query",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "customerId", "type": "Long" },
      { "name": "listCursorId", "type": "java.util.UUID" },
      { "name": "requestStatus", "type": "com.foo.Status" }
    ],
    "responseFields": [
      { "name": "responseStatus", "type": "com.bar.Status" },
      { "name": "summary", "type": "Summary", "nullable": true },
      { "name": "summary.updatedAt", "type": "java.time.LocalDateTime" },
      { "name": "summary.summaryId", "type": "java.util.UUID" }
    ]
  },
  {
    "tag": "qry",
    "package": "order.read",
    "name": "FindOrderPage",
    "desc": "find order page query",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "keyword", "type": "String" },
      { "name": "createdAfter", "type": "java.time.LocalDateTime" },
      { "name": "requestStatus", "type": "com.foo.Status" }
    ],
    "responseFields": [
      { "name": "responseStatus", "type": "com.bar.Status" },
      { "name": "snapshot", "type": "Snapshot", "nullable": true },
      { "name": "snapshot.publishedAt", "type": "java.time.LocalDateTime" },
      { "name": "snapshot.snapshotId", "type": "java.util.UUID" }
    ]
  }
]
```

- [ ] **Step 4: Replace the representative default query override and add list/page override templates**

Replace `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb` with:

```pebble
{{ use("com.only4.cap4k.ddd.core.application.RequestParam") -}}
package {{ packageName }}
// override: representative default query migration template
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
{% if requestFields|length > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : RequestParam<Response> {
{% else %}
    class Request : RequestParam<Response> {
{% endif %}
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }

{% if responseFields|length > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) {
{% else %}
    class Response {
{% endif %}
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
}
```

Create `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list.kt.peb` with:

```pebble
{{ use("com.only4.cap4k.ddd.core.application.query.ListQueryParam") -}}
package {{ packageName }}
// override: representative list query migration template
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
{% if requestFields|length > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : ListQueryParam<Response> {
{% else %}
    class Request : ListQueryParam<Response> {
{% endif %}
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }

{% if responseFields|length > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) {
{% else %}
    class Response {
{% endif %}
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
}
```

Create `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page.kt.peb` with:

```pebble
{{ use("com.only4.cap4k.ddd.core.application.query.PageQueryParam") -}}
package {{ packageName }}
// override: representative page query migration template
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
{% if requestFields|length > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : PageQueryParam<Response>() {
{% else %}
    class Request : PageQueryParam<Response>() {
{% endif %}
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }

{% if responseFields|length > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) {
{% else %}
    class Response {
{% endif %}
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
}
```

- [ ] **Step 5: Re-run the focused functional tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kPlan writes pretty printed plan json" --tests "*cap4kGenerate renders command and query files from repository config" --tests "*cap4kGenerate renders list and page query variants from repository config" --tests "*cap4kGenerate supports migration friendly override design templates" --tests "*cap4kGenerate supports override list and page query templates" --rerun-tasks
```

Expected: PASS with:

- plan output containing `design/query.kt.peb`, `design/query_list.kt.peb`, and `design/query_page.kt.peb`
- preset-generated `FindOrderQry` implementing `RequestParam<Response>`
- preset-generated `FindOrderListQry` implementing `ListQueryParam<Response>`
- preset-generated `FindOrderPageQry` implementing `PageQueryParam<Response>()`
- override-generated list/page queries containing the marker comments and the same bounded request contracts

- [ ] **Step 6: Commit the functional fixture and override family update**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page.kt.peb cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: extend representative design migration fixtures"
```

### Task 4: Run the Full Verification Sweep

**Files:**
- Test: `cap4k-plugin-pipeline-generator-design`
- Test: `cap4k-plugin-pipeline-renderer-pebble`
- Test: `cap4k-plugin-pipeline-gradle`

- [ ] **Step 1: Run the full design generator test suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test
```

Expected: PASS with the new bounded query template routing locked in.

- [ ] **Step 2: Run the full Pebble renderer test suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: PASS with default/list/page preset contracts and the pre-existing helper regression coverage all green.

- [ ] **Step 3: Run the full Gradle functional suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS with plan generation, preset rendering, override rendering, type/import resolution, and default-value behavior all still green against the expanded fixture.

- [ ] **Step 4: Capture the final diff summary for review**

Run:

```bash
git diff --stat HEAD~3..HEAD
```

Expected: the final diff is limited to:

- `DesignArtifactPlanner.kt`
- `DesignArtifactPlannerTest.kt`
- `ddd-default/design/query.kt.peb`
- `ddd-default/design/query_list.kt.peb`
- `ddd-default/design/query_page.kt.peb`
- `PebbleArtifactRendererTest.kt`
- `functional/design-sample/design/design.json`
- `functional/design-sample/codegen/templates/design/query.kt.peb`
- `functional/design-sample/codegen/templates/design/query_list.kt.peb`
- `functional/design-sample/codegen/templates/design/query_page.kt.peb`
- `PipelinePluginFunctionalTest.kt`

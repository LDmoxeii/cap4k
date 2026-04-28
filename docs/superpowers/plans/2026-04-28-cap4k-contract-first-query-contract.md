# Cap4k Contract-First Query Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace generated list/page query shortcuts with one strict request-response query contract and explicit page request traits.

**Architecture:** Keep the canonical model contract-first: design input carries explicit `traits`, query handlers always use `Query<Request, Response>`, and response collection/page/tree shapes are generated as fields inside `Response`. Source parsing rejects legacy tags, unsupported traits, and `self` recursion before canonical assembly; generator providers collapse the query/list/page template family into one query template and one query handler template.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, Pebble templates, Gson, PowerShell.

---

## Source Specs And Constraints

Primary spec:

- `docs/superpowers/specs/2026-04-27-cap4k-contract-first-query-contract-design.md`

User decisions to preserve:

- Delete `QueryVariant`; do not leave it as an unused branch.
- Do not infer query semantics from type-name suffixes such as `FindOrderList` or `FindOrderPage`.
- Do not auto-wrap old item-shaped list/page response fields into response envelopes.
- Response collection, page, and tree shapes must be explicit design fields.
- Nested fields use dotted paths and `[]`, for example `items[].id` and `page.list[].id`.
- Do not allow `self` for response recursion or local nested recursion in this slice; tree types must name the nested type explicitly, for example `List<Node>`.
- `traits` is a general design-entry field, but first implementation accepts only `PAGE` on `query` and `api_payload`.
- Sorting is not part of `PAGE`.
- Source tags must be canonical. Reject legacy aliases `cmd`, `qry`, `cli`, `clients`, `payload`, `de`, `query_list`, and `query_page`.
- Old default query variant templates and fixture override templates are removed, not kept as compatibility targets.

## File Map

Shared API and model tests:

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Adds `RequestTrait`.
  - Adds `traits` to `DesignSpecEntry`, `QueryModel`, and `ApiPayloadModel`.
  - Removes `QueryVariant`.
- Modify `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
  - Removes `QueryVariant` assertions and adds typed trait assertions.

ddd-core query contract:

- Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageRequest.kt`
  - Lightweight page request trait with `pageNum` and `pageSize`.
- Delete `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/ListQuery.kt`
- Delete `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageQuery.kt`
- Delete `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/ListQueryParam.kt`
- Delete `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageQueryParam.kt`
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisor.kt`
  - Removes `ListQuery` and `PageQuery` fallback generic resolution.
- Modify `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisor.kt`
  - Removes `ListQuery` and `PageQuery` fallback generic resolution.
- Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/query/PageRequestTest.kt`
  - Verifies `PageRequest` is a simple read-only contract.

Design JSON source:

- Modify `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
  - Normalizes tags to canonical lowercase tags.
  - Rejects legacy aliases and unsupported tags.
  - Parses `traits` into `Set<RequestTrait>`.
  - Rejects unknown traits, unsupported tag/trait combinations, and `self` field types.
- Modify `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
  - Adds parser tests for traits, legacy tag rejection, unsupported trait rejection, and `self` rejection.
- Modify `cap4k-plugin-pipeline-source-design-json/src/test/resources/fixtures/design/design.json`
  - Uses canonical `command` and `query` tags.

Canonical assembly:

- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  - Accepts only canonical tags from design-json and drawing-board snapshots.
  - Carries query/API payload traits into canonical models.
  - Removes suffix-based query variant resolution.
- Modify `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
  - Replaces old alias and variant tests with canonical tag and explicit trait tests.

Design generator providers:

- Delete `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryTemplateIds.kt`
- Modify `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlanner.kt`
  - Always plans `design/query.kt.peb`.
- Modify `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlanner.kt`
  - Always plans `design/query_handler.kt.peb`.
- Modify `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt`
  - Adds `pageRequest` to the Pebble context.
- Modify `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`
  - Sets `pageRequest` from `RequestTrait.PAGE`.
  - Removes root `self` resolution.
  - Supports explicit `PageData<Item>` response fields with nested `page.list[].field` item fields.
- Modify query planner tests and API payload planner tests under `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/`.

Pebble renderer:

- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
  - Adds `PageRequest` for page query requests.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb`
  - Adds `PageRequest` for page API payload requests without `RequestParam<Response>`.
- Delete `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb`
- Delete `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb`
- Delete `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb`
- Delete `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb`
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  - Removes old variant template assertions and adds page trait rendering assertions.

Functional fixtures and tests:

- Modify `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Modify `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/design/design.json`
- Modify `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample/design/design.json`
- Delete old query variant override templates under `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/`.
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

Docs after implementation:

- Modify `docs/superpowers/mainline-roadmap.md` only after the implementation and verification commands pass.

---

### Task 1: Shared Pipeline API Contract

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

- [ ] **Step 1: Replace the query variant model test with trait assertions**

In `PipelineModelsTest.kt`, change the first test so the query uses traits and no variant:

```kotlin
@Test
fun `canonical model stores commands queries api payloads and clients separately`() {
    val model = CanonicalModel(
        commands = listOf(
            CommandModel(
                packageName = "orders",
                typeName = "CreateOrderCmd",
                description = "create order",
                aggregateRef = AggregateRef(
                    name = "Order",
                    packageName = "com.acme.demo.domain.aggregates.order",
                ),
                requestFields = listOf(FieldModel(name = "id", type = "Long")),
                responseFields = emptyList(),
                variant = CommandVariant.DEFAULT,
            )
        ),
        queries = listOf(
            QueryModel(
                packageName = "orders",
                typeName = "FindOrderPageQry",
                description = "find order page",
                aggregateRef = AggregateRef(
                    name = "Order",
                    packageName = "com.acme.demo.domain.aggregates.order",
                ),
                requestFields = emptyList(),
                responseFields = emptyList(),
                traits = setOf(RequestTrait.PAGE),
            )
        ),
        apiPayloads = listOf(
            ApiPayloadModel(
                packageName = "orders",
                typeName = "FindOrderPage",
                description = "find order page payload",
                traits = setOf(RequestTrait.PAGE),
            )
        ),
        clients = listOf(
            ClientModel(
                packageName = "remote",
                typeName = "SyncStockCli",
                description = "sync stock",
                aggregateRef = null,
                requestFields = emptyList(),
                responseFields = emptyList(),
            )
        ),
    )

    assertEquals(1, model.commands.size)
    assertEquals(setOf(RequestTrait.PAGE), model.queries.single().traits)
    assertEquals(setOf(RequestTrait.PAGE), model.apiPayloads.single().traits)
    assertEquals(1, model.clients.size)
}
```

- [ ] **Step 2: Run the API model test and verify it is red**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest.canonical model stores commands queries api payloads and clients separately"
```

Expected: compilation fails because `RequestTrait` and trait constructor arguments do not exist.

- [ ] **Step 3: Add typed request traits and remove `QueryVariant`**

In `PipelineModels.kt`, add the enum near `CommandVariant`:

```kotlin
enum class RequestTrait {
    PAGE,
}
```

Replace `DesignSpecEntry` with:

```kotlin
data class DesignSpecEntry(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String>,
    val persist: Boolean? = null,
    val traits: Set<RequestTrait> = emptySet(),
    val requestFields: List<FieldModel>,
    val responseFields: List<FieldModel>,
)
```

Delete this enum:

```kotlin
enum class QueryVariant {
    DEFAULT,
    LIST,
    PAGE,
}
```

Replace `QueryModel` with:

```kotlin
data class QueryModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef? = null,
    override val requestFields: List<FieldModel> = emptyList(),
    override val responseFields: List<FieldModel> = emptyList(),
    val traits: Set<RequestTrait> = emptySet(),
) : DesignInteractionModel
```

Replace `ApiPayloadModel` with:

```kotlin
data class ApiPayloadModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val requestFields: List<FieldModel> = emptyList(),
    val responseFields: List<FieldModel> = emptyList(),
    val traits: Set<RequestTrait> = emptySet(),
)
```

- [ ] **Step 4: Update remaining API test constructors**

In `PipelineModelsTest.kt`, remove every `QueryVariant` import and remove every `variant = QueryVariant...` constructor argument.

Keep command variants unchanged:

```kotlin
variant = CommandVariant.DEFAULT
```

- [ ] **Step 5: Run API tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit shared API contract**

Run:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
git add cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add request traits to pipeline models"
```

---

### Task 2: ddd-core Query Shortcut Removal

**Files:**

- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageRequest.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/ListQuery.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageQuery.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/ListQueryParam.kt`
- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageQueryParam.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisor.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisor.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/query/PageRequestTest.kt`

- [ ] **Step 1: Add failing test for the lightweight page request trait**

Create `PageRequestTest.kt`:

```kotlin
package com.only4.cap4k.ddd.core.application.query

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageRequestTest {

    @Test
    fun `page request exposes only page coordinates`() {
        val request = object : PageRequest {
            override val pageNum: Int = 2
            override val pageSize: Int = 50
        }

        assertEquals(2, request.pageNum)
        assertEquals(50, request.pageSize)
    }
}
```

- [ ] **Step 2: Run the new ddd-core test and verify it is red**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.application.query.PageRequestTest.page request exposes only page coordinates"
```

Expected: compilation fails because `PageRequest` does not exist.

- [ ] **Step 3: Add `PageRequest`**

Create `PageRequest.kt`:

```kotlin
package com.only4.cap4k.ddd.core.application.query

interface PageRequest {
    val pageNum: Int
    val pageSize: Int
}
```

- [ ] **Step 4: Delete old query shortcut files**

Delete:

```text
ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/ListQuery.kt
ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageQuery.kt
ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/ListQueryParam.kt
ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageQueryParam.kt
```

- [ ] **Step 5: Remove shortcut fallback generic resolution**

In `DefaultRequestSupervisor.kt`, remove:

```kotlin
import com.only4.cap4k.ddd.core.application.query.ListQuery
import com.only4.cap4k.ddd.core.application.query.PageQuery
```

In both `resolveGenericTypeClass` calls, replace:

```kotlin
Query::class.java, ListQuery::class.java, PageQuery::class.java
```

with:

```kotlin
Query::class.java
```

In `DefaultSagaSupervisor.kt`, remove:

```kotlin
import com.only4.cap4k.ddd.core.application.query.ListQuery
import com.only4.cap4k.ddd.core.application.query.PageQuery
```

In the handler map `resolveGenericTypeClass` call, replace:

```kotlin
Query::class.java, ListQuery::class.java, PageQuery::class.java,
SagaHandler::class.java
```

with:

```kotlin
Query::class.java,
SagaHandler::class.java
```

- [ ] **Step 6: Run ddd-core tests and deletion check**

Run:

```powershell
.\gradlew.bat :ddd-core:test
rg "ListQuery|PageQuery|ListQueryParam|PageQueryParam" ddd-core/src/main/kotlin
```

Expected: Gradle reports BUILD SUCCESSFUL. The `rg` command prints no matches.

- [ ] **Step 7: Commit ddd-core query contract cleanup**

Run:

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageRequest.kt
git add ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/query/PageRequestTest.kt
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/impl/DefaultRequestSupervisor.kt
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/saga/impl/DefaultSagaSupervisor.kt
git rm ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/ListQuery.kt
git rm ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageQuery.kt
git rm ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/ListQueryParam.kt
git rm ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/query/PageQueryParam.kt
git commit -m "feat: replace query shortcuts with page request trait"
```

---

### Task 3: Strict Design JSON Tags, Traits, And `self` Rejection

**Files:**

- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/test/resources/fixtures/design/design.json`

- [ ] **Step 1: Convert source fixture tags to canonical names**

In `src/test/resources/fixtures/design/design.json`, replace:

```json
"tag": "cmd"
```

with:

```json
"tag": "command"
```

Replace:

```json
"tag": "qry"
```

with:

```json
"tag": "query"
```

- [ ] **Step 2: Update the existing source test expectations**

In `DesignJsonSourceProviderTest.kt`, rename `loads command and query entries from configured files` to:

```kotlin
fun `loads canonical command and query entries from configured files`()
```

Change the tag assertions to:

```kotlin
assertEquals("command", snapshot.entries.first().tag)
assertEquals("query", snapshot.entries.last().tag)
```

In `collects design entries from manifest file`, change the in-test JSON tags from `cmd` and `qry` to `command` and `query`.

- [ ] **Step 3: Add failing parser test for page traits**

Append this test to `DesignJsonSourceProviderTest.kt`:

```kotlin
@Test
fun `reads page traits for query and api payload entries`() {
    val tempFile = tempDir.resolve("page-traits.json")
    Files.writeString(
        tempFile,
        """
            [
              {
                "tag": "query",
                "package": "order.read",
                "name": "FindOrderPage",
                "desc": "find order page",
                "traits": ["page"],
                "requestFields": [],
                "responseFields": [
                  { "name": "page", "type": "com.only4.cap4k.ddd.core.share.PageData<Item>" },
                  { "name": "page.list[].orderId", "type": "Long" }
                ]
              },
              {
                "tag": "api_payload",
                "package": "order.read",
                "name": "FindOrderPage",
                "desc": "find order page payload",
                "traits": ["PAGE"],
                "requestFields": [],
                "responseFields": [
                  { "name": "page", "type": "com.only4.cap4k.ddd.core.share.PageData<Item>" },
                  { "name": "page.list[].orderId", "type": "Long" }
                ]
              }
            ]
        """.trimIndent(),
        StandardCharsets.UTF_8,
    )

    val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot

    assertEquals(setOf(RequestTrait.PAGE), snapshot.entries[0].traits)
    assertEquals(setOf(RequestTrait.PAGE), snapshot.entries[1].traits)
}
```

Add these imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
```

If `configFor` does not exist in this test class, add it near the bottom:

```kotlin
private fun configFor(vararg files: String): ProjectConfig =
    ProjectConfig(
        basePackage = "com.only4.cap4k",
        layout = ProjectLayout.SINGLE_MODULE,
        modules = emptyMap(),
        sources = mapOf(
            "design-json" to SourceConfig(
                enabled = true,
                options = mapOf("files" to files.toList()),
            ),
        ),
        generators = emptyMap(),
        templates = TemplateConfig(
            preset = "default",
            overrideDirs = emptyList(),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )
```

- [ ] **Step 4: Add failing parser test for legacy tag aliases**

Append:

```kotlin
@Test
fun `rejects legacy design tag aliases`() {
    val legacyTags = listOf("cmd", "qry", "cli", "clients", "payload", "de", "query_list", "query_page")

    legacyTags.forEach { legacyTag ->
        val tempFile = tempDir.resolve("legacy-${legacyTag}.json")
        Files.writeString(
            tempFile,
            """
                [
                  {
                    "tag": "$legacyTag",
                    "package": "order.read",
                    "name": "LegacyTag",
                    "desc": "legacy tag",
                    "requestFields": [],
                    "responseFields": []
                  }
                ]
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
        }

        assertEquals("unsupported design tag for LegacyTag: $legacyTag", error.message)
    }
}
```

- [ ] **Step 5: Add failing parser tests for trait boundaries**

Append:

```kotlin
@Test
fun `rejects unknown request trait`() {
    val tempFile = tempDir.resolve("unknown-trait.json")
    Files.writeString(
        tempFile,
        """
            [
              {
                "tag": "query",
                "package": "order.read",
                "name": "FindOrder",
                "desc": "find order",
                "traits": ["cursor"],
                "requestFields": [],
                "responseFields": []
              }
            ]
        """.trimIndent(),
        StandardCharsets.UTF_8,
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
    }

    assertEquals("design entry FindOrder has unsupported trait: cursor", error.message)
}

@Test
fun `rejects request traits on unsupported tags`() {
    val tempFile = tempDir.resolve("trait-on-command.json")
    Files.writeString(
        tempFile,
        """
            [
              {
                "tag": "command",
                "package": "order.submit",
                "name": "SubmitOrder",
                "desc": "submit order",
                "traits": ["page"],
                "requestFields": [],
                "responseFields": []
              }
            ]
        """.trimIndent(),
        StandardCharsets.UTF_8,
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
    }

    assertEquals("design entry SubmitOrder cannot use request traits on tag: command", error.message)
}
```

- [ ] **Step 6: Add failing parser test for `self`**

Append:

```kotlin
@Test
fun `rejects self in design field types`() {
    val tempFile = tempDir.resolve("self-recursion.json")
    Files.writeString(
        tempFile,
        """
            [
              {
                "tag": "api_payload",
                "package": "category",
                "name": "GetCategoryTree",
                "desc": "get category tree",
                "requestFields": [],
                "responseFields": [
                  { "name": "nodes", "type": "List<Node>" },
                  { "name": "nodes[].children", "type": "List<self>" }
                ]
              }
            ]
        """.trimIndent(),
        StandardCharsets.UTF_8,
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
    }

    assertEquals("design entry GetCategoryTree field nodes[].children must use an explicit type name instead of self", error.message)
}
```

- [ ] **Step 7: Run source parser tests and verify they are red**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest"
```

Expected: compilation fails until `RequestTrait` parsing is implemented, then runtime assertions fail until tag and trait validation is implemented.

- [ ] **Step 8: Implement canonical tag and trait parsing**

In `DesignJsonSourceProvider.kt`, add:

```kotlin
import com.google.gson.JsonObject
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
```

Add constants inside the class:

```kotlin
private val supportedTags = setOf(
    "command",
    "query",
    "client",
    "api_payload",
    "domain_event",
    "validator",
)
private val requestTraitTags = setOf("query", "api_payload")
private val selfToken = Regex("""(?<![A-Za-z0-9_.])self(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE)
```

In `parseFile`, normalize the tag before validation:

```kotlin
val rawTag = obj["tag"].asString
val name = obj["name"].asString
val tag = parseTag(rawTag, name)
val requestFields = parseFields(obj["requestFields"]?.asJsonArray)
val responseFields = parseFields(obj["responseFields"]?.asJsonArray)
val traits = parseTraits(obj, tag, name)
validateReservedFields(tag, name, requestFields)
validateNoSelfTypes(name, requestFields + responseFields)
DesignSpecEntry(
    tag = tag,
    packageName = readPackageName(obj["package"]?.asString, tag),
    name = name,
    description = obj["desc"]?.asString ?: "",
    aggregates = obj["aggregates"]?.asJsonArray?.map { it.asString } ?: emptyList(),
    persist = obj["persist"]?.asBoolean,
    traits = traits,
    requestFields = requestFields,
    responseFields = responseFields,
)
```

Add helpers:

```kotlin
private fun parseTag(rawTag: String, name: String): String {
    val tag = rawTag.trim().lowercase(Locale.ROOT)
    require(tag in supportedTags) {
        "unsupported design tag for $name: $rawTag"
    }
    return tag
}

private fun parseTraits(obj: JsonObject, tag: String, name: String): Set<RequestTrait> {
    val rawTraits = obj["traits"]
        ?.asJsonArray
        ?.map { it.asString.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    val traits = rawTraits.map { rawTrait ->
        val normalized = rawTrait.uppercase(Locale.ROOT)
        runCatching { RequestTrait.valueOf(normalized) }.getOrElse {
            throw IllegalArgumentException("design entry $name has unsupported trait: $rawTrait")
        }
    }.toSet()

    require(traits.isEmpty() || tag in requestTraitTags) {
        "design entry $name cannot use request traits on tag: $tag"
    }

    return traits
}

private fun validateNoSelfTypes(name: String, fields: List<FieldModel>) {
    fields.firstOrNull { field -> selfToken.containsMatchIn(field.type) }?.let { field ->
        throw IllegalArgumentException(
            "design entry $name field ${field.name} must use an explicit type name instead of self",
        )
    }
}
```

- [ ] **Step 9: Run source design-json tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit strict source parsing**

Run:

```powershell
git add cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt
git add cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt
git add cap4k-plugin-pipeline-source-design-json/src/test/resources/fixtures/design/design.json
git commit -m "feat: parse strict design tags and request traits"
```

---

### Task 4: Canonical Assembly Without Query Variants

**Files:**

- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Update the main canonical split test**

In `DefaultCanonicalAssemblerTest.kt`, remove:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.QueryVariant
```

Add:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
```

Rename:

```kotlin
fun `assembler splits cmd qry cli into typed canonical collections`()
```

to:

```kotlin
fun `assembler splits canonical command query client into typed canonical collections`()
```

Change in-test tags from `cmd`, `qry`, and `cli` to `command`, `query`, and `client`.

Remove assertions that read `query.variant`.

- [ ] **Step 2: Replace query variant tests with trait and suffix tests**

Delete the old test named:

```kotlin
fun `resolves page list and default query variants canonically`()
```

Add:

```kotlin
@Test
fun `query names with list and page suffixes do not imply request traits`() {
    val assembler = DefaultCanonicalAssembler()
    val result = assembler.assemble(
        config = projectConfig(),
        snapshots = listOf(
            DesignSpecSnapshot(
                entries = listOf(
                    DesignSpecEntry(
                        tag = "query",
                        packageName = "order.read",
                        name = "FindOrderList",
                        description = "find order list",
                        aggregates = emptyList(),
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                    ),
                    DesignSpecEntry(
                        tag = "query",
                        packageName = "order.read",
                        name = "FindOrderPage",
                        description = "find order page",
                        aggregates = emptyList(),
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                    ),
                ),
            ),
        ),
    )

    assertEquals(listOf("FindOrderListQry", "FindOrderPageQry"), result.model.queries.map { it.typeName })
    assertEquals(listOf(emptySet<RequestTrait>(), emptySet<RequestTrait>()), result.model.queries.map { it.traits })
}

@Test
fun `assembler carries page traits on query and api payload canonical models`() {
    val assembler = DefaultCanonicalAssembler()
    val result = assembler.assemble(
        config = projectConfig(),
        snapshots = listOf(
            DesignSpecSnapshot(
                entries = listOf(
                    DesignSpecEntry(
                        tag = "query",
                        packageName = "order.read",
                        name = "FindOrderPage",
                        description = "find order page",
                        aggregates = emptyList(),
                        traits = setOf(RequestTrait.PAGE),
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                    ),
                    DesignSpecEntry(
                        tag = "api_payload",
                        packageName = "order.read",
                        name = "FindOrderPage",
                        description = "find order page payload",
                        aggregates = emptyList(),
                        traits = setOf(RequestTrait.PAGE),
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                    ),
                ),
            ),
        ),
    )

    assertEquals(setOf(RequestTrait.PAGE), result.model.queries.single().traits)
    assertEquals(setOf(RequestTrait.PAGE), result.model.apiPayloads.single().traits)
}
```

- [ ] **Step 3: Add canonical drawing-board tag boundary test**

Append:

```kotlin
@Test
fun `drawing board projection accepts only canonical design tags`() {
    val assembler = DefaultCanonicalAssembler()
    val result = assembler.assemble(
        config = projectConfig(),
        snapshots = listOf(
            IrAnalysisSnapshot(
                inputDirs = emptyList(),
                nodes = emptyList(),
                edges = emptyList(),
                designElements = listOf(
                    DesignElementSnapshot(
                        tag = "query",
                        packageName = "order.read",
                        name = "FindOrder",
                        description = "find order",
                    ),
                    DesignElementSnapshot(
                        tag = "qry",
                        packageName = "order.read",
                        name = "LegacyFindOrder",
                        description = "legacy find order",
                    ),
                ),
            ),
        ),
    )

    val board = requireNotNull(result.model.drawingBoard)
    assertEquals(listOf("FindOrder"), board.elements.map { it.name })
    assertEquals(listOf("query"), board.elementsByTag.keys.toList())
}
```

- [ ] **Step 4: Run canonical tests and verify they are red**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: compilation fails on `QueryVariant` call sites or assertions fail because alias and suffix behavior still exists.

- [ ] **Step 5: Implement strict canonical filters and trait carry**

In `DefaultCanonicalAssembler.kt`, remove:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.QueryVariant
```

Change command filtering:

```kotlin
.filter { entry -> entry.tag.lowercase(Locale.ROOT) == "command" }
```

Change query filtering and model construction:

```kotlin
val queries = designSnapshot?.entries.orEmpty()
    .asSequence()
    .filter { entry -> entry.tag.lowercase(Locale.ROOT) == "query" }
    .map { entry ->
        QueryModel(
            packageName = entry.packageName,
            typeName = "${entry.name}Qry",
            description = entry.description,
            aggregateRef = entry.requestAggregateRef(aggregateLookup),
            requestFields = entry.requestFields,
            responseFields = entry.responseFields,
            traits = entry.traits,
        )
    }
    .toList()
```

Change client filtering:

```kotlin
.filter { entry -> entry.tag.lowercase(Locale.ROOT) == "client" }
```

Change API payload construction:

```kotlin
ApiPayloadModel(
    packageName = entry.packageName,
    typeName = entry.name.normalizeUpperCamelTypeName(),
    description = entry.description,
    requestFields = entry.requestFields,
    responseFields = entry.responseFields,
    traits = entry.traits,
)
```

Delete the `resolveQueryVariant` function.

Replace drawing-board tag normalization with canonical-only mapping:

```kotlin
private fun normalizeDrawingBoardTag(tag: String): String? =
    when (tag.lowercase(Locale.ROOT)) {
        "command" -> "command"
        "query" -> "query"
        "client" -> "client"
        "api_payload" -> "api_payload"
        "domain_event" -> "domain_event"
        "validator" -> "validator"
        else -> null
    }
```

- [ ] **Step 6: Update all canonical tests to canonical tags**

Run this search:

```powershell
rg 'tag = "(cmd|qry|cli|clients|payload|de|query_list|query_page)"|QueryVariant|variant = QueryVariant' cap4k-plugin-pipeline-core/src/test/kotlin
```

For every match in `DefaultCanonicalAssemblerTest.kt`, use canonical tags and remove query variant arguments. Keep `CommandVariant` unchanged.

- [ ] **Step 7: Run core tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit canonical query cleanup**

Run:

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: assemble strict query contracts"
```

---

### Task 5: Query Generator Planning And Render Context

**Files:**

- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryTemplateIds.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlannerTest.kt`

- [ ] **Step 1: Replace query artifact planner variant tests**

In `DesignQueryArtifactPlannerTest.kt`, remove:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.QueryVariant
```

Add:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
```

Replace the variant tests with:

```kotlin
@Test
fun `designQuery always plans query template and exposes page request context`() {
    val planner = DesignQueryArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            queries = listOf(
                queryModel(typeName = "FindOrderQry"),
                queryModel(
                    typeName = "FindOrderPageQry",
                    traits = setOf(RequestTrait.PAGE),
                    responseFields = listOf(
                        FieldModel("page", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                        FieldModel("page.list[].orderId", "Long"),
                    ),
                ),
            ),
        ),
    )

    assertEquals(listOf("design/query.kt.peb", "design/query.kt.peb"), items.map { it.templateId })
    assertEquals(listOf(false, true), items.map { it.context["pageRequest"] })
    assertEquals(
        listOf(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt",
        ),
        items.map { it.outputPath },
    )
}

@Test
fun `query suffixes do not affect template selection`() {
    val planner = DesignQueryArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("application" to "demo-application")),
        model = CanonicalModel(
            queries = listOf(
                queryModel(typeName = "FindOrderListQry"),
                queryModel(typeName = "FindOrderPageQry"),
            ),
        ),
    )

    assertEquals(listOf("design/query.kt.peb", "design/query.kt.peb"), items.map { it.templateId })
    assertEquals(listOf(false, false), items.map { it.context["pageRequest"] })
}
```

Update the helper:

```kotlin
private fun queryModel(
    typeName: String = "FindOrderQry",
    traits: Set<RequestTrait> = emptySet(),
    responseFields: List<FieldModel> = emptyList(),
) = QueryModel(
    packageName = "order.read",
    typeName = typeName,
    description = "find order",
    aggregateRef = AggregateRef("Order", "com.acme.demo.domain.aggregates.order"),
    traits = traits,
    responseFields = responseFields,
)
```

- [ ] **Step 2: Replace query handler planner variant tests**

In `DesignQueryHandlerArtifactPlannerTest.kt`, remove `QueryVariant` imports and constructor arguments.

Change the main expected template list to:

```kotlin
assertEquals(
    listOf(
        "design/query_handler.kt.peb",
        "design/query_handler.kt.peb",
        "design/query_handler.kt.peb",
    ),
    items.map { it.templateId },
)
```

Rename the suffix test to:

```kotlin
fun `query suffixes do not affect handler template selection`()
```

Keep this assertion:

```kotlin
assertTrue(items.all { it.templateId == "design/query_handler.kt.peb" })
```

- [ ] **Step 3: Add API payload planner test for page trait and page envelope fields**

In `DesignApiPayloadArtifactPlannerTest.kt`, add:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
```

Append:

```kotlin
@Test
fun `api payload planner supports page request trait with PageData item envelope`() {
    val planner = DesignApiPayloadArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
        model = CanonicalModel(
            apiPayloads = listOf(
                ApiPayloadModel(
                    packageName = "order",
                    typeName = "FindOrderPage",
                    description = "find order page",
                    traits = setOf(RequestTrait.PAGE),
                    responseFields = listOf(
                        FieldModel("page", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                        FieldModel("page.list[].orderId", "Long"),
                        FieldModel("page.list[].title", "String"),
                    ),
                ),
            ),
        ),
    )

    val payload = items.single()
    assertEquals(true, payload.context["pageRequest"])
    assertEquals(
        listOf(DesignRenderFieldModel(name = "page", renderedType = "PageData<Item>")),
        payload.context["responseFields"],
    )
    assertEquals(
        listOf(
            DesignRenderNestedTypeModel(
                name = "Item",
                fields = listOf(
                    DesignRenderFieldModel(name = "orderId", renderedType = "Long"),
                    DesignRenderFieldModel(name = "title", renderedType = "String"),
                ),
            ),
        ),
        payload.context["responseNestedTypes"],
    )
    assertEquals(listOf("com.only4.cap4k.ddd.core.share.PageData"), payload.context["imports"])
}
```

- [ ] **Step 4: Replace direct `self` planner tests**

If `DesignApiPayloadArtifactPlannerTest.kt` contains a test named:

```kotlin
fun `api payload planner resolves self as response root type`()
```

replace it with:

```kotlin
@Test
fun `api payload planner rejects self response recursion`() {
    val planner = DesignApiPayloadArtifactPlanner()

    val error = assertThrows(IllegalArgumentException::class.java) {
        planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                apiPayloads = listOf(
                    ApiPayloadModel(
                        packageName = "category",
                        typeName = "GetCategoryTree",
                        description = "get category tree",
                        responseFields = listOf(
                            FieldModel("nodes", "List<Node>"),
                            FieldModel("nodes[].children", "List<self>"),
                        ),
                    ),
                ),
            ),
        )
    }

    assertTrue(error.message?.contains("self") == true)
}
```

Add imports if missing:

```kotlin
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
```

- [ ] **Step 5: Update command planner helper QueryModel constructors**

In `DesignCommandArtifactPlannerTest.kt`, remove:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.QueryVariant
```

Remove `variant = QueryVariant.DEFAULT` from any `QueryModel(...)` constructor.

- [ ] **Step 6: Run generator-design tests and verify they are red**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryArtifactPlannerTest" --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryHandlerArtifactPlannerTest" --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlannerTest"
```

Expected: compilation fails because production planners still reference `query.variant` and `DesignQueryTemplateIds`.

- [ ] **Step 7: Collapse query planner template routing**

In `DesignQueryArtifactPlanner.kt`, replace:

```kotlin
templateId = query.variant.requestTemplateId,
```

with:

```kotlin
templateId = "design/query.kt.peb",
```

In `DesignQueryHandlerArtifactPlanner.kt`, replace:

```kotlin
templateId = query.variant.handlerTemplateId,
```

with:

```kotlin
templateId = "design/query_handler.kt.peb",
```

Delete `DesignQueryTemplateIds.kt`.

- [ ] **Step 8: Add `pageRequest` to render context**

In `DesignRenderModels.kt`, add `pageRequest` to `DesignRenderModel`:

```kotlin
val pageRequest: Boolean = false,
```

Add it to `toContextMap()`:

```kotlin
"pageRequest" to pageRequest,
```

In `DesignPayloadRenderModelFactory.kt`, add imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.QueryModel
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
```

Change `create(...)` to pass:

```kotlin
pageRequest = interaction is QueryModel && RequestTrait.PAGE in interaction.traits,
```

Change `createForApiPayload(...)` to pass:

```kotlin
pageRequest = RequestTrait.PAGE in payload.traits,
```

Add the parameter to `createRenderModel(...)`:

```kotlin
pageRequest: Boolean = false,
```

Pass it into `DesignRenderModel(...)`:

```kotlin
pageRequest = pageRequest,
```

- [ ] **Step 9: Remove root `self` resolution from render model factory**

In `DesignPayloadRenderModelFactory.kt`, remove `rootTypeName` from:

```kotlin
buildNamespace(...)
PayloadPathNode.toPreparedField(...)
resolveDesignType(...)
```

Remove this branch:

```kotlin
val isSelf = type.tokenText == "self"
val parsedRawText = if (isSelf) {
    rootTypeName?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("self is not supported in $namespace namespace")
} else {
    type.tokenText
}
val resolvesToInner = isSelf || parsedRawText in innerTypeNames
```

Replace it with:

```kotlin
val parsedRawText = type.tokenText
val resolvesToInner = parsedRawText in innerTypeNames
```

This makes `self` an unresolved short type in direct canonical model tests, while design-json rejects it earlier with a clearer source error.

- [ ] **Step 10: Support explicit `PageData<Item>` envelope item paths**

In `DesignPayloadRenderModelFactory.kt`, add:

```kotlin
private val pageEnvelopeTypes = setOf("PageData")
private const val pageEnvelopeListField = "list"

private data class PageEnvelopeNode(
    val itemNode: PayloadPathNode,
    val itemTypeName: String,
)
```

Add helper:

```kotlin
private fun pageEnvelopeNode(node: PayloadPathNode, namespace: String): PageEnvelopeNode? {
    val directField = node.explicitDeclarations.singleOrNull() ?: return null
    val itemTypeName = pageEnvelopeItemTypeName(directField.type) ?: return null
    require(node.children.keys == setOf(pageEnvelopeListField)) {
        "PageData field ${node.pathText} in $namespace namespace must declare nested item fields under list[]"
    }
    return PageEnvelopeNode(
        itemNode = node.children.getValue(pageEnvelopeListField),
        itemTypeName = itemTypeName,
    )
}

private fun pageEnvelopeItemTypeName(type: String): String? {
    val trimmed = type.trim()
    val genericStart = trimmed.indexOf('<')
    if (genericStart < 0) {
        return null
    }
    val genericEnd = trimmed.lastIndexOf('>')
    if (genericEnd <= genericStart) {
        return null
    }
    val rootType = simpleTypeName(trimmed.substring(0, genericStart))
    if (rootType !in pageEnvelopeTypes) {
        return null
    }
    val arguments = splitGenericArguments(trimmed.substring(genericStart + 1, genericEnd))
    require(arguments.size == 1) {
        "PageData field type must declare exactly one item type: $type"
    }
    return simpleNestedTypeCandidate(arguments.single())
        ?: throw IllegalArgumentException("PageData item type must be an explicit nested type: $type")
}
```

In `collectNestedTypeNodes(...)`, check the page envelope before normal nested container validation:

```kotlin
val pageEnvelope = pageEnvelopeNode(child, namespace)
if (pageEnvelope != null) {
    val nestedTypeName = pageEnvelope.itemTypeName
    if (!nestedTypeNames.add(nestedTypeName)) {
        throw IllegalArgumentException("duplicate nested type name in $namespace namespace: $nestedTypeName")
    }
    pageEnvelope.itemNode.nestedTypeName = nestedTypeName
    nestedTypeNodes += pageEnvelope.itemNode
    collectNestedTypeNodes(pageEnvelope.itemNode, namespace, nestedTypeNames, nestedTypeNodes)
    return@forEach
}
```

Keep the current collection handling for ordinary `List<Item>` fields. This targeted branch only prevents `page` itself from becoming a generated nested type; it generates the `Item` nested type from `page.list[]` children.

- [ ] **Step 11: Run generator-design tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-design:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: Verify query variant code is gone from generator-design**

Run:

```powershell
rg "QueryVariant|DesignQueryTemplateIds|query.variant|requestTemplateId|handlerTemplateId" cap4k-plugin-pipeline-generator-design/src
```

Expected: no matches.

- [ ] **Step 13: Commit generator planning and render context**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design
git add cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design
git rm cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryTemplateIds.kt
git commit -m "feat: collapse query generator variants"
```

---

### Task 6: Default Pebble Templates For PageRequest

**Files:**

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Update preset coverage test**

In `PebbleArtifactRendererTest.kt`, remove these template IDs from any default preset template list:

```kotlin
"design/query_list.kt.peb"
"design/query_page.kt.peb"
"design/query_list_handler.kt.peb"
"design/query_page_handler.kt.peb"
```

Keep:

```kotlin
"design/query.kt.peb"
"design/query_handler.kt.peb"
```

- [ ] **Step 2: Replace old query list/page rendering test**

Remove assertions that expect:

```kotlin
ListQueryParam
PageQueryParam
ListQuery<
PageQuery<
List<FindOrderListQry.Response>
PageData<FindOrderPageQry.Response>
```

Add a query page request rendering test:

```kotlin
@Test
fun `renders query page request with complete response envelope`() {
    val renderer = PebbleArtifactRenderer()
    val rendered = renderer.render(
        listOf(
            ArtifactPlanItem(
                generatorId = "design-query",
                moduleRole = "application",
                templateId = "design/query.kt.peb",
                outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderPageQry.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.application.queries",
                    "typeName" to "FindOrderPageQry",
                    "description" to "find order page",
                    "descriptionText" to "find order page",
                    "descriptionCommentText" to "find order page",
                    "descriptionKotlinStringLiteral" to "\"find order page\"",
                    "aggregateName" to null,
                    "imports" to listOf("com.only4.cap4k.ddd.core.share.PageData"),
                    "pageRequest" to true,
                    "requestFields" to listOf(
                        DesignRenderFieldModel(name = "keyword", renderedType = "String?"),
                    ),
                    "responseFields" to listOf(
                        DesignRenderFieldModel(name = "page", renderedType = "PageData<Item>"),
                    ),
                    "requestNestedTypes" to emptyList<DesignRenderNestedTypeModel>(),
                    "responseNestedTypes" to listOf(
                        DesignRenderNestedTypeModel(
                            name = "Item",
                            fields = listOf(
                                DesignRenderFieldModel(name = "orderId", renderedType = "Long"),
                                DesignRenderFieldModel(name = "title", renderedType = "String"),
                            ),
                        ),
                    ),
                ),
                conflictPolicy = ConflictPolicy.OVERWRITE,
            ),
        ),
    ).single().content

    assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.application.query.PageRequest"))
    assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
    assertTrue(rendered.contains("override val pageNum: Int = 1"))
    assertTrue(rendered.contains("override val pageSize: Int = 10"))
    assertTrue(rendered.contains("val keyword: String?"))
    assertTrue(rendered.contains(") : PageRequest, RequestParam<Response>"))
    assertTrue(rendered.contains("val page: PageData<Item>"))
    assertTrue(rendered.contains("data class Item("))
    assertFalse(rendered.contains("ListQueryParam"))
    assertFalse(rendered.contains("PageQueryParam"))
}
```

- [ ] **Step 3: Add API payload page request rendering test**

Append:

```kotlin
@Test
fun `renders api payload page request without RequestParam`() {
    val renderer = PebbleArtifactRenderer()
    val rendered = renderer.render(
        listOf(
            ArtifactPlanItem(
                generatorId = "design-api-payload",
                moduleRole = "adapter",
                templateId = "design/api_payload.kt.peb",
                outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/payload/FindOrderPage.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.adapter.payload",
                    "typeName" to "FindOrderPage",
                    "description" to "find order page",
                    "descriptionText" to "find order page",
                    "descriptionCommentText" to "find order page",
                    "descriptionKotlinStringLiteral" to "\"find order page\"",
                    "aggregateName" to null,
                    "imports" to listOf("com.only4.cap4k.ddd.core.share.PageData"),
                    "pageRequest" to true,
                    "requestFields" to listOf(
                        DesignRenderFieldModel(name = "keyword", renderedType = "String?"),
                    ),
                    "responseFields" to listOf(
                        DesignRenderFieldModel(name = "page", renderedType = "PageData<Item>"),
                    ),
                    "requestNestedTypes" to emptyList<DesignRenderNestedTypeModel>(),
                    "responseNestedTypes" to listOf(
                        DesignRenderNestedTypeModel(
                            name = "Item",
                            fields = listOf(DesignRenderFieldModel(name = "orderId", renderedType = "Long")),
                        ),
                    ),
                ),
                conflictPolicy = ConflictPolicy.OVERWRITE,
            ),
        ),
    ).single().content

    assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.application.query.PageRequest"))
    assertTrue(rendered.contains("override val pageNum: Int = 1"))
    assertTrue(rendered.contains("override val pageSize: Int = 10"))
    assertTrue(rendered.contains("val keyword: String?"))
    assertTrue(rendered.contains(") : PageRequest"))
    assertFalse(rendered.contains("RequestParam<Response>"))
}
```

- [ ] **Step 4: Run renderer tests and verify they are red**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: renderer tests fail because templates do not use `pageRequest` yet and removed template IDs still exist.

- [ ] **Step 5: Update `query.kt.peb`**

At the top, change:

```peb
{{ use("com.only4.cap4k.ddd.core.application.RequestParam") -}}
```

to:

```peb
{{ use("com.only4.cap4k.ddd.core.application.RequestParam") -}}
{% if pageRequest %}{{ use("com.only4.cap4k.ddd.core.application.query.PageRequest") -}}{% endif %}
```

Change every request class branch so:

- Non-page request with fields renders `) : RequestParam<Response>`.
- Non-page request without fields renders `class Request : RequestParam<Response>`.
- Page request with or without fields renders a `data class Request(...) : PageRequest, RequestParam<Response>`.
- Page request always declares:

```kotlin
override val pageNum: Int = 1
override val pageSize: Int = 10
```

Use this request branch shape:

```peb
{% if requestFields.size > 0 or pageRequest %}
{% if requestNestedTypes.size > 0 %}
    data class Request(
{% if pageRequest %}
        override val pageNum: Int = 1,
        override val pageSize: Int = 10{% if requestFields.size > 0 %},{% endif %}
{% endif %}
{%- for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{%- endfor %}
    ) : {% if pageRequest %}PageRequest, {% endif %}RequestParam<Response> {
{%- for nestedType in requestNestedTypes %}
        data class {{ nestedType.name }}(
{%- for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{%- endfor %}
        )
{%- endfor %}
    }
{% else %}
    data class Request(
{% if pageRequest %}
        override val pageNum: Int = 1,
        override val pageSize: Int = 10{% if requestFields.size > 0 %},{% endif %}
{% endif %}
{%- for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{%- endfor %}
    ) : {% if pageRequest %}PageRequest, {% endif %}RequestParam<Response>
{% endif %}
{% else %}
    class Request : RequestParam<Response>
{% endif %}
```

- [ ] **Step 6: Update `api_payload.kt.peb`**

Add the conditional import at the top:

```peb
{% if pageRequest %}{{ use("com.only4.cap4k.ddd.core.application.query.PageRequest") -}}{% endif %}
```

Change request branches so:

- Non-page API payloads keep the current no-supertype shape.
- Page API payloads render `data class Request(...) : PageRequest`.
- API payloads never render `RequestParam<Response>`.

Use this supertype expression on page branches:

```peb
{% if pageRequest %} : PageRequest{% endif %}
```

- [ ] **Step 7: Delete old default query variant templates**

Delete:

```text
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb
```

- [ ] **Step 8: Run renderer tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Verify removed template IDs are gone from renderer module**

Run:

```powershell
rg "query_list|query_page|ListQuery|PageQuery|ListQueryParam|PageQueryParam" cap4k-plugin-pipeline-renderer-pebble/src
```

Expected: no matches.

- [ ] **Step 10: Commit template cleanup**

Run:

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git rm cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb
git rm cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb
git rm cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb
git rm cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb
git commit -m "feat: render page requests without query shortcuts"
```

---

### Task 7: Functional Fixtures And Compile Coverage

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/design/design.json`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample/design/design.json`
- Delete: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list.kt.peb`
- Delete: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page.kt.peb`
- Delete: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list_handler.kt.peb`
- Delete: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Normalize `design-sample` tags and response envelopes**

In `design-sample/design/design.json`, replace legacy tags:

```json
"tag": "cmd"
"tag": "qry"
"tag": "cli"
```

with:

```json
"tag": "command"
"tag": "query"
"tag": "client"
```

For `FindOrderList`, keep the name but rewrite response fields to an explicit list envelope:

```json
"responseFields": [
  { "name": "items", "type": "List<Item>" },
  { "name": "items[].responseStatus", "type": "com.bar.Status" },
  { "name": "items[].summary", "type": "Summary", "nullable": true },
  { "name": "items[].summary.updatedAt", "type": "java.time.LocalDateTime" },
  { "name": "items[].summary.summaryId", "type": "java.util.UUID" }
]
```

For `FindOrderPage`, add the page trait and rewrite response fields to an explicit page envelope:

```json
"traits": ["page"],
"responseFields": [
  { "name": "page", "type": "com.only4.cap4k.ddd.core.share.PageData<Item>" },
  { "name": "page.list[].responseStatus", "type": "com.bar.Status" },
  { "name": "page.list[].snapshot", "type": "Snapshot", "nullable": true },
  { "name": "page.list[].snapshot.publishedAt", "type": "java.time.LocalDateTime" },
  { "name": "page.list[].snapshot.snapshotId", "type": "java.util.UUID" }
]
```

Do not add `pageNum` or `pageSize` to design input manually; the `PAGE` trait owns those request fields.

- [ ] **Step 2: Normalize `design-compile-sample`**

In `design-compile-sample/design/design.json`, keep all query tags as `query`.

For `FindOrderList`, use:

```json
"responseFields": [
  { "name": "items", "type": "List<Item>" },
  { "name": "items[].count", "type": "Int" }
]
```

For `FindOrderPage`, add:

```json
"traits": ["page"],
"responseFields": [
  { "name": "page", "type": "com.only4.cap4k.ddd.core.share.PageData<Item>" },
  { "name": "page.list[].total", "type": "Long" }
]
```

- [ ] **Step 3: Remove `self` from nested recursion fixture**

In `design-nested-recursion-compile-sample/design/design.json`, replace the response fields using `List<self>` with explicit `Node` fields:

```json
"responseFields": [
  { "name": "nodes", "type": "List<Node>", "nullable": false },
  { "name": "nodes[].categoryId", "type": "Long", "nullable": false },
  { "name": "nodes[].children", "type": "List<Node>", "nullable": false },
  { "name": "list", "type": "List<Item>", "nullable": false },
  { "name": "list[].messageType", "type": "Int", "nullable": false },
  { "name": "list[].count", "type": "Int", "nullable": false }
]
```

- [ ] **Step 4: Delete old fixture override templates**

Delete:

```text
cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list.kt.peb
cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page.kt.peb
cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list_handler.kt.peb
cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page_handler.kt.peb
```

Keep:

```text
cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb
cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_handler.kt.peb
```

- [ ] **Step 5: Update functional plan assertions**

In `PipelinePluginFunctionalTest.kt`, update `cap4kPlan writes pretty printed plan json`:

```kotlin
assertTrue(planFile.readText().contains("\"templateId\": \"design/query.kt.peb\""))
assertFalse(planFile.readText().contains("\"templateId\": \"design/query_list.kt.peb\""))
assertFalse(planFile.readText().contains("\"templateId\": \"design/query_page.kt.peb\""))
```

Update `cap4kPlan includes query handler artifacts when enabled`:

```kotlin
assertTrue(content.contains("\"templateId\": \"design/query_handler.kt.peb\""))
assertFalse(content.contains("\"templateId\": \"design/query_list_handler.kt.peb\""))
assertFalse(content.contains("\"templateId\": \"design/query_page_handler.kt.peb\""))
```

- [ ] **Step 6: Replace generated list/page query assertions**

Rename:

```kotlin
fun `cap4kGenerate renders list and page query variants from repository config`()
```

to:

```kotlin
fun `cap4kGenerate renders contract first list and page query envelopes`()
```

For `FindOrderListQry.kt`, assert:

```kotlin
assertTrue(listQueryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
assertTrue(listQueryContent.contains("class Request : RequestParam<Response>"))
assertTrue(listQueryContent.contains("data class Response("))
assertTrue(listQueryContent.contains("val items: List<Item>"))
assertTrue(listQueryContent.contains("data class Item("))
assertFalse(listQueryContent.contains("ListQueryParam"))
assertFalse(listQueryContent.contains("ListQuery<"))
```

For `FindOrderPageQry.kt`, assert:

```kotlin
assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.application.query.PageRequest"))
assertTrue(pageQueryContent.contains("import com.only4.cap4k.ddd.core.share.PageData"))
assertTrue(pageQueryContent.contains("override val pageNum: Int = 1"))
assertTrue(pageQueryContent.contains("override val pageSize: Int = 10"))
assertTrue(pageQueryContent.contains(") : PageRequest, RequestParam<Response>"))
assertTrue(pageQueryContent.contains("val page: PageData<Item>"))
assertTrue(pageQueryContent.contains("data class Item("))
assertFalse(pageQueryContent.contains("PageQueryParam"))
assertFalse(pageQueryContent.contains("PageQuery<"))
```

- [ ] **Step 7: Replace query handler variant assertions**

Rename:

```kotlin
fun `cap4kGenerate renders query handler variants into adapter module`()
```

to:

```kotlin
fun `cap4kGenerate renders query handlers with the unified query contract`()
```

For every generated query handler content, assert:

```kotlin
assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
assertTrue(content.contains(" : Query<"))
assertTrue(content.contains(".Request, "))
assertTrue(content.contains(".Response>"))
assertFalse(content.contains("ListQuery<"))
assertFalse(content.contains("PageQuery<"))
assertFalse(content.contains("List<FindOrderListQry.Response>"))
assertFalse(content.contains("PageData<FindOrderPageQry.Response>"))
```

Keep exact handler class assertions in this shape:

```kotlin
assertTrue(listContent.contains("class FindOrderListQryHandler : Query<FindOrderListQry.Request, FindOrderListQry.Response>"))
assertTrue(pageContent.contains("class FindOrderPageQryHandler : Query<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
```

- [ ] **Step 8: Update override-template functional tests**

Delete or rename the test:

```kotlin
fun `cap4kGenerate supports override list and page query templates`()
```

Use this replacement:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate supports unified query template override for all query names`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-query-override")
    copyFixture(projectDir)

    val buildFile = projectDir.resolve("build.gradle.kts")
    val buildFileContent = buildFile.readText().replace("\r\n", "\n")
    buildFile.writeText(
        buildFileContent.replace(
            """
            templates {
                conflictPolicy.set(ConflictPolicy.OVERWRITE)
            }
            """.trimIndent(),
            """
            templates {
                conflictPolicy.set(ConflictPolicy.OVERWRITE)
                overrideDirs.from("codegen/templates")
            }
            """.trimIndent(),
        )
    )

    val metadataFile = projectDir.resolve("domain/build/generated/ksp/main/resources/metadata/aggregate-Order.json")
    Files.createDirectories(metadataFile.parent)
    Files.writeString(metadataFile, aggregateMetadataJson())

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val defaultContent = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
    ).readText()
    val listContent = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt",
    ).readText()
    val pageContent = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt",
    ).readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(defaultContent.contains("// override: representative default query migration template"))
    assertTrue(listContent.contains("// override: representative default query migration template"))
    assertTrue(pageContent.contains("// override: representative default query migration template"))
}
```

Update `cap4kGenerate supports override query handler templates` so all generated handlers use the same `query_handler.kt.peb` override marker. Remove assertions for representative list/page handler override markers.

- [ ] **Step 9: Update compile functional assertions**

In `PipelinePluginCompileFunctionalTest.kt`, replace assertions that expect:

```kotlin
val children: List<Response>?
```

with:

```kotlin
val nodes: List<Node>
val children: List<Node>
```

For query compile sample assertions, add:

```kotlin
assertTrue(pageQueryContent.contains(") : PageRequest, RequestParam<Response>"))
assertTrue(pageQueryContent.contains("val page: PageData<Item>"))
assertTrue(listQueryContent.contains("val items: List<Item>"))
```

- [ ] **Step 10: Run targeted functional tests with long timeouts**

Run these commands separately:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kPlan writes pretty printed plan json"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate renders contract first list and page query envelopes"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate renders query handlers with the unified query contract"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate supports unified query template override for all query names"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: each command reports BUILD SUCCESSFUL. If an agent tool runs these commands, set timeout to at least `1500000` milliseconds.

- [ ] **Step 11: Verify fixture cleanup**

Run:

```powershell
rg '"tag": "(cmd|qry|cli|clients|payload|de|query_list|query_page)"' cap4k-plugin-pipeline-gradle/src/test/resources/functional cap4k-plugin-pipeline-source-design-json/src/test/resources
rg "query_list|query_page|ListQuery|PageQuery|ListQueryParam|PageQueryParam|List<FindOrderListQry.Response>|PageData<FindOrderPageQry.Response>|List<self>" cap4k-plugin-pipeline-gradle/src/test
```

Expected: no matches.

- [ ] **Step 12: Commit functional migration**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/design/design.json
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample/design/design.json
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git rm cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list.kt.peb
git rm cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page.kt.peb
git rm cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_list_handler.kt.peb
git rm cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query_page_handler.kt.peb
git commit -m "test: migrate query fixtures to contract first envelopes"
```

---

### Task 8: Final Verification And Roadmap Update

**Files:**

- Modify: `docs/superpowers/mainline-roadmap.md`

- [ ] **Step 1: Run module tests as separate commands**

Run:

```powershell
.\gradlew.bat :ddd-core:test
.\gradlew.bat :cap4k-plugin-pipeline-api:test
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test
.\gradlew.bat :cap4k-plugin-pipeline-core:test
.\gradlew.bat :cap4k-plugin-pipeline-generator-design:test
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: every command reports BUILD SUCCESSFUL.

- [ ] **Step 2: Run Gradle plugin functional suites**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: both commands report BUILD SUCCESSFUL. If an agent tool runs these commands, set timeout to at least `1500000` milliseconds.

- [ ] **Step 3: Run repository cleanup scans**

Run:

```powershell
rg "QueryVariant|ListQuery|PageQuery|ListQueryParam|PageQueryParam|query_list|query_page" ddd-core/src/main cap4k-plugin-pipeline-api/src cap4k-plugin-pipeline-core/src/main cap4k-plugin-pipeline-generator-design/src/main cap4k-plugin-pipeline-renderer-pebble/src/main cap4k-plugin-pipeline-gradle/src/test/resources
rg '"tag": "(cmd|qry|cli|clients|payload|de|query_list|query_page)"' cap4k-plugin-pipeline-gradle/src/test/resources cap4k-plugin-pipeline-source-design-json/src/test/resources
rg "List<Response>|PageData<Response>|List<self>|List<FindOrderListQry.Response>|PageData<FindOrderPageQry.Response>" cap4k-plugin-pipeline-gradle/src/test cap4k-plugin-pipeline-renderer-pebble/src/test cap4k-plugin-pipeline-generator-design/src/test
```

Expected:

- First command prints no matches.
- Second command prints no matches.
- Third command prints no matches outside tests that explicitly assert rejection of legacy input or unsupported `self`.

- [ ] **Step 4: Update roadmap status**

In `docs/superpowers/mainline-roadmap.md`, under `### 1. Contract-first query contract`, replace:

```markdown
Status:

- spec-only by design
```

with:

```markdown
Status:

- implementation complete
- verified through ddd-core, pipeline API, design-json source, canonical assembly, design generator, Pebble renderer, and Gradle functional tests
```

Replace the `Next action` bullets for this section with:

```markdown
Next action:

- continue with ddd-core nullability contract stabilization as the next independent mainline slice
```

- [ ] **Step 5: Run diff and status checks**

Run:

```powershell
git diff --check
git status --short
```

Expected: `git diff --check` prints no whitespace errors. `git status --short` shows only the roadmap change before the final docs commit.

- [ ] **Step 6: Commit roadmap update**

Run:

```powershell
git add docs/superpowers/mainline-roadmap.md
git commit -m "docs: mark contract first query implementation complete"
```

- [ ] **Step 7: Final clean status**

Run:

```powershell
git status --short
```

Expected: no output.

## Self-Review Checklist

- Spec requirement: delete `QueryVariant` -> Task 1, Task 4, Task 5, Task 8 cleanup scan.
- Spec requirement: no query suffix inference -> Task 4 and Task 5 suffix tests.
- Spec requirement: strict `tag = "query"` and no old query tags -> Task 3, Task 4, Task 7 fixture scan.
- User decision: delete broader legacy tag aliases -> Task 3 source parser and Task 4 drawing-board canonical boundary.
- Spec requirement: no automatic response wrapping -> Task 7 rewrites fixtures explicitly and Task 5 adds only explicit `PageData<Item>` nested path support.
- Spec requirement: nested fields use dotted paths and `[]` -> Task 3 fixtures, Task 5 `page.list[]`, Task 7 functional fixtures.
- Spec requirement: reject `self` recursion -> Task 3 source validation, Task 5 generator test, Task 7 fixture migration.
- Spec requirement: `PAGE` trait only on `query` and `api_payload` -> Task 3 parser tests and Task 4 canonical carry.
- Spec requirement: `PageRequest` contains only `pageNum/pageSize` -> Task 2 ddd-core trait and Task 6 templates.
- Spec requirement: API payload page trait does not implement `RequestParam<Response>` -> Task 6 API payload renderer test.
- Spec requirement: remove old default query templates -> Task 5 planner cleanup, Task 6 template deletion, Task 8 cleanup scan.
- Verification requirement: compile-level list/page/tree envelopes -> Task 7 compile functional fixture updates and Task 8 functional suite.

## Execution Options

Plan complete and saved to `docs/superpowers/plans/2026-04-28-cap4k-contract-first-query-contract.md`.

Two execution options:

1. **Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?

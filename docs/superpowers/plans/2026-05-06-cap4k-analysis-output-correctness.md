# Cap4k Analysis Output Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the `analysis -> drawing-board -> flow` correctness slice by restoring `CommandHandlerToEntityMethod` for top-level aggregate behavior extensions, preserving stable supported `defaultValue` expressions through analysis projection, and disabling HTML escaping in drawing-board JSON output.

**Architecture:** Keep fixes at the earliest broken stage. `cap4k-plugin-code-analysis-compiler` recovers missing graph semantics and preserves the supported `defaultValue` subset; `cap4k-plugin-pipeline-renderer-pebble` fixes shared JSON readability. Downstream drawing-board and flow modules are used for regression verification, not redesign.

**Tech Stack:** Kotlin, Gradle, JUnit 5, compile-testing, Kotlin compiler IR plugin, Gson, Pebble.

---

## Scope Guard

This plan is intentionally narrow.

Do:

- work in an isolated git worktree
- add regression coverage before implementation
- change only the compiler analysis path and the shared Pebble JSON filter unless a downstream test proves a tiny follow-up is required

Do not:

- restructure broad `irAnalysis`
- redesign flow rendering
- touch unrelated anonymous-flow aggregation
- add a new diagnostics subsystem for unsupported defaults
- perform downstream `only-danmuku-zero` dogfood as part of this execution

## File Map

Expected production changes:

- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt`

Expected test changes:

- Create: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

Verification-only modules:

- `cap4k-plugin-pipeline-generator-drawing-board`
- `cap4k-plugin-pipeline-generator-flow`

### Task 1: Create Isolated Worktree And Capture Baseline

**Files:**
- Modify: none
- Test: focused Gradle commands listed below

- [ ] **Step 1: Create the isolated worktree**

Run:

```powershell
git worktree add .worktrees/analysis-output-correctness -b feature/analysis-output-correctness
```

Expected: a new worktree is created at `.worktrees/analysis-output-correctness` on branch `feature/analysis-output-correctness`.

- [ ] **Step 2: Verify the execution context is the new worktree**

Run:

```powershell
git -C .worktrees/analysis-output-correctness status --short --branch
git -C .worktrees/analysis-output-correctness rev-parse --show-toplevel
```

Expected:

- branch output starts with `## feature/analysis-output-correctness`
- top-level path ends with `.worktrees/analysis-output-correctness`

- [ ] **Step 3: Run baseline focused tests before making changes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest" --tests "com.only4.cap4k.plugin.codeanalysis.compiler.TestCompileHelperTest"
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Workdir for both commands: the new worktree root.

Expected: PASS. This confirms the starting point is green before the new regressions are added.

### Task 2: Add And Fix The Top-Level Behavior Extension Graph Regression

**Files:**
- Create: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`
- Test: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt`

- [ ] **Step 1: Write the failing compiler graph regression**

Create `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt` with this content:

```kotlin
package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalysisOutputCorrectnessTest {
    @Test
    fun `emits entity method edge for top level behavior extension and keeps domain event edge`() {
        val sources = listOf(
            SourceFile.kotlin(
                "RequestParam.kt",
                "package com.only4.cap4k.ddd.core.application; interface RequestParam<T>"
            ),
            SourceFile.kotlin(
                "RequestHandler.kt",
                """
                    package com.only4.cap4k.ddd.core.application

                    interface RequestHandler<RQ : RequestParam<RS>, RS> {
                        fun handle(request: RQ): RS
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Command.kt",
                """
                    package com.only4.cap4k.ddd.core.application.command

                    interface Command<RQ : com.only4.cap4k.ddd.core.application.RequestParam<RS>, RS> :
                        com.only4.cap4k.ddd.core.application.RequestHandler<RQ, RS>
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Aggregate.kt",
                """
                    package com.only4.cap4k.ddd.core.domain.aggregate.annotation

                    annotation class Aggregate(
                        val aggregate: String = "",
                        val type: String = "",
                        val root: Boolean = false,
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "DomainEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.domain.event.annotation

                    annotation class DomainEvent(
                        val value: String = "",
                        val persist: Boolean = false,
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Category.kt",
                """
                    package demo.domain.aggregates.category
                    import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate

                    @Aggregate(aggregate = "Category", type = "entity", root = true)
                    class Category {
                        fun publish(event: Any) = event
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CategorySortChanged.kt",
                """
                    package demo.domain.aggregates.category.events
                    import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
                    import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

                    @DomainEvent
                    @Aggregate(aggregate = "Category", type = "domain-event")
                    data class CategorySortChanged(val sort: Int)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CategoryBehavior.kt",
                """
                    package demo.domain.aggregates.category
                    import demo.domain.aggregates.category.events.CategorySortChanged

                    fun Category.changeSort(sort: Int) {
                        publish(CategorySortChanged(sort))
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "UpdateCategorySortCmd.kt",
                """
                    package demo.application.commands.category
                    import demo.domain.aggregates.category.Category
                    import demo.domain.aggregates.category.changeSort

                    object UpdateCategorySortCmd {
                        data class Request(val sort: Int) :
                            com.only4.cap4k.ddd.core.application.RequestParam<Response>

                        data class Response(val ok: Boolean)

                        class Handler :
                            com.only4.cap4k.ddd.core.application.command.Command<Request, Response> {
                            override fun handle(request: Request): Response {
                                val category = Category()
                                category.changeSort(request.sort)
                                return Response(true)
                            }
                        }
                    }
                """.trimIndent()
            ),
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertRelPresent(
            rels = rels,
            fromId = "demo.application.commands.category.UpdateCategorySortCmd.Handler",
            toId = "demo.domain.aggregates.category.Category::changeSort",
            type = "CommandHandlerToEntityMethod",
        )
        assertRelPresent(
            rels = rels,
            fromId = "demo.domain.aggregates.category.Category",
            toId = "demo.domain.aggregates.category.Category::changeSort",
            type = "AggregateToEntityMethod",
        )
        assertRelPresent(
            rels = rels,
            fromId = "demo.domain.aggregates.category.Category::changeSort",
            toId = "demo.domain.aggregates.category.events.CategorySortChanged",
            type = "EntityMethodToDomainEvent",
        )
    }

    private fun assertRelPresent(rels: String, fromId: String, toId: String, type: String) {
        assertTrue(
            rels.contains("\"fromId\":\"$fromId\",\"toId\":\"$toId\",\"type\":\"$type\""),
            "Missing relationship $type from $fromId to $toId in $rels",
        )
    }
}
```

- [ ] **Step 2: Run the new graph regression and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.AnalysisOutputCorrectnessTest.emits entity method edge for top level behavior extension and keeps domain event edge"
```

Expected: FAIL because top-level extension behavior is not yet converted into `CommandHandlerToEntityMethod` with a stable entity-method ID.

- [ ] **Step 3: Implement the minimal extension-receiver entity-method recovery**

Update `Cap4kIrGenerationExtension.kt` with the following targeted changes.

First, add this helper data class near `AggregateInfo`:

```kotlin
private data class ExtensionAggregateTarget(
    val entityFq: String,
    val aggregateRootFq: String,
)
```

Then update the start of `visitFunctionNew` to derive the method identity from the extension receiver when the function is top-level aggregate behavior:

```kotlin
override fun visitFunctionNew(declaration: IrFunction): IrStatement {
    val parentClass = declaration.parent as? IrClass
    val methodName = declaration.name.asString()
    val parentFqcn = parentClass?.fqNameWhenAvailable?.asString()
    val extensionAggregateTarget = resolveExtensionAggregateTarget(declaration)
    val methodId = when {
        parentFqcn != null -> "$parentFqcn::$methodName"
        extensionAggregateTarget != null -> "${extensionAggregateTarget.entityFq}::$methodName"
        else -> methodName
    }
    val methodDisplayName = when {
        parentClass != null -> buildMethodDisplayName(parentClass, methodName)
        extensionAggregateTarget != null -> buildMethodDisplayNameFromFqcn(extensionAggregateTarget.entityFq, methodName)
        else -> methodName
    }
```

Replace the existing entity-method node registration block with:

```kotlin
    val aggInfo = parentFqcn?.let { index.aggregateInfoByClass[it] }
    if ((aggInfo != null && aggInfo.type == AGG_TYPE_ENTITY) || extensionAggregateTarget != null) {
        addNode(Node(id = methodId, name = methodDisplayName, fullName = methodId, type = NodeType.entitymethod))
    }
```

Inside `visitCall`, replace the current `handlerContext.isNotEmpty()` block with:

```kotlin
                if (handlerContext.isNotEmpty()) {
                    val handlerId = handlerContext.lastOrNull()
                    val targetClass = expression.symbol.owner.parent as? IrClass
                    val extensionTarget = resolveExtensionAggregateTarget(expression.symbol.owner)
                    if (extensionTarget != null) {
                        val calleeId = "${extensionTarget.entityFq}::${expression.symbol.owner.name.asString()}"
                        val calleeName = buildMethodDisplayNameFromFqcn(
                            extensionTarget.entityFq,
                            expression.symbol.owner.name.asString(),
                        )
                        addNode(Node(id = calleeId, name = calleeName, fullName = calleeId, type = NodeType.entitymethod))
                        if (handlerId != null) {
                            addRel(
                                Relationship(
                                    fromId = handlerId,
                                    toId = calleeId,
                                    type = RelationshipType.CommandHandlerToEntityMethod,
                                )
                            )
                        }
                        addNode(
                            Node(
                                id = extensionTarget.aggregateRootFq,
                                name = typeDisplayNameForFqcn(extensionTarget.aggregateRootFq),
                                fullName = extensionTarget.aggregateRootFq,
                                type = NodeType.aggregate,
                            )
                        )
                        addRel(
                            Relationship(
                                fromId = extensionTarget.aggregateRootFq,
                                toId = calleeId,
                                type = RelationshipType.AggregateToEntityMethod,
                            )
                        )
                    } else if (targetClass != null) {
                        val targetFq = targetClass.fqNameWhenAvailable?.asString()
                        val targetAggInfo = targetClass.aggregateInfo()
                        if (targetAggInfo != null && targetAggInfo.type == AGG_TYPE_ENTITY) {
                            val aggRootFq = if (targetAggInfo.root) {
                                targetFq
                            } else {
                                targetAggInfo.aggregateName
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { aggregateRootsByName[it] }
                                    ?: targetFq
                            }
                            val calleeId = "$targetFq::${expression.symbol.owner.name.asString()}"
                            val calleeName = buildMethodDisplayName(targetClass, expression.symbol.owner.name.asString())
                            addNode(Node(id = calleeId, name = calleeName, fullName = calleeId, type = NodeType.entitymethod))
                            if (handlerId != null) {
                                addRel(Relationship(fromId = handlerId, toId = calleeId, type = RelationshipType.CommandHandlerToEntityMethod))
                            }
                            if (aggRootFq != null) {
                                addNode(Node(id = aggRootFq, name = typeDisplayNameForFqcn(aggRootFq), fullName = aggRootFq, type = NodeType.aggregate))
                                addRel(Relationship(fromId = aggRootFq, toId = calleeId, type = RelationshipType.AggregateToEntityMethod))
                            }
                        }
                    }
                }
```

Finally, add this helper inside `GraphCollector`, near `resolveAggregateRootFromType`:

```kotlin
    private fun resolveExtensionAggregateTarget(function: IrFunction): ExtensionAggregateTarget? {
        val receiverType = function.extensionReceiverParameter?.type as? IrSimpleType ?: return null
        val receiverClass = receiverType.classifier?.owner as? IrClass ?: return null
        val entityFq = receiverClass.fqNameWhenAvailable?.asString() ?: return null
        val info = receiverClass.aggregateInfo() ?: return null
        if (info.type != AGG_TYPE_ENTITY) return null
        val aggregateRootFq = if (info.root) {
            aggregateRootsByName.putIfAbsent(info.aggregateName, entityFq)
            entityFq
        } else {
            aggregateRootsByName[info.aggregateName] ?: entityFq
        }
        return ExtensionAggregateTarget(entityFq = entityFq, aggregateRootFq = aggregateRootFq)
    }
```

- [ ] **Step 4: Run the graph regression again**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.AnalysisOutputCorrectnessTest.emits entity method edge for top level behavior extension and keeps domain event edge"
```

Expected: PASS.

- [ ] **Step 5: Commit the graph regression repair**

Run:

```powershell
git add cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt
git commit -m "fix: restore top level behavior analysis edges"
```

### Task 3: Add And Fix The Stable `defaultValue` Projection Regression

**Files:**
- Modify: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- Test: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt`

- [ ] **Step 1: Add the failing stable-default regression to the compiler test file**

Append these tests and helpers to `AnalysisOutputCorrectnessTest.kt`:

```kotlin
    @Test
    fun `preserves stable supported default values in design elements json`() {
        val sources = listOf(
            SourceFile.kotlin(
                "RequestParam.kt",
                "package com.only4.cap4k.ddd.core.application; interface RequestParam<T>"
            ),
            SourceFile.kotlin(
                "CaptchaChannel.kt",
                """
                    package demo.application.commands.auth

                    enum class CaptchaChannel {
                        INLINE,
                        SMS,
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "IssueCaptchaCmd.kt",
                """
                    package demo.application.commands.auth

                    object IssueCaptchaCmd {
                        data class Request(
                            val nullableToken: String? = null,
                            val retryCount: Int = 1,
                            val enabled: Boolean = true,
                            val tags: List<String> = emptyList(),
                            val scopes: Set<String> = emptySet(),
                            val headers: Map<String, String> = emptyMap(),
                            val channel: CaptchaChannel = CaptchaChannel.INLINE,
                        ) : com.only4.cap4k.ddd.core.application.RequestParam<Response>

                        data class Response(val ok: Boolean)
                    }
                """.trimIndent()
            ),
        )

        val outputDir = compileWithCap4kPlugin(sources)
        val json = outputDir.resolve("design-elements.json").toFile().readText()
        val commandElement = findObject(extractTopLevelObjects(json), "command", "IssueCaptcha")

        assertFieldDefaultValue(commandElement, "nullableToken", "null")
        assertFieldDefaultValue(commandElement, "retryCount", "1")
        assertFieldDefaultValue(commandElement, "enabled", "true")
        assertFieldDefaultValue(commandElement, "tags", "emptyList()")
        assertFieldDefaultValue(commandElement, "scopes", "emptySet()")
        assertFieldDefaultValue(commandElement, "headers", "emptyMap()")
        assertFieldDefaultValue(commandElement, "channel", "CaptchaChannel.INLINE")
    }

    @Test
    fun `fails fast for unsupported default expressions in analysis projection`() {
        val sources = listOf(
            SourceFile.kotlin(
                "RequestParam.kt",
                "package com.only4.cap4k.ddd.core.application; interface RequestParam<T>"
            ),
            SourceFile.kotlin(
                "IssueCaptchaCmd.kt",
                """
                    package demo.application.commands.auth

                    object IssueCaptchaCmd {
                        data class Request(
                            val channels: List<String> = listOf("inline"),
                        ) : com.only4.cap4k.ddd.core.application.RequestParam<Response>

                        data class Response(val ok: Boolean)
                    }
                """.trimIndent()
            ),
        )

        val messages = compileWithCap4kPluginExpectingFailure(sources)

        assertTrue(
            messages.contains(
                "unsupported defaultValue expression for command IssueCaptcha request field channels",
            ),
        )
    }

    private fun assertFieldDefaultValue(elementJson: String, fieldName: String, defaultValue: String) {
        assertTrue(
            elementJson.contains("\"name\":\"$fieldName\"") &&
                elementJson.contains("\"defaultValue\":\"$defaultValue\""),
            "Missing defaultValue $defaultValue for $fieldName in $elementJson",
        )
    }

    private fun extractTopLevelObjects(json: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escape = false
        json.forEachIndexed { index, ch ->
            if (escape) {
                escape = false
                return@forEachIndexed
            }
            if (ch == '\\' && inString) {
                escape = true
                return@forEachIndexed
            }
            if (ch == '"') {
                inString = !inString
                return@forEachIndexed
            }
            if (inString) return@forEachIndexed
            when (ch) {
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects.add(json.substring(start, index + 1))
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun findObject(objects: List<String>, tag: String, name: String): String {
        return objects.firstOrNull { it.contains("\"tag\":\"$tag\"") && it.contains("\"name\":\"$name\"") }
            ?: error("Missing element tag=$tag name=$name")
    }
```

- [ ] **Step 2: Run the new default-value regression and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.AnalysisOutputCorrectnessTest.preserves stable supported default values in design elements json"
```

Expected:

- the supported-default test FAILS because `DesignElementCollector.resolveDefaultValue` currently preserves only the narrow constant path and drops `null`, collection factories, and enum references
- the unsupported-default test FAILS because there is no explicit compiler-side fail-fast message yet

- [ ] **Step 3: Implement a narrow stable-default whitelist in the collector**

In `DesignElementCollector.kt`, add these imports:

```kotlin
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
```

Replace `resolveDefaultValue` with this implementation:

```kotlin
    private fun resolveDefaultValue(param: IrValueParameter): String? {
        val expression = param.defaultValue?.expression?.unwrapDefaultValueExpression() ?: return null
        return renderStableDefaultValue(expression)
    }
```

Add these helpers below it:

```kotlin
    private fun renderStableDefaultValue(expression: IrExpression): String? {
        return when (expression) {
            is IrConst<*> -> expression.value?.toString() ?: "null"
            is IrGetEnumValue -> {
                val owner = expression.symbol.owner
                "${owner.parentAsClass.name.asString()}.${owner.name.asString()}"
            }
            is IrGetField -> {
                val owner = expression.symbol.owner
                val parentClass = owner.parent as? IrClass
                when {
                    parentClass != null -> "${parentClass.name.asString()}.${owner.name.asString()}"
                    owner.isConst -> owner.name.asString()
                    else -> null
                }
            }
            is IrCall -> renderStableFactoryCall(expression)
            else -> null
        }
    }

    private fun renderStableFactoryCall(expression: IrCall): String? {
        val calleeFq = expression.symbol.owner.fqNameWhenAvailable?.asString() ?: return null
        return when (calleeFq) {
            "kotlin.collections.emptyList" -> "emptyList()"
            "kotlin.collections.emptySet" -> "emptySet()"
            "kotlin.collections.emptyMap" -> "emptyMap()"
            else -> null
        }
    }

    private fun IrExpression.unwrapDefaultValueExpression(): IrExpression {
        var current: IrExpression = this
        while (true) {
            current = when (current) {
                is IrTypeOperatorCall -> current.argument
                is IrBlock -> current.statements.lastOrNull() as? IrExpression ?: return current
                is IrComposite -> current.statements.lastOrNull() as? IrExpression ?: return current
                else -> return current
            }
        }
    }
```

Then add an explicit checked wrapper and use it from field and parameter collection sites.

Update the two call sites that currently assign `defaultValue = resolveDefaultValue(...)` so they instead pass projection context:

```kotlin
            val defaultValue = resolveDefaultValueOrFail(
                ownerTag = kind.tag,
                ownerName = name,
                fieldRole = "request field",
                fieldName = fieldPath,
                parameter = param,
            )
```

and:

```kotlin
                defaultValue = resolveDefaultValueOrFail(
                    ownerTag = "validator",
                    ownerName = annotationClass.name.asString(),
                    fieldRole = "parameter",
                    fieldName = name,
                    parameter = parameter,
                ),
```

Add this helper:

```kotlin
    private fun resolveDefaultValueOrFail(
        ownerTag: String,
        ownerName: String,
        fieldRole: String,
        fieldName: String,
        parameter: IrValueParameter,
    ): String? {
        val expression = parameter.defaultValue?.expression?.unwrapDefaultValueExpression() ?: return null
        return renderStableDefaultValue(expression)
            ?: error("unsupported defaultValue expression for $ownerTag $ownerName $fieldRole $fieldName")
    }
```

Do not add broader expression support.
If the expression is not covered by the explicit whitelist, fail fast with the targeted message above.

- [ ] **Step 4: Run the default-value regression again**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.AnalysisOutputCorrectnessTest.preserves stable supported default values in design elements json"
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.AnalysisOutputCorrectnessTest.fails fast for unsupported default expressions in analysis projection"
```

Expected: both commands PASS.

- [ ] **Step 5: Commit the default-value repair**

Run:

```powershell
git add cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt
git commit -m "fix: preserve stable analysis default values"
```

### Task 4: Add And Fix The Drawing-Board JSON Escaping Regression

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add the failing renderer regression**

Append this test near the existing drawing-board JSON tests in `PebbleArtifactRendererTest.kt`:

```kotlin
    @Test
    fun `renders drawing board json without html escaping generic types and stable defaults`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = emptyList()
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = "design/command.json",
                    context = mapOf(
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "command",
                                packageName = "orders",
                                name = "DescribeOrder",
                                description = "describe order",
                                requestFields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "items",
                                        type = "List<Response>",
                                        nullable = false,
                                        defaultValue = "emptyList()",
                                    ),
                                    DrawingBoardFieldModel(
                                        name = "lookup",
                                        type = "Map<String, Response>",
                                        nullable = false,
                                        defaultValue = "emptyMap()",
                                    ),
                                    DrawingBoardFieldModel(
                                        name = "channel",
                                        type = "CaptchaChannel",
                                        nullable = false,
                                        defaultValue = "CaptchaChannel.INLINE",
                                    ),
                                ),
                                responseFields = emptyList(),
                            )
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
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
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("\"type\": \"List<Response>\""))
        assertTrue(content.contains("\"type\": \"Map<String, Response>\""))
        assertTrue(content.contains("\"defaultValue\": \"emptyList()\""))
        assertTrue(content.contains("\"defaultValue\": \"emptyMap()\""))
        assertTrue(content.contains("\"defaultValue\": \"CaptchaChannel.INLINE\""))
        assertFalse(content.contains("\\u003c"))
        assertFalse(content.contains("\\u003e"))
    }
```

- [ ] **Step 2: Run the new renderer regression and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.renders drawing board json without html escaping generic types and stable defaults"
```

Expected: FAIL because the shared Pebble `json` filter still uses default Gson HTML escaping.

- [ ] **Step 3: Disable HTML escaping in the shared JSON filter**

In `PipelinePebbleExtension.kt`, add this import if it is not already present:

```kotlin
import com.google.gson.GsonBuilder
```

Then replace the `JsonFilter` declaration with:

```kotlin
private class JsonFilter(
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .create(),
) : Filter {
    override fun getArgumentNames(): List<String> = emptyList()

    override fun apply(
        input: Any?,
        args: MutableMap<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int,
    ): Any = gson.toJson(input)
}
```

- [ ] **Step 4: Run the renderer regression again**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.renders drawing board json without html escaping generic types and stable defaults"
```

Expected: PASS.

- [ ] **Step 5: Commit the renderer repair**

Run:

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt
git commit -m "fix: disable html escaping in drawing board json"
```

### Task 5: Run Repository Verification And Prepare Handoff Evidence

**Files:**
- Modify: none
- Test: targeted Gradle and git verification commands

- [ ] **Step 1: Run the focused compiler correctness class**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.AnalysisOutputCorrectnessTest"
```

Expected: PASS.

- [ ] **Step 2: Run the full changed-module test suites**

Run these as separate commands from the worktree root:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: each command reports `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run verification-only downstream module tests**

Run these as separate commands from the worktree root:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-drawing-board:test --tests "com.only4.cap4k.plugin.pipeline.generator.drawingboard.DrawingBoardArtifactPlannerTest"
.\gradlew.bat :cap4k-plugin-pipeline-generator-flow:test --tests "com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlannerTest"
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify the worktree is clean and free of whitespace errors**

Run:

```powershell
git diff --check
git status --short
```

Expected:

- `git diff --check` prints nothing
- `git status --short` is empty because the implementation commits from Tasks 2-4 are already recorded

- [ ] **Step 5: Capture the evidence for the final handoff**

Record these items in the final implementation handoff:

- changed files list
- verification commands run
- the specific tests added:
  - `AnalysisOutputCorrectnessTest.emits entity method edge for top level behavior extension and keeps domain event edge`
  - `AnalysisOutputCorrectnessTest.preserves stable supported default values in design elements json`
  - `PebbleArtifactRendererTest.renders drawing board json without html escaping generic types and stable defaults`
- residual risks:
  - unsupported complex default expressions now fail fast, so existing projects using them will need explicit repair before analysis projection succeeds
  - downstream dogfood against `only-danmuku-zero` remains deferred to the coordinating agent

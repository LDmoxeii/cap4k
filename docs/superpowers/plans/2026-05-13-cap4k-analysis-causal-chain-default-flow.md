# cap4k Analysis Causal Chain Default Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing `flow` generator emit a default causal-chain projection instead of a broad technical dependency flow.

**Architecture:** Keep `FlowArtifactPlanner` and the generated artifact contract unchanged. Rework `FlowGraphSupport.kt` so it first projects raw analysis edges into a causal-chain edge set, collapses command handlers into `CommandToEntityMethod` edges, filters root entries by "no upstream causal edge", and then reuses the existing DFS traversal and JSON/Mermaid rendering.

**Tech Stack:** Kotlin JVM 17, Gradle, JUnit 5, Gson, cap4k pipeline API models.

---

## File Structure

- Modify: `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt`
  - Owns causal-chain edge vocabulary, command-handler collapse, root entry selection, DFS traversal, JSON payload construction, and Mermaid rendering.
- Modify: `cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt`
  - Owns planner-level behavior tests for emitted artifacts, JSON content, index counts, and compatibility with the existing `flow` generator contract.
- Do not modify: `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlanner.kt`
  - The generator id, template ids, output root, and artifact layout remain unchanged.
- Do not modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - `AnalysisGraphModel`, `AnalysisNodeModel`, and `AnalysisEdgeModel` stay unchanged.

## Task 1: Add Causal-Chain Planner Tests

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt`

- [ ] **Step 1: Update the existing allowed-entry test to expect command-handler collapse**

Replace the body of `plans json mermaid and index artifacts from allowed entry graph` with:

```kotlin
@Test
fun `plans json mermaid and index artifacts from allowed entry graph`() {
    val planner = FlowArtifactPlanner()
    val model = CanonicalModel(
        analysisGraph = AnalysisGraphModel(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                AnalysisNodeModel(
                    id = "OrderController::submit",
                    name = "OrderController::submit",
                    fullName = "OrderController::submit",
                    type = "controllermethod",
                ),
                AnalysisNodeModel(
                    id = "SubmitOrderCmd",
                    name = "SubmitOrderCmd",
                    fullName = "SubmitOrderCmd",
                    type = "command",
                ),
                AnalysisNodeModel(
                    id = "SubmitOrderHandler",
                    name = "SubmitOrderHandler",
                    fullName = "SubmitOrderHandler",
                    type = "commandhandler",
                ),
                AnalysisNodeModel(
                    id = "Order::submit",
                    name = "Order::submit",
                    fullName = "Order::submit",
                    type = "entitymethod",
                ),
                AnalysisNodeModel(
                    id = "IgnoredAggregate",
                    name = "IgnoredAggregate",
                    fullName = "IgnoredAggregate",
                    type = "aggregate",
                ),
            ),
            edges = listOf(
                AnalysisEdgeModel("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                AnalysisEdgeModel("SubmitOrderCmd", "SubmitOrderHandler", "CommandToCommandHandler"),
                AnalysisEdgeModel("SubmitOrderHandler", "Order::submit", "CommandHandlerToEntityMethod"),
                AnalysisEdgeModel("SubmitOrderHandler", "IgnoredAggregate", "CommandHandlerToAggregate"),
            ),
        ),
    )

    val plan = planner.plan(config(), model)
    val entryJson = plan[0].context["jsonContent"] as String
    val mermaidText = plan[1].context["mermaidText"] as String
    val indexJson = plan[2].context["jsonContent"] as String

    assertEquals(3, plan.size)
    assertEquals("flow/entry.json.peb", plan[0].templateId)
    assertEquals("flows/OrderController_submit.json", plan[0].outputPath)
    assertEquals("flow/entry.mmd.peb", plan[1].templateId)
    assertEquals("flows/OrderController_submit.mmd", plan[1].outputPath)
    assertEquals("flow/index.json.peb", plan[2].templateId)
    assertEquals("flows/index.json", plan[2].outputPath)
    assertTrue(entryJson.contains("\"edgeCount\": 2"))
    assertTrue(entryJson.contains("CommandToEntityMethod"))
    assertTrue(entryJson.contains("Order::submit"))
    assertTrue(!entryJson.contains("SubmitOrderHandler"))
    assertTrue(!entryJson.contains("IgnoredAggregate"))
    assertTrue(mermaidText.contains("flowchart TD"))
    assertTrue(mermaidText.contains("CommandToEntityMethod"))
    assertTrue(indexJson.contains("\"flowCount\": 1"))
}
```

- [ ] **Step 2: Add a test for query, cli, and validator exclusion**

Add this test before `adds digest suffix when slugified entry ids collide`:

```kotlin
@Test
fun `excludes query cli and validator paths from default causal chain`() {
    val planner = FlowArtifactPlanner()
    val model = CanonicalModel(
        analysisGraph = AnalysisGraphModel(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                AnalysisNodeModel("QuerySender::find", "QuerySender::find", "QuerySender::find", "querysendermethod"),
                AnalysisNodeModel("FindOrderQry", "FindOrderQry", "FindOrderQry", "query"),
                AnalysisNodeModel("FindOrderQryHandler", "FindOrderQryHandler", "FindOrderQryHandler", "queryhandler"),
                AnalysisNodeModel("CliSender::run", "CliSender::run", "CliSender::run", "clisendermethod"),
                AnalysisNodeModel("RebuildIndexCli", "RebuildIndexCli", "RebuildIndexCli", "cli"),
                AnalysisNodeModel("RebuildIndexCliHandler", "RebuildIndexCliHandler", "RebuildIndexCliHandler", "clihandler"),
                AnalysisNodeModel("SubmitOrderValidator", "SubmitOrderValidator", "SubmitOrderValidator", "validator"),
            ),
            edges = listOf(
                AnalysisEdgeModel("QuerySender::find", "FindOrderQry", "QuerySenderMethodToQuery"),
                AnalysisEdgeModel("FindOrderQry", "FindOrderQryHandler", "QueryToQueryHandler"),
                AnalysisEdgeModel("CliSender::run", "RebuildIndexCli", "CliSenderMethodToCli"),
                AnalysisEdgeModel("RebuildIndexCli", "RebuildIndexCliHandler", "CliToCliHandler"),
                AnalysisEdgeModel("SubmitOrderValidator", "FindOrderQry", "ValidatorToQuery"),
            ),
        ),
    )

    val plan = planner.plan(config(), model)
    val indexJson = plan.single().context["jsonContent"] as String

    assertEquals("flow/index.json.peb", plan.single().templateId)
    assertTrue(indexJson.contains("\"flowCount\": 0"))
    assertTrue(!indexJson.contains("querysendermethod"))
    assertTrue(!indexJson.contains("clisendermethod"))
    assertTrue(!indexJson.contains("validator"))
}
```

- [ ] **Step 3: Add a test for root integration-event filtering**

Add this test after the exclusion test:

```kotlin
@Test
fun `does not emit integration event as separate flow when it has upstream causal edge`() {
    val planner = FlowArtifactPlanner()
    val model = CanonicalModel(
        analysisGraph = AnalysisGraphModel(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                AnalysisNodeModel("OrderController::pay", "OrderController::pay", "OrderController::pay", "controllermethod"),
                AnalysisNodeModel("PayOrderCmd", "PayOrderCmd", "PayOrderCmd", "command"),
                AnalysisNodeModel("PayOrderHandler", "PayOrderHandler", "PayOrderHandler", "commandhandler"),
                AnalysisNodeModel("Order::pay", "Order::pay", "Order::pay", "entitymethod"),
                AnalysisNodeModel("OrderPaidDomainEvent", "OrderPaidDomainEvent", "OrderPaidDomainEvent", "domainevent"),
                AnalysisNodeModel("OrderPaidIntegrationEvent", "OrderPaidIntegrationEvent", "OrderPaidIntegrationEvent", "integrationevent"),
                AnalysisNodeModel("OrderPaidIntegrationEventHandler", "OrderPaidIntegrationEventHandler", "OrderPaidIntegrationEventHandler", "integrationeventhandler"),
                AnalysisNodeModel("SendReceiptCmd", "SendReceiptCmd", "SendReceiptCmd", "command"),
            ),
            edges = listOf(
                AnalysisEdgeModel("OrderController::pay", "PayOrderCmd", "ControllerMethodToCommand"),
                AnalysisEdgeModel("PayOrderCmd", "PayOrderHandler", "CommandToCommandHandler"),
                AnalysisEdgeModel("PayOrderHandler", "Order::pay", "CommandHandlerToEntityMethod"),
                AnalysisEdgeModel("Order::pay", "OrderPaidDomainEvent", "EntityMethodToDomainEvent"),
                AnalysisEdgeModel("OrderPaidDomainEvent", "OrderPaidIntegrationEvent", "DomainEventToIntegrationEvent"),
                AnalysisEdgeModel("OrderPaidIntegrationEvent", "OrderPaidIntegrationEventHandler", "IntegrationEventToHandler"),
                AnalysisEdgeModel("OrderPaidIntegrationEventHandler", "SendReceiptCmd", "IntegrationEventHandlerToCommand"),
            ),
        ),
    )

    val plan = planner.plan(config(), model)
    val jsonEntries = plan.filter { it.templateId == "flow/entry.json.peb" }
    val indexJson = plan.last().context["jsonContent"] as String
    val entryJson = jsonEntries.single().context["jsonContent"] as String

    assertEquals(1, jsonEntries.size)
    assertEquals("flows/OrderController_pay.json", jsonEntries.single().outputPath)
    assertTrue(indexJson.contains("\"flowCount\": 1"))
    assertTrue(indexJson.contains("\"controllermethod\": 1"))
    assertTrue(!indexJson.contains("\"integrationevent\": 1"))
    assertTrue(entryJson.contains("OrderPaidIntegrationEvent"))
    assertTrue(entryJson.contains("OrderPaidIntegrationEventHandler"))
    assertTrue(entryJson.contains("SendReceiptCmd"))
}
```

- [ ] **Step 4: Add a test for event-handler visibility and DFS natural stop**

Add this test after the root filtering test:

```kotlin
@Test
fun `keeps empty domain event handler visible and stops naturally`() {
    val planner = FlowArtifactPlanner()
    val model = CanonicalModel(
        analysisGraph = AnalysisGraphModel(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                AnalysisNodeModel("OrderController::cancel", "OrderController::cancel", "OrderController::cancel", "controllermethod"),
                AnalysisNodeModel("CancelOrderCmd", "CancelOrderCmd", "CancelOrderCmd", "command"),
                AnalysisNodeModel("CancelOrderHandler", "CancelOrderHandler", "CancelOrderHandler", "commandhandler"),
                AnalysisNodeModel("Order::cancel", "Order::cancel", "Order::cancel", "entitymethod"),
                AnalysisNodeModel("OrderCancelledDomainEvent", "OrderCancelledDomainEvent", "OrderCancelledDomainEvent", "domainevent"),
                AnalysisNodeModel("OrderCancelledDomainEventHandler", "OrderCancelledDomainEventHandler", "OrderCancelledDomainEventHandler", "domaineventhandler"),
            ),
            edges = listOf(
                AnalysisEdgeModel("OrderController::cancel", "CancelOrderCmd", "ControllerMethodToCommand"),
                AnalysisEdgeModel("CancelOrderCmd", "CancelOrderHandler", "CommandToCommandHandler"),
                AnalysisEdgeModel("CancelOrderHandler", "Order::cancel", "CommandHandlerToEntityMethod"),
                AnalysisEdgeModel("Order::cancel", "OrderCancelledDomainEvent", "EntityMethodToDomainEvent"),
                AnalysisEdgeModel("OrderCancelledDomainEvent", "OrderCancelledDomainEventHandler", "DomainEventToHandler"),
            ),
        ),
    )

    val plan = planner.plan(config(), model)
    val entryJson = plan.first().context["jsonContent"] as String

    assertTrue(entryJson.contains("OrderCancelledDomainEventHandler"))
    assertTrue(entryJson.contains("\"edgeCount\": 4"))
    assertTrue(!entryJson.contains("CancelOrderHandler"))
}
```

- [ ] **Step 5: Add a test for inbound integration-event chains**

Add this test after the domain event handler test:

```kotlin
@Test
fun `keeps integration event handler visible when inbound event sends command`() {
    val planner = FlowArtifactPlanner()
    val model = CanonicalModel(
        analysisGraph = AnalysisGraphModel(
            inputDirs = listOf("app/build/cap4k-code-analysis"),
            nodes = listOf(
                AnalysisNodeModel("MediaProcessedIntegrationEvent", "MediaProcessedIntegrationEvent", "MediaProcessedIntegrationEvent", "integrationevent"),
                AnalysisNodeModel("MediaProcessedIntegrationEventHandler", "MediaProcessedIntegrationEventHandler", "MediaProcessedIntegrationEventHandler", "integrationeventhandler"),
                AnalysisNodeModel("AttachMediaCmd", "AttachMediaCmd", "AttachMediaCmd", "command"),
                AnalysisNodeModel("AttachMediaHandler", "AttachMediaHandler", "AttachMediaHandler", "commandhandler"),
                AnalysisNodeModel("Content::attachMedia", "Content::attachMedia", "Content::attachMedia", "entitymethod"),
            ),
            edges = listOf(
                AnalysisEdgeModel("MediaProcessedIntegrationEvent", "MediaProcessedIntegrationEventHandler", "IntegrationEventToHandler"),
                AnalysisEdgeModel("MediaProcessedIntegrationEventHandler", "AttachMediaCmd", "IntegrationEventHandlerToCommand"),
                AnalysisEdgeModel("AttachMediaCmd", "AttachMediaHandler", "CommandToCommandHandler"),
                AnalysisEdgeModel("AttachMediaHandler", "Content::attachMedia", "CommandHandlerToEntityMethod"),
            ),
        ),
    )

    val plan = planner.plan(config(), model)
    val entryJson = plan.first().context["jsonContent"] as String
    val indexJson = plan.last().context["jsonContent"] as String

    assertEquals("flows/MediaProcessedIntegrationEvent.json", plan.first().outputPath)
    assertTrue(indexJson.contains("\"integrationevent\": 1"))
    assertTrue(entryJson.contains("MediaProcessedIntegrationEventHandler"))
    assertTrue(entryJson.contains("AttachMediaCmd"))
    assertTrue(entryJson.contains("Content::attachMedia"))
    assertTrue(entryJson.contains("CommandToEntityMethod"))
    assertTrue(!entryJson.contains("AttachMediaHandler"))
}
```

- [ ] **Step 6: Run the focused test class and confirm it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-flow:test --tests "com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlannerTest"
```

Expected: FAIL. The existing implementation still emits broad edges, still creates query/cli/validator flows, still emits command handler nodes, and does not filter root entries by upstream causal edges.

- [ ] **Step 7: Commit the failing tests**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt
git commit -m "test: cover causal chain flow projection"
```

## Task 2: Implement Causal Edge Projection

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt`

- [ ] **Step 1: Replace broad flow constants with causal-chain constants**

In `FlowGraphSupport.kt`, replace `allowedEdgeTypes` and `entryNodeTypes` with:

```kotlin
private const val CONTROLLER_METHOD_TO_COMMAND = "ControllerMethodToCommand"
private const val COMMAND_SENDER_METHOD_TO_COMMAND = "CommandSenderMethodToCommand"
private const val COMMAND_TO_COMMAND_HANDLER = "CommandToCommandHandler"
private const val COMMAND_HANDLER_TO_ENTITY_METHOD = "CommandHandlerToEntityMethod"
private const val COMMAND_TO_ENTITY_METHOD = "CommandToEntityMethod"
private const val ENTITY_METHOD_TO_ENTITY_METHOD = "EntityMethodToEntityMethod"
private const val ENTITY_METHOD_TO_DOMAIN_EVENT = "EntityMethodToDomainEvent"
private const val DOMAIN_EVENT_TO_HANDLER = "DomainEventToHandler"
private const val DOMAIN_EVENT_HANDLER_TO_COMMAND = "DomainEventHandlerToCommand"
private const val DOMAIN_EVENT_TO_INTEGRATION_EVENT = "DomainEventToIntegrationEvent"
private const val INTEGRATION_EVENT_TO_HANDLER = "IntegrationEventToHandler"
private const val INTEGRATION_EVENT_HANDLER_TO_COMMAND = "IntegrationEventHandlerToCommand"

private val rawCausalEdgeTypes = setOf(
    CONTROLLER_METHOD_TO_COMMAND,
    COMMAND_SENDER_METHOD_TO_COMMAND,
    COMMAND_TO_COMMAND_HANDLER,
    COMMAND_HANDLER_TO_ENTITY_METHOD,
    ENTITY_METHOD_TO_ENTITY_METHOD,
    ENTITY_METHOD_TO_DOMAIN_EVENT,
    DOMAIN_EVENT_TO_HANDLER,
    DOMAIN_EVENT_HANDLER_TO_COMMAND,
    DOMAIN_EVENT_TO_INTEGRATION_EVENT,
    INTEGRATION_EVENT_TO_HANDLER,
    INTEGRATION_EVENT_HANDLER_TO_COMMAND,
)

private val entryNodeTypes = setOf(
    "controllermethod",
    "commandsendermethod",
    "integrationevent",
)
```

- [ ] **Step 2: Add a projection helper that collapses command handlers**

Add this function below `buildPlannedFlows` or above it if preferred for readability:

```kotlin
private fun projectCausalEdges(rawEdges: List<AnalysisEdgeModel>): List<AnalysisEdgeModel> {
    val causalEdges = rawEdges
        .filter { it.type in rawCausalEdgeTypes }
        .distinctBy { EdgeKey(it.fromId, it.toId, it.type, it.label) }

    val entityEdgesByHandler = causalEdges
        .filter { it.type == COMMAND_HANDLER_TO_ENTITY_METHOD }
        .groupBy { it.fromId }

    val collapsedCommandEdges = causalEdges
        .filter { it.type == COMMAND_TO_COMMAND_HANDLER }
        .flatMap { commandToHandler ->
            entityEdgesByHandler[commandToHandler.toId].orEmpty().map { handlerToEntity ->
                AnalysisEdgeModel(
                    fromId = commandToHandler.fromId,
                    toId = handlerToEntity.toId,
                    type = COMMAND_TO_ENTITY_METHOD,
                    label = handlerToEntity.label,
                )
            }
        }

    val visibleEdges = causalEdges.filterNot {
        it.type == COMMAND_TO_COMMAND_HANDLER || it.type == COMMAND_HANDLER_TO_ENTITY_METHOD
    }

    return (visibleEdges + collapsedCommandEdges)
        .distinctBy { EdgeKey(it.fromId, it.toId, it.type, it.label) }
}
```

This keeps raw analysis unchanged while hiding command handlers from the flow projection.

- [ ] **Step 3: Use the projection helper in `buildPlannedFlows`**

Replace:

```kotlin
val edges = graph.edges
    .filter { it.type in allowedEdgeTypes }
    .distinctBy { EdgeKey(it.fromId, it.toId, it.type, it.label) }
val adjacency = edges.groupBy { it.fromId }
```

with:

```kotlin
val edges = projectCausalEdges(graph.edges)
val adjacency = edges.groupBy { it.fromId }
```

- [ ] **Step 4: Run the focused test class and inspect remaining failures**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-flow:test --tests "com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlannerTest"
```

Expected: tests related to query/cli/validator exclusion and command-handler collapse pass. The root-entry filtering test can still fail until Task 3 is implemented.

- [ ] **Step 5: Commit causal edge projection**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt
git commit -m "feat: project flow edges as causal chain"
```

## Task 3: Implement Root Entry Filtering

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt`

- [ ] **Step 1: Add a root-entry helper**

Add this function near `projectCausalEdges`:

```kotlin
private fun selectRootEntryNodes(
    nodesById: Map<String, AnalysisNodeModel>,
    edges: List<AnalysisEdgeModel>,
): List<AnalysisNodeModel> {
    val nodesWithUpstream = edges.mapTo(linkedSetOf()) { it.toId }
    return nodesById.values
        .filter { it.type.lowercase() in entryNodeTypes }
        .filterNot { it.id in nodesWithUpstream }
        .sortedBy { it.id }
}
```

- [ ] **Step 2: Use the helper in `buildPlannedFlows`**

Replace:

```kotlin
val entryNodes = nodesById.values
    .filter { it.type.lowercase() in entryNodeTypes }
    .sortedBy { it.id }
```

with:

```kotlin
val entryNodes = selectRootEntryNodes(nodesById, edges)
```

- [ ] **Step 3: Run the focused test class and confirm it passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-flow:test --tests "com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlannerTest"
```

Expected: PASS.

- [ ] **Step 4: Commit root entry filtering**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt
git commit -m "feat: filter causal flow roots by upstream edges"
```

## Task 4: Regression Verification

**Files:**
- Verify: `cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt`
- Verify: `cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt`

- [ ] **Step 1: Run the module test suite**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-flow:test
```

Expected: PASS.

- [ ] **Step 2: Check generated test reports if Gradle reports a failure**

If the command fails, open the module report path printed by Gradle. The expected default path is:

```text
cap4k-plugin-pipeline-generator-flow/build/reports/tests/test/index.html
```

Fix only failures caused by this change. Do not adjust unrelated modules in this task.

- [ ] **Step 3: Inspect the final diff**

Run:

```powershell
git diff -- cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt
```

Expected:

- `FlowArtifactPlanner.kt` is unchanged.
- `PipelineModels.kt` is unchanged.
- `FlowGraphSupport.kt` contains causal constants, `projectCausalEdges`, and `selectRootEntryNodes`.
- `FlowArtifactPlannerTest.kt` contains tests for query/cli/validator exclusion, handler collapse, root filtering, event handler visibility, inbound integration event continuation, and natural stop.

- [ ] **Step 4: Commit any verification fixes**

If Task 4 required small fixes, commit them:

```powershell
git add cap4k-plugin-pipeline-generator-flow/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowGraphSupport.kt cap4k-plugin-pipeline-generator-flow/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/flow/FlowArtifactPlannerTest.kt
git commit -m "test: verify causal chain flow behavior"
```

If Task 4 produced no new changes, do not create an empty commit.

## Task 5: Lifecycle Update

**Files:**
- Verify: GitHub issue #43

- [ ] **Step 1: Confirm working tree status**

Run:

```powershell
git status --short
```

Expected: no uncommitted source or test changes.

- [ ] **Step 2: Update #43 after implementation is complete**

After implementation commits exist and module tests pass, update #43:

```powershell
gh issue comment 43 --repo LDmoxeii/cap4k --body 'Implementation completed for the causal-chain default flow projection. Verified with .\gradlew.bat :cap4k-plugin-pipeline-generator-flow:test. Remaining work: release if required and downstream verification if required.'
```

After the implementation commits are merged into the target branch, edit the issue body to mark:

```text
- [x] implementation merged
```

Only mark `released if required` after Maven publish is done. Only mark `downstream verified if required` after the downstream project consumes and validates the published artifact.

## Self-Review Checklist

- Spec coverage: tasks cover causal edge narrowing, query/cli/validator exclusion, root-entry filtering, command-handler collapse, event-handler visibility, DFS natural stop, artifact compatibility, and issue lifecycle.
- Scope: tasks do not add debug-flow, HTML, snapshots, cross-service event stitching, endpoint/job analysis, or analysis model metadata.
- Type consistency: implementation snippets use existing `AnalysisGraphModel`, `AnalysisNodeModel`, `AnalysisEdgeModel`, `EdgeKey`, `FlowArtifactPlanner`, and `FlowArtifactPlannerTest` names.
- Verification: focused planner test and full flow module test commands are included.

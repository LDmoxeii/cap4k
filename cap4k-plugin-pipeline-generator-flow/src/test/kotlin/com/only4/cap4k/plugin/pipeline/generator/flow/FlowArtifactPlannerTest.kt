package com.only4.cap4k.plugin.pipeline.generator.flow

import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.OutputRootLayout
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityActivation
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class FlowArtifactPlannerTest {

    @Test
    fun `plans json mermaid and index artifacts from allowed entry graph`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("OrderController::submit", "controllermethod"),
                    node("SubmitOrderCmd", "command"),
                    node("SubmitOrderHandler", "commandhandler"),
                    node("Order::submit", "entitymethod"),
                    node("IgnoredAggregate", "aggregate"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                    edge("SubmitOrderCmd", "SubmitOrderHandler", "CommandToCommandHandler"),
                    edge("SubmitOrderHandler", "Order::submit", "CommandHandlerToEntityMethod"),
                    edge("SubmitOrderHandler", "IgnoredAggregate", "CommandHandlerToAggregate"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonContent = plan[0].context["jsonContent"] as String

        assertEquals(3, plan.size)
        assertEquals(PipelineCapabilityActivation.EXPLICIT_CONFIGURATION, planner.descriptor.activation)
        assertEquals(
            listOf(
                "Input: Raw Analysis Graph Evidence",
                "Entry-centered Causal Flow Evidence",
                "Trigger Families: Actor, Event, Time",
                "Current Actor Detectors: Spring HTTP Controller Method, Typed Endpoint MVC Binding",
                "Endpoint HTTP Binding: Command Root, Query Graph-only",
                "Current Event Detector: Inbound Integration Event",
                "Current Time Detector: Spring @Scheduled Method",
                "Visible: Concrete Trigger, Command, Domain Event, Integration Event",
                "Hidden: Command Handler, Domain Event Handler, Integration Event Handler, Entity Method",
                "Projection: Hidden Path Contraction, Root After Projection, Cycle Preservation",
            ),
            planner.descriptor.tacticalCarriers,
        )
        assertEquals("flow/entry.json.peb", plan[0].templateId)
        assertEquals("flows/OrderController_submit.json", plan[0].outputPath)
        assertEquals("flow/entry.mmd.peb", plan[1].templateId)
        assertEquals("flows/OrderController_submit.mmd", plan[1].outputPath)
        assertEquals("flow/index.json.peb", plan[2].templateId)
        assertEquals("flows/index.json", plan[2].outputPath)
        assertTrue(plan.all { it.outputKind == ArtifactOutputKind.OUTPUT_ARTIFACT })
        assertTrue(plan.all { it.resolvedOutputRoot == "flows" })
        assertEquals(
            listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE),
            planner.descriptor.tasks,
        )
        assertTrue(jsonContent.contains("SubmitOrderCmd"))
        assertTrue(jsonContent.contains("\"edgeCount\": 1"))
        assertFalse(jsonContent.contains("Order::submit"))
        assertFalse(jsonContent.contains("SubmitOrderHandler"))
        assertFalse(jsonContent.contains("IgnoredAggregate"))
        assertFalse(jsonContent.contains("CommandToEntityMethod"))
        assertTrue((plan[1].context["mermaidText"] as String).contains("flowchart TD"))
        assertTrue((plan[2].context["jsonContent"] as String).contains("\"flowCount\": 1"))
    }

    @Test
    fun `flow planner reads graph facts without validating aggregate structure metadata`() {
        val planner = FlowArtifactPlanner()
        val plan = planner.plan(
            config(),
            CanonicalModel(
                analysisGraph = AnalysisGraphModel(
                    inputDirs = listOf("domain/build/cap4k-code-analysis"),
                    nodes = listOf(
                        AnalysisNodeModel(
                            id = "demo.domain.Order",
                            name = "Order",
                            fullName = "demo.domain.Order",
                            type = "aggregate",
                            missingMetadata = listOf("com.only4.cap4k.analysis.metadata.AggregateElementMetadata"),
                            metadataOwner = "demo.domain.Order",
                        )
                    ),
                    edges = emptyList(),
                ),
            ),
        )

        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
    }

    @Test
    fun `excludes query capability and validator paths from default causal chain`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("SearchOrdersQuerySender", "querysendermethod"),
                    node("SearchOrdersQuery", "query"),
                    node("SearchOrdersQueryHandler", "queryhandler"),
                    node("ExportOrdersCapabilitySender", "capabilitysendermethod"),
                    node("ExportOrdersCapability", "capability"),
                    node("ExportOrdersCapabilityHandler", "capabilityhandler"),
                    node("SearchOrdersValidator", "validator"),
                ),
                edges = listOf(
                    edge("SearchOrdersQuerySender", "SearchOrdersQuery", "QuerySenderMethodToQuery"),
                    edge("SearchOrdersQuery", "SearchOrdersQueryHandler", "QueryToQueryHandler"),
                    edge("ExportOrdersCapabilitySender", "ExportOrdersCapability", "CapabilitySenderMethodToCapability"),
                    edge("ExportOrdersCapability", "ExportOrdersCapabilityHandler", "CapabilityToCapabilityHandler"),
                    edge("SearchOrdersValidator", "SearchOrdersQuery", "ValidatorToQuery"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        assertEquals(1, plan.size)
        val indexJson = plan.last().context["jsonContent"] as String

        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        assertEquals("flows/index.json", plan.last().outputPath)
        assertTrue(indexJson.contains("\"flowCount\": 0"))
        assertFalse(indexJson.contains("\"querysendermethod\": 1"))
        assertFalse(indexJson.contains("\"capabilitysendermethod\": 1"))
        assertFalse(indexJson.contains("\"validator\": 1"))
    }

    @Test
    fun `does not emit controller query roots without causal outgoing edges`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("QueryController::getContent", "controllermethod"),
                    node("GetContentQuery", "query"),
                    node("GetContentQueryHandler", "queryhandler"),
                ),
                edges = listOf(
                    edge("QueryController::getContent", "GetContentQuery", "ControllerMethodToQuery"),
                    edge("GetContentQuery", "GetContentQueryHandler", "QueryToQueryHandler"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val indexJson = plan.last().context["jsonContent"] as String

        assertEquals(1, plan.size)
        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        assertEquals("flows/index.json", plan.last().outputPath)
        assertTrue(indexJson.contains("\"flowCount\": 0"))
        assertFalse(indexJson.contains("QueryController::getContent"))
        assertFalse(indexJson.contains("\"controllermethod\": 1"))
    }

    @Test
    fun `does not emit controller capability roots without causal outgoing edges`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("OpsController::rebuildIndex", "controllermethod"),
                    node("RebuildIndexCapability", "capability"),
                    node("RebuildIndexCapabilityHandler", "capabilityhandler"),
                ),
                edges = listOf(
                    edge("OpsController::rebuildIndex", "RebuildIndexCapability", "ControllerMethodToCapability"),
                    edge("RebuildIndexCapability", "RebuildIndexCapabilityHandler", "CapabilityToCapabilityHandler"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val indexJson = plan.last().context["jsonContent"] as String

        assertEquals(1, plan.size)
        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        assertEquals("flows/index.json", plan.last().outputPath)
        assertTrue(indexJson.contains("\"flowCount\": 0"))
        assertFalse(indexJson.contains("OpsController::rebuildIndex"))
        assertFalse(indexJson.contains("\"controllermethod\": 1"))
    }

    @Test
    fun `does not emit integration event as separate flow when it has upstream causal edge`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("OrderController::submit", "controllermethod"),
                    node("SubmitOrderCmd", "command"),
                    node("Order::submit", "entitymethod"),
                    node("OrderUpdated", "domainevent"),
                    node("MediaProcessedIntegrationEvent", "integrationevent"),
                    node("MediaProcessedIntegrationEventHandler", "integrationeventhandler"),
                    node("MediaProcessedCmd", "command"),
                    node("Media::process", "entitymethod"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                    edge("SubmitOrderCmd", "Order::submit", "CommandToEntityMethod"),
                    edge("Order::submit", "OrderUpdated", "EntityMethodToDomainEvent"),
                    edge("OrderUpdated", "MediaProcessedIntegrationEvent", "DomainEventToIntegrationEvent"),
                    edge("MediaProcessedIntegrationEvent", "MediaProcessedIntegrationEventHandler", "IntegrationEventToHandler"),
                    edge("MediaProcessedIntegrationEventHandler", "MediaProcessedCmd", "IntegrationEventHandlerToCommand"),
                    edge("MediaProcessedCmd", "Media::process", "CommandToEntityMethod"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)

        assertEquals(3, plan.size)
        assertEquals(1, plan.count { it.templateId == "flow/entry.json.peb" })
        assertEquals("flows/OrderController_submit.json", plan.first { it.templateId == "flow/entry.json.peb" }.outputPath)
        assertFalse(plan.any { it.outputPath == "flows/MediaProcessedIntegrationEvent.json" })
        assertTrue((plan.last().context["jsonContent"] as String).contains("\"flowCount\": 1"))
    }

    @Test
    fun `hides an empty domain event handler and stops naturally at the event`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("OrderController::submit", "controllermethod"),
                    node("SubmitOrderCmd", "command"),
                    node("Order::submit", "entitymethod"),
                    node("OrderUpdated", "domainevent"),
                    node("OrderUpdatedHandler", "domaineventhandler"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                    edge("SubmitOrderCmd", "Order::submit", "CommandToEntityMethod"),
                    edge("Order::submit", "OrderUpdated", "EntityMethodToDomainEvent"),
                    edge("OrderUpdated", "OrderUpdatedHandler", "DomainEventToHandler"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonContent = plan[0].context["jsonContent"] as String

        assertEquals(3, plan.size)
        assertTrue(jsonContent.contains("OrderUpdated"))
        assertTrue(jsonContent.contains("\"edgeCount\": 2"))
        assertTrue(jsonContent.contains("\"CommandToDomainEvent\""))
        assertFalse(jsonContent.contains("Order::submit"))
        assertFalse(jsonContent.contains("OrderUpdatedHandler"))
    }

    @Test
    fun `projects inbound integration event through hidden handler to command`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("MediaProcessedIntegrationEvent", "integrationevent"),
                    node("MediaProcessedIntegrationEventHandler", "integrationeventhandler"),
                    node("MediaProcessedCmd", "command"),
                    node("Media::process", "entitymethod"),
                ),
                edges = listOf(
                    edge("MediaProcessedIntegrationEvent", "MediaProcessedIntegrationEventHandler", "IntegrationEventToHandler"),
                    edge("MediaProcessedIntegrationEventHandler", "MediaProcessedCmd", "IntegrationEventHandlerToCommand"),
                    edge("MediaProcessedCmd", "Media::process", "CommandToEntityMethod"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonContent = plan[0].context["jsonContent"] as String
        val indexJson = plan.last().context["jsonContent"] as String

        assertEquals("flows/MediaProcessedIntegrationEvent.json", plan[0].outputPath)
        assertTrue(indexJson.contains("\"integrationevent\": 1"))
        assertTrue(jsonContent.contains("MediaProcessedCmd"))
        assertTrue(jsonContent.contains("\"IntegrationEventToCommand\""))
        assertFalse(jsonContent.contains("MediaProcessedIntegrationEventHandler"))
        assertFalse(jsonContent.contains("Media::process"))
    }

    @Test
    fun `hides both event and command handlers in one causal projection`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("MediaProcessedIntegrationEvent", "integrationevent"),
                    node("MediaProcessedIntegrationEventHandler", "integrationeventhandler"),
                    node("MediaProcessedCmd", "command"),
                    node("MediaProcessedCmdHandler", "commandhandler"),
                    node("Media::process", "entitymethod"),
                    node("MediaProcessed", "domainevent"),
                ),
                edges = listOf(
                    edge("MediaProcessedIntegrationEvent", "MediaProcessedIntegrationEventHandler", "IntegrationEventToHandler"),
                    edge("MediaProcessedIntegrationEventHandler", "MediaProcessedCmd", "IntegrationEventHandlerToCommand"),
                    edge("MediaProcessedCmd", "MediaProcessedCmdHandler", "CommandToCommandHandler"),
                    edge("MediaProcessedCmdHandler", "Media::process", "CommandHandlerToEntityMethod"),
                    edge("Media::process", "MediaProcessed", "EntityMethodToDomainEvent"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonContent = plan[0].context["jsonContent"] as String

        assertEquals("flows/MediaProcessedIntegrationEvent.json", plan[0].outputPath)
        assertTrue(jsonContent.contains("MediaProcessedCmd"))
        assertTrue(jsonContent.contains("MediaProcessed"))
        assertTrue(jsonContent.contains("\"IntegrationEventToCommand\""))
        assertTrue(jsonContent.contains("\"CommandToDomainEvent\""))
        assertFalse(jsonContent.contains("MediaProcessedIntegrationEventHandler"))
        assertFalse(jsonContent.contains("MediaProcessedCmdHandler"))
        assertFalse(jsonContent.contains("Media::process"))
    }

    @Test
    fun `keeps a complete inbound integration event causal chain in one flow`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("MediaProcessingCompletedIntegrationEvent", "integrationevent"),
                    node("MediaProcessingCompletedHandler", "integrationeventhandler"),
                    node("RecordMediaProcessingCmd", "command"),
                    node("RecordMediaProcessingHandler", "commandhandler"),
                    node("MediaProcessing::record", "entitymethod"),
                    node("MediaProcessing::publish", "entitymethod"),
                    node("MediaProcessingRecorded", "domainevent"),
                    node("MediaProcessingRecordedHandler", "domaineventhandler"),
                    node("PublishContentCmd", "command"),
                ),
                edges = listOf(
                    edge(
                        "MediaProcessingCompletedIntegrationEvent",
                        "MediaProcessingCompletedHandler",
                        "IntegrationEventToHandler",
                    ),
                    edge(
                        "MediaProcessingCompletedHandler",
                        "RecordMediaProcessingCmd",
                        "IntegrationEventHandlerToCommand",
                    ),
                    edge("RecordMediaProcessingCmd", "RecordMediaProcessingHandler", "CommandToCommandHandler"),
                    edge(
                        "RecordMediaProcessingHandler",
                        "MediaProcessing::record",
                        "CommandHandlerToEntityMethod",
                    ),
                    edge("MediaProcessing::record", "MediaProcessing::publish", "EntityMethodToEntityMethod"),
                    edge("MediaProcessing::publish", "MediaProcessingRecorded", "EntityMethodToDomainEvent"),
                    edge("MediaProcessingRecorded", "MediaProcessingRecordedHandler", "DomainEventToHandler"),
                    edge(
                        "MediaProcessingRecordedHandler",
                        "PublishContentCmd",
                        "DomainEventHandlerToCommand",
                    ),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val entryPlans = plan.filter { it.templateId == "flow/entry.json.peb" }
        val jsonContent = entryPlans.single().context["jsonContent"] as String
        val mermaidText = plan.single { it.templateId == "flow/entry.mmd.peb" }.context["mermaidText"] as String
        val indexJson = plan.single { it.templateId == "flow/index.json.peb" }.context["jsonContent"] as String

        assertEquals(1, entryPlans.size)
        assertEquals("flows/MediaProcessingCompletedIntegrationEvent.json", entryPlans.single().outputPath)
        assertTrue(jsonContent.contains("\"nodeCount\": 4"))
        assertTrue(jsonContent.contains("\"edgeCount\": 3"))
        assertTrue(jsonContent.contains("MediaProcessingCompletedIntegrationEvent"))
        assertTrue(jsonContent.contains("RecordMediaProcessingCmd"))
        assertTrue(jsonContent.contains("MediaProcessingRecorded"))
        assertTrue(jsonContent.contains("PublishContentCmd"))
        assertTrue(jsonContent.contains("IntegrationEventToCommand"))
        assertTrue(jsonContent.contains("CommandToDomainEvent"))
        assertTrue(jsonContent.contains("DomainEventToCommand"))
        assertFalse(jsonContent.contains("MediaProcessingCompletedHandler"))
        assertFalse(jsonContent.contains("RecordMediaProcessingHandler"))
        assertFalse(jsonContent.contains("MediaProcessing::record"))
        assertFalse(jsonContent.contains("MediaProcessing::publish"))
        assertFalse(jsonContent.contains("MediaProcessingRecordedHandler"))
        assertTrue(mermaidText.contains("MediaProcessingCompletedIntegrationEvent"))
        assertTrue(mermaidText.contains("PublishContentCmd"))
        assertFalse(mermaidText.contains("Handler"))
        assertTrue(indexJson.contains("\"flowCount\": 1"))
    }

    @Test
    fun `keeps two real entries as separate flows when they share a downstream suffix`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("InboundCompleted", "integrationevent"),
                    node("InboundCompletedHandler", "integrationeventhandler"),
                    node("CommandA", "command"),
                    node("CommandAHandler", "commandhandler"),
                    node("Aggregate::fromInbound", "entitymethod"),
                    node("Job::run", "temporaltriggermethod"),
                    node("CommandB", "command"),
                    node("CommandBHandler", "commandhandler"),
                    node("Aggregate::fromJob", "entitymethod"),
                    node("SharedEvent", "domainevent"),
                    node("SharedEventHandler", "domaineventhandler"),
                    node("SharedCommand", "command"),
                ),
                edges = listOf(
                    edge("InboundCompleted", "InboundCompletedHandler", "IntegrationEventToHandler"),
                    edge("InboundCompletedHandler", "CommandA", "IntegrationEventHandlerToCommand"),
                    edge("CommandA", "CommandAHandler", "CommandToCommandHandler"),
                    edge("CommandAHandler", "Aggregate::fromInbound", "CommandHandlerToEntityMethod"),
                    edge("Aggregate::fromInbound", "SharedEvent", "EntityMethodToDomainEvent"),
                    edge("Job::run", "CommandB", "TemporalTriggerMethodToCommand"),
                    edge("CommandB", "CommandBHandler", "CommandToCommandHandler"),
                    edge("CommandBHandler", "Aggregate::fromJob", "CommandHandlerToEntityMethod"),
                    edge("Aggregate::fromJob", "SharedEvent", "EntityMethodToDomainEvent"),
                    edge("SharedEvent", "SharedEventHandler", "DomainEventToHandler"),
                    edge("SharedEventHandler", "SharedCommand", "DomainEventHandlerToCommand"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val entryJsonByPath = plan
            .filter { it.templateId == "flow/entry.json.peb" }
            .associate { it.outputPath to (it.context["jsonContent"] as String) }
        val indexJson = plan.single { it.templateId == "flow/index.json.peb" }.context["jsonContent"] as String

        assertEquals(
            setOf("flows/InboundCompleted.json", "flows/Job_run.json"),
            entryJsonByPath.keys,
        )
        assertTrue(indexJson.contains("\"flowCount\": 2"))
        assertTrue(entryJsonByPath.getValue("flows/InboundCompleted.json").contains("SharedEvent"))
        assertTrue(entryJsonByPath.getValue("flows/InboundCompleted.json").contains("SharedCommand"))
        assertFalse(entryJsonByPath.getValue("flows/InboundCompleted.json").contains("CommandB"))
        assertTrue(entryJsonByPath.getValue("flows/Job_run.json").contains("SharedEvent"))
        assertTrue(entryJsonByPath.getValue("flows/Job_run.json").contains("SharedCommand"))
        assertFalse(entryJsonByPath.getValue("flows/Job_run.json").contains("CommandA"))
    }

    @Test
    fun `does not accept unimplemented rpc adapter evidence by relationship suffix`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("adapter/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("OrderRpcAdapter::submit", "rpcadaptermethod"),
                    node("SubmitOrderCmd", "command"),
                ),
                edges = listOf(
                    edge("OrderRpcAdapter::submit", "SubmitOrderCmd", "RpcAdapterMethodToCommand"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        assertEquals(listOf("flows/index.json"), plan.map { it.outputPath })
        val jsonContent = plan.single().context["jsonContent"] as String
        assertTrue(jsonContent.contains("\"flowCount\": 0"))
    }
    @Test
    fun `contracts arbitrary hidden paths with fan out merge cycle and source evidence`() {
        val nodes = listOf(
            node("Job::run", "temporaltriggermethod"),
            node("StartCmd", "command"),
            node("StartHandler", "commandhandler"),
            node("Aggregate::stepA", "entitymethod"),
            node("Aggregate::stepB", "entitymethod"),
            node("EventA", "domainevent"),
            node("EventB", "domainevent"),
            node("EventAHandler", "domaineventhandler"),
            node("FollowCmd", "command"),
            node("FollowHandler", "commandhandler"),
            node("Aggregate::loop", "entitymethod"),
        )
        val nodesById = nodes.associateBy(AnalysisNodeModel::id)
        val projection = projectCausalGraph(
            nodesById = nodesById,
            edges = listOf(
                edge("Job::run", "StartCmd", "TemporalTriggerMethodToCommand"),
                edge("StartCmd", "StartHandler", "CommandToCommandHandler"),
                edge("StartHandler", "Aggregate::stepA", "CommandHandlerToEntityMethod"),
                edge("Aggregate::stepA", "Aggregate::stepB", "EntityMethodToEntityMethod"),
                edge("Aggregate::stepB", "EventA", "EntityMethodToDomainEvent"),
                edge("Aggregate::stepA", "EventB", "EntityMethodToDomainEvent"),
                edge("EventA", "EventAHandler", "DomainEventToHandler"),
                edge("EventAHandler", "FollowCmd", "DomainEventHandlerToCommand"),
                edge("FollowCmd", "FollowHandler", "CommandToCommandHandler"),
                edge("FollowHandler", "Aggregate::loop", "CommandHandlerToEntityMethod"),
                edge("Aggregate::loop", "Aggregate::loop", "EntityMethodToEntityMethod"),
                edge("Aggregate::loop", "EventA", "EntityMethodToDomainEvent"),
                edge("StartCmd", "StartHandler", "CommandToCommandHandler"),
            ),
        )

        assertEquals(setOf("Job::run"), projection.entryNodeIds)
        assertEquals(
            setOf(
                "Job::run->StartCmd:TemporalTriggerMethodToCommand",
                "StartCmd->EventA:CommandToDomainEvent",
                "StartCmd->EventB:CommandToDomainEvent",
                "EventA->FollowCmd:DomainEventToCommand",
                "FollowCmd->EventA:CommandToDomainEvent",
            ),
            projection.edges.map { "${it.fromId}->${it.toId}:${it.type}" }.toSet(),
        )
        val startToEvent = projection.evidence.single {
            it.projectedEdge.fromId == "StartCmd" && it.projectedEdge.toId == "EventA"
        }
        assertEquals(
            listOf(
                "CommandToCommandHandler",
                "CommandHandlerToEntityMethod",
                "EntityMethodToEntityMethod",
                "EntityMethodToDomainEvent",
            ),
            startToEvent.rawPath.map(AnalysisEdgeModel::type),
        )
    }

    @Test
    fun `does not invent a root for a pure visible cycle`() {
        val planner = FlowArtifactPlanner()
        val model = CanonicalModel(
            analysisGraph = AnalysisGraphModel(
                inputDirs = listOf("app/build/cap4k-code-analysis"),
                nodes = listOf(
                    node("EventA", "domainevent"),
                    node("EventAHandler", "domaineventhandler"),
                    node("FollowCmd", "command"),
                    node("FollowHandler", "commandhandler"),
                    node("Aggregate::loop", "entitymethod"),
                ),
                edges = listOf(
                    edge("EventA", "EventAHandler", "DomainEventToHandler"),
                    edge("EventAHandler", "FollowCmd", "DomainEventHandlerToCommand"),
                    edge("FollowCmd", "FollowHandler", "CommandToCommandHandler"),
                    edge("FollowHandler", "Aggregate::loop", "CommandHandlerToEntityMethod"),
                    edge("Aggregate::loop", "EventA", "EntityMethodToDomainEvent"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        assertEquals(listOf("flow/index.json.peb"), plan.map { it.templateId })
        assertTrue((plan.single().context["jsonContent"] as String).contains("\"flowCount\": 0"))
    }

    @Test
    fun `fails clearly when a causal relationship endpoint is missing`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            projectCausalGraph(
                nodesById = mapOf("OrderController::submit" to node("OrderController::submit", "controllermethod")),
                edges = listOf(edge("OrderController::submit", "MissingCmd", "ControllerMethodToCommand")),
            )
        }

        assertEquals(
            "Flow causal relationship 'ControllerMethodToCommand' references missing toId 'MissingCmd'",
            error.message,
        )
    }
    @Test
    fun `adds digest suffix when slugified entry ids collide`() {
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
                        id = "OrderController submit",
                        name = "OrderController submit",
                        fullName = "OrderController submit",
                        type = "controllermethod",
                    ),
                    node("SubmitOrderCmdA", "command"),
                    node("SubmitOrderCmdB", "command"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmdA", "ControllerMethodToCommand"),
                    edge("OrderController submit", "SubmitOrderCmdB", "ControllerMethodToCommand"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonOutputs = plan
            .filter { it.templateId == "flow/entry.json.peb" }
            .map { it.outputPath }

        assertEquals(2, jsonOutputs.size)
        assertEquals("flows/OrderController_submit.json", jsonOutputs.first())
        assertTrue(Regex("""flows/OrderController_submit_[0-9a-f]{8}\.json""").matches(jsonOutputs.last()))
    }

    @Test
    fun `returns empty plan when analysis graph is absent`() {
        val planner = FlowArtifactPlanner()

        val plan = planner.plan(config(), CanonicalModel())

        assertEquals(emptyList<Any>(), plan)
    }

    @Test
    fun `supports custom flow output root`() {
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
                    node("SubmitOrderCmd", "command"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                ),
            ),
        )

        val plan = planner.plan(config(outputRoot = "build/cap4k/flows"), model)

        assertEquals("build/cap4k/flows/OrderController_submit.json", plan.first().outputPath)
    }

    @Test
    fun `rejects absolute and parent traversing flow output root`() {
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
                ),
                edges = emptyList(),
            ),
        )

        val absolutePath = Path.of("flows").toAbsolutePath().toString()
        val absoluteEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputRoot = absolutePath), model)
        }
        assertEquals(
            "flow outputRoot must be a valid relative filesystem path: $absolutePath",
            absoluteEx.message,
        )

        val traversalEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputRoot = "../flows"), model)
        }
        assertEquals(
            "flow outputRoot must be a valid relative filesystem path: ../flows",
            traversalEx.message,
        )
    }

    @Test
    fun `keeps index json deterministic`() {
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
                ),
                edges = emptyList(),
            ),
        )

        val indexJson = planner.plan(config(), model).last().context["jsonContent"] as String

        assertTrue(!indexJson.contains("generatedAt"))
    }

    @Test
    fun `plans overwrite conflict policy for observation outputs`() {
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
                    node("SubmitOrderCmd", "command"),
                ),
                edges = listOf(
                    edge("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)

        assertTrue(plan.isNotEmpty())
        assertTrue(plan.all { it.conflictPolicy == ConflictPolicy.OVERWRITE })
    }

    @Test
    fun `deduplicates duplicate entry node ids before planning artifacts`() {
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
                        id = "OrderController::submit",
                        name = "OrderController::submit duplicate",
                        fullName = "OrderController::submit duplicate",
                        type = "controllermethod",
                    ),
                    AnalysisNodeModel(
                        id = "SubmitOrderCmd",
                        name = "SubmitOrderCmd",
                        fullName = "SubmitOrderCmd",
                        type = "command",
                    ),
                ),
                edges = listOf(
                    AnalysisEdgeModel("OrderController::submit", "SubmitOrderCmd", "ControllerMethodToCommand"),
                ),
            ),
        )

        val plan = planner.plan(config(), model)
        val jsonEntries = plan.filter { it.templateId == "flow/entry.json.peb" }
        val mermaidEntries = plan.filter { it.templateId == "flow/entry.mmd.peb" }
        val indexJson = plan.last().context["jsonContent"] as String

        assertEquals(1, jsonEntries.size)
        assertEquals(1, mermaidEntries.size)
        assertEquals("flows/OrderController_submit.json", jsonEntries.single().outputPath)
        assertTrue(indexJson.contains("\"flowCount\": 1"))
        assertTrue(indexJson.contains("\"controllermethod\": 1"))
    }

    private fun config(outputRoot: String = "flows"): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = mapOf(
                "flow" to GeneratorConfig(),
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            artifactLayout = ArtifactLayoutConfig(flow = OutputRootLayout(outputRoot)),
        )

    private fun node(id: String, type: String): AnalysisNodeModel =
        AnalysisNodeModel(
            id = id,
            name = id,
            fullName = id,
            type = type,
        )

    private fun edge(fromId: String, toId: String, type: String, label: String? = null): AnalysisEdgeModel =
        AnalysisEdgeModel(
            fromId = fromId,
            toId = toId,
            type = type,
            label = label,
        )
}

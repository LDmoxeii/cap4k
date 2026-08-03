package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.*
import java.util.Locale

class DrawingBoardArtifactPlanner : GeneratorProvider {
    override val id: String = "drawing-board"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Drawing Board Generator",
        kind = PipelineCapabilityKind.GENERATOR,
        module = "cap4k-plugin-pipeline-generator-drawing-board",
        activation = PipelineCapabilityActivation.EXPLICIT_CONFIGURATION,
        tacticalCarriers = listOf("Drawing Board Evidence"),
        executionLanes = listOf(PipelineExecutionLane.ANALYSIS),
        tasks = listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "drawing-board-analysis",
                capabilityIds = listOf("pipeline.source.ir-analysis"),
            ),
        ),
        outputKinds = listOf(ArtifactOutputKind.OUTPUT_ARTIFACT),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_GENERATOR),
            PipelineCapabilityBoundary(PipelineBoundaryKind.ANALYZER, PipelineBoundaryAuthorities.ANALYZER_OBSERVATION),
        ),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        requireDrawingBoardAnalysisMetadata(model)
        val elementsByTag = model.drawingBoard?.elementsByTag ?: return emptyList()

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val outputRoot = artifactLayout.drawingBoardOutputRoot()

        return supportedTags.flatMap { tag ->
            val elements = elementsByTag[tag].orEmpty()
            if (elements.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ArtifactPlanItem(
                        generatorId = id,
                        moduleRole = "project",
                        templateId = "drawing-board/document.json.peb",
                        outputPath = artifactLayout.projectResourcePath(outputRoot, "drawing_board_$tag.json"),
                        context = mapOf(
                            "drawingBoardTag" to tag,
                            "elements" to elements.map(DrawingBoardElementModel::toRenderModel),
                        ),
                        conflictPolicy = ConflictPolicy.OVERWRITE,
                        outputKind = ArtifactOutputKind.OUTPUT_ARTIFACT,
                        resolvedOutputRoot = outputRoot,
                    ),
                )
            }
        }
    }

    private companion object {
        val supportedTags = listOf(
            "command",
            "query",
            "capability",
            "api_payload",
            "domain_event",
            "integration_event",
            "domain_service",
        )
    }
}

internal data class DrawingBoardRenderField(
    val name: String,
    val type: String,
    val defaultValue: String? = null,
)

internal data class DrawingBoardRenderElement(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String>,
    val designJsonArtifacts: List<ArtifactSelectionModel>,
    val includeDesignJsonArtifacts: Boolean,
    val persist: Boolean?,
    val fields: List<DrawingBoardRenderField>,
    val resultFields: List<DrawingBoardRenderField>,
    val eventName: String?,
)

private fun DrawingBoardElementModel.toRenderModel(): DrawingBoardRenderElement =
    DrawingBoardRenderElement(
        tag = tag,
        packageName = packageName,
        name = name,
        description = description,
        aggregates = aggregates,
        designJsonArtifacts = designJsonArtifacts.sortedWith(ArtifactComparator),
        includeDesignJsonArtifacts = includeDesignJsonArtifacts,
        persist = persist,
        fields = request.toSourceFields(),
        resultFields = response?.toSourceFields().orEmpty(),
        eventName = eventName,
    )

private fun SemanticValueDefinition.toSourceFields(): List<DrawingBoardRenderField> {
    val page = envelope as? SemanticValueEnvelope.Page
    if (page != null) {
        return listOf(
            DrawingBoardRenderField(
                name = "page",
                type = "PageData<${page.itemDefinition.identity.simpleName}>",
            ),
        ) + page.itemDefinition.flattenFields("page.list[]")
    }
    return flattenFields(prefix = null)
}

private fun SemanticValueDefinition.flattenFields(prefix: String?): List<DrawingBoardRenderField> {
    val nestedDefinitions = nestedDefinitions.recursively().associateBy { definition -> definition.identity.fqn }
    val nestedIdentities = nestedDefinitions.keys
    return fields.flatMap { field ->
        val path = prefix?.let { "$it.${field.name}" } ?: field.name
        val rendered = DrawingBoardRenderField(
            name = path,
            type = field.type.toSourceExpression(nestedIdentities),
            defaultValue = field.defaultValue?.sourceExpression,
        )
        val nestedReference = field.type.nestedReference(nestedDefinitions) ?: return@flatMap listOf(rendered)
        listOf(rendered) + nestedReference.definition.flattenFields("$path${nestedReference.pathSuffix}")
    }
}

private fun List<SemanticValueDefinition>.recursively(): List<SemanticValueDefinition> = buildList {
    this@recursively.forEach { definition ->
        add(definition)
        addAll(definition.nestedDefinitions.recursively())
    }
}

private fun SemanticTypeRef.nestedReference(
    nestedDefinitions: Map<String, SemanticValueDefinition>,
): NestedReference? = when (this) {
    is SemanticNamedTypeRef -> nestedDefinitions[symbol.fqn]?.let { NestedReference(it, "") }
    is SemanticArrayTypeRef -> (elementType as? SemanticNamedTypeRef)
        ?.let { named -> nestedDefinitions[named.symbol.fqn] }
        ?.let { definition -> NestedReference(definition, "[]") }
    is SemanticListTypeRef -> (elementType as? SemanticNamedTypeRef)
        ?.let { named -> nestedDefinitions[named.symbol.fqn] }
        ?.let { definition -> NestedReference(definition, "[]") }
    else -> null
}

private fun SemanticTypeRef.toSourceExpression(nestedIdentities: Set<String>): String {
    val rendered = when (this) {
        is SemanticBuiltinTypeRef -> kind.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        is SemanticNamedTypeRef -> if (symbol.fqn in nestedIdentities) symbol.simpleName else symbol.fqn
        is SemanticArrayTypeRef -> "Array<${elementType.toSourceExpression(nestedIdentities)}>"
        is SemanticListTypeRef -> "List<${elementType.toSourceExpression(nestedIdentities)}>"
        is SemanticSetTypeRef -> "Set<${elementType.toSourceExpression(nestedIdentities)}>"
        is SemanticMapTypeRef ->
            "Map<${keyType.toSourceExpression(nestedIdentities)}, ${valueType.toSourceExpression(nestedIdentities)}>"
    }
    return if (nullable) "$rendered?" else rendered
}

private data class NestedReference(
    val definition: SemanticValueDefinition,
    val pathSuffix: String,
)

private val ArtifactComparator =
    compareBy<ArtifactSelectionModel> { it.family }
        .thenBy { it.variant }

private fun requireDrawingBoardAnalysisMetadata(model: CanonicalModel) {
    val missing = model.analysisGraph?.nodes.orEmpty()
        .filter { node -> DESIGN_BLOCK_METADATA_FQ in node.missingMetadata }
        .groupBy { node -> node.metadataOwner ?: node.fullName }
    if (missing.isEmpty()) {
        return
    }
    val details = missing.keys.sorted().joinToString(separator = System.lineSeparator()) { symbol ->
        "- symbol: $symbol; missing metadata: $DESIGN_BLOCK_METADATA_FQ; affected capability: Drawing Board"
    }
    throw IllegalArgumentException(
        buildString {
            appendLine("Cap4k analysis metadata contract violation.")
            appendLine(details)
            append(
                "Recovery: restore the default ddd-default generator template for each symbol, or add the listed " +
                    "metadata annotation and keep io.github.ldmoxeii:cap4k-analysis-metadata on the owning business " +
                    "module compileOnly classpath. Custom templates that omit analysis metadata explicitly opt out " +
                    "of Drawing Board; Cap4k will not emit an apparently complete partial result."
            )
        }
    )
}

private const val DESIGN_BLOCK_METADATA_FQ =
    "com.only4.cap4k.analysis.metadata.DesignBlockMetadata"

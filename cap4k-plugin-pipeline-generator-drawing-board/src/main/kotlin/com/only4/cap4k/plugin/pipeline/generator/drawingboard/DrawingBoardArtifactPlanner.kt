package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueEnvelope
import java.util.Locale

class DrawingBoardArtifactPlanner : GeneratorProvider {
    override val id: String = "drawing-board"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
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
        fields = request.toSourceFields().sortedWith(DrawingBoardFieldComparator),
        resultFields = response?.toSourceFields().orEmpty().sortedWith(DrawingBoardFieldComparator),
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
    is SemanticListTypeRef -> (elementType as? SemanticNamedTypeRef)
        ?.let { named -> nestedDefinitions[named.symbol.fqn] }
        ?.let { definition -> NestedReference(definition, "[]") }
    else -> null
}

private fun SemanticTypeRef.toSourceExpression(nestedIdentities: Set<String>): String {
    val rendered = when (this) {
        is SemanticBuiltinTypeRef -> kind.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        is SemanticNamedTypeRef -> if (symbol.fqn in nestedIdentities) symbol.simpleName else symbol.fqn
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

private val DrawingBoardFieldComparator =
    compareBy<DrawingBoardRenderField> { it.name }
        .thenBy { it.type }
        .thenBy { it.defaultValue.orEmpty() }

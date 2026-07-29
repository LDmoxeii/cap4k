package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole

internal class CreationValueArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val nodes = model.aggregateCreationGraphs.flatMap { it.ownedNodes }
        val duplicateIdentity = nodes
            .groupBy { it.value.identity.fqn }
            .entries
            .sortedBy { it.key }
            .firstOrNull { (_, matchingNodes) -> matchingNodes.size > 1 }
        require(duplicateIdentity == null) {
            "duplicate aggregate creation value identity: ${duplicateIdentity?.key}"
        }

        return nodes.sortedBy { it.value.identity.fqn }.map { node ->
            val definition = node.value
            require(definition.role == SemanticValueRole.OWNED_ENTITY_CREATION) {
                "aggregate creation value ${definition.identity.fqn} must use OWNED_ENTITY_CREATION role"
            }
            require(definition.identity.kind == CanonicalTypeKind.CREATION_VALUE) {
                "aggregate creation value ${definition.identity.fqn} must use CREATION_VALUE identity kind"
            }
            require(definition.identity.typePath.size == 1) {
                "aggregate creation value must be top-level: ${definition.identity.fqn}"
            }
            require(definition.identity.packageName == node.entity.packageName) {
                "aggregate creation value ${definition.identity.fqn} must use the aggregate domain package " +
                    node.entity.packageName
            }
            require(definition.identity.simpleName == "${node.entity.simpleName}Creation") {
                "aggregate creation value ${definition.identity.fqn} must be named " +
                    "${node.entity.simpleName}Creation"
            }
            val declarationNameCollisions = definition.fields
                .flatMap { field -> field.type.namedSymbols() }
                .filter { identity ->
                    identity.simpleName == definition.identity.simpleName &&
                        identity != definition.identity
                }
                .map { "${it.fqn} [${it.kind}]" }
                .distinct()
                .sorted()
            require(declarationNameCollisions.isEmpty()) {
                "aggregate creation value ${definition.identity.fqn} has declaration type simple name collision " +
                    "${definition.identity.simpleName}: ${declarationNameCollisions.joinToString(", ")}"
            }

            val renderer = AggregateSemanticTypeRenderer(
                currentPackage = definition.identity.packageName,
                definitions = listOf(definition),
            )
            val fields = definition.fields.map(renderer::render)
            val imports = fields
                .flatMap { field -> (field["typeImports"] as? List<*>)?.filterIsInstance<String>().orEmpty() }
                .distinct()
                .sorted()

            checkedInKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                packageName = definition.identity.packageName,
                typeName = definition.identity.simpleName,
                templateId = "aggregate/creation.kt.peb",
                context = mapOf(
                    "packageName" to definition.identity.packageName,
                    "typeName" to definition.identity.simpleName,
                    "fields" to fields,
                    "empty" to fields.isEmpty(),
                    "imports" to imports,
                ),
                conflictPolicy = ConflictPolicy.SKIP,
            )
        }
    }
}

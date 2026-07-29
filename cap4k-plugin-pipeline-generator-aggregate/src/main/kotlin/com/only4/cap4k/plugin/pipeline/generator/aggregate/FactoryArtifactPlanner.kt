package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationGraphModel
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationNodeModel
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationRelationModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole

internal class FactoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val aggregateRootFqns = model.entities
            .filter { it.aggregateRoot }
            .map { "${it.packageName}.${it.name}" }
            .sorted()
        if (aggregateRootFqns.isEmpty()) {
            return emptyList()
        }
        val graphRootFqns = model.aggregateCreationGraphs.map { it.rootEntity.fqn }.toSet()
        val missingGraphRoots = aggregateRootFqns.filterNot(graphRootFqns::contains)
        require(missingGraphRoots.isEmpty()) {
            "aggregate roots are missing canonical creation graphs: ${missingGraphRoots.joinToString(", ")}"
        }

        return planCreationGraphs(config, model)
    }

    private fun planCreationGraphs(
        config: ProjectConfig,
        model: CanonicalModel,
    ): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val entitiesByFqn = model.entities.associateBy { "${it.packageName}.${it.name}" }
        val duplicateRoot = model.aggregateCreationGraphs
            .groupBy { it.rootEntity.fqn }
            .entries
            .sortedBy { it.key }
            .firstOrNull { (_, graphs) -> graphs.size > 1 }
        require(duplicateRoot == null) {
            "duplicate aggregate creation graph root: ${duplicateRoot?.key}"
        }

        return model.aggregateCreationGraphs
            .sortedBy { it.rootEntity.fqn }
            .map { graph ->
                val rootEntity = requireNotNull(entitiesByFqn[graph.rootEntity.fqn]) {
                    "aggregate creation graph root entity is missing: ${graph.rootEntity.fqn}"
                }
                require(rootEntity.aggregateRoot) {
                    "aggregate creation graph root is not an aggregate root: ${graph.rootEntity.fqn}"
                }
                require(graph.factoryPayload.role == SemanticValueRole.FACTORY_PAYLOAD) {
                    "aggregate creation payload ${graph.factoryPayload.identity.fqn} must use FACTORY_PAYLOAD role"
                }

                val packageName = artifactLayout.aggregateFactoryPackage(rootEntity.packageName)
                val typeName = "${rootEntity.name}Factory"
                validateGeneratedNames(graph, packageName, typeName)
                require(graph.factoryPayload.identity.kind == CanonicalTypeKind.NESTED_VALUE) {
                    "aggregate creation payload ${graph.factoryPayload.identity.fqn} must use NESTED_VALUE identity kind"
                }
                require(
                    graph.factoryPayload.identity.packageName == packageName &&
                        graph.factoryPayload.identity.typePath == listOf(typeName, "Payload")
                ) {
                    "aggregate creation payload must use canonical identity $packageName.$typeName.Payload: " +
                        graph.factoryPayload.identity.fqn
                }
                val definitions = listOf(graph.factoryPayload) + graph.ownedNodes.map { it.value }
                val typeRenderer = AggregateSemanticTypeRenderer(packageName, definitions)
                val payloadFields = graph.factoryPayload.fields.map(typeRenderer::render)
                val rootConstructorFields = constructorFields(
                    definition = graph.factoryPayload,
                    constructorFieldNames = graph.rootConstructorFieldNames,
                    typeRenderer = typeRenderer,
                    entityFqn = graph.rootEntity.fqn,
                )
                val nodesByEntityFqn = graph.ownedNodes.associateBy { it.entity.fqn }
                val rootRelations = relationContexts(
                    relations = graph.relations.filter { it.ownerEntity.fqn == graph.rootEntity.fqn },
                    sourceDefinition = graph.factoryPayload,
                    nodesByEntityFqn = nodesByEntityFqn,
                )
                val helpers = graph.ownedNodes.map { node ->
                    helperContext(node, typeRenderer, nodesByEntityFqn)
                }
                val imports = buildList {
                    add(graph.rootEntity.fqn)
                    addAll(graph.ownedNodes.map { it.entity.fqn })
                    addAll(graph.ownedNodes.map { it.value.identity.fqn })
                    addAll(payloadFields.flatMap(::fieldImports))
                    helpers.forEach { helper ->
                        @Suppress("UNCHECKED_CAST")
                        addAll((helper["constructorFields"] as List<Map<String, Any?>>).flatMap(::fieldImports))
                    }
                }
                    .filterNot { it.substringBeforeLast('.', missingDelimiterValue = "") == packageName }
                    .distinct()
                    .sorted()

                checkedInKotlinArtifact(
                    config = config,
                    artifactLayout = artifactLayout,
                    moduleRole = "domain",
                    templateId = "aggregate/factory.kt.peb",
                    packageName = packageName,
                    typeName = typeName,
                    context = mapOf(
                        "packageName" to packageName,
                        "typeName" to typeName,
                        "aggregateElement" to aggregateElementContext(
                            aggregate = rootEntity.name,
                            name = typeName,
                            packageName = packageName,
                            description = rootEntity.comment,
                            type = "factory",
                        ),
                        "payloadTypeName" to "Payload",
                        "payloadMetadataName" to "${rootEntity.name}Payload",
                        "payloadFields" to payloadFields,
                        "rootConstructorFields" to rootConstructorFields,
                        "rootRelations" to rootRelations,
                        "helpers" to helpers,
                        "entityName" to rootEntity.name,
                        "entityTypeFqn" to graph.rootEntity.fqn,
                        "aggregateName" to rootEntity.name,
                        "comment" to rootEntity.comment,
                        "imports" to imports,
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            }
    }

    private fun validateGeneratedNames(
        graph: AggregateCreationGraphModel,
        factoryPackageName: String,
        factoryTypeName: String,
    ) {
        val duplicateNodeEntity = graph.ownedNodes
            .groupBy { it.entity.fqn }
            .entries
            .sortedBy { it.key }
            .firstOrNull { (_, nodes) -> nodes.size > 1 }
        require(duplicateNodeEntity == null) {
            "aggregate creation graph ${graph.rootEntity.fqn} has duplicate owned creation node entity " +
                duplicateNodeEntity?.key
        }

        requireUniqueName(
            graph = graph,
            category = "owned entity simple name",
            names = graph.ownedNodes.map { node -> node.entity.simpleName to node.entity.fqn },
        )
        requireUniqueName(
            graph = graph,
            category = "creation value simple name",
            names = graph.ownedNodes.map { node -> node.value.identity.simpleName to node.value.identity.fqn },
        )
        requireUniqueName(
            graph = graph,
            category = "factory helper name",
            names = graph.ownedNodes.map { node -> helperName(node.entity.simpleName) to node.entity.fqn },
        )

        val fixedVisibleTypeNames = buildList {
            add(factoryTypeName to "$factoryPackageName.$factoryTypeName [GENERATED_FACTORY]")
            add("Payload" to "$factoryPackageName.$factoryTypeName.Payload [NESTED_VALUE]")
            add(graph.rootEntity.simpleName to canonicalVisibleType(graph.rootEntity))
            addAll(graph.ownedNodes.map { node -> node.entity.simpleName to canonicalVisibleType(node.entity) })
            addAll(
                graph.ownedNodes.map { node ->
                    node.value.identity.simpleName to canonicalVisibleType(node.value.identity)
                }
            )
        }
        val definitions = listOf(graph.factoryPayload) + graph.ownedNodes.map { it.value }
        val fieldTypeNames = buildList {
            definitions.forEach { definition ->
                definition.fields.forEach { field ->
                    addAll(
                        field.type.namedSymbols().map { identity ->
                            identity.simpleName to canonicalVisibleType(identity)
                        }
                    )
                }
            }
        }
        val fixedSimpleNames = fixedVisibleTypeNames.mapTo(linkedSetOf(), Pair<String, String>::first)
        val visibleTypeCollision = (fixedVisibleTypeNames + fieldTypeNames)
            .groupBy(keySelector = Pair<String, String>::first, valueTransform = Pair<String, String>::second)
            .mapValues { (_, fqns) -> fqns.distinct().sorted() }
            .entries
            .sortedBy { it.key }
            .firstOrNull { (simpleName, identities) ->
                simpleName in fixedSimpleNames && identities.size > 1
            }
        require(visibleTypeCollision == null) {
            "aggregate creation graph ${graph.rootEntity.fqn} has factory visible type simple name collision " +
                "${visibleTypeCollision?.key}: ${visibleTypeCollision?.value?.joinToString(", ")}"
        }

        val duplicateTarget = graph.relations
            .groupBy { it.targetEntity.fqn }
            .mapValues { (_, relations) -> relations.map { it.path.joinToString(".") }.sorted() }
            .entries
            .sortedBy { it.key }
            .firstOrNull { (_, paths) -> paths.size > 1 }
        require(duplicateTarget == null) {
            "aggregate creation graph ${graph.rootEntity.fqn} reaches owned entity ${duplicateTarget?.key} " +
                "through multiple relation paths: ${duplicateTarget?.value?.joinToString(", ")}"
        }
    }

    private fun requireUniqueName(
        graph: AggregateCreationGraphModel,
        category: String,
        names: List<Pair<String, String>>,
    ) {
        val duplicate = names
            .groupBy(keySelector = Pair<String, String>::first, valueTransform = Pair<String, String>::second)
            .mapValues { (_, identities) -> identities.distinct().sorted() }
            .entries
            .sortedBy { it.key }
            .firstOrNull { (_, identities) -> identities.size > 1 }
        require(duplicate == null) {
            "aggregate creation graph ${graph.rootEntity.fqn} has duplicate $category ${duplicate?.key}: " +
                duplicate?.value?.joinToString(", ")
        }
    }

    private fun helperContext(
        node: AggregateCreationNodeModel,
        typeRenderer: AggregateSemanticTypeRenderer,
        nodesByEntityFqn: Map<String, AggregateCreationNodeModel>,
    ): Map<String, Any?> {
        require(node.value.role == SemanticValueRole.OWNED_ENTITY_CREATION) {
            "aggregate creation value ${node.value.identity.fqn} must use OWNED_ENTITY_CREATION role"
        }
        val constructorFields = constructorFields(
            definition = node.value,
            constructorFieldNames = node.constructorFieldNames,
            typeRenderer = typeRenderer,
            entityFqn = node.entity.fqn,
        )
        return mapOf(
            "helperName" to helperName(node.entity.simpleName),
            "entityName" to node.entity.simpleName,
            "entityFqn" to node.entity.fqn,
            "valueTypeName" to node.value.identity.simpleName,
            "valueTypeFqn" to node.value.identity.fqn,
            "constructorFields" to constructorFields,
            "relations" to relationContexts(
                relations = node.relations,
                sourceDefinition = node.value,
                nodesByEntityFqn = nodesByEntityFqn,
            ),
        )
    }

    private fun constructorFields(
        definition: SemanticValueDefinition,
        constructorFieldNames: List<String>,
        typeRenderer: AggregateSemanticTypeRenderer,
        entityFqn: String,
    ): List<Map<String, Any?>> {
        val fieldsByName = definition.fields.associateBy { it.name }
        return constructorFieldNames.map { fieldName ->
            val field = requireNotNull(fieldsByName[fieldName]) {
                "aggregate creation value ${definition.identity.fqn} is missing constructor field $fieldName for $entityFqn"
            }
            typeRenderer.render(field)
        }
    }

    private fun relationContexts(
        relations: List<AggregateCreationRelationModel>,
        sourceDefinition: SemanticValueDefinition,
        nodesByEntityFqn: Map<String, AggregateCreationNodeModel>,
    ): List<Map<String, Any?>> {
        val sourceFields = sourceDefinition.fields.associateBy { it.name }
        return relations.map { relation ->
            require(sourceFields.containsKey(relation.fieldName)) {
                "aggregate creation value ${sourceDefinition.identity.fqn} is missing relation field " +
                    "${relation.fieldName} for ${relation.path.joinToString(".")}"
            }
            val targetNode = requireNotNull(nodesByEntityFqn[relation.targetEntity.fqn]) {
                "aggregate creation relation ${relation.path.joinToString(".")} has no target creation node " +
                    relation.targetEntity.fqn
            }
            mapOf(
                "fieldName" to relation.fieldName,
                "attachmentAccessorName" to relation.attachmentAccessorName,
                "cardinality" to relation.cardinality.name,
                "targetHelperName" to helperName(targetNode.entity.simpleName),
                "path" to relation.path.joinToString("."),
            )
        }
    }

    private fun helperName(entitySimpleName: String): String = "create$entitySimpleName"

    private fun canonicalVisibleType(identity: CanonicalTypeIdentity): String =
        "${identity.fqn} [${identity.kind}]"

    private fun fieldImports(field: Map<String, Any?>): List<String> =
        (field["typeImports"] as? List<*>)?.filterIsInstance<String>().orEmpty()
}

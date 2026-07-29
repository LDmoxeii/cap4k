package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateColumnJpaModel
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationGraphModel
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationNodeModel
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateEntityJpaModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticDefaultExpression
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateCreationArtifactPlannerTest {
    @Test
    fun `factory planner requires one canonical creation graph per aggregate root`() {
        val packageName = "com.acme.demo.domain.aggregates.booking_demand"
        val root = entity("BookingDemand", packageName, aggregateRoot = true, "title")

        val failure = assertThrows(IllegalArgumentException::class.java) {
            FactoryArtifactPlanner().plan(config(), CanonicalModel(entities = listOf(root)))
        }

        assertEquals(
            "aggregate roots are missing canonical creation graphs: $packageName.BookingDemand",
            failure.message,
        )
        assertTrue(FactoryArtifactPlanner().plan(config(), CanonicalModel()).isEmpty())
    }

    @Test
    fun `aggregate planner emits reusable checked in child creation values before recursive factory`() {
        val fixture = creationFixture()

        val artifacts = AggregateArtifactPlanner().plan(config(), fixture.model)
        val creations = artifacts.filter { it.templateId == "aggregate/creation.kt.peb" }
        val factory = artifacts.single { it.templateId == "aggregate/factory.kt.peb" }

        assertEquals(
            listOf("DemandCargoLineCreation", "DemandFileCreation", "DemandFileVariantCreation"),
            creations.map { it.context["typeName"] },
        )
        assertFalse(creations.any { it.context["typeName"] == "BookingDemandCreation" })
        creations.forEach { artifact ->
            assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, artifact.outputKind)
            assertEquals(ConflictPolicy.SKIP, artifact.conflictPolicy)
            assertTrue(artifact.outputPath.contains("/domain/aggregates/booking_demand/"))
            assertFalse(artifact.outputPath.contains("/factory/"))
        }
        assertTrue(artifacts.indexOf(creations.first()) < artifacts.indexOf(factory))

        val cargoLineFields = fields(creations.single { it.context["typeName"] == "DemandCargoLineCreation" })
        assertEquals(listOf("cargoName"), cargoLineFields.map { it["name"] })
        assertFalse(cargoLineFields.any { it["name"] == "id" })

        val fileFields = fields(creations.single { it.context["typeName"] == "DemandFileCreation" })
        val variants = fileFields.single { it["name"] == "variants" }
        assertEquals("List<DemandFileVariantCreation>", variants["renderedType"])
        assertEquals("emptyList()", variants["defaultValue"])

        assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, factory.outputKind)
        assertEquals(ConflictPolicy.SKIP, factory.conflictPolicy)
        assertEquals(listOf("title", "cargoLines", "file"), fields(factory).map { it["name"] })
        assertEquals(listOf("title"), contextList(factory.context, "rootConstructorFields").map { it["name"] })

        val rootRelations = contextList(factory.context, "rootRelations")
        assertEquals("MANY", rootRelations.single { it["fieldName"] == "cargoLines" }["cardinality"])
        assertEquals("cargoLines", rootRelations.single { it["fieldName"] == "cargoLines" }["attachmentAccessorName"])
        assertEquals("ONE", rootRelations.single { it["fieldName"] == "file" }["cardinality"])
        assertEquals("file", rootRelations.single { it["fieldName"] == "file" }["attachmentAccessorName"])

        val helpers = contextList(factory.context, "helpers")
        val fileHelper = helpers.single { it["entityName"] == "DemandFile" }
        @Suppress("UNCHECKED_CAST")
        val descendantRelations = fileHelper["relations"] as List<Map<String, Any?>>
        assertEquals("variants", descendantRelations.single()["attachmentAccessorName"])
        assertEquals("createDemandFileVariant", descendantRelations.single()["targetHelperName"])
    }

    @Test
    fun `factory planning names unresolved relation path and target deterministically`() {
        val fixture = creationFixture()
        val graph = fixture.model.aggregateCreationGraphs.single()
        val missingTarget = identity(
            graph.rootEntity.packageName,
            "MissingCargoLine",
            CanonicalTypeKind.ENTITY,
        )
        val brokenRelation = graph.relations
            .single { it.fieldName == "cargoLines" }
            .copy(targetEntity = missingTarget)
        val brokenGraph = graph.copy(
            relations = graph.relations.map { relation ->
                if (relation.fieldName == "cargoLines") brokenRelation else relation
            }
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            FactoryArtifactPlanner().plan(
                config(),
                fixture.model.copy(aggregateCreationGraphs = listOf(brokenGraph)),
            )
        }

        assertTrue(failure.message.orEmpty().contains("BookingDemand.cargoLines"))
        assertTrue(failure.message.orEmpty().contains(missingTarget.fqn))
    }

    @Test
    fun `factory planning rejects duplicate child generated names before rendering`() {
        val fixture = creationFixture()
        val graph = fixture.model.aggregateCreationGraphs.single()
        val existingNode = graph.ownedNodes.single { it.entity.simpleName == "DemandCargoLine" }
        val duplicatePackage = "com.acme.other.domain.aggregates.booking_demand"
        val duplicateNode = existingNode.copy(
            entity = identity(duplicatePackage, "DemandCargoLine", CanonicalTypeKind.ENTITY),
            value = existingNode.value.copy(
                identity = identity(
                    duplicatePackage,
                    "DemandCargoLineCreation",
                    CanonicalTypeKind.CREATION_VALUE,
                )
            ),
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            FactoryArtifactPlanner().plan(
                config(),
                fixture.model.copy(
                    aggregateCreationGraphs = listOf(
                        graph.copy(ownedNodes = graph.ownedNodes + duplicateNode)
                    )
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains(graph.rootEntity.fqn))
        assertTrue(failure.message.orEmpty().contains("duplicate owned entity simple name DemandCargoLine"))
        assertTrue(failure.message.orEmpty().contains(existingNode.entity.fqn))
        assertTrue(failure.message.orEmpty().contains(duplicateNode.entity.fqn))
    }

    @Test
    fun `factory planning rejects visible type names that collide across entity and creation categories`() {
        val fixture = creationFixture()
        val graph = fixture.model.aggregateCreationGraphs.single()
        val packageName = "com.acme.other.domain.aggregates.booking_demand"
        val collidingEntity = identity(
            packageName,
            "DemandCargoLineCreation",
            CanonicalTypeKind.ENTITY,
        )
        val collidingNode = AggregateCreationNodeModel(
            entity = collidingEntity,
            value = creationValue(
                packageName,
                "DemandCargoLineCreationCreation",
                listOf(stringField("label")),
            ),
            constructorFieldNames = listOf("label"),
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            FactoryArtifactPlanner().plan(
                config(),
                fixture.model.copy(
                    aggregateCreationGraphs = listOf(
                        graph.copy(ownedNodes = graph.ownedNodes + collidingNode)
                    )
                ),
            )
        }

        val existingCreation = graph.ownedNodes.single { it.entity.simpleName == "DemandCargoLine" }.value.identity
        assertTrue(failure.message.orEmpty().contains("factory visible type simple name collision DemandCargoLineCreation"))
        assertTrue(failure.message.orEmpty().contains(existingCreation.fqn))
        assertTrue(failure.message.orEmpty().contains(collidingEntity.fqn))
    }

    @Test
    fun `factory planning rejects root entity name that is also a child creation name`() {
        val packageName = "com.acme.demo.domain.aggregates.line_creation"
        val root = identity(packageName, "LineCreation", CanonicalTypeKind.ENTITY)
        val child = identity(packageName, "Line", CanonicalTypeKind.ENTITY)
        val childCreation = creationValue(
            packageName,
            "LineCreation",
            listOf(stringField("label")),
        )
        val childrenRelation = relation(
            root,
            child,
            "lines",
            OwnedRelationCardinality.MANY,
            "lines",
        )
        val graph = AggregateCreationGraphModel(
            rootEntity = root,
            factoryPayload = SemanticValueDefinition(
                identity = CanonicalTypeIdentity(
                    packageName = "$packageName.factory",
                    typePath = listOf("LineCreationFactory", "Payload"),
                    kind = CanonicalTypeKind.NESTED_VALUE,
                ),
                role = SemanticValueRole.FACTORY_PAYLOAD,
                fields = listOf(
                    SemanticValueField(
                        name = "lines",
                        type = SemanticListTypeRef(SemanticNamedTypeRef(childCreation.identity)),
                        defaultValue = default("emptyList()"),
                    )
                ),
            ),
            rootConstructorFieldNames = emptyList(),
            ownedNodes = listOf(
                AggregateCreationNodeModel(
                    entity = child,
                    value = childCreation,
                    constructorFieldNames = listOf("label"),
                )
            ),
            relations = listOf(childrenRelation),
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            FactoryArtifactPlanner().plan(
                config(),
                CanonicalModel(
                    entities = listOf(
                        entity("LineCreation", packageName, aggregateRoot = true, "title"),
                        entity("Line", packageName, aggregateRoot = false, "label"),
                    ),
                    aggregateCreationGraphs = listOf(graph),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("factory visible type simple name collision LineCreation"))
        assertTrue(failure.message.orEmpty().contains("${root.fqn} [ENTITY]"))
        assertTrue(failure.message.orEmpty().contains("${childCreation.identity.fqn} [CREATION_VALUE]"))
    }

    @Test
    fun `factory planning rejects one owned target reached through multiple relation paths`() {
        val fixture = creationFixture()
        val graph = fixture.model.aggregateCreationGraphs.single()
        val cargoLines = graph.relations.single { it.fieldName == "cargoLines" }
        val duplicatePath = cargoLines.copy(path = listOf("BookingDemand", "archivedCargoLines"))

        val failure = assertThrows(IllegalArgumentException::class.java) {
            FactoryArtifactPlanner().plan(
                config(),
                fixture.model.copy(
                    aggregateCreationGraphs = listOf(
                        graph.copy(relations = graph.relations + duplicatePath)
                    )
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains(cargoLines.targetEntity.fqn))
        assertTrue(failure.message.orEmpty().contains("BookingDemand.cargoLines"))
        assertTrue(failure.message.orEmpty().contains("BookingDemand.archivedCargoLines"))
    }

    @Test
    fun `creation planning rejects field type that collides with its declaration name`() {
        val packageName = "com.acme.demo.domain.aggregates.booking_demand"
        val externalCreation = identity(
            packageName,
            "PricingCreation",
            CanonicalTypeKind.ENTITY,
        )
        val definition = creationValue(
            packageName,
            "PricingCreation",
            listOf(
                SemanticValueField(
                    name = "parent",
                    type = SemanticNamedTypeRef(externalCreation, nullable = true),
                    defaultValue = default("null"),
                )
            ),
        )
        val graph = AggregateCreationGraphModel(
            rootEntity = identity(packageName, "BookingDemand", CanonicalTypeKind.ENTITY),
            factoryPayload = SemanticValueDefinition(
                identity = CanonicalTypeIdentity(
                    packageName = "$packageName.factory",
                    typePath = listOf("BookingDemandFactory", "Payload"),
                    kind = CanonicalTypeKind.NESTED_VALUE,
                ),
                role = SemanticValueRole.FACTORY_PAYLOAD,
            ),
            rootConstructorFieldNames = emptyList(),
            ownedNodes = listOf(
                AggregateCreationNodeModel(
                    entity = identity(packageName, "Pricing", CanonicalTypeKind.ENTITY),
                    value = definition,
                    constructorFieldNames = listOf("parent"),
                )
            ),
            relations = emptyList(),
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            CreationValueArtifactPlanner().plan(
                config(),
                CanonicalModel(aggregateCreationGraphs = listOf(graph)),
            )
        }

        assertTrue(failure.message.orEmpty().contains(definition.identity.fqn))
        assertTrue(failure.message.orEmpty().contains("declaration type simple name collision PricingCreation"))
        assertTrue(failure.message.orEmpty().contains("${externalCreation.fqn} [ENTITY]"))
    }

    @Test
    fun `semantic type rendering follows resolved nested type tree and preserves node nullability`() {
        val packageName = "com.acme.demo.domain.aggregates.booking_demand"
        val money = identity(packageName, "Money", CanonicalTypeKind.VALUE_OBJECT)
        val definition = SemanticValueDefinition(
            identity = identity(packageName, "PricingCreation", CanonicalTypeKind.CREATION_VALUE),
            role = SemanticValueRole.OWNED_ENTITY_CREATION,
            fields = listOf(
                SemanticValueField(
                    name = "prices",
                    type = SemanticListTypeRef(
                        elementType = SemanticNamedTypeRef(money, nullable = true),
                        nullable = true,
                    ),
                )
            ),
        )

        val artifact = CreationValueArtifactPlanner().plan(
            config(),
            CanonicalModel(
                aggregateCreationGraphs = listOf(
                    AggregateCreationGraphModel(
                        rootEntity = identity(packageName, "BookingDemand", CanonicalTypeKind.ENTITY),
                        factoryPayload = SemanticValueDefinition(
                            identity = CanonicalTypeIdentity(
                                packageName = "$packageName.factory",
                                typePath = listOf("BookingDemandFactory", "Payload"),
                                kind = CanonicalTypeKind.NESTED_VALUE,
                            ),
                            role = SemanticValueRole.FACTORY_PAYLOAD,
                        ),
                        rootConstructorFieldNames = emptyList(),
                        ownedNodes = listOf(
                            AggregateCreationNodeModel(
                                entity = identity(packageName, "Pricing", CanonicalTypeKind.ENTITY),
                                value = definition,
                                constructorFieldNames = listOf("prices"),
                            )
                        ),
                        relations = emptyList(),
                    )
                )
            ),
        ).single()

        assertEquals("List<Money?>?", fields(artifact).single()["renderedType"])
    }

    private fun creationFixture(): CreationFixture {
        val packageName = "com.acme.demo.domain.aggregates.booking_demand"
        val root = identity(packageName, "BookingDemand", CanonicalTypeKind.ENTITY)
        val cargoLine = identity(packageName, "DemandCargoLine", CanonicalTypeKind.ENTITY)
        val file = identity(packageName, "DemandFile", CanonicalTypeKind.ENTITY)
        val variant = identity(packageName, "DemandFileVariant", CanonicalTypeKind.ENTITY)
        val cargoLineCreation = creationValue(
            packageName,
            "DemandCargoLineCreation",
            listOf(stringField("cargoName")),
        )
        val variantCreation = creationValue(
            packageName,
            "DemandFileVariantCreation",
            listOf(stringField("variantKey")),
        )
        val fileCreation = creationValue(
            packageName,
            "DemandFileCreation",
            listOf(
                stringField("storageKey"),
                SemanticValueField(
                    name = "variants",
                    type = SemanticListTypeRef(SemanticNamedTypeRef(variantCreation.identity)),
                    defaultValue = default("emptyList()"),
                ),
            ),
        )
        val cargoLinesRelation = relation(
            root,
            cargoLine,
            "cargoLines",
            OwnedRelationCardinality.MANY,
            "cargoLines",
        )
        val fileRelation = relation(
            root,
            file,
            "file",
            OwnedRelationCardinality.ONE,
            "file",
        )
        val variantsRelation = relation(
            file,
            variant,
            "variants",
            OwnedRelationCardinality.MANY,
            "variants",
        )
        val payload = SemanticValueDefinition(
            identity = CanonicalTypeIdentity(
                packageName = "$packageName.factory",
                typePath = listOf("BookingDemandFactory", "Payload"),
                kind = CanonicalTypeKind.NESTED_VALUE,
            ),
            role = SemanticValueRole.FACTORY_PAYLOAD,
            fields = listOf(
                stringField("title"),
                SemanticValueField(
                    name = "cargoLines",
                    type = SemanticListTypeRef(SemanticNamedTypeRef(cargoLineCreation.identity)),
                    defaultValue = default("emptyList()"),
                ),
                SemanticValueField(
                    name = "file",
                    type = SemanticNamedTypeRef(fileCreation.identity, nullable = true),
                    defaultValue = default("null"),
                ),
            ),
        )
        val graph = AggregateCreationGraphModel(
            rootEntity = root,
            factoryPayload = payload,
            rootConstructorFieldNames = listOf("title"),
            ownedNodes = listOf(
                AggregateCreationNodeModel(
                    entity = cargoLine,
                    value = cargoLineCreation,
                    constructorFieldNames = listOf("cargoName"),
                ),
                AggregateCreationNodeModel(
                    entity = file,
                    value = fileCreation,
                    constructorFieldNames = listOf("storageKey"),
                    relations = listOf(variantsRelation),
                ),
                AggregateCreationNodeModel(
                    entity = variant,
                    value = variantCreation,
                    constructorFieldNames = listOf("variantKey"),
                ),
            ),
            relations = listOf(cargoLinesRelation, fileRelation, variantsRelation),
        )
        val entities = listOf(
            entity("BookingDemand", packageName, aggregateRoot = true, "title"),
            entity("DemandCargoLine", packageName, aggregateRoot = false, "cargoName"),
            entity("DemandFile", packageName, aggregateRoot = false, "storageKey"),
            entity("DemandFileVariant", packageName, aggregateRoot = false, "variantKey"),
        )
        return CreationFixture(
            CanonicalModel(
                entities = entities,
                aggregateEntityJpa = entities.map(::jpa),
                aggregateCreationGraphs = listOf(graph),
            )
        )
    }

    private fun entity(
        name: String,
        packageName: String,
        aggregateRoot: Boolean,
        businessField: String,
    ): EntityModel {
        val id = FieldModel("id", "Long", columnName = "id")
        return EntityModel(
            name = name,
            packageName = packageName,
            tableName = name.lowercase(),
            comment = name,
            fields = listOf(id, FieldModel(businessField, "String", columnName = businessField)),
            idField = id,
            aggregateRoot = aggregateRoot,
            parentEntityName = if (aggregateRoot) null else "BookingDemand",
        )
    }

    private fun jpa(entity: EntityModel): AggregateEntityJpaModel = AggregateEntityJpaModel(
        entityName = entity.name,
        entityPackageName = entity.packageName,
        entityEnabled = true,
        tableName = entity.tableName,
        columns = entity.fields.map { field ->
            AggregateColumnJpaModel(
                fieldName = field.name,
                columnName = field.columnName ?: field.name,
                isId = field.name == entity.idField.name,
            )
        },
    )

    private fun relation(
        owner: CanonicalTypeIdentity,
        target: CanonicalTypeIdentity,
        fieldName: String,
        cardinality: OwnedRelationCardinality,
        attachmentAccessorName: String,
    ): AggregateCreationRelationModel = AggregateCreationRelationModel(
        path = listOf(owner.simpleName, fieldName),
        ownerEntity = owner,
        targetEntity = target,
        fieldName = fieldName,
        cardinality = cardinality,
        attachmentAccessorName = attachmentAccessorName,
    )

    private fun creationValue(
        packageName: String,
        name: String,
        fields: List<SemanticValueField>,
    ): SemanticValueDefinition = SemanticValueDefinition(
        identity = identity(packageName, name, CanonicalTypeKind.CREATION_VALUE),
        role = SemanticValueRole.OWNED_ENTITY_CREATION,
        fields = fields,
    )

    private fun stringField(name: String): SemanticValueField = SemanticValueField(
        name = name,
        type = SemanticBuiltinTypeRef(SemanticBuiltinType.STRING),
    )

    private fun default(expression: String): SemanticDefaultExpression =
        SemanticDefaultExpression(kotlinExpression = expression, sourceExpression = expression)

    private fun identity(
        packageName: String,
        name: String,
        kind: CanonicalTypeKind,
    ): CanonicalTypeIdentity = CanonicalTypeIdentity(packageName, listOf(name), kind)

    private fun config(): ProjectConfig = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = mapOf("domain" to "demo-domain"),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.FAIL),
    )

    @Suppress("UNCHECKED_CAST")
    private fun fields(artifact: com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem): List<Map<String, Any?>> =
        artifact.context["fields"] as? List<Map<String, Any?>>
            ?: artifact.context["payloadFields"] as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun contextList(
        context: Map<String, Any?>,
        name: String,
    ): List<Map<String, Any?>> = context[name] as List<Map<String, Any?>>

    private data class CreationFixture(val model: CanonicalModel)
}

package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import com.only4.cap4k.plugin.pipeline.api.ValueObjectStorage
import com.only4.cap4k.plugin.pipeline.generator.common.types.MANIFEST_VALUE_OBJECT_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolIdentity
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ValueObjectArtifactPlannerTest {

    @Test
    fun `empty model returns empty without requiring domain module`() {
        val items = ValueObjectArtifactPlanner().plan(config(modules = emptyMap()), CanonicalModel())

        assertEquals(emptyList<Any>(), items)
    }

    @Test
    fun `plans checked in json value object under domain module using declared package`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Money",
                        packageName = "com.acme.demo.domain.shared.values",
                        storage = ValueObjectStorage.JSON,
                        fields = listOf(
                            FieldModel("amount", "java.math.BigDecimal"),
                            FieldModel("currency", "CurrencyCode", nullable = true),
                        ),
                    )
                )
            ),
        ).single()

        assertEquals("types-value-object", item.generatorId)
        assertEquals("domain", item.moduleRole)
        assertEquals("types/value-object", item.templateId)
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/values/Money.kt",
            item.outputPath,
        )
        assertEquals(ArtifactOutputKind.CHECKED_IN_SOURCE, item.outputKind)
        assertEquals(ConflictPolicy.SKIP, item.conflictPolicy)
        assertEquals("demo-domain/src/main/kotlin", item.resolvedOutputRoot)

        assertEquals("com.acme.demo.domain.shared.values", item.context["packageName"])
        assertEquals("Money", item.context["typeName"])
        assertEquals("Money", item.context["name"])
        assertEquals(null, item.context["description"])
        assertEquals("ValueObjectArtifactPlanner", item.context["planner"])
        assertEquals(emptyList<String>(), item.context["aggregates"])
        assertEquals(ValueObjectStorage.JSON.name, item.context["storage"])
        assertEquals(
            listOf("com.acme.demo.domain.shared.types.CurrencyCode", "java.math.BigDecimal"),
            item.context["imports"],
        )

        val buildingBlock = item.context["buildingBlock"] as Map<*, *>
        assertEquals("value_object", buildingBlock["tag"])
        assertEquals("Money", buildingBlock["name"])
        assertEquals("com.acme.demo.domain.shared.values", buildingBlock["packageName"])
        assertEquals(null, buildingBlock["description"])
        assertEquals("\"\"", buildingBlock["descriptionKotlinStringLiteral"])
        assertEquals(emptyList<String>(), buildingBlock["aggregates"])
        assertEquals("", buildingBlock["eventName"])
        assertEquals("value-object", buildingBlock["family"])
        assertEquals("", buildingBlock["variant"])

        val fields = item.context["fields"] as List<*>
        assertEquals(
            mapOf("name" to "amount", "type" to "BigDecimal", "renderedType" to "BigDecimal", "nullable" to false),
            fields[0],
        )
        assertEquals(
            mapOf("name" to "currency", "type" to "CurrencyCode?", "renderedType" to "CurrencyCode?", "nullable" to true),
            fields[1],
        )
    }

    @Test
    fun `shared and aggregate local value objects both use declared package`() {
        val items = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Money",
                        packageName = "com.acme.demo.domain.shared.values",
                        fields = listOf(FieldModel("amount", "String")),
                    ),
                    ValueObjectModel(
                        name = "OrderSnapshot",
                        packageName = "com.acme.demo.domain.aggregates.order.values",
                        description = "Captured order state",
                        aggregates = listOf("Order"),
                        fields = listOf(FieldModel("content", "String")),
                    ),
                )
            ),
        )

        assertEquals(
            listOf(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/values/Money.kt",
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/values/OrderSnapshot.kt",
            ),
            items.map { it.outputPath },
        )

        val aggregateLocal = items.single { it.context["typeName"] == "OrderSnapshot" }
        assertEquals(listOf("Order"), aggregateLocal.context["aggregates"])
        assertEquals("Captured order state", aggregateLocal.context["description"])

        val buildingBlock = aggregateLocal.context["buildingBlock"] as Map<*, *>
        assertEquals("value_object", buildingBlock["tag"])
        assertEquals("OrderSnapshot", buildingBlock["name"])
        assertEquals("com.acme.demo.domain.aggregates.order.values", buildingBlock["packageName"])
        assertEquals("Captured order state", buildingBlock["description"])
        assertEquals("\"Captured order state\"", buildingBlock["descriptionKotlinStringLiteral"])
        assertEquals(listOf("Order"), buildingBlock["aggregates"])
        assertEquals("", buildingBlock["eventName"])
        assertEquals("value-object", buildingBlock["family"])
        assertEquals("", buildingBlock["variant"])
    }

    @Test
    fun `manifest managed value object fields resolve imports from canonical value objects`() {
        val money = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Currency",
                        packageName = "com.acme.demo.domain.shared.types",
                        fields = listOf(FieldModel("code", "String")),
                    ),
                    ValueObjectModel(
                        name = "Money",
                        packageName = "com.acme.demo.domain.shared.values",
                        fields = listOf(FieldModel("currency", "Currency")),
                    ),
                )
            ),
        ).single { it.context["typeName"] == "Money" }

        assertEquals(listOf("com.acme.demo.domain.shared.types.Currency"), money.context["imports"])

        val fields = money.context["fields"] as List<*>
        assertEquals(
            mapOf("name" to "currency", "type" to "Currency", "renderedType" to "Currency", "nullable" to false),
            fields.single(),
        )
    }

    @Test
    fun `manifest managed value object fields resolve aggregate root strong ids`() {
        val snapshot = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "ContentId",
                        packageName = "com.acme.demo.domain.aggregates.content",
                        kind = StrongIdKind.OWN_ID,
                        ownerAggregateName = "Content",
                        ownerAggregatePackageName = "com.acme.demo.domain.aggregates.content",
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "ContentSnapshot",
                        packageName = "com.acme.demo.domain.aggregates.audit.values",
                        aggregates = listOf("Audit"),
                        fields = listOf(FieldModel("contentId", "ContentId")),
                    ),
                ),
            ),
        ).single()

        assertEquals(listOf("com.acme.demo.domain.aggregates.content.ContentId"), snapshot.context["imports"])

        val fields = snapshot.context["fields"] as List<*>
        assertEquals(
            mapOf("name" to "contentId", "type" to "ContentId", "renderedType" to "ContentId", "nullable" to false),
            fields.single(),
        )
    }

    @Test
    fun `manifest managed value object fields resolve reference strong ids`() {
        val snapshot = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "ReviewerId",
                        packageName = "com.acme.demo.domain.shared.ids",
                        kind = StrongIdKind.REFERENCE,
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "ReviewSnapshot",
                        packageName = "com.acme.demo.domain.aggregates.review.values",
                        aggregates = listOf("Review"),
                        fields = listOf(FieldModel("reviewerId", "ReviewerId")),
                    ),
                ),
            ),
        ).single()

        assertEquals(listOf("com.acme.demo.domain.shared.ids.ReviewerId"), snapshot.context["imports"])

        val fields = snapshot.context["fields"] as List<*>
        assertEquals(
            mapOf("name" to "reviewerId", "type" to "ReviewerId", "renderedType" to "ReviewerId", "nullable" to false),
            fields.single(),
        )
    }

    @Test
    fun `Strong ID collision with explicit registry fails without matching owner context`() {
        val error = assertThrows<AmbiguousValueObjectFieldTypeFailure> {
            ValueObjectArtifactPlanner().plan(
                config(
                    typeRegistry = TypeRegistryConfig(
                        entries = mapOf(
                            "ContentId" to TypeRegistryEntry("com.acme.external.ContentId"),
                        ),
                    ),
                ),
                CanonicalModel(
                    strongIds = listOf(
                        StrongIdModel(
                            typeName = "ContentId",
                            packageName = "com.acme.demo.domain.aggregates.content",
                            kind = StrongIdKind.OWN_ID,
                            ownerAggregateName = "Content",
                            ownerAggregatePackageName = "com.acme.demo.domain.aggregates.content",
                        ),
                    ),
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "ContentSnapshot",
                            packageName = "com.acme.demo.domain.aggregates.audit.values",
                            aggregates = listOf("Audit"),
                            fields = listOf(FieldModel("contentId", "ContentId")),
                        ),
                    ),
                ),
            )
        }

        assertEquals(true, error.message?.contains("ambiguous value object field type: ContentId"))
    }

    @Test
    fun `manifest managed value object fields resolve shared enum imports`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            model = CanonicalModel(
                sharedEnums = listOf(sharedEnum("TransportType", packageName = "shared")),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "DemandSnapshot",
                        packageName = "booking",
                        fields = listOf(FieldModel("transportType", "TransportType")),
                    ),
                ),
            ),
        ).single()

        val fields = item.context["fields"] as List<Map<*, *>>

        assertEquals(listOf("com.acme.demo.domain.shared.enums.TransportType"), item.context["imports"])
        assertEquals("TransportType", fields.single()["type"])
    }

    @Test
    fun `manifest managed value object fields resolve shared enum imports inside generics`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            model = CanonicalModel(
                sharedEnums = listOf(sharedEnum("DocumentType", packageName = "shared")),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "DemandDocumentRequirement",
                        packageName = "booking",
                        fields = listOf(FieldModel("requiredDocumentTypes", "List<DocumentType>")),
                    ),
                ),
            ),
        ).single()

        val fields = item.context["fields"] as List<Map<*, *>>

        assertEquals(listOf("com.acme.demo.domain.shared.enums.DocumentType"), item.context["imports"])
        assertEquals("List<DocumentType>", fields.single()["type"])
    }

    @Test
    fun `manifest managed value object fields resolve aggregate owned enum imports`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            model = CanonicalModel(
                entities = listOf(
                    aggregateRootEntity(
                        name = "CarrierResourceConfirmation",
                        packageName = "com.acme.demo.domain.aggregates.carrier_resource_confirmation",
                    ),
                ),
                sharedEnums = listOf(
                    sharedEnum(
                        typeName = "CarrierResourceType",
                        packageName = "carrier_resource_confirmation",
                        aggregates = listOf("CarrierResourceConfirmation"),
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "CarrierResourceIdentity",
                        packageName = "booking",
                        aggregates = listOf("CarrierResourceConfirmation"),
                        fields = listOf(FieldModel("resourceType", "CarrierResourceType")),
                    ),
                ),
            ),
        ).single()

        val fields = item.context["fields"] as List<Map<*, *>>

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.carrier_resource_confirmation.enums.CarrierResourceType"),
            item.context["imports"],
        )
        assertEquals("CarrierResourceType", fields.single()["type"])
    }

    @Test
    fun `globally unique aggregate owned enum imports without owner context`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            model = CanonicalModel(
                entities = listOf(
                    aggregateRootEntity(
                        name = "CarrierResourceConfirmation",
                        packageName = "com.acme.demo.domain.aggregates.carrier_resource_confirmation",
                    ),
                ),
                sharedEnums = listOf(
                    sharedEnum(
                        typeName = "CarrierResourceType",
                        packageName = "carrier_resource_confirmation",
                        aggregates = listOf("CarrierResourceConfirmation"),
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "CarrierResourceIdentity",
                        packageName = "booking",
                        fields = listOf(FieldModel("resourceType", "CarrierResourceType")),
                    ),
                ),
            ),
        ).single()

        val fields = item.context["fields"] as List<Map<*, *>>

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.carrier_resource_confirmation.enums.CarrierResourceType"),
            item.context["imports"],
        )
        assertEquals("CarrierResourceType", fields.single()["type"])
    }

    @Test
    fun `owner local enum supersedes shared value object fallback`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                entities = listOf(
                    aggregateRootEntity(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                ),
                sharedEnums = listOf(
                    sharedEnum(
                        typeName = "Status",
                        packageName = "order",
                        aggregates = listOf("Order"),
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Status",
                        packageName = "com.acme.demo.domain.shared.values",
                        fields = listOf(FieldModel("code", "String")),
                    ),
                    ValueObjectModel(
                        name = "OrderSnapshot",
                        packageName = "booking",
                        aggregates = listOf("Order"),
                        fields = listOf(FieldModel("status", "Status")),
                    ),
                ),
            ),
        ).single { it.context["typeName"] == "OrderSnapshot" }

        val fields = item.context["fields"] as List<Map<*, *>>

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.order.enums.Status"),
            item.context["imports"],
        )
        assertEquals("Status", fields.single()["type"])
    }

    @Test
    fun `owner local enum wins over explicit registry in matching owner context`() {
        val item = ValueObjectArtifactPlanner().plan(
                config(
                    typeRegistry = TypeRegistryConfig(
                        entries = mapOf("Status" to TypeRegistryEntry("com.acme.external.Status")),
                    ),
                ),
                CanonicalModel(
                    entities = listOf(
                        aggregateRootEntity(
                            name = "Order",
                            packageName = "com.acme.demo.domain.aggregates.order",
                        ),
                    ),
                    sharedEnums = listOf(
                        sharedEnum(
                            typeName = "Status",
                            packageName = "order",
                            aggregates = listOf("Order"),
                        ),
                    ),
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "OrderSnapshot",
                            packageName = "booking",
                            aggregates = listOf("Order"),
                            fields = listOf(FieldModel("status", "Status")),
                        ),
                    ),
                ),
            ).single()

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.order.enums.Status"),
            item.context["imports"],
        )
    }

    @Test
    fun `owner local enum wins over Strong ID in matching owner context`() {
        val item = ValueObjectArtifactPlanner().plan(
                config(),
                CanonicalModel(
                    entities = listOf(
                        aggregateRootEntity(
                            name = "Order",
                            packageName = "com.acme.demo.domain.aggregates.order",
                        ),
                    ),
                    sharedEnums = listOf(
                        sharedEnum(
                            typeName = "Status",
                            packageName = "order",
                            aggregates = listOf("Order"),
                        ),
                    ),
                    strongIds = listOf(
                        StrongIdModel(
                            typeName = "Status",
                            packageName = "com.acme.demo.domain.shared.ids",
                            kind = StrongIdKind.REFERENCE,
                        ),
                    ),
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "OrderSnapshot",
                            packageName = "booking",
                            aggregates = listOf("Order"),
                            fields = listOf(FieldModel("status", "Status")),
                        ),
                    ),
                ),
            ).single()

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.order.enums.Status"),
            item.context["imports"],
        )
    }

    @Test
    fun `unknown short field type fails planning`() {
        val error = assertThrows<UnknownValueObjectFieldTypeFailure> {
            ValueObjectArtifactPlanner().plan(
                config(typeRegistry = TypeRegistryConfig()),
                CanonicalModel(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "Money",
                            packageName = "com.acme.demo.domain.shared.values",
                            fields = listOf(FieldModel("currency", "CurrencyCode")),
                        ),
                    ),
                ),
            )
        }

        assertEquals(true, error.message?.contains("unknown value object field type: CurrencyCode"))
    }

    @Test
    fun `owner local enum ignores unrelated aggregate local value object`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                entities = listOf(
                    aggregateRootEntity(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                    aggregateRootEntity(
                        name = "Customer",
                        packageName = "com.acme.demo.domain.aggregates.customer",
                    ),
                ),
                sharedEnums = listOf(
                    sharedEnum(
                        typeName = "Status",
                        packageName = "order",
                        aggregates = listOf("Order"),
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Status",
                        packageName = "com.acme.demo.domain.aggregates.customer.values",
                        aggregates = listOf("Customer"),
                        fields = listOf(FieldModel("code", "String")),
                    ),
                    ValueObjectModel(
                        name = "OrderSnapshot",
                        packageName = "booking",
                        aggregates = listOf("Order"),
                        fields = listOf(FieldModel("status", "Status")),
                    ),
                ),
            ),
        ).single { it.context["typeName"] == "OrderSnapshot" }

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.order.enums.Status"),
            item.context["imports"],
        )
    }

    @Test
    fun `no context duplicate local short type fails ambiguous`() {
        val error = assertThrows<AmbiguousValueObjectFieldTypeFailure> {
            ValueObjectArtifactPlanner().plan(
                config(),
                CanonicalModel(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "Snapshot",
                            packageName = "com.acme.demo.domain.aggregates.order.values",
                            aggregates = listOf("Order"),
                            fields = listOf(FieldModel("code", "String")),
                        ),
                        ValueObjectModel(
                            name = "Snapshot",
                            packageName = "com.acme.demo.domain.aggregates.customer.values",
                            aggregates = listOf("Customer"),
                            fields = listOf(FieldModel("code", "String")),
                        ),
                        ValueObjectModel(
                            name = "AuditEntry",
                            packageName = "com.acme.demo.domain.shared.values",
                            fields = listOf(FieldModel("snapshot", "Snapshot")),
                        ),
                    ),
                ),
            )
        }

        assertEquals(true, error.message?.contains("ambiguous value object field type: Snapshot"))
    }

    @Test
    fun `multiple owner contexts do not select a local short type`() {
        val error = assertThrows<AmbiguousValueObjectFieldTypeFailure> {
            ValueObjectTypeResolver.resolve(
                type = ValueObjectTypeParser.parse("Snapshot"),
                symbolRegistry = TypeSymbolRegistry(
                    listOf(
                        TypeSymbolIdentity(
                            packageName = "com.acme.demo.domain.aggregates.order.values",
                            typeName = "Snapshot",
                            source = MANIFEST_VALUE_OBJECT_SOURCE,
                            ownerAggregateName = "Order",
                            manifestOwned = true,
                        ),
                        TypeSymbolIdentity(
                            packageName = "com.acme.demo.domain.aggregates.customer.values",
                            typeName = "Snapshot",
                            source = MANIFEST_VALUE_OBJECT_SOURCE,
                            ownerAggregateName = "Customer",
                            manifestOwned = true,
                        ),
                    ),
                ),
                aggregateContext = listOf("Order", "Customer"),
            )
        }

        assertEquals("Snapshot", error.shortType)
        assertEquals(
            listOf(
                "com.acme.demo.domain.aggregates.order.values.Snapshot",
                "com.acme.demo.domain.aggregates.customer.values.Snapshot",
            ),
            error.candidates,
        )
        assertEquals(
            "ambiguous value object field type: Snapshot -> " +
                "com.acme.demo.domain.aggregates.order.values.Snapshot, " +
                "com.acme.demo.domain.aggregates.customer.values.Snapshot",
            error.message,
        )
    }

    @Test
    fun `explicit FQCN sibling fields with the same name render fully qualified`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "StatusPair",
                        packageName = "com.acme.demo.domain.shared.values",
                        fields = listOf(
                            FieldModel("fooStatus", "com.foo.Status"),
                            FieldModel("barStatus", "com.bar.Status"),
                        ),
                    ),
                ),
            ),
        ).single()

        val fields = item.context["fields"] as List<Map<*, *>>

        assertEquals(emptyList<String>(), item.context["imports"])
        assertEquals("com.foo.Status", fields[0]["type"])
        assertEquals("com.bar.Status", fields[1]["type"])
    }

    @Test
    fun `short sibling resolves from value object explicit FQCN registry`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "StatusEnvelope",
                        packageName = "com.acme.demo.domain.shared.values",
                        fields = listOf(
                            FieldModel("explicitStatus", "com.foo.Status"),
                            FieldModel("shortStatus", "Status"),
                        ),
                    ),
                ),
            ),
        ).single()

        val fields = item.context["fields"] as List<Map<*, *>>

        assertEquals(listOf("com.foo.Status"), item.context["imports"])
        assertEquals("Status", fields[0]["type"])
        assertEquals("Status", fields[1]["type"])
    }

    @Test
    fun `unrelated aggregate ambiguity does not affect another value object`() {
        val item = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                entities = ambiguousOrderEntities(),
                sharedEnums = listOf(
                    sharedEnum(
                        typeName = "OrderStatus",
                        packageName = "order",
                        aggregates = listOf("Order"),
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Money",
                        packageName = "com.acme.demo.domain.shared.values",
                        fields = listOf(FieldModel("amount", "Int")),
                    ),
                ),
            ),
        ).single()

        assertEquals(emptyList<String>(), item.context["imports"])
    }

    @Test
    fun `matching owner ambiguity fails only when referenced`() {
        val error = assertThrows<AmbiguousValueObjectFieldTypeFailure> {
            ValueObjectArtifactPlanner().plan(
                config(),
                CanonicalModel(
                    entities = ambiguousOrderEntities(),
                    sharedEnums = listOf(
                        sharedEnum(
                            typeName = "OrderStatus",
                            packageName = "order",
                            aggregates = listOf("Order"),
                        ),
                    ),
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "OrderSnapshot",
                            packageName = "booking",
                            aggregates = listOf("Order"),
                            fields = listOf(FieldModel("status", "OrderStatus")),
                        ),
                    ),
                ),
            )
        }

        assertEquals(true, error.message?.contains("ambiguous value object field type: OrderStatus"))
    }

    @Test
    fun `json value object requires at least one field`() {
        val error = assertThrows<IllegalArgumentException> {
            ValueObjectArtifactPlanner().plan(
                config(),
                CanonicalModel(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "Money",
                            packageName = "com.acme.demo.domain.shared.values",
                            fields = emptyList(),
                        )
                    )
                ),
            )
        }

        assertEquals("value object Money must declare at least one field", error.message)
    }

    @Test
    fun `non empty model requires domain module`() {
        val error = assertThrows<IllegalArgumentException> {
            ValueObjectArtifactPlanner().plan(
                config(modules = emptyMap()),
                CanonicalModel(
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "Money",
                            packageName = "com.acme.demo.domain.shared.values",
                            fields = listOf(FieldModel("amount", "String")),
                        )
                    )
                ),
            )
        }

        assertEquals("domain module is required", error.message)
    }

    private fun sharedEnum(
        typeName: String,
        packageName: String,
        aggregates: List<String> = emptyList(),
    ): SharedEnumDefinition = SharedEnumDefinition(
        typeName = typeName,
        packageName = packageName,
        items = listOf(EnumItemModel(0, "DEFINED", "Defined")),
        aggregates = aggregates,
    )

    private fun aggregateRootEntity(
        name: String,
        packageName: String,
    ): EntityModel = EntityModel(
        name = name,
        packageName = packageName,
        tableName = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase(),
        comment = name,
        fields = emptyList(),
        idField = FieldModel("id", "Long"),
        aggregateRoot = true,
    )

    private fun ambiguousOrderEntities(): List<EntityModel> = listOf(
        aggregateRootEntity(
            name = "Order",
            packageName = "com.acme.demo.domain.aggregates.order",
        ),
        EntityModel(
            name = "OrderLine",
            packageName = "com.acme.demo.domain.aggregates.order.lines",
            tableName = "order_line",
            comment = "OrderLine",
            fields = emptyList(),
            idField = FieldModel("id", "Long"),
            aggregateRoot = false,
            parentEntityName = "Order",
        ),
    )

    private fun config(
        modules: Map<String, String> = mapOf("domain" to "demo-domain"),
        typeRegistry: TypeRegistryConfig = TypeRegistryConfig(
            entries = mapOf(
                "CurrencyCode" to TypeRegistryEntry("com.acme.demo.domain.shared.types.CurrencyCode"),
            ),
        ),
    ): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = modules,
            generators = mapOf("types-value-object" to GeneratorConfig()),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            typeRegistry = typeRegistry,
        )
}

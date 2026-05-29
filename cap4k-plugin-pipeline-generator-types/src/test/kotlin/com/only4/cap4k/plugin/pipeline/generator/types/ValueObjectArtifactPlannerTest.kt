package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import com.only4.cap4k.plugin.pipeline.api.ValueObjectStorage
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
                        kind = StrongIdKind.AGGREGATE_ROOT,
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
    fun `canonical strong ids override same name registry entries`() {
        val snapshot = ValueObjectArtifactPlanner().plan(
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
                        kind = StrongIdKind.AGGREGATE_ROOT,
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

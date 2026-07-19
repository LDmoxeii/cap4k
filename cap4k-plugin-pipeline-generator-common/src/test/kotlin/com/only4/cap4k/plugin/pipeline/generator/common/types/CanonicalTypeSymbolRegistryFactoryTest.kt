package com.only4.cap4k.plugin.pipeline.generator.common.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CanonicalTypeSymbolRegistryFactoryTest {
    @Test
    fun `registers registry strong id shared enum local enum shared value object and local value object`() {
        val registry = CanonicalTypeSymbolRegistryFactory.from(
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                typeRegistry = TypeRegistryConfig(
                    entries = mapOf("ExternalStatus" to TypeRegistryEntry("com.acme.external.ExternalStatus")),
                ),
            ),
            model = CanonicalModel(
                entities = listOf(
                    entity(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                ),
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "OrderId",
                        packageName = "com.acme.demo.domain.aggregates.order",
                        kind = StrongIdKind.AGGREGATE_ROOT,
                        ownerAggregateName = "Order",
                    ),
                ),
                sharedEnums = listOf(
                    enumDefinition("TransportType", "shared"),
                    enumDefinition("OrderStatus", "order", aggregates = listOf("Order")),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Money",
                        packageName = "com.acme.demo.domain.shared.values",
                    ),
                    ValueObjectModel(
                        name = "OrderSnapshot",
                        packageName = "com.acme.demo.domain.aggregates.order.values",
                        aggregates = listOf("Order"),
                    ),
                ),
            ),
            artifactLayout = ArtifactLayoutResolver("com.acme.demo", ArtifactLayoutConfig()),
        )

        assertEquals(
            "com.acme.external.ExternalStatus",
            registry.findBySimpleName("ExternalStatus").single().fqcn,
        )
        assertEquals(STRONG_ID_SOURCE, registry.findBySimpleName("OrderId").single().source)
        assertEquals(MANIFEST_ENUM_SOURCE, registry.findBySimpleName("TransportType").single().source)
        assertEquals(
            "com.acme.demo.domain.aggregates.order.enums.OrderStatus",
            registry.findBySimpleName("OrderStatus").single().fqcn,
        )
        assertEquals(MANIFEST_VALUE_OBJECT_SOURCE, registry.findBySimpleName("Money").single().source)
        assertEquals("Order", registry.findBySimpleName("OrderSnapshot").single().ownerAggregateName)
    }

    @Test
    fun `local enum ownership resolves through child entity parent chain`() {
        val registry = CanonicalTypeSymbolRegistryFactory.from(
            config = ProjectConfig(basePackage = "com.acme.demo"),
            model = CanonicalModel(
                entities = listOf(
                    entity(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                    entity(
                        name = "OrderLine",
                        packageName = "com.acme.demo.domain.aggregates.order",
                        aggregateRoot = false,
                        parentEntityName = "Order",
                    ),
                ),
                sharedEnums = listOf(
                    enumDefinition("LineStatus", "order", aggregates = listOf("Order")),
                ),
            ),
            artifactLayout = ArtifactLayoutResolver("com.acme.demo", ArtifactLayoutConfig()),
        )

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.order.enums.LineStatus"),
            registry.findBySimpleName("LineStatus").map { it.fqcn },
        )
    }

    @Test
    fun `local enum ownership resolves duplicate entity names by package scoped parent`() {
        val registry = CanonicalTypeSymbolRegistryFactory.from(
            config = ProjectConfig(basePackage = "com.acme.demo"),
            model = CanonicalModel(
                entities = listOf(
                    entity(
                        name = "Order",
                        packageName = "com.acme.demo.domain.aggregates.order",
                    ),
                    entity(
                        name = "Item",
                        packageName = "com.acme.demo.domain.aggregates.order",
                        aggregateRoot = false,
                        parentEntityName = "Order",
                    ),
                    entity(
                        name = "OrderAdjustment",
                        packageName = "com.acme.demo.domain.aggregates.order",
                        aggregateRoot = false,
                        parentEntityName = "Item",
                    ),
                    entity(
                        name = "Customer",
                        packageName = "com.acme.demo.domain.aggregates.customer",
                    ),
                    entity(
                        name = "Item",
                        packageName = "com.acme.demo.domain.aggregates.customer",
                        aggregateRoot = false,
                        parentEntityName = "Customer",
                    ),
                ),
                sharedEnums = listOf(
                    enumDefinition("OrderStatus", "order", aggregates = listOf("Order")),
                ),
            ),
            artifactLayout = ArtifactLayoutResolver("com.acme.demo", ArtifactLayoutConfig()),
        )

        assertEquals(
            listOf("com.acme.demo.domain.aggregates.order.enums.OrderStatus"),
            registry.findBySimpleName("OrderStatus").map { it.fqcn },
        )
    }

    private fun entity(
        name: String,
        packageName: String,
        aggregateRoot: Boolean = true,
        parentEntityName: String? = null,
    ): EntityModel =
        EntityModel(
            name = name,
            tableName = name.lowercase(),
            packageName = packageName,
            comment = "",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
            aggregateRoot = aggregateRoot,
            parentEntityName = parentEntityName,
        )

    private fun enumDefinition(
        typeName: String,
        packageName: String,
        aggregates: List<String> = emptyList(),
    ): SharedEnumDefinition =
        SharedEnumDefinition(
            typeName = typeName,
            packageName = packageName,
            items = listOf(EnumItemModel(value = 1, name = "ACTIVE", description = "Active")),
            aggregates = aggregates,
        )
}

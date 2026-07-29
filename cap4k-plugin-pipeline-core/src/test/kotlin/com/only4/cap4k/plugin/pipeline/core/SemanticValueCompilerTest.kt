package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueEnvelope
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemanticValueCompilerTest {
    @Test
    fun `parses recursive closed semantic type algebra with node nullability`() {
        val money = identity("com.acme.types", "Money", CanonicalTypeKind.VALUE_OBJECT)
        val type = CanonicalTypeCatalog(listOf(money)).resolveExpression(
            expression = "Map<String, List<Money?>?>?",
            fieldPath = "Order.values",
        )

        val map = type as SemanticMapTypeRef
        assertTrue(map.nullable)
        assertEquals(SemanticBuiltinType.STRING, (map.keyType as SemanticBuiltinTypeRef).kind)
        val list = map.valueType as SemanticListTypeRef
        assertTrue(list.nullable)
        assertTrue((list.elementType as SemanticNamedTypeRef).nullable)
        assertEquals(money, (list.elementType as SemanticNamedTypeRef).symbol)
    }

    @Test
    fun `rejects unsupported generic constructors with field evidence`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            SemanticTypeExpressionParser.parse("MutableList<String>", "Order.items")
        }

        assertTrue(error.message.orEmpty().contains("Order.items"))
        assertTrue(error.message.orEmpty().contains("MutableList<String>"))
    }

    @Test
    fun `accepts only unqualified canonical collection constructors`() {
        listOf(
            "java.util.List<String>",
            "kotlin.collections.List<String>",
            "com.foo.Map<String, Int>",
        ).forEach { expression ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                SemanticTypeExpressionParser.parse(expression, "Payload.value")
            }
            assertTrue(error.message.orEmpty().contains("qualified container constructor is unsupported"))
            assertTrue(error.message.orEmpty().contains(expression))
        }
    }

    @Test
    fun `rejects malformed generic expressions deterministically`() {
        listOf(
            "List<String",
            "List<>",
            "Map<String,>",
            "List<out String>",
            "List<*>",
            "Pair<String, Int>",
            "Custom<String>",
        ).forEach { expression ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                SemanticTypeExpressionParser.parse(expression, "Payload.value")
            }
            assertTrue(error.message.orEmpty().contains("Payload.value"))
            assertTrue(error.message.orEmpty().contains(expression))
        }
    }

    @Test
    fun `resolves short names conservatively and explicit fqns authoritatively`() {
        val localMoney = CanonicalTypeIdentity(
            "com.acme.order.values",
            listOf("Money"),
            CanonicalTypeKind.VALUE_OBJECT,
            ownerAggregateName = "Order",
        )
        val sharedMoney = identity("com.acme.shared", "Money", CanonicalTypeKind.VALUE_OBJECT)
        val alternateSharedMoney = identity("com.acme.billing", "Money", CanonicalTypeKind.VALUE_OBJECT)
        val catalog = CanonicalTypeCatalog(listOf(localMoney, sharedMoney, alternateSharedMoney))

        val local = catalog.resolveExpression(
            expression = "Money",
            fieldPath = "Order.amount",
            aggregateContext = listOf("Order"),
        ) as SemanticNamedTypeRef
        assertEquals(localMoney, local.symbol)

        val explicitExternal = catalog.resolveExpression(
            expression = "com.vendor.types.Money",
            fieldPath = "Order.vendorAmount",
        ) as SemanticNamedTypeRef
        assertEquals(CanonicalTypeKind.EXTERNAL, explicitExternal.symbol.kind)
        assertEquals("com.vendor.types.Money", explicitExternal.symbol.fqn)

        val ambiguous = assertThrows(IllegalArgumentException::class.java) {
            catalog.resolveExpression("Money", "Shared.amount")
        }
        assertTrue(ambiguous.message.orEmpty().contains("ambiguous short type: Money"))

        val unknown = assertThrows(IllegalArgumentException::class.java) {
            catalog.resolveExpression("Missing", "Order.missing")
        }
        assertTrue(unknown.message.orEmpty().contains("unknown short type: Missing"))
    }

    @Test
    fun `uses unique explicit fqns from source expressions as conservative short-name evidence`() {
        val catalog = CanonicalTypeCatalog(
            sourceTypeExpressions = listOf(
                "java.time.LocalDateTime",
                "List<java.util.UUID>",
            ),
        )

        val dateTime = catalog.resolveExpression("LocalDateTime", "Payload.completedAt") as SemanticNamedTypeRef
        val uuid = catalog.resolveExpression("UUID", "Payload.id") as SemanticNamedTypeRef

        assertEquals("java.time.LocalDateTime", dateTime.symbol.fqn)
        assertEquals("java.util.UUID", uuid.symbol.fqn)
    }

    @Test
    fun `does not reinterpret a declared canonical FQN as external source evidence`() {
        val orderId = identity("com.acme.order", "OrderId", CanonicalTypeKind.STRONG_ID)
        val catalog = CanonicalTypeCatalog(
            identities = listOf(orderId),
            sourceTypeExpressions = listOf("com.acme.order.OrderId"),
        )

        val resolved = catalog.resolveExpression("OrderId", "Order.id") as SemanticNamedTypeRef

        assertEquals(orderId, resolved.symbol)
    }

    @Test
    fun `keeps short-name source evidence ambiguous when multiple fqns are explicit`() {
        val catalog = CanonicalTypeCatalog(
            sourceTypeExpressions = listOf(
                "com.alpha.ExternalId",
                "com.beta.ExternalId",
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            catalog.resolveExpression("ExternalId", "Payload.id")
        }

        assertTrue(error.message.orEmpty().contains("ambiguous short type: ExternalId"))
        assertTrue(error.message.orEmpty().contains("com.alpha.ExternalId"))
        assertTrue(error.message.orEmpty().contains("com.beta.ExternalId"))
    }

    @Test
    fun `rejects conflicting declarations with the same canonical FQN`() {
        val valueObject = CanonicalTypeIdentity(
            "com.acme.order",
            listOf("LineCreation"),
            CanonicalTypeKind.VALUE_OBJECT,
            ownerAggregateName = "Order",
        )
        val creationValue = valueObject.copy(kind = CanonicalTypeKind.CREATION_VALUE)

        val error = assertThrows(IllegalArgumentException::class.java) {
            CanonicalTypeCatalog(listOf(valueObject, creationValue))
        }

        assertTrue(error.message.orEmpty().contains("com.acme.order.LineCreation"))
    }

    @Test
    fun `rejects same-kind canonical FQN reused by different aggregate owners`() {
        val first = CanonicalTypeIdentity(
            "com.acme.shared",
            listOf("Window"),
            CanonicalTypeKind.VALUE_OBJECT,
            ownerAggregateName = "Order",
        )
        val second = first.copy(ownerAggregateName = "Shipment")

        assertThrows(IllegalArgumentException::class.java) {
            CanonicalTypeCatalog(listOf(first, second))
        }
    }

    @Test
    fun `preserves explicit empty and whitespace String defaults`() {
        val stringType = SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)

        assertEquals("\"\"", SemanticDefaultCompiler.compile("", stringType, "Payload.empty")?.kotlinExpression)
        assertEquals("\"  \"", SemanticDefaultCompiler.compile("  ", stringType, "Payload.spaces")?.kotlinExpression)
    }

    @Test
    fun `normalizes safe scalar collection and named defaults`() {
        val status = identity("com.acme.types", "Status", CanonicalTypeKind.ENUM)

        assertEquals(
            "42L",
            SemanticDefaultCompiler.compile(
                "42",
                SemanticBuiltinTypeRef(SemanticBuiltinType.LONG),
                "Payload.sequence",
            )?.kotlinExpression,
        )
        assertEquals(
            "emptyList()",
            SemanticDefaultCompiler.compile(
                "emptyList()",
                SemanticListTypeRef(SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)),
                "Payload.items",
            )?.kotlinExpression,
        )
        assertEquals(
            "com.acme.types.Status.READY",
            SemanticDefaultCompiler.compile(
                "com.acme.types.Status.READY",
                SemanticNamedTypeRef(status),
                "Payload.status",
            )?.kotlinExpression,
        )
        assertEquals(
            "com.acme.types.Status.READY",
            SemanticDefaultCompiler.compile(
                "Status.READY",
                SemanticNamedTypeRef(status),
                "Payload.shortStatus",
            )?.kotlinExpression,
        )
    }

    @Test
    fun `rejects unsafe or type-incompatible defaults with field evidence`() {
        val nonNullableNull = assertThrows(IllegalArgumentException::class.java) {
            SemanticDefaultCompiler.compile(
                "null",
                SemanticBuiltinTypeRef(SemanticBuiltinType.STRING),
                "Payload.requiredName",
            )
        }
        assertTrue(nonNullableNull.message.orEmpty().contains("Payload.requiredName"))

        val arbitraryCollection = assertThrows(IllegalArgumentException::class.java) {
            SemanticDefaultCompiler.compile(
                "listOf(\"unsafe\")",
                SemanticListTypeRef(SemanticBuiltinTypeRef(SemanticBuiltinType.STRING)),
                "Payload.items",
            )
        }
        assertTrue(arbitraryCollection.message.orEmpty().contains("Payload.items"))

        listOf(
            Triple("2147483648", SemanticBuiltinTypeRef(SemanticBuiltinType.INT), "Payload.count"),
            Triple("true.value", SemanticBuiltinTypeRef(SemanticBuiltinType.BOOLEAN), "Payload.enabled"),
            Triple("Status.READY", SemanticNamedTypeRef(identity("com.acme.types", "OtherStatus", CanonicalTypeKind.ENUM)), "Payload.status"),
            Triple("com.evil.Status.READY", SemanticNamedTypeRef(identity("com.acme.types", "Status", CanonicalTypeKind.ENUM)), "Payload.evilStatus"),
            Triple("\"${'$'}danger\"", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING), "Payload.template"),
            Triple("\"\\u12G4\"", SemanticBuiltinTypeRef(SemanticBuiltinType.STRING), "Payload.unicode"),
        ).forEach { (expression, type, fieldPath) ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                SemanticDefaultCompiler.compile(expression, type, fieldPath)
            }
            assertTrue(error.message.orEmpty().contains(fieldPath))
        }

        assertEquals(
            "\"\\${'$'}amount\"",
            SemanticDefaultCompiler.compile(
                "\"\\${'$'}amount\"",
                SemanticBuiltinTypeRef(SemanticBuiltinType.STRING),
                "Payload.escapedTemplate",
            )?.kotlinExpression,
        )
        assertEquals(
            "\"\\u0041\"",
            SemanticDefaultCompiler.compile(
                "\"\\u0041\"",
                SemanticBuiltinTypeRef(SemanticBuiltinType.STRING),
                "Payload.validUnicode",
            )?.kotlinExpression,
        )
    }

    @Test
    fun `compiles nested paths into flattened Kotlin identities before rendering`() {
        val definition = SemanticValueCompiler(CanonicalTypeCatalog()).compile(
            identity = identity("com.acme.api", "Payload.Request", CanonicalTypeKind.NESTED_VALUE),
            role = SemanticValueRole.API_PAYLOAD_REQUEST,
            fields = listOf(
                SemanticFieldSnapshot("files", "List<FileItem>"),
                SemanticFieldSnapshot("files[].index", "Int"),
                SemanticFieldSnapshot("files[].variants", "List<VariantItem>"),
                SemanticFieldSnapshot("files[].variants[].quality", "String", defaultValue = ""),
            ),
        )

        val fileItem = definition.nestedDefinitions.single()
        val variantItem = fileItem.nestedDefinitions.single()
        assertEquals("com.acme.api.Payload.Request.FileItem", fileItem.identity.fqn)
        assertEquals("com.acme.api.Payload.Request.VariantItem", variantItem.identity.fqn)
        assertEquals("\"\"", variantItem.fields.single().defaultValue?.kotlinExpression)
    }

    @Test
    fun `infers Item once for an undeclared items collection path`() {
        val definition = SemanticValueCompiler(CanonicalTypeCatalog()).compile(
            identity = identity("com.acme.api", "Payload.Request", CanonicalTypeKind.NESTED_VALUE),
            role = SemanticValueRole.API_PAYLOAD_REQUEST,
            fields = listOf(SemanticFieldSnapshot("items[].name", "String")),
        )

        assertEquals("Item", definition.nestedDefinitions.single().identity.simpleName)
        assertEquals(
            "Item",
            ((definition.fields.single().type as SemanticListTypeRef).elementType as SemanticNamedTypeRef)
                .symbol
                .simpleName,
        )
    }

    @Test
    fun `compiles PageData as response-only envelope outside generic algebra`() {
        val definition = SemanticValueCompiler(CanonicalTypeCatalog()).compile(
            identity = identity("com.acme.api", "FindOrders.Response", CanonicalTypeKind.NESTED_VALUE),
            role = SemanticValueRole.QUERY_RESPONSE,
            fields = listOf(
                SemanticFieldSnapshot("page", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                SemanticFieldSnapshot("page.list[].id", "Long"),
            ),
            allowPageEnvelope = true,
        )

        val page = definition.envelope as SemanticValueEnvelope.Page
        assertEquals("Item", page.itemDefinition.identity.simpleName)
        assertEquals(listOf("id"), page.itemDefinition.fields.map { it.name })
        assertTrue(definition.fields.isEmpty())
    }

    private fun identity(packageName: String, typePath: String, kind: CanonicalTypeKind) =
        CanonicalTypeIdentity(packageName, typePath.split('.'), kind)
}

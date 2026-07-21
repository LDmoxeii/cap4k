package com.only4.cap4k.plugin.pipeline.generator.common.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeSymbolSelectorTest {
    @Test
    fun `single aggregate context prefers matching local manifest candidates`() {
        val shared = TypeSymbolIdentity(
            packageName = "com.acme.shared",
            typeName = "Status",
            source = MANIFEST_ENUM_SOURCE,
            manifestOwned = true,
            shared = true,
        )
        val orderLocal = TypeSymbolIdentity(
            packageName = "com.acme.order",
            typeName = "Status",
            source = MANIFEST_ENUM_SOURCE,
            ownerAggregateName = "Order",
            manifestOwned = true,
            shared = false,
        )
        val customerLocal = TypeSymbolIdentity(
            packageName = "com.acme.customer",
            typeName = "Status",
            source = MANIFEST_VALUE_OBJECT_SOURCE,
            ownerAggregateName = "Customer",
            manifestOwned = true,
            shared = false,
        )

        val selected = TypeSymbolSelector.selectShortNameCandidates(
            candidates = listOf(shared, orderLocal, customerLocal),
            aggregateContext = listOf("Order"),
        )

        assertEquals(listOf(orderLocal), selected)
    }

    @Test
    fun `no context returns all unique candidates`() {
        val first = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val duplicate = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val second = TypeSymbolIdentity(packageName = "com.bar", typeName = "Status")

        val selected = TypeSymbolSelector.selectShortNameCandidates(
            candidates = listOf(first, duplicate, second),
            aggregateContext = emptyList(),
        )

        assertEquals(listOf(first, second), selected)
    }

    @Test
    fun `multi aggregate context does not choose a local owner`() {
        val orderLocal = TypeSymbolIdentity(
            packageName = "com.acme.order",
            typeName = "Snapshot",
            ownerAggregateName = "Order",
            manifestOwned = true,
            shared = false,
        )
        val customerLocal = TypeSymbolIdentity(
            packageName = "com.acme.customer",
            typeName = "Snapshot",
            ownerAggregateName = "Customer",
            manifestOwned = true,
            shared = false,
        )

        val selected = TypeSymbolSelector.selectShortNameCandidates(
            candidates = listOf(orderLocal, customerLocal),
            aggregateContext = listOf("Order", "Customer"),
        )

        assertEquals(listOf(orderLocal, customerLocal), selected)
    }
}

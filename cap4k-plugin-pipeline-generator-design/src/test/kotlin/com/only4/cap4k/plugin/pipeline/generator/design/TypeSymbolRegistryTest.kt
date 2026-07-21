package com.only4.cap4k.plugin.pipeline.generator.design

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolIdentity
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry

class TypeSymbolRegistryTest {

    @Test
    fun `registering one symbol resolves by simple name`() {
        val registry = TypeSymbolRegistry()
        val symbol = TypeSymbolIdentity(
            packageName = "com.foo.orders",
            typeName = "Order",
        )

        registry.register(symbol)

        assertEquals(listOf(symbol), registry.findBySimpleName("Order"))
    }

    @Test
    fun `registering two symbols with the same simple name retains both candidates`() {
        val registry = TypeSymbolRegistry()
        val first = TypeSymbolIdentity(
            packageName = "com.foo.orders",
            typeName = "Status",
        )
        val second = TypeSymbolIdentity(
            packageName = "com.bar.shipping",
            typeName = "Status",
        )

        registry.register(first)
        registry.register(second)

        assertEquals(listOf(first, second), registry.findBySimpleName("Status"))
    }

    @Test
    fun `registry preserves package identity for same-name candidates`() {
        val registry = TypeSymbolRegistry()
        val first = TypeSymbolIdentity(
            packageName = "com.foo.orders",
            typeName = "Status",
        )
        val second = TypeSymbolIdentity(
            packageName = "com.bar.shipping",
            typeName = "Status",
        )

        registry.register(first)
        registry.register(second)

        val candidates = registry.findBySimpleName("Status")

        assertEquals("com.foo.orders", candidates[0].packageName)
        assertEquals("com.bar.shipping", candidates[1].packageName)
        assertEquals("Status", candidates[0].typeName)
        assertEquals("Status", candidates[1].typeName)
    }
}

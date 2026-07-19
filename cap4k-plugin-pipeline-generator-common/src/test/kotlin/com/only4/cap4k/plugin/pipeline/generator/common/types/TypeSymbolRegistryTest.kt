package com.only4.cap4k.plugin.pipeline.generator.common.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeSymbolRegistryTest {
    @Test
    fun `stores multiple candidates for the same simple name`() {
        val first = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val second = TypeSymbolIdentity(packageName = "com.bar", typeName = "Status")

        val registry = TypeSymbolRegistry(listOf(first, second))

        assertEquals(listOf(first, second), registry.findBySimpleName("Status"))
        assertEquals(listOf(first, second), registry.allSymbols())
    }

    @Test
    fun `deduplicates exact symbol identity while preserving distinct fqns`() {
        val first = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val second = TypeSymbolIdentity(packageName = "com.foo", typeName = "Status")
        val third = TypeSymbolIdentity(packageName = "com.bar", typeName = "Status")

        val registry = TypeSymbolRegistry(listOf(first, second, third))

        assertEquals(listOf(first, third), registry.findBySimpleName("Status"))
    }
}

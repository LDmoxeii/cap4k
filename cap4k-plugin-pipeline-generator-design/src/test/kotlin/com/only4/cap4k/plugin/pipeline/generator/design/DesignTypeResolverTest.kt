package com.only4.cap4k.plugin.pipeline.generator.design

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignTypeResolverTest {

    @Test
    fun `built-in type stays unqualified and unimported`() {
        val resolved = resolve("String?")
        val plan = plan("String?")

        assertEquals(DesignResolvedTypeKind.BUILTIN, resolved.kind)
        assertEquals(emptySet<String>(), resolved.importCandidates)
        assertEquals("String?", plan.renderedTypes.single().renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertFalse(plan.renderedTypes.single().qualifiedFallback)
    }

    @Test
    fun `inner type wins over external same-name type`() {
        val resolvedInner = resolve("Address", innerTypes = setOf("Address"))
        val resolvedExternal = resolve("com.foo.Address", innerTypes = setOf("Address"))
        val plan = plan(
            types = listOf("Address", "com.foo.Address"),
            innerTypes = setOf("Address"),
        )

        assertEquals(DesignResolvedTypeKind.INNER, resolvedInner.kind)
        assertEquals(DesignResolvedTypeKind.EXPLICIT_FQCN, resolvedExternal.kind)
        assertEquals("Address", plan.renderedTypes[0].renderedText)
        assertEquals("com.foo.Address", plan.renderedTypes[1].renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertFalse(plan.renderedTypes[0].qualifiedFallback)
        assertTrue(plan.renderedTypes[1].qualifiedFallback)
    }

    @Test
    fun `inner type wins over built-in name`() {
        val resolved = resolve("Pair", innerTypes = setOf("Pair"))
        val plan = plan(
            type = "Pair",
            innerTypes = setOf("Pair"),
        )

        assertEquals(DesignResolvedTypeKind.INNER, resolved.kind)
        assertEquals("Pair", plan.renderedTypes.single().renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertFalse(plan.renderedTypes.single().qualifiedFallback)
    }

    @Test
    fun `unique fqcn imports and renders short name`() {
        val resolved = resolve("java.time.LocalDateTime")
        val plan = plan("java.time.LocalDateTime")

        assertEquals(DesignResolvedTypeKind.EXPLICIT_FQCN, resolved.kind)
        assertEquals(setOf("java.time.LocalDateTime"), resolved.importCandidates)
        assertEquals("LocalDateTime", plan.renderedTypes.single().renderedText)
        assertEquals(listOf("java.time.LocalDateTime"), plan.imports)
        assertFalse(plan.renderedTypes.single().qualifiedFallback)
    }

    @Test
    fun `fqcn that collides with a built-in stays qualified and unimported`() {
        val plan = plan("java.util.List<java.lang.String>")

        assertEquals("java.util.List<java.lang.String>", plan.renderedTypes.single().renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertTrue(plan.renderedTypes.single().qualifiedFallback)
    }

    @Test
    fun `colliding fqcn names render qualified names and produce no imports`() {
        val resolvedFoo = resolve("com.foo.Status")
        val resolvedBar = resolve("com.bar.Status")
        val plan = plan(
            types = listOf("com.foo.Status", "com.bar.Status"),
        )

        assertEquals(DesignResolvedTypeKind.EXPLICIT_FQCN, resolvedFoo.kind)
        assertEquals(DesignResolvedTypeKind.EXPLICIT_FQCN, resolvedBar.kind)
        assertEquals(setOf("com.foo.Status"), resolvedFoo.importCandidates)
        assertEquals(setOf("com.bar.Status"), resolvedBar.importCandidates)
        assertEquals("com.foo.Status", plan.renderedTypes[0].renderedText)
        assertEquals("com.bar.Status", plan.renderedTypes[1].renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertTrue(plan.renderedTypes[0].qualifiedFallback)
        assertTrue(plan.renderedTypes[1].qualifiedFallback)
    }

    @Test
    fun `unresolved short name stays unresolved and unimported`() {
        val resolved = resolve("UserId")
        val plan = plan("UserId")

        assertEquals(DesignResolvedTypeKind.UNRESOLVED, resolved.kind)
        assertEquals(emptySet<String>(), resolved.importCandidates)
        assertEquals("UserId", plan.renderedTypes.single().renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertFalse(plan.renderedTypes.single().qualifiedFallback)
    }

    @Test
    fun `recursive generics preserve qualified fallback inside children`() {
        val resolved = resolve("List<com.foo.Status?>")
        val plan = plan(
            types = listOf("List<com.foo.Status?>", "com.bar.Status"),
        )

        assertEquals(DesignResolvedTypeKind.BUILTIN, resolved.kind)
        assertEquals(setOf("com.foo.Status"), resolved.arguments.single().importCandidates)
        assertEquals("List<com.foo.Status?>", plan.renderedTypes[0].renderedText)
        assertEquals("com.bar.Status", plan.renderedTypes[1].renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertTrue(plan.renderedTypes[0].qualifiedFallback)
        assertTrue(plan.renderedTypes[1].qualifiedFallback)
    }

    @Test
    fun `external fqcn does not import when unresolved short name shares simple name`() {
        val plan = plan(
            types = listOf("Status", "com.foo.Status"),
        )

        assertEquals("Status", plan.renderedTypes[0].renderedText)
        assertEquals("com.foo.Status", plan.renderedTypes[1].renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertTrue(plan.renderedTypes[1].qualifiedFallback)
    }

    @Test
    fun `number resolves as built-in and stays unimported`() {
        assertBuiltInAndUnimported("Number")
    }

    @Test
    fun `pair resolves as built-in and stays unimported`() {
        assertBuiltInAndUnimported("Pair")
    }

    @Test
    fun `triple resolves as built-in and stays unimported`() {
        assertBuiltInAndUnimported("Triple")
    }

    private fun resolve(
        raw: String,
        innerTypes: Set<String> = emptySet(),
    ): DesignResolvedTypeModel =
        DesignTypeResolver.resolve(
            type = DesignTypeParser.parse(raw),
            innerTypeNames = innerTypes,
        )

    private fun plan(
        types: List<String>,
        innerTypes: Set<String> = emptySet(),
    ): DesignImportPlan = DesignImportPlanner.plan(
        types = types.map { resolve(it, innerTypes) },
        innerTypeNames = innerTypes,
    )

    private fun plan(
        type: String,
        innerTypes: Set<String> = emptySet(),
    ): DesignImportPlan = plan(listOf(type), innerTypes)

    private fun assertBuiltInAndUnimported(raw: String) {
        val resolved = resolve(raw)
        val plan = plan(raw)

        assertEquals(DesignResolvedTypeKind.BUILTIN, resolved.kind)
        assertEquals(emptySet<String>(), resolved.importCandidates)
        assertEquals(raw, plan.renderedTypes.single().renderedText)
        assertEquals(emptyList<String>(), plan.imports)
        assertFalse(plan.renderedTypes.single().qualifiedFallback)
    }
}

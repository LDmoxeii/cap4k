package com.only4.cap4k.plugin.pipeline.generator.aggregate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregateTypeImportsTest {
    @Test
    fun `rendered type keeps generic suffix when importing qualified raw type`() {
        val rendered = aggregateRenderedType("com.acme.demo.domain.shared.types.Box<com.acme.demo.domain.shared.types.Money>?")

        assertEquals("Box<com.acme.demo.domain.shared.types.Money>", rendered.renderedType)
        assertEquals(listOf("com.acme.demo.domain.shared.types.Box"), rendered.imports)
    }

    @Test
    fun `rendered type preserves existing qualified uuid boundary`() {
        val rendered = aggregateRenderedType("java.util.UUID")

        assertEquals("java.util.UUID", rendered.renderedType)
        assertEquals(emptyList<String>(), rendered.imports)
        assertEquals(listOf("java.util.UUID"), aggregateTypeImports("UUID"))
    }
}

package com.only4.cap4k.plugin.pipeline.generator.design.types

import com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeParser
import com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImportResolverTest {

    @Test
    fun `unknown short type exposes structured failure`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            ImportResolver.resolve(
                type = DesignTypeResolver.resolve(DesignTypeParser.parse("UserId")),
            )
        }

        assertEquals("UnknownShortTypeFailure", ex.javaClass.simpleName)
    }

    @Test
    fun `ambiguous short type exposes structured failure`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            ImportResolver.resolve(
                type = DesignTypeResolver.resolve(DesignTypeParser.parse("Status")),
                symbolRegistry = DesignSymbolRegistry(
                    listOf(
                        SymbolIdentity(packageName = "com.foo", typeName = "Status"),
                        SymbolIdentity(packageName = "com.bar", typeName = "Status"),
                    ),
                ),
            )
        }

        assertEquals("AmbiguousShortTypeFailure", ex.javaClass.simpleName)
    }
}

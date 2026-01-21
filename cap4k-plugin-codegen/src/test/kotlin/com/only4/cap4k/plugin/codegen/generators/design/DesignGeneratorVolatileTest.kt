package com.only4.cap4k.plugin.codegen.generators.design

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class DesignGeneratorVolatileTest {

    @Test
    fun `cached fields are volatile`() {
        val classes = listOf(
            CommandGenerator::class.java,
            QueryGenerator::class.java,
            ClientGenerator::class.java,
            ValidatorGenerator::class.java,
            ApiPayloadGenerator::class.java,
            QueryHandlerGenerator::class.java,
            ClientHandlerGenerator::class.java,
            DomainEventGenerator::class.java,
            DomainEventHandlerGenerator::class.java,
        )

        classes.forEach { clazz ->
            assertVolatile(clazz, "currentType")
            assertVolatile(clazz, "currentFullName")
        }
    }

    private fun assertVolatile(clazz: Class<*>, fieldName: String) {
        val field = clazz.getDeclaredField(fieldName)
        assertTrue(
            Modifier.isVolatile(field.modifiers),
            "${clazz.simpleName}.$fieldName should be volatile",
        )
    }
}

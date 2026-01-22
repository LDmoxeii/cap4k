package com.only4.cap4k.plugin.codegen.pebble

import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiscTemplateAutoImportTest {
    private fun loadTemplate(name: String): String {
        val resource = javaClass.classLoader.getResource("templates/$name")
            ?: error("Template not found: $name")
        return resource.openStream().bufferedReader().use { it.readText() }
    }

    @Test
    fun `saga template adds static imports via use`() {
        val template = loadTemplate("saga.kt.peb")
        val context = mapOf(
            "basePackage" to "com.example",
            "templatePackage" to "",
            "package" to "",
            "date" to "2026-01-21",
            "Saga" to "Order",
            "Comment" to "desc",
            "typeMapping" to emptyMap<String, String>()
        )

        val rendered = renderString(template, context)
        assertTrue(rendered.contains("import org.springframework.stereotype.Service"))
        assertTrue(rendered.contains("import org.slf4j.LoggerFactory"))
        assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
    }
}

package com.only4.cap4k.plugin.codegen.pebble

import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignTemplateAutoImportTest {
    private fun loadTemplate(name: String): String {
        val resource = javaClass.classLoader.getResource("templates/$name")
            ?: error("Template not found: $name")
        return resource.openStream().bufferedReader().use { it.readText() }
    }

    @Test
    fun `command template adds static imports via use`() {
        val template = loadTemplate("command.kt.peb")
        val context = mapOf(
            "basePackage" to "com.example",
            "templatePackage" to "",
            "package" to "",
            "date" to "2026-01-21",
            "Command" to "CreateThing",
            "Comment" to "desc",
            "requestFields" to emptyList<Map<String, String>>(),
            "responseFields" to emptyList<Map<String, String>>(),
            "typeMapping" to emptyMap<String, String>()
        )

        val rendered = renderString(template, context)
        assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.Mediator"))
        assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.application.RequestParam"))
        assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.application.command.Command"))
        assertTrue(rendered.contains("import org.springframework.stereotype.Service"))
    }

    @Test
    fun `query page handler imports query type and page data via use`() {
        val template = loadTemplate("query_page_handler.kt.peb")
        val context = mapOf(
            "basePackage" to "com.example",
            "templatePackage" to "",
            "package" to "",
            "date" to "2026-01-21",
            "Query" to "GetThingsPageQry",
            "QueryHandler" to "GetThingsPageQryHandler",
            "Comment" to "desc",
            "responseFields" to emptyList<Map<String, String>>(),
            "QueryType" to "com.example.app.GetThingsPageQry",
            "typeMapping" to emptyMap<String, String>()
        )

        val rendered = renderString(template, context)
        assertTrue(rendered.contains("import com.example.app.GetThingsPageQry"))
        assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.application.query.PageQuery"))
        assertTrue(rendered.contains("import com.only4.cap4k.ddd.core.share.PageData"))
        assertTrue(rendered.contains("import org.springframework.stereotype.Service"))
    }

    @Test
    fun `validator template imports kotlin reflect types via use`() {
        val template = loadTemplate("validator.kt.peb")
        val context = mapOf(
            "basePackage" to "com.example",
            "templatePackage" to "",
            "package" to "",
            "date" to "2026-01-21",
            "Validator" to "ValidThing",
            "Comment" to "desc",
            "ValueType" to "Long",
            "typeMapping" to emptyMap<String, String>()
        )

        val rendered = renderString(template, context)
        assertTrue(rendered.contains("import kotlin.reflect.KClass"))
    }
}

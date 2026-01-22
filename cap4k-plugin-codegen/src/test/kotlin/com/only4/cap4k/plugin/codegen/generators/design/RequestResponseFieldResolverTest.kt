package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.AggregateInfo
import com.only4.cap4k.plugin.codegen.context.design.models.BaseDesign
import com.only4.cap4k.plugin.codegen.context.design.models.CommandDesign
import com.only4.cap4k.plugin.codegen.context.design.models.DesignElement
import com.only4.cap4k.plugin.codegen.context.design.models.QueryDesign
import com.only4.cap4k.plugin.codegen.context.design.models.common.PayloadField
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestResponseFieldResolverTest {

    @Test
    fun `formats default values based on type`() {
        val context = TestDesignContext()
        val design = commandDesign()

        val requestFields = listOf(
            PayloadField(name = "note", type = "String", defaultValue = "ABC", nullable = true),
            PayloadField(name = "count", type = "Long", defaultValue = "10"),
        )

        val resolved = with(context) {
            resolveRequestResponseFields(design, requestFields, emptyList())
        }

        val defaults = resolved.requestFieldsForTemplate.associate { it["name"] to it["defaultValue"] }
        assertEquals("\"ABC\"", defaults["note"])
        assertEquals("10L", defaults["count"])
    }

    @Test
    fun `extracts nested list fields into nested types`() {
        val context = TestDesignContext()
        val design = commandDesign()

        val requestFields = listOf(
            PayloadField(name = "accountList", type = "List<Item>"),
            PayloadField(name = "accountList[].accountNumber", type = "String"),
            PayloadField(name = "accountList[].accountName", type = "String"),
            PayloadField(name = "accountList[].bankName", type = "String"),
            PayloadField(name = "accountList[].currency", type = "String"),
        )

        val resolved = with(context) {
            resolveRequestResponseFields(design, requestFields, emptyList())
        }

        val topLevelNames = resolved.requestFieldsForTemplate.mapNotNull { it["name"] }
        assertEquals(listOf("accountList"), topLevelNames)

        val getter = resolved.javaClass.getMethod("getNestedTypesForTemplate")
        val nestedTypes = getter.invoke(resolved) as List<*>
        assertEquals(1, nestedTypes.size)

        val nested = nestedTypes.first() as Map<*, *>
        assertEquals("Item", nested["name"])

        val nestedFields = nested["fields"] as List<*>
        val nestedNames = nestedFields.mapNotNull { (it as Map<*, *>)["name"] }
        assertTrue(
            nestedNames.containsAll(
                listOf("accountNumber", "accountName", "bankName", "currency")
            ),
        )
    }

    private fun commandDesign(): CommandDesign =
        CommandDesign(
            type = "cmd",
            `package` = "user",
            name = "CreateUser",
            desc = "create user",
            aggregate = null,
            aggregates = emptyList(),
            primaryAggregateMetadata = null,
            aggregateMetadataList = emptyList(),
        )

    private class TestDesignContext : DesignContext {
        override val baseMap: Map<String, Any?> = mapOf("basePackage" to "com.acme")
        override val adapterPath: String = ""
        override val domainPath: String = ""
        override val applicationPath: String = ""
        override val typeMapping: MutableMap<String, String> = mutableMapOf()
        override val templateParentPath: MutableMap<String, String> = mutableMapOf()
        override val templatePackage: MutableMap<String, String> = mutableMapOf()
        override val templateNodeMap: MutableMap<String, MutableList<TemplateNode>> = mutableMapOf()
        override val designElementMap: Map<String, List<DesignElement>> = emptyMap()
        override val aggregateMap: Map<String, AggregateInfo> = emptyMap()
        override val designMap: Map<String, List<BaseDesign>> = emptyMap()

        override fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any) {
            this[variable] = value
        }
    }
}

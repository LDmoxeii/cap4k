package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.AggregateInfo
import com.only4.cap4k.plugin.codegen.context.design.models.CommandDesign
import com.only4.cap4k.plugin.codegen.context.design.models.DesignElement
import com.only4.cap4k.plugin.codegen.context.design.models.QueryDesign
import com.only4.cap4k.plugin.codegen.context.design.models.BaseDesign
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignGeneratorCachingTest {

    @Test
    fun `shouldGenerate caches command name`() {
        val context = TestDesignContext()
        val design = CommandDesign(
            type = "cmd",
            `package` = "user",
            name = "CreateUser",
            desc = "create user",
            aggregate = null,
            aggregates = emptyList(),
            primaryAggregateMetadata = null,
            aggregateMetadataList = emptyList()
        )
        val generator = CommandGenerator()

        val shouldGenerate = with(context) { generator.shouldGenerate(design) }

        assertTrue(shouldGenerate)
        assertEquals("CreateUserCmd", generator.generatorName())
        assertEquals("com.acme.user.CreateUserCmd", generator.generatorFullName())
    }

    @Test
    fun `shouldGenerate caches query handler name`() {
        val context = TestDesignContext()
        val design = QueryDesign(
            type = "qry",
            `package` = "user",
            name = "GetUser",
            desc = "get user",
            aggregate = null,
            aggregates = emptyList(),
            primaryAggregateMetadata = null,
            aggregateMetadataList = emptyList()
        )
        val generator = QueryHandlerGenerator()

        val shouldGenerate = with(context) { generator.shouldGenerate(design) }

        assertTrue(shouldGenerate)
        assertEquals("GetUserQryHandler", generator.generatorName())
        assertEquals("com.acme.user.GetUserQryHandler", generator.generatorFullName())
    }

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

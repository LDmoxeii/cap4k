package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateGeneratorCachingTest {

    @Test
    fun `shouldGenerate caches schema base full name`() {
        val context = TestAggregateContext()
        val generator = SchemaBaseGenerator()

        val shouldGenerate = with(context) { generator.shouldGenerate(emptyMap()) }

        assertTrue(shouldGenerate)
        assertEquals("Schema", generator.generatorName())
        assertEquals("com.acme.domain._share.meta.Schema", generator.generatorFullName())
    }

    private class TestAggregateContext : AggregateContext {
        override val baseMap: Map<String, Any?> = mapOf("basePackage" to "com.acme")
        override val adapterPath: String = ""
        override val domainPath: String = ""
        override val applicationPath: String = ""
        override val typeMapping: MutableMap<String, String> = mutableMapOf()
        override val templateParentPath: MutableMap<String, String> = mutableMapOf()
        override val templatePackage: MutableMap<String, String> = mutableMapOf(
            "schema_base" to "domain._share.meta"
        )
        override val templateNodeMap: MutableMap<String, MutableList<TemplateNode>> = mutableMapOf()

        override val dbType: String = "mysql"
        override val tableMap: Map<String, Map<String, Any?>> = emptyMap()
        override val columnsMap: Map<String, List<Map<String, Any?>>> = emptyMap()
        override val tablePackageMap: Map<String, String> = emptyMap()
        override val entityTypeMap: Map<String, String> = emptyMap()
        override val tableModuleMap: Map<String, String> = emptyMap()
        override val tableAggregateMap: Map<String, String> = emptyMap()
        override val annotationsMap: Map<String, Map<String, String>> = emptyMap()
        override val relationsMap: Map<String, Map<String, String>> = emptyMap()
        override val entityClassExtraImports: List<String> = emptyList()
        override val enumConfigMap: Map<String, Map<Int, Array<String>>> = emptyMap()
        override val enumPackageMap: Map<String, String> = emptyMap()
        override val uniqueConstraintsMap: Map<String, List<Map<String, Any?>>> = emptyMap()

        override fun resolveAggregateWithModule(tableName: String): String = ""

        override fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any) {
            this[variable] = value
        }
    }
}

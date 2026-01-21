package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.core.LoggerAdapter
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class UnitGenerationCollectorTest {
    @Test
    fun preAppliesExportsBeforeLaterCollect() {
        val context = TestAggregateContext()

        val exportGenerator = object : AggregateUnitGenerator {
            override val tag: String = "enum"
            override val order: Int = 10

            context(ctx: AggregateContext)
            override fun collect(): List<GenerationUnit> {
                return listOf(
                    GenerationUnit(
                        id = "enum:test",
                        tag = tag,
                        name = "TestEnum",
                        order = order,
                        templateNodes = emptyList(),
                        context = emptyMap(),
                        exportTypes = mapOf("TestEnum" to "com.example.TestEnum"),
                    )
                )
            }
        }

        val dependentGenerator = object : AggregateUnitGenerator {
            override val tag: String = "entity"
            override val order: Int = 20

            context(ctx: AggregateContext)
            override fun collect(): List<GenerationUnit> {
                check(ctx.typeMapping.containsKey("TestEnum")) { "TestEnum should be available before collect" }
                return emptyList()
            }
        }

        val collector = newCollectorInstance()
        val collect = collector.javaClass.methods.firstOrNull {
            it.name == "collect" && it.parameterTypes.size == 2
        } ?: fail("UnitGenerationCollector.collect missing")

        collect.invoke(collector, listOf(exportGenerator, dependentGenerator), context)
        assertEquals("com.example.TestEnum", context.typeMapping["TestEnum"])
    }

    private fun newCollectorInstance(): Any {
        val collectorClass = try {
            Class.forName("com.only4.cap4k.plugin.codegen.generators.unit.UnitGenerationCollector")
        } catch (e: ClassNotFoundException) {
            throw AssertionError("UnitGenerationCollector missing", e)
        }

        val noArg = collectorClass.declaredConstructors.firstOrNull { it.parameterCount == 0 }
        if (noArg != null) {
            return noArg.newInstance()
        }

        val logCtor = collectorClass.declaredConstructors.firstOrNull { ctor ->
            ctor.parameterCount == 1 && LoggerAdapter::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }
        if (logCtor != null) {
            return logCtor.newInstance(null)
        }

        throw AssertionError("UnitGenerationCollector constructor missing")
    }

    private class TestAggregateContext : AggregateContext {
        override val dbType: String = "test"
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

        override val baseMap: Map<String, Any?> = emptyMap()
        override val adapterPath: String = ""
        override val domainPath: String = ""
        override val applicationPath: String = ""
        override val typeMapping: MutableMap<String, String> = mutableMapOf()
        override val templateParentPath: MutableMap<String, String> = mutableMapOf()
        override val templatePackage: MutableMap<String, String> = mutableMapOf()
        override val templateNodeMap: MutableMap<String, MutableList<TemplateNode>> = mutableMapOf()

        override fun resolveAggregateWithModule(tableName: String): String = tableName

        override fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any) {
            this[variable] = value
        }
    }
}

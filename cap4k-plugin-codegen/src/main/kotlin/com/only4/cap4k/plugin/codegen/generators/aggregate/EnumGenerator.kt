package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * 枚举文件生成器
 */
class EnumGenerator : AggregateGenerator {
    override val tag = "enum"
    override val order = 10

    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        with(ctx) {
            if (SqlSchemaUtils.isIgnore(table)) return false
            if (SqlSchemaUtils.hasRelation(table)) return false

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = columnsMap[tableName] ?: return false

            // 检查是否有未生成的枚举列
            val enumType =
                columns.filter { column -> SqlSchemaUtils.hasEnum(column) && !(SqlSchemaUtils.isIgnore(column)) }
                    .map { column -> SqlSchemaUtils.getType(column) }
                    .firstOrNull { currentEnumType ->
                        currentEnumType.isNotBlank() && !typeMapping.containsKey(currentEnumType)
                    }

            if (enumType == null) return false

            val aggregate = resolveAggregateWithModule(tableName)

            currentType = enumType
            currentFullName = resolveFullName(this, aggregate)
            return true
        }
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)

            val enumConfig = enumConfigMap[currentType]!!

            val enumItems = enumConfig.toSortedMap().map { (value, arr) ->
                mapOf(
                    "value" to value,
                    "name" to arr[0],
                    "desc" to arr[1]
                )
            }

            val resultContext = baseMap.toMutableMap()

            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(
                tag,
                "package",
                refPackage(concatPackage(refPackage(aggregate), refPackage("enums")))
            )

            resultContext.putContext(tag, "Enum", currentType)

            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "EnumValueField", getString("enumValueField"))
            resultContext.putContext(tag, "EnumNameField", getString("enumNameField"))
            resultContext.putContext(tag, "EnumItems", enumItems)

            return resultContext
        }
    }

    override fun generatorFullName(): String = currentFullName

    override fun generatorName(): String = currentType

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@EnumGenerator.tag
                name = "{{ Enum }}.kt"
                format = "resource"
                data = "templates/enum.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(
        table: Map<String, Any?>,
    ) {
        ctx.typeMapping[currentType] = generatorFullName()
    }

    private fun resolveFullName(ctx: AggregateContext, aggregate: String): String {
        val defaultEnumPackage = "enums"
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(aggregate)
        return "$basePackage${templatePackage}${`package`}${refPackage(defaultEnumPackage)}${refPackage(currentType)}"
    }
}


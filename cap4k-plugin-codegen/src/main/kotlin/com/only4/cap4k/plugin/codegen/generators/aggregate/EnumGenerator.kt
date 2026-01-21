package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.EnumImportManager
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
    private lateinit var currentEnumType: String

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        with(ctx) {
            if (SqlSchemaUtils.isIgnore(table)) return false
            if (SqlSchemaUtils.hasRelation(table)) return false

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = columnsMap[tableName] ?: return false

            // 检查是否有未生成的枚举列
            return columns.any { column ->
                if (!SqlSchemaUtils.hasEnum(column) || SqlSchemaUtils.isIgnore(column)) {
                    false
                } else {
                    val enumType = SqlSchemaUtils.getType(column)
                    !typeMapping.containsKey(enumType)
                }
            }
        }
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = columnsMap[tableName]!!
            val aggregate = resolveAggregateWithModule(tableName)

            // 创建 ImportManager
            val importManager = EnumImportManager()
            importManager.addBaseImports()

            columns.first { column ->
                if (SqlSchemaUtils.hasEnum(column) && !SqlSchemaUtils.isIgnore(column)) {
                    val enumType = SqlSchemaUtils.getType(column)
                    if (!typeMapping.containsKey(enumType)) {
                        currentEnumType = enumType
                        true
                    } else false
                } else false
            }

            val enumConfig = enumConfigMap[currentEnumType]!!

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
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(aggregate), refPackage("enums"))))

            resultContext.putContext(tag, "Enum", currentEnumType)

            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "EnumValueField", getString("enumValueField"))
            resultContext.putContext(tag, "EnumNameField", getString("enumNameField"))
            resultContext.putContext(tag, "EnumItems", enumItems)

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())

            return resultContext
        }
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(table: Map<String, Any?>): String {
        with(ctx) {
            val defaultEnumPackage = "enums"

            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(aggregate)

            return "$basePackage${templatePackage}${`package`}${refPackage(defaultEnumPackage)}${
                refPackage(
                    currentEnumType
                )
            }"
        }
    }

    context(ctx: AggregateContext)
    override fun generatorName(table: Map<String, Any?>): String {
        return currentEnumType
    }

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
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }
}


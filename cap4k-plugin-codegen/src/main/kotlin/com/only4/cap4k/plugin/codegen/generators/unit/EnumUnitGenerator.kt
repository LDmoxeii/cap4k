package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.EnumImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class EnumUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "enum"
    override val order: Int = 10

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@EnumUnitGenerator.tag
                name = "{{ Enum }}.kt"
                format = "resource"
                data = "templates/enum.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun collect(): List<GenerationUnit> {
        val units = mutableListOf<GenerationUnit>()
        val seen = mutableSetOf<String>()

        ctx.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach
            if (SqlSchemaUtils.hasRelation(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = ctx.columnsMap[tableName] ?: return@forEach
            val aggregate = ctx.resolveAggregateWithModule(tableName)

            columns.forEach { column ->
                if (!SqlSchemaUtils.hasEnum(column) || SqlSchemaUtils.isIgnore(column)) return@forEach

                val enumType = SqlSchemaUtils.getType(column)
                if (enumType.isBlank()) return@forEach
                if (!seen.add(enumType)) return@forEach
                if (ctx.typeMapping.containsKey(enumType)) return@forEach

                val enumConfig = ctx.enumConfigMap[enumType] ?: return@forEach
                val enumItems = enumConfig.toSortedMap().map { (value, arr) ->
                    mapOf(
                        "value" to value,
                        "name" to arr[0],
                        "desc" to arr[1],
                    )
                }

                val importManager = EnumImportManager()
                importManager.addBaseImports()

                val resultContext = ctx.baseMap.toMutableMap()
                with(ctx) {
                    resultContext.putContext(tag, "modulePath", domainPath)
                    resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                    resultContext.putContext(
                        tag,
                        "package",
                        refPackage(concatPackage(refPackage(aggregate), refPackage("enums")))
                    )

                    resultContext.putContext(tag, "Enum", enumType)
                    resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
                    resultContext.putContext(tag, "EnumValueField", getString("enumValueField"))
                    resultContext.putContext(tag, "EnumNameField", getString("enumNameField"))
                    resultContext.putContext(tag, "EnumItems", enumItems)
                    resultContext.putContext(tag, "imports", importManager.toImportLines())
                }

                val fullName = enumFullName(ctx, aggregate, enumType)
                units.add(
                    GenerationUnit(
                        id = "enum:${aggregate}:${enumType}",
                        tag = tag,
                        name = enumType,
                        order = order,
                        templateNodes = defaultTemplateNodes(),
                        context = resultContext,
                        exportTypes = mapOf(enumType to fullName),
                    )
                )
            }
        }

        return units
    }

    private fun enumFullName(
        ctx: AggregateContext,
        aggregate: String,
        enumType: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        val enumPkg = refPackage("enums")
        return "$basePackage${templatePackage}${pkg}${enumPkg}${refPackage(enumType)}"
    }
}

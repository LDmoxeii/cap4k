package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.TranslationImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toSnakeCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class EnumTranslationUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "translation"
    override val order: Int = 20

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@EnumTranslationUnitGenerator.tag
                name = "{{ EnumTranslation }}.kt"
                format = "resource"
                data = "templates/enum_translation.kt.peb"
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
                val enumName = AggregateNaming.enumName(enumType)

                val translationName = AggregateNaming.enumTranslationName(enumName)
                if (!seen.add(translationName)) return@forEach
                if (ctx.typeMapping.containsKey(translationName)) return@forEach

                val enumFullName = ctx.typeMapping[enumName] ?: enumFullName(ctx, aggregate, enumName)

                val importManager = TranslationImportManager()
                importManager.addBaseImports()
                importManager.add(enumFullName)

                val resultContext = ctx.baseMap.toMutableMap()
                with(ctx) {
                    resultContext.putContext(tag, "modulePath", adapterPath)
                    resultContext.putContext(
                        tag,
                        "templatePackage",
                        refPackage(templatePackage[tag] ?: refPackage("domain.translation"))
                    )
                    resultContext.putContext(tag, "package", refPackage(aggregate))
                    resultContext.putContext(tag, "Enum", enumName)
                    resultContext.putContext(tag, "EnumTranslation", translationName)

                    val snake = toSnakeCase(enumName) ?: enumName
                    val typeConst = "${snake}_code_to_desc".uppercase()
                    val typeValue = "${snake}_code_to_desc"
                    resultContext.putContext(tag, "TranslationTypeConst", typeConst)
                    resultContext.putContext(tag, "TranslationTypeValue", typeValue)
                    resultContext.putContext(tag, "EnumNameField", getString("enumNameField"))

                    val fullName = translationFullName(ctx, aggregate, translationName)
                    importManager.add("${fullName}.Companion.${typeConst}")
                    resultContext.putContext(tag, "imports", importManager.toImportLines())

                    val deps = if (ctx.typeMapping.containsKey(enumName)) {
                        emptyList()
                    } else {
                        listOf("enum:${aggregate}:${enumName}")
                    }

                    units.add(
                        GenerationUnit(
                            id = "enumTranslation:${aggregate}:${enumName}",
                            tag = tag,
                            name = translationName,
                            order = order,
                            deps = deps,
                            templateNodes = defaultTemplateNodes(),
                            context = resultContext,
                            exportTypes = mapOf(translationName to fullName),
                        )
                    )
                }
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
        val templatePackage = refPackage(ctx.templatePackage["enum"] ?: "")
        val pkg = refPackage(aggregate)
        val enumPkg = refPackage("enums")
        return "$basePackage${templatePackage}${pkg}${enumPkg}${refPackage(enumType)}"
    }

    private fun translationFullName(
        ctx: AggregateContext,
        aggregate: String,
        translationName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: refPackage("domain.translation"))
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(translationName)}"
    }
}

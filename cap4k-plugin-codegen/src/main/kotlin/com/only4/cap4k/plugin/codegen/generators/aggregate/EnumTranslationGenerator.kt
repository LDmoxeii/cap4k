package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.TranslationImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toSnakeCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class EnumTranslationGenerator : AggregateGenerator {
    override val tag: String = "translation"
    override val order: Int = 20

    @Volatile
    private lateinit var currentType: String

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        with(ctx) {
            if (SqlSchemaUtils.isIgnore(table)) return false
            if (SqlSchemaUtils.hasRelation(table)) return false

            val tableName = SqlSchemaUtils.getTableName(table)
            val columns = columnsMap[tableName] ?: return false

            // 是否存在尚未生成翻译器的枚举
            val enumTranslationType =
                columns.filter { column -> SqlSchemaUtils.hasEnum(column) && !(SqlSchemaUtils.isIgnore(column)) }
                    .map { column ->
                        val enumType = SqlSchemaUtils.getType(column)
                        "${enumType}Translation"
                    }
                    .firstOrNull { currentEnumType ->
                        currentEnumType.isNotBlank() && !typeMapping.containsKey(currentEnumType)
                    }

            if (enumTranslationType == null) return false

            currentType = enumTranslationType
            return true
        }
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)

            val importManager = TranslationImportManager()
            importManager.addBaseImports()

            // 引入枚举类型
            val enumType = currentType.replace("Translation", "")
            typeMapping[enumType]?.let { importManager.add(it) }

            val resultContext = baseMap.toMutableMap()

            resultContext.putContext(tag, "modulePath", adapterPath)
            resultContext.putContext(
                tag,
                "templatePackage",
                refPackage(templatePackage[tag] ?: refPackage("domain.translation"))
            )
            resultContext.putContext(tag, "package", refPackage(aggregate))

            // 枚举与翻译类名
            resultContext.putContext(tag, "Enum", enumType)
            resultContext.putContext(tag, "EnumTranslation", currentType)

            // 常量名与值：VIDEO_STATUS_CODE_TO_DESC / "video_status_code_to_desc"
            val snake = toSnakeCase(enumType) ?: enumType
            val typeConst = "${snake}_code_to_desc".uppercase()
            val typeValue = "${snake}_code_to_desc"
            resultContext.putContext(tag, "TranslationTypeConst", typeConst)
            resultContext.putContext(tag, "TranslationTypeValue", typeValue)

            // 枚举描述字段，如 desc
            resultContext.putContext(tag, "EnumNameField", getString("enumNameField"))

            // imports
            importManager.add("${generatorFullName(table)}.Companion.${typeConst}")
            resultContext.putContext(tag, "imports", importManager.toImportLines())

            return resultContext
        }
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(table: Map<String, Any?>): String {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: refPackage("domain.translation"))
            val `package` = refPackage(aggregate)
            return "$basePackage${templatePackage}${`package`}${refPackage(currentType)}"
        }
    }

    override fun generatorName(): String = currentType

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@EnumTranslationGenerator.tag
                name = "{{ EnumTranslation }}.kt"
                format = "resource"
                data = "templates/enum_translation.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[currentType] = generatorFullName(table)
    }
}


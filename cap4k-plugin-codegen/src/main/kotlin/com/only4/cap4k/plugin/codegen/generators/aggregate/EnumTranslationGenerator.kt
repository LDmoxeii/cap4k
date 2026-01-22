package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toSnakeCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class EnumTranslationGenerator : AggregateGenerator {
    override val tag: String = "translation"
    override val order: Int = 20

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

            val aggregate = resolveAggregateWithModule(tableName)

            currentType = enumTranslationType
            currentFullName = resolveFullName(this, aggregate)
            return true
        }
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)

            // 引入枚举类型
            val enumType = currentType.replace("Translation", "")
            val fullEnumType = typeMapping[enumType]
                ?: enumPackageMap[enumType]?.let { "$it.$enumType" }
                ?: enumType

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
            resultContext.putContext(tag, "EnumType", fullEnumType)

            // 常量名与值：VIDEO_STATUS_CODE_TO_DESC / "video_status_code_to_desc"
            val snake = toSnakeCase(enumType) ?: enumType
            val typeConst = "${snake}_code_to_desc".uppercase()
            val typeValue = "${snake}_code_to_desc"
            resultContext.putContext(tag, "TranslationTypeConst", typeConst)
            resultContext.putContext(tag, "TranslationTypeValue", typeValue)

            // 枚举描述字段，如 desc
            resultContext.putContext(tag, "EnumNameField", getString("enumNameField"))

            resultContext.putContext(tag, "TranslationTypeImport", "${generatorFullName()}.Companion.${typeConst}")

            return resultContext
        }
    }

    override fun generatorFullName(): String = currentFullName

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
        ctx.typeMapping[currentType] = generatorFullName()
    }

    private fun resolveFullName(ctx: AggregateContext, aggregate: String): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: refPackage("domain.translation"))
        val `package` = refPackage(aggregate)
        return "$basePackage${templatePackage}${`package`}${refPackage(currentType)}"
    }
}


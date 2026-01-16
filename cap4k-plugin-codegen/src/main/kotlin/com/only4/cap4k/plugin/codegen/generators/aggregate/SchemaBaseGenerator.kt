package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.SchemaBaseImportManager
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * Schema 基类生成器
 * 生成包含 JPA Criteria API 辅助类的 Schema 基类
 */
class SchemaBaseGenerator : AggregateTemplateGenerator {
    override val tag = "schema_base"
    override val order = 10

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean
        = !ctx.typeMapping.containsKey(generatorName(table))

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val resultContext = ctx.baseMap.toMutableMap()

        // 创建 ImportManager
        val importManager = SchemaBaseImportManager()
        importManager.addBaseImports()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", "")

            resultContext.putContext(tag, "SchemaBase", generatorName(table))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(
        table: Map<String, Any?>
    ): String {
        with(ctx) {
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = ""

            return "$basePackage$templatePackage$`package`${refPackage(generatorName(table))}"
        }
    }

    context(ctx: AggregateContext)
    override fun generatorName(
        table: Map<String, Any?>
    ): String = "Schema"

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SchemaBaseGenerator.tag
                name = "{{ SchemaBase }}.kt"
                format = "resource"
                data = "templates/schema_base.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }
}

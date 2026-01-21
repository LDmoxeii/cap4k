package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.SchemaBaseImportManager
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class SchemaBaseUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "schema_base"
    override val order: Int = 10

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SchemaBaseUnitGenerator.tag
                name = "{{ SchemaBase }}.kt"
                format = "resource"
                data = "templates/schema_base.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun collect(): List<GenerationUnit> {
        val name = AggregateNaming.schemaBaseName()
        if (ctx.typeMapping.containsKey(name)) return emptyList()

        val importManager = SchemaBaseImportManager()
        importManager.addBaseImports()

        val resultContext = ctx.baseMap.toMutableMap()
        with(ctx) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", "")
            resultContext.putContext(tag, "SchemaBase", name)
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        val fullName = schemaBaseFullName(ctx, name)
        return listOf(
            GenerationUnit(
                id = "schemaBase",
                tag = tag,
                name = name,
                order = order,
                templateNodes = defaultTemplateNodes(),
                context = resultContext,
                exportTypes = mapOf(name to fullName),
            )
        )
    }

    private fun schemaBaseFullName(ctx: AggregateContext, name: String): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        return "$basePackage${templatePackage}${refPackage(name)}"
    }
}

package com.only4.cap4k.codegen.generators.design

import com.only4.cap4k.codegen.context.design.DesignContext
import com.only4.cap4k.codegen.context.design.models.CommandDesign
import com.only4.cap4k.codegen.imports.CommandImportManager
import com.only4.cap4k.codegen.misc.refPackage
import com.only4.cap4k.codegen.template.TemplateNode

class CommandGenerator : DesignTemplateGenerator {

    override val tag: String = "command"
    override val order: Int = 10

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean =
        !ctx.typeMapping.containsKey(generatorName(design))

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is CommandDesign) { "Design must be CommandDesign" }

        val resultContext = ctx.baseMap.toMutableMap()

        val importManager = CommandImportManager()
        importManager.addBaseImports()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Command", generatorName(design))
            resultContext.putContext(tag, "Comment", design.desc)

            val fieldContext = resolveRequestResponseFields(design, design.requestFields, design.responseFields)
            resultContext.putContext(tag, "requestFields", fieldContext.requestFieldsForTemplate)
            resultContext.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)
            importManager.add(*fieldContext.imports.toTypedArray())

            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    context(ctx: DesignContext)
    override fun generatorFullName(design: Any): String {
        require(design is CommandDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(design.`package`)

        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is CommandDesign)
        return design.className()
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@CommandGenerator.tag
                name = "{{ Command }}.kt"
                format = "resource"
                data = "templates/command.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        ctx.typeMapping[generatorName(design)] = generatorFullName(design)
    }
}


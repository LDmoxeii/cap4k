package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.CommandDesign
import com.only4.cap4k.plugin.codegen.imports.CommandImportManager
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class CommandGenerator : DesignGenerator {

    override val tag: String = "command"
    override val order: Int = 10
    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        require(design is CommandDesign)
        val name = design.className()
        if (ctx.typeMapping.containsKey(name)) return false
        currentType = name
        currentFullName = resolveFullName(ctx, design)
        return true
    }

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

            resultContext.putContext(tag, "Command", generatorName())
            resultContext.putContext(tag, "Comment", design.desc)

            val fieldContext = resolveRequestResponseFields(design, design.requestFields, design.responseFields)
            resultContext.putContext(tag, "requestFields", fieldContext.requestFieldsForTemplate)
            resultContext.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)
            importManager.add(*fieldContext.imports.toTypedArray())

            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    override fun generatorFullName(): String = currentFullName

    override fun generatorName(): String = currentType

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
        ctx.typeMapping[generatorName()] = generatorFullName()
    }

    private fun resolveFullName(ctx: DesignContext, design: CommandDesign): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(design.`package`)
        return "$basePackage$templatePackage$`package`${refPackage(generatorName())}"
    }
}


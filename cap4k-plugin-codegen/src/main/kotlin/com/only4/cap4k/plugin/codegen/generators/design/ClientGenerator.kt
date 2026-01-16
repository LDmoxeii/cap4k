package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.ClientDesign
import com.only4.cap4k.plugin.codegen.imports.ClientImportManager
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class ClientGenerator : DesignTemplateGenerator {

    override val tag: String = "client"
    override val order: Int = 10

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean =
        !ctx.typeMapping.containsKey(generatorName(design))

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is ClientDesign) { "Design must be ClientDesign" }

        val resultContext = ctx.baseMap.toMutableMap()

        val importManager = ClientImportManager()
        importManager.addBaseImports()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Client", generatorName(design))
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
        require(design is ClientDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(design.`package`)

        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is ClientDesign)
        return design.className()
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@ClientGenerator.tag
                name = "{{ Client }}.kt"
                format = "resource"
                data = "templates/client.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        ctx.typeMapping[generatorName(design)] = generatorFullName(design)
    }
}


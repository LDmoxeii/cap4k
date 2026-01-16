package com.only4.cap4k.codegen.generators.design

import com.only4.cap4k.codegen.context.design.DesignContext
import com.only4.cap4k.codegen.context.design.models.ClientDesign
import com.only4.cap4k.codegen.imports.ClientHandlerImportManager
import com.only4.cap4k.codegen.misc.refPackage
import com.only4.cap4k.codegen.template.TemplateNode

class ClientHandlerGenerator : DesignTemplateGenerator {

    override val tag: String = "client_handler"
    override val order: Int = 20

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is ClientDesign) return false
        if (ctx.typeMapping.containsKey(generatorName(design))) return false
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is ClientDesign) { "Design must be ClientDesign" }

        val resultContext = ctx.baseMap.toMutableMap()
        val clientName = design.className()
        val clientType = ctx.typeMapping[clientName]!!

        val importManager = ClientHandlerImportManager()
        importManager.addBaseImports()
        importManager.add(clientType)

        val fieldContext = resolveRequestResponseFields(design, design.requestFields, design.responseFields)

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.adapterPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Client", clientName)
            resultContext.putContext(tag, "Comment", design.desc)

            resultContext.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)

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
        val client = design.className()
        return "${client}Handler"
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@ClientHandlerGenerator.tag
                name = "{{ Client }}Handler.kt"
                format = "resource"
                data = "templates/client_handler.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        if (design is ClientDesign) {
            val fullName = generatorFullName(design)
            ctx.typeMapping[generatorName(design)] = fullName
        }
    }
}


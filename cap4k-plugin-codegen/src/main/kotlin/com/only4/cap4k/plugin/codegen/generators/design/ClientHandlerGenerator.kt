package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.ClientDesign
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class ClientHandlerGenerator : DesignGenerator {

    override val tag: String = "client_handler"
    override val order: Int = 20
    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is ClientDesign) return false
        val clientName = design.className()
        val handlerName = "${clientName}Handler"
        if (ctx.typeMapping.containsKey(handlerName)) return false
        currentType = handlerName
        currentFullName = resolveFullName(ctx, design)
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is ClientDesign) { "Design must be ClientDesign" }

        val resultContext = ctx.baseMap.toMutableMap()
        val clientName = design.className()
        val clientType = ctx.typeMapping[clientName]!!

        val fieldContext = resolveRequestResponseFields(design, design.requestFields, design.responseFields)

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.adapterPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Client", clientName)
            resultContext.putContext(tag, "ClientType", clientType)
            resultContext.putContext(tag, "Comment", design.desc)

            resultContext.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)
        }

        return resultContext
    }

    override fun generatorFullName(): String = currentFullName

    override fun generatorName(): String = currentType

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
            val fullName = generatorFullName()
            ctx.typeMapping[generatorName()] = fullName
        }
    }

    private fun resolveFullName(ctx: DesignContext, design: ClientDesign): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(design.`package`)
        return "$basePackage$templatePackage$`package`${refPackage(generatorName())}"
    }
}


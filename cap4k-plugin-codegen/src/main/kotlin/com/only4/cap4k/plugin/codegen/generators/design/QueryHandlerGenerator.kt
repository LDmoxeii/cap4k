package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.QueryDesign
import com.only4.cap4k.plugin.codegen.imports.QueryHandlerImportManager
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class QueryHandlerGenerator : DesignGenerator {

    override val tag: String = "query_handler"
    override val order: Int = 20
    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is QueryDesign) return false
        val queryName = design.className()
        val handlerName = "${queryName}Handler"
        if (ctx.typeMapping.containsKey(handlerName)) return false
        currentType = handlerName
        currentFullName = resolveFullName(ctx, design)
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is QueryDesign) { "Design must be QueryDesign" }

        val resultContext = ctx.baseMap.toMutableMap()
        val queryType = ctx.typeMapping[design.className()]!!

        val handlerQueryType = QueryHandlerImportManager.inferQueryType(design.name)

        val importManager = QueryHandlerImportManager(handlerQueryType)
        importManager.addBaseImports()
        importManager.add(queryType)

        val fieldContext = resolveRequestResponseFields(design, design.requestFields, design.responseFields)

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.adapterPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "QueryHandler", generatorName())
            resultContext.putContext(tag, "Query", design.className())

            resultContext.putContext(tag, "Comment", design.desc)
            resultContext.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)

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
                tag = this@QueryHandlerGenerator.tag
                pattern = "^(?!.*(List|list|Page|page)).*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/query_handler.kt.peb"
                conflict = "skip"
            },
            TemplateNode().apply {
                type = "file"
                tag = this@QueryHandlerGenerator.tag
                pattern = "^.*(List|list).*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/query_list_handler.kt.peb"
                conflict = "skip"
            },
            TemplateNode().apply {
                type = "file"
                tag = this@QueryHandlerGenerator.tag
                pattern = "^.*(Page|page).*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/query_page_handler.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        if (design is QueryDesign) {
            val fullName = generatorFullName()
            ctx.typeMapping[generatorName()] = fullName
        }
    }

    private fun resolveFullName(ctx: DesignContext, design: QueryDesign): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(design.`package`)
        return "$basePackage$templatePackage$`package`${refPackage(generatorName())}"
    }
}


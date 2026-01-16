package com.only4.cap4k.codegen.generators.design

import com.only4.cap4k.codegen.context.design.DesignContext
import com.only4.cap4k.codegen.context.design.models.QueryDesign
import com.only4.cap4k.codegen.imports.QueryHandlerImportManager
import com.only4.cap4k.codegen.misc.refPackage
import com.only4.cap4k.codegen.template.TemplateNode

class QueryHandlerGenerator : DesignTemplateGenerator {

    override val tag: String = "query_handler"
    override val order: Int = 20

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is QueryDesign) return false
        if (ctx.typeMapping.containsKey(generatorName(design))) return false
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

            resultContext.putContext(tag, "QueryHandler", generatorName(design))
            resultContext.putContext(tag, "Query", design.className())

            resultContext.putContext(tag, "Comment", design.desc)
            resultContext.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)

            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    context(ctx: DesignContext)
    override fun generatorFullName(design: Any): String {
        require(design is QueryDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(design.`package`)

        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is QueryDesign)
        val queryName = design.className()
        return "${queryName}Handler"
    }

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
            val fullName = generatorFullName(design)
            ctx.typeMapping[generatorName(design)] = fullName
        }
    }
}


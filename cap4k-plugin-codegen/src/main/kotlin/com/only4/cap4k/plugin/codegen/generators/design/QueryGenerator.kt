package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.QueryDesign
import com.only4.cap4k.plugin.codegen.imports.QueryImportManager
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class QueryGenerator : DesignTemplateGenerator {

    override val tag: String = "query"
    override val order: Int = 10

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

        // ������������ƶϲ�ѯ����
        val queryType = QueryImportManager.inferQueryType(design.name)

        // ���� ImportManager
        val importManager = QueryImportManager(queryType)
        importManager.addBaseImports()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Query", generatorName(design))
            resultContext.putContext(tag, "Comment", design.desc)

            val fieldContext = resolveRequestResponseFields(design, design.requestFields, design.responseFields)
            resultContext.putContext(tag, "requestFields", fieldContext.requestFieldsForTemplate)
            resultContext.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)
            importManager.add(*fieldContext.imports.toTypedArray())

            // ���� imports
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
        return design.className()
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@QueryGenerator.tag
                pattern = "^(?!.*(List|list|Page|page)).*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/query.kt.peb"
                conflict = "skip"
            },
            TemplateNode().apply {
                type = "file"
                tag = this@QueryGenerator.tag
                pattern = "^.*(List|list).*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/query_list.kt.peb"
                conflict = "skip"
            },
            TemplateNode().apply {
                type = "file"
                tag = this@QueryGenerator.tag
                pattern = "^.*(Page|page).*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/query_page.kt.peb"
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


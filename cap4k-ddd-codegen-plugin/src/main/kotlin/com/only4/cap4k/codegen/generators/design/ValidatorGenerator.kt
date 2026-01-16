package com.only4.cap4k.codegen.generators.design

import com.only4.cap4k.codegen.context.design.DesignContext
import com.only4.cap4k.codegen.context.design.models.ValidatorDesign
import com.only4.cap4k.codegen.misc.refPackage
import com.only4.cap4k.codegen.template.TemplateNode
import com.only4.cap4k.codegen.imports.ValidatorImportManager

class ValidatorGenerator : DesignTemplateGenerator {

    override val tag: String = "validator"
    override val order: Int = 10

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean = !ctx.typeMapping.containsKey(generatorName(design))

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is ValidatorDesign) { "Design must be ValidatorDesign" }

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag]!!))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Validator", generatorName(design))
            resultContext.putContext(tag, "Comment", design.desc)

            // 值类型（默认 Long）
            resultContext.putContext(tag, "ValueType", "Long")
            
            // imports via ImportManager
            val importManager = ValidatorImportManager().apply { addBaseImports() }
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    context(ctx: DesignContext)
    override fun generatorFullName(design: Any): String {
        require(design is ValidatorDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage("application")
        val `package` = refPackage("validater")
        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is ValidatorDesign)
        return design.className()
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@ValidatorGenerator.tag
                name = "{{ Validator }}.kt"
                format = "resource"
                data = "templates/validator.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        ctx.typeMapping[generatorName(design)] = generatorFullName(design)
    }
}

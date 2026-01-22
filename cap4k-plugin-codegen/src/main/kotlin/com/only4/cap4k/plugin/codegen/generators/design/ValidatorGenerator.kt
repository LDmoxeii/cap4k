package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.ValidatorDesign
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class ValidatorGenerator : DesignGenerator {

    override val tag: String = "validator"
    override val order: Int = 10
    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        require(design is ValidatorDesign)
        val name = design.className()
        if (ctx.typeMapping.containsKey(name)) return false
        currentType = name
        currentFullName = resolveFullName(ctx)
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is ValidatorDesign) { "Design must be ValidatorDesign" }

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag]!!))
            resultContext.putContext(tag, "package", refPackage(design.`package`))

            resultContext.putContext(tag, "Validator", generatorName())
            resultContext.putContext(tag, "Comment", design.desc)

            // 值类型（默认 Long）
            resultContext.putContext(tag, "ValueType", "Long")

        }

        return resultContext
    }

    override fun generatorFullName(): String = currentFullName

    override fun generatorName(): String = currentType

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
        ctx.typeMapping[generatorName()] = generatorFullName()
    }

    private fun resolveFullName(ctx: DesignContext): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage("application")
        val `package` = refPackage("validater")
        return "$basePackage$templatePackage$`package`${refPackage(generatorName())}"
    }
}

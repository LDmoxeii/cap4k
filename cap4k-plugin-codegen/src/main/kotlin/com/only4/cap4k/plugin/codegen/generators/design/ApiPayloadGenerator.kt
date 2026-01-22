package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.ApiPayloadDesign
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * 生成适配层 portal/api/payload 下的请求负载（单体/列表/分页）
 * 模板来源：resources/templates/api_payload_*.kt.peb
 */
class ApiPayloadGenerator : DesignGenerator {

    override val tag: String = "api_payload"
    override val order: Int = 10
    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is ApiPayloadDesign) return false
        // 避免重复生成（按名称唯一）
        val name = design.className()
        if (ctx.typeMapping.containsKey(name)) return false
        currentType = name
        currentFullName = resolveFullName(ctx, design)
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is ApiPayloadDesign) { "Design must be ApiPayloadDesign" }

        val result = ctx.baseMap.toMutableMap()

        with(ctx) {
            // 输出到 adapter 模块
            result.putContext(tag, "modulePath", ctx.adapterPath)
            result.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: "adapter.portal.api.payload"))
            result.putContext(tag, "package", refPackage(design.`package`))

            result.putContext(tag, "Payload", generatorName())
            result.putContext(tag, "Comment", design.desc)

            // 字段解析与类型推断
            val fieldContext = resolveRequestResponseFields(design, design.requestFields, design.responseFields)
            result.putContext(tag, "requestFields", fieldContext.requestFieldsForTemplate)
            result.putContext(tag, "responseFields", fieldContext.responseFieldsForTemplate)
            result.putContext(tag, "nestedTypes", fieldContext.nestedTypesForTemplate)
        }

        return result
    }

    override fun generatorFullName(): String = currentFullName

    override fun generatorName(): String = currentType

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            // 单对象（非 List/Page）
            TemplateNode().apply {
                type = "file"
                tag = this@ApiPayloadGenerator.tag
                pattern = "^(?!.*(List|list|Page|page)).*$"
                name = "{{ Payload }}.kt"
                format = "resource"
                data = "templates/api_payload_single.kt.peb"
                conflict = "skip"
            },
            // 列表
            TemplateNode().apply {
                type = "file"
                tag = this@ApiPayloadGenerator.tag
                pattern = "^.*(List|list).*$"
                name = "{{ Payload }}.kt"
                format = "resource"
                data = "templates/api_payload_list.kt.peb"
                conflict = "skip"
            },
            // 分页
            TemplateNode().apply {
                type = "file"
                tag = this@ApiPayloadGenerator.tag
                pattern = "^.*(Page|page).*$"
                name = "{{ Payload }}.kt"
                format = "resource"
                data = "templates/api_payload_page.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        if (design is ApiPayloadDesign) {
            val full = generatorFullName()
            ctx.typeMapping[generatorName()] = full
        }
    }

    private fun resolveFullName(ctx: DesignContext, design: ApiPayloadDesign): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(".adapter.portal.api.payload")
        val `package` = refPackage(design.`package`)
        return "$basePackage$templatePackage$`package`${refPackage(generatorName())}"
    }
}


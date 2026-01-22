package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.DomainEventDesign
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class DomainEventGenerator : DesignGenerator {

    private val logger = Logging.getLogger(DomainEventGenerator::class.java)

    override val tag: String = "domain_event"
    override val order: Int = 10
    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is DomainEventDesign) return false
        val name = design.className()
        if (ctx.typeMapping.containsKey(name)) return false
        currentType = name
        currentFullName = resolveFullName(ctx, design)
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is DomainEventDesign) { "Design must be DomainEventDesign" }

        val fullAggregateType = ctx.typeMapping[design.aggregate]!!

        val fieldContext = resolveRequestResponseFields(design, design.requestFields, design.responseFields)

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(design.`package`), refPackage("events"))))

            resultContext.putContext(tag, "Name", generatorName())
            resultContext.putContext(tag, "DomainEvent", generatorName())

            resultContext.putContext(tag, "Aggregate", design.aggregate)
            resultContext.putContext(tag, "AggregateType", fullAggregateType)
            resultContext.putContext(tag, "persist", design.persist.toString())

            resultContext.putContext(tag, "Comment", design.desc)

            resultContext.putContext(tag, "fields", fieldContext.requestFieldsForTemplate)
            resultContext.putContext(tag, "nestedTypes", fieldContext.nestedTypesForTemplate)
        }

        return resultContext
    }

    override fun generatorFullName(): String = currentFullName

    override fun generatorName(): String = currentType

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@DomainEventGenerator.tag
                name = "{{ DomainEvent }}.kt"
                format = "resource"
                data = "templates/domain_event.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        if (design is DomainEventDesign) {
            val fullName = generatorFullName()
            ctx.typeMapping[generatorName()] = fullName
            logger.lifecycle("Generated domain event: $fullName")
        }
    }

    private fun resolveFullName(ctx: DesignContext, design: DomainEventDesign): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(concatPackage(refPackage(design.`package`), refPackage("events")))
        return "$basePackage$templatePackage$`package`${refPackage(generatorName())}"
    }
}


package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.DomainEventDesign
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class DomainEventHandlerGenerator : DesignGenerator {

    private val logger = Logging.getLogger(DomainEventHandlerGenerator::class.java)

    override val tag: String = "domain_event_handler"
    override val order: Int = 20
    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is DomainEventDesign) return false
        val domainEventName = design.className()
        val handlerName = "${domainEventName}Subscriber"
        if (ctx.typeMapping.containsKey(handlerName)) return false
        currentType = handlerName
        currentFullName = resolveFullName(ctx, design)
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is DomainEventDesign) { "Design must be DomainEventDesign" }

        val domainEventName = design.className()
        val fullDomainEventType = ctx.typeMapping[domainEventName]!!

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(design.`package`))))

            resultContext.putContext(tag, "DomainEvent", domainEventName)
            resultContext.putContext(tag, "DomainEventType", fullDomainEventType)
            resultContext.putContext(tag, "DomainEventHandler", generatorName())
            resultContext.putContext(tag, "Name", generatorName())

            resultContext.putContext(tag, "Aggregate", design.aggregate)
            resultContext.putContext(tag, "AggregateVar", design.aggregate.replaceFirstChar { it.lowercase() })
            resultContext.putContext(tag, "AggregateRoot", design.aggregate)

            resultContext.putContext(tag, "Comment", design.desc)
        }

        return resultContext
    }

    override fun generatorFullName(): String = currentFullName

    override fun generatorName(): String = currentType

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@DomainEventHandlerGenerator.tag
                name = "{{ DomainEventHandler }}.kt"
                format = "resource"
                data = "templates/domain_event_handler.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: DesignContext)
    override fun onGenerated(design: Any) {
        if (design is DomainEventDesign) {
            val fullName = generatorFullName()
            ctx.typeMapping[generatorName()] = fullName
            logger.lifecycle("Generated domain event handler: $fullName")
        }
    }

    private fun resolveFullName(ctx: DesignContext, design: DomainEventDesign): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(concatPackage(refPackage(design.`package`), refPackage("events")))
        return "$basePackage$templatePackage$`package`${refPackage(generatorName())}"
    }
}

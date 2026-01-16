package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.DomainEventDesign
import com.only4.cap4k.plugin.codegen.imports.DomainEventHandlerImportManager
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.api.logging.Logging

class DomainEventHandlerGenerator : DesignTemplateGenerator {

    private val logger = Logging.getLogger(DomainEventHandlerGenerator::class.java)

    override val tag: String = "domain_event_handler"
    override val order: Int = 20

    context(ctx: DesignContext)
    override fun shouldGenerate(design: Any): Boolean {
        if (design !is DomainEventDesign) return false
        if (ctx.typeMapping.containsKey(generatorName(design))) return false
        return true
    }

    context(ctx: DesignContext)
    override fun buildContext(design: Any): Map<String, Any?> {
        require(design is DomainEventDesign) { "Design must be DomainEventDesign" }

        val domainEventName = design.className()
        val fullDomainEventType = ctx.typeMapping[domainEventName]!!

        // 创建 ImportManager
        val importManager = DomainEventHandlerImportManager()
        importManager.addBaseImports()
        importManager.add(fullDomainEventType)

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(design.`package`))))

            resultContext.putContext(tag, "DomainEvent", domainEventName)
            resultContext.putContext(tag, "DomainEventHandler", generatorName(design))
            resultContext.putContext(tag, "Name", generatorName(design))

            resultContext.putContext(tag, "Aggregate", design.aggregate)
            resultContext.putContext(tag, "AggregateVar", design.aggregate.replaceFirstChar { it.lowercase() })
            resultContext.putContext(tag, "AggregateRoot", design.aggregate)

            resultContext.putContext(tag, "Comment", design.desc)

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    context(ctx: DesignContext)
    override fun generatorFullName(design: Any): String {
        require(design is DomainEventDesign)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(concatPackage(refPackage(design.`package`), refPackage("events")))

        return "$basePackage$templatePackage$`package`${refPackage(generatorName(design))}"
    }

    context(ctx: DesignContext)
    override fun generatorName(design: Any): String {
        require(design is DomainEventDesign)
        val domainEventName = design.className()
        return "${domainEventName}Subscriber"
    }

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
            val fullName = generatorFullName(design)
            ctx.typeMapping[generatorName(design)] = fullName
            logger.lifecycle("Generated domain event handler: $fullName")
        }
    }
}

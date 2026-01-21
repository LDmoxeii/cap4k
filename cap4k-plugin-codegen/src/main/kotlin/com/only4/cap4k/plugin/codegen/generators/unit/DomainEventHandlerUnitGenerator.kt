package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.DomainEventHandlerImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class DomainEventHandlerUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "domain_event_handler"
    override val order: Int = 30

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@DomainEventHandlerUnitGenerator.tag
                name = "{{ DomainEventHandler }}.kt"
                format = "resource"
                data = "templates/domain_event_handler.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun collect(): List<GenerationUnit> {
        val units = mutableListOf<GenerationUnit>()
        val seen = mutableSetOf<String>()

        ctx.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach
            if (SqlSchemaUtils.hasRelation(table)) return@forEach
            if (!SqlSchemaUtils.isAggregateRoot(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = ctx.resolveAggregateWithModule(tableName)

            SqlSchemaUtils.getDomainEvents(table).forEach { eventInfo ->
                val eventNameRaw = eventInfo.split(":").firstOrNull() ?: return@forEach
                val eventName = AggregateNaming.domainEventName(eventNameRaw)
                val handlerName = AggregateNaming.domainEventHandlerName(eventName)
                val key = "$aggregate:$handlerName"
                if (!seen.add(key)) return@forEach
                if (ctx.typeMapping.containsKey(handlerName)) return@forEach

                val fullDomainEventType = ctx.typeMapping[eventName] ?: domainEventFullName(ctx, aggregate, eventName)

                val importManager = DomainEventHandlerImportManager()
                importManager.addBaseImports()
                importManager.add(fullDomainEventType)

                val resultContext = ctx.baseMap.toMutableMap()
                with(ctx) {
                    resultContext.putContext(tag, "modulePath", applicationPath)
                    resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                    resultContext.putContext(tag, "package", refPackage(aggregate))
                    resultContext.putContext(tag, "DomainEvent", eventName)
                    resultContext.putContext(tag, "DomainEventHandler", handlerName)
                    resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
                    resultContext.putContext(tag, "imports", importManager.toImportLines())
                }

                val deps = if (ctx.typeMapping.containsKey(eventName)) {
                    emptyList()
                } else {
                    listOf("domainEvent:${aggregate}:${eventName}")
                }

                val fullName = handlerFullName(ctx, aggregate, handlerName)
                units.add(
                    GenerationUnit(
                        id = "domainEventHandler:${aggregate}:${eventName}",
                        tag = tag,
                        name = handlerName,
                        order = order,
                        deps = deps,
                        templateNodes = defaultTemplateNodes(),
                        context = resultContext,
                        exportTypes = mapOf(handlerName to fullName),
                    )
                )
            }
        }

        return units
    }

    private fun domainEventFullName(
        ctx: AggregateContext,
        aggregate: String,
        eventName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage["domain_event"] ?: "")
        val pkg = refPackage(aggregate)
        val eventPkg = refPackage("events")
        return "$basePackage${templatePackage}${pkg}${eventPkg}${refPackage(eventName)}"
    }

    private fun handlerFullName(
        ctx: AggregateContext,
        aggregate: String,
        handlerName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(handlerName)}"
    }
}

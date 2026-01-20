package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.DomainEventImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class DomainEventUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "domain_event"
    override val order: Int = 30

    companion object {
        private const val DEFAULT_DOMAIN_EVENT_PACKAGE = "events"
    }

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@DomainEventUnitGenerator.tag
                name = "{{ DomainEvent }}.kt"
                format = "resource"
                data = "templates/domain_event.kt.peb"
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
            val entityType = ctx.entityTypeMap[tableName] ?: return@forEach
            val fullEntityType = ctx.typeMapping[entityType] ?: return@forEach

            SqlSchemaUtils.getDomainEvents(table).forEach { eventInfo ->
                val eventNameRaw = eventInfo.split(":").firstOrNull() ?: return@forEach
                val eventName = DomainEventNaming.eventClassName(eventNameRaw)
                val key = "$aggregate:$eventName"
                if (!seen.add(key)) return@forEach
                if (ctx.typeMapping.containsKey(eventName)) return@forEach

                val importManager = DomainEventImportManager()
                importManager.addBaseImports()
                importManager.add(fullEntityType)

                val resultContext = ctx.baseMap.toMutableMap()
                with(ctx) {
                    resultContext.putContext(tag, "modulePath", domainPath)
                    resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                    resultContext.putContext(
                        tag,
                        "package",
                        refPackage(concatPackage(refPackage(aggregate), refPackage(DEFAULT_DOMAIN_EVENT_PACKAGE)))
                    )

                    resultContext.putContext(tag, "DomainEvent", eventName)
                    resultContext.putContext(tag, "Entity", entityType)
                    resultContext.putContext(tag, "persist", getBoolean("domainEventPersist", false))
                    resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
                    resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
                    resultContext.putContext(tag, "imports", importManager.toImportLines())
                }

                val fullName = domainEventFullName(ctx, aggregate, eventName)
                units.add(
                    GenerationUnit(
                        id = "domainEvent:${aggregate}:${eventName}",
                        tag = tag,
                        name = eventName,
                        order = order,
                        templateNodes = defaultTemplateNodes(),
                        context = resultContext,
                        exportTypes = mapOf(eventName to fullName),
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
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        val eventPkg = refPackage(DEFAULT_DOMAIN_EVENT_PACKAGE)
        return "$basePackage${templatePackage}${pkg}${eventPkg}${refPackage(eventName)}"
    }
}

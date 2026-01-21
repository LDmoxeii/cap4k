package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.DomainEventHandlerImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import kotlin.collections.map
import kotlin.text.split

/**
 * 领域事件处理器文件生成器
 * 为聚合根生成领域事件订阅者（处理器）基类
 */
class DomainEventHandlerGenerator : AggregateGenerator {
    override val tag = "domain_event_handler"
    override val order = 40

    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    private fun generateDomainEventName(eventName: String): String =
        (toUpperCamelCase(eventName) ?: eventName).let { base ->
            if (base.endsWith("Event") || base.endsWith("Evt")) base else "${base}DomainEvent"
        }

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        val domainEvents = SqlSchemaUtils.getDomainEvents(table)

        val domainEventHandlerType = domainEvents.map { domainEventInfo ->
                val infos = domainEventInfo.split(":")
                val eventName = generateDomainEventName(infos[0])
                "${eventName}Subscriber"
            }.firstOrNull { currentDomainEvent ->
            currentDomainEvent.isNotBlank() && !(ctx.typeMapping.containsKey(currentDomainEvent))
        }

        if (domainEventHandlerType == null) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)

        currentType = domainEventHandlerType
        currentFullName = resolveFullName(ctx, aggregate)
        return true
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)

        val domainEvent = currentType.replace("Subscriber", "")
        val fullDomainEventType = ctx.typeMapping[domainEvent]!!

        // 创建 ImportManager
        val importManager = DomainEventHandlerImportManager()
        importManager.addBaseImports()
        importManager.add(fullDomainEventType)

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "DomainEvent", domainEvent)

            resultContext.putContext(tag, "DomainEventHandler", currentType)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
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
                conflict = "skip" // 事件处理器包含业务逻辑，不覆盖已有文件
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[currentType] = generatorFullName()
    }

    private fun resolveFullName(ctx: AggregateContext, aggregate: String): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(aggregate)
        return "$basePackage${templatePackage}${`package`}${refPackage(currentType)}"
    }
}

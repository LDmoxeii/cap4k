package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.AggregateImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * 聚合封装类生成器
 * 为聚合根生成聚合封装类，用于管理聚合根及其相关操作
 */
class AggregateGenerator : AggregateTemplateGenerator {
    override val tag = "aggregate"
    override val order = 40

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false
        if (!ctx.getBoolean("generateAggregate", false)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        return !ctx.typeMapping.containsKey(generatorName(table))
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = ctx.columnsMap[tableName]
        val aggregate = ctx.resolveAggregateWithModule(tableName)

        val entityType = ctx.entityTypeMap[tableName]!!
        val factoryType = "${entityType}Factory"
        val fullFactoryType = ctx.typeMapping[factoryType]!!

        val ids = columns!!.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

        // 创建 ImportManager
        val importManager = AggregateImportManager()
        importManager.addBaseImports()
        importManager.add(fullFactoryType)

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", ctx.domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(ctx.templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(refPackage(aggregate)))

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "IdentityType", identityType)

            resultContext.putContext(tag, "AggregateName", generatorName(table))

            resultContext.putContext(tag, "Factory", factoryType)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            // 添加 imports
            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }

        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(
        table: Map<String, Any?>
    ): String {
        return with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val entityType = entityTypeMap[tableName]!!

            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(aggregate)

            val aggregateTypeTemplate = getString("aggregateTypeTemplate")
            val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

            "$basePackage${templatePackage}${`package`}${refPackage(aggregateType)}"

        }
    }


    context(ctx: AggregateContext)
    override fun generatorName(
        table: Map<String, Any?>
    ): String {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val entityType = entityTypeMap[tableName]!!

            val aggregateTypeTemplate = getString("aggregateTypeTemplate")
            val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

            return aggregateType
        }
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@AggregateGenerator.tag
                name = "{{ AggregateName }}.kt"
                format = "resource"
                data = "templates/aggregate.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }
}

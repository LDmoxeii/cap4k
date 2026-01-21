package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.FactoryImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * Factory 文件生成器
 * 为聚合根生成工厂类
 */
class FactoryGenerator : EntityGenerator() {
    override val tag = "factory"
    override val order = 30

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        if (!(SqlSchemaUtils.hasFactory(table)) && ctx.getBoolean("generateAggregate", false)) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val columns = ctx.columnsMap[tableName] ?: return false
        val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
        if (ids.isEmpty()) return false

        if (ctx.typeMapping.containsKey(generatorName(table))) return false

        return true
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)

        val entityType = ctx.entityTypeMap[tableName]!!
        val fullEntityType = ctx.typeMapping[entityType]!!

        // 创建 ImportManager
        val importManager = FactoryImportManager()
        importManager.addBaseImports()
        importManager.add(fullEntityType)

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(aggregate), refPackage(tag))))

            resultContext.putContext(tag, "Factory", generatorName(table))
            resultContext.putContext(tag, "Payload", "${entityType}Payload")

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)

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
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(aggregate)

            val fullFactoryType =
                "$basePackage${templatePackage}${`package`}${refPackage(tag)}${refPackage(generatorName(table))}"
            return fullFactoryType
        }
    }

    context(ctx: AggregateContext)
    override fun generatorName(table: Map<String, Any?>): String = "${super.generatorName(table)}Factory"

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@FactoryGenerator.tag
                name = "{{ Factory }}.kt"
                format = "resource"
                data = "templates/factory.kt.peb"
                conflict = "skip" // Factory 通常包含业务逻辑，不覆盖已有文件
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }

}


package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.SpecificationImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * Specification 文件生成器
 * 为每个实体生成规约（Specification）基类
 */
class SpecificationGenerator : EntityGenerator() {
    override val tag = "specification"
    override val order = 30

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        if (!(SqlSchemaUtils.hasSpecification(table)) && ctx.getBoolean("generateAggregate")) return false

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

        val resultContext = ctx.baseMap.toMutableMap()

        // 创建 ImportManager
        val importManager = SpecificationImportManager()
        importManager.addBaseImports()
        importManager.add(fullEntityType)

        with(ctx) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(concatPackage(refPackage(aggregate), refPackage(tag))))

            resultContext.putContext(tag, "Specification", generatorName(table))

            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "fullEntityType", fullEntityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            resultContext.putContext(tag, "imports", importManager.toImportLines())
        }


        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(table: Map<String, Any?>): String {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(aggregate)

            return "$basePackage${templatePackage}${`package`}${refPackage(tag)}${refPackage(generatorName(table))}"
        }
    }

    context(ctx: AggregateContext)
    override fun generatorName(table: Map<String, Any?>): String = "${super.generatorName(table)}Specification"

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SpecificationGenerator.tag
                name = "{{ DEFAULT_SPEC_PACKAGE }}{{ SEPARATOR }}{{ Specification }}.kt"
                format = "resource"
                data = "templates/specification.kt.peb"
                conflict = "skip" // Specification 通常包含业务逻辑，不覆盖已有文件
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }
}


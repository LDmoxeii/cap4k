package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.AggregateImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class AggregateWrapperUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "aggregate"
    override val order: Int = 40

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@AggregateWrapperUnitGenerator.tag
                name = "{{ AggregateName }}.kt"
                format = "resource"
                data = "templates/aggregate.kt.peb"
                conflict = "skip"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun collect(): List<GenerationUnit> {
        val units = mutableListOf<GenerationUnit>()

        ctx.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach
            if (SqlSchemaUtils.hasRelation(table)) return@forEach
            if (!ctx.getBoolean("generateAggregate", false)) return@forEach
            if (!SqlSchemaUtils.isAggregateRoot(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val entityTypeRaw = ctx.entityTypeMap[tableName] ?: return@forEach
            val entityType = AggregateNaming.entityName(entityTypeRaw)
            val aggregate = ctx.resolveAggregateWithModule(tableName)

            val aggregateName = AggregateNaming.aggregateName(ctx, entityType)
            if (ctx.typeMapping.containsKey(aggregateName)) return@forEach

            val columns = ctx.columnsMap[tableName] ?: return@forEach
            val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
            val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

            val factoryName = AggregateNaming.factoryName(entityType)
            val fullFactoryType = ctx.typeMapping[factoryName] ?: factoryFullName(ctx, aggregate, factoryName)

            val importManager = AggregateImportManager()
            importManager.addBaseImports()
            importManager.add(fullFactoryType)

            val resultContext = ctx.baseMap.toMutableMap()
            with(ctx) {
                resultContext.putContext(tag, "modulePath", domainPath)
                resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                resultContext.putContext(tag, "package", refPackage(refPackage(aggregate)))

                resultContext.putContext(tag, "Entity", entityType)
                resultContext.putContext(tag, "IdentityType", identityType)
                resultContext.putContext(tag, "AggregateName", aggregateName)
                resultContext.putContext(tag, "Factory", factoryName)
                resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
                resultContext.putContext(tag, "imports", importManager.toImportLines())
            }

            val deps = if (ctx.typeMapping.containsKey(factoryName)) {
                emptyList()
            } else {
                listOf("factory:$aggregate:$factoryName")
            }

            val fullName = aggregateFullName(ctx, aggregate, aggregateName)
            units.add(
                GenerationUnit(
                    id = "aggregate:$aggregate:$aggregateName",
                    tag = tag,
                    name = aggregateName,
                    order = order,
                    deps = deps,
                    templateNodes = defaultTemplateNodes(),
                    context = resultContext,
                    exportTypes = mapOf(aggregateName to fullName),
                )
            )
        }

        return units
    }

    private fun factoryFullName(
        ctx: AggregateContext,
        aggregate: String,
        factoryName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage["factory"] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage("factory")}${refPackage(factoryName)}"
    }

    private fun aggregateFullName(
        ctx: AggregateContext,
        aggregate: String,
        aggregateName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(aggregateName)}"
    }
}
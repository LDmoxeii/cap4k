package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.FactoryImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class FactoryUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "factory"
    override val order: Int = 30

    companion object {
        private const val DEFAULT_FAC_PACKAGE = "factory"
    }

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@FactoryUnitGenerator.tag
                name = "{{ Factory }}.kt"
                format = "resource"
                data = "templates/factory.kt.peb"
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
            if (!SqlSchemaUtils.isAggregateRoot(table)) return@forEach
            if (!SqlSchemaUtils.hasFactory(table) && ctx.getBoolean("generateAggregate")) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val entityTypeRaw = ctx.entityTypeMap[tableName] ?: return@forEach
            val entityType = AggregateNaming.entityName(entityTypeRaw)
            val columns = ctx.columnsMap[tableName] ?: return@forEach
            val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
            if (ids.isEmpty()) return@forEach

            val factoryName = AggregateNaming.factoryName(entityType)
            if (ctx.typeMapping.containsKey(factoryName)) return@forEach

            val aggregate = ctx.resolveAggregateWithModule(tableName)
            val fullEntityType = ctx.typeMapping[entityType] ?: entityFullName(ctx, aggregate, entityType)

            val importManager = FactoryImportManager()
            importManager.addBaseImports()
            importManager.add(fullEntityType)

            val resultContext = ctx.baseMap.toMutableMap()
            with(ctx) {
                resultContext.putContext(tag, "modulePath", domainPath)
                resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                resultContext.putContext(
                    tag,
                    "package",
                    refPackage(concatPackage(refPackage(aggregate), refPackage(DEFAULT_FAC_PACKAGE)))
                )

                resultContext.putContext(tag, "Factory", factoryName)
                resultContext.putContext(tag, "Payload", AggregateNaming.factoryPayloadName(entityType))
                resultContext.putContext(tag, "Entity", entityType)
                resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
                resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
                resultContext.putContext(tag, "imports", importManager.toImportLines())
            }

            val deps = if (ctx.typeMapping.containsKey(entityType)) {
                emptyList()
            } else {
                listOf("entity:$aggregate:$entityType")
            }

            val fullName = factoryFullName(ctx, aggregate, factoryName)
            units.add(
                GenerationUnit(
                    id = "factory:$aggregate:$factoryName",
                    tag = tag,
                    name = factoryName,
                    order = order,
                    deps = deps,
                    templateNodes = defaultTemplateNodes(),
                    context = resultContext,
                    exportTypes = mapOf(factoryName to fullName),
                )
            )
        }

        return units
    }

    private fun entityFullName(
        ctx: AggregateContext,
        aggregate: String,
        entityType: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage["entity"] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(entityType)}"
    }

    private fun factoryFullName(
        ctx: AggregateContext,
        aggregate: String,
        factoryName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(DEFAULT_FAC_PACKAGE)}${refPackage(factoryName)}"
    }
}
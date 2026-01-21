package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.SpecificationImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class SpecificationUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "specification"
    override val order: Int = 30

    companion object {
        private const val DEFAULT_SPEC_PACKAGE = "specs"
    }

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SpecificationUnitGenerator.tag
                name = "{{ DEFAULT_SPEC_PACKAGE }}{{ SEPARATOR }}{{ Specification }}.kt"
                format = "resource"
                data = "templates/specification.kt.peb"
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
            if (!SqlSchemaUtils.hasSpecification(table) && ctx.getBoolean("generateAggregate")) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val entityTypeRaw = ctx.entityTypeMap[tableName] ?: return@forEach
            val entityType = AggregateNaming.entityName(entityTypeRaw)
            val columns = ctx.columnsMap[tableName] ?: return@forEach
            val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
            if (ids.isEmpty()) return@forEach

            val specName = AggregateNaming.specificationName(entityType)
            if (ctx.typeMapping.containsKey(specName)) return@forEach

            val aggregate = ctx.resolveAggregateWithModule(tableName)
            val fullEntityType = ctx.typeMapping[entityType] ?: entityFullName(ctx, aggregate, entityType)

            val importManager = SpecificationImportManager()
            importManager.addBaseImports()
            importManager.add(fullEntityType)

            val resultContext = ctx.baseMap.toMutableMap()
            with(ctx) {
                resultContext.putContext(tag, "modulePath", domainPath)
                resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                resultContext.putContext(
                    tag,
                    "package",
                    refPackage(concatPackage(refPackage(aggregate), refPackage(DEFAULT_SPEC_PACKAGE)))
                )

                resultContext.putContext(tag, "Specification", specName)
                resultContext.putContext(tag, "Entity", entityType)
                resultContext.putContext(tag, "fullEntityType", fullEntityType)
                resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
                resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
                resultContext.putContext(tag, "imports", importManager.toImportLines())
            }

            val deps = if (ctx.typeMapping.containsKey(entityType)) {
                emptyList()
            } else {
                listOf("entity:$aggregate:$entityType")
            }

            val fullName = specificationFullName(ctx, aggregate, specName)
            units.add(
                GenerationUnit(
                    id = "specification:$aggregate:$specName",
                    tag = tag,
                    name = specName,
                    order = order,
                    deps = deps,
                    templateNodes = defaultTemplateNodes(),
                    context = resultContext,
                    exportTypes = mapOf(specName to fullName),
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

    private fun specificationFullName(
        ctx: AggregateContext,
        aggregate: String,
        specName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(DEFAULT_SPEC_PACKAGE)}${refPackage(specName)}"
    }
}
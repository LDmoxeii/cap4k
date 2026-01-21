package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.RepositoryImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class RepositoryUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "repository"
    override val order: Int = 30

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@RepositoryUnitGenerator.tag
                name = "{{ Repository }}.kt"
                format = "resource"
                data = "templates/repository.kt.peb"
                conflict = "overwrite"
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

            val tableName = SqlSchemaUtils.getTableName(table)
            val entityTypeRaw = ctx.entityTypeMap[tableName] ?: return@forEach
            val entityType = AggregateNaming.entityName(entityTypeRaw)

            val repositoryName = AggregateNaming.repositoryName(ctx, entityType)
            if (ctx.typeMapping.containsKey(repositoryName)) return@forEach

            val columns = ctx.columnsMap[tableName] ?: return@forEach
            val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
            val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

            val fullEntityType = ctx.typeMapping[entityType] ?: entityFullName(ctx, table, entityType)

            val imports = RepositoryImportManager()
            imports.addBaseImports()
            imports.add(fullEntityType)

            val fullIdType = ctx.typeMapping[identityType]
            if (fullIdType != null) {
                imports.add(fullIdType)
            }

            val supportQuerydsl = ctx.getBoolean("repositorySupportQuerydsl")
            if (supportQuerydsl) {
                imports.add("org.springframework.data.querydsl.QuerydslPredicateExecutor")
                imports.add("com.only4.cap4k.ddd.domain.repo.querydsl.AbstractQuerydslRepository")
            }

            val resultContext = ctx.baseMap.toMutableMap()
            with(ctx) {
                resultContext.putContext(tag, "modulePath", adapterPath)
                resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                resultContext.putContext(tag, "package", "")

                resultContext.putContext(tag, "supportQuerydsl", supportQuerydsl)
                resultContext.putContext(tag, "imports", imports.toImportLines())
                resultContext.putContext(tag, "Aggregate", entityType)
                resultContext.putContext(tag, "IdentityType", identityType)
                resultContext.putContext(tag, "Repository", repositoryName)

                resultContext.putContext(tag, "Comment", "Repository for $entityType aggregate")
            }

            val deps = if (ctx.typeMapping.containsKey(entityType)) {
                emptyList()
            } else {
                val aggregate = ctx.resolveAggregateWithModule(tableName)
                listOf("entity:$aggregate:$entityType")
            }

            val fullName = repositoryFullName(ctx, repositoryName)
            units.add(
                GenerationUnit(
                    id = "repository:$repositoryName",
                    tag = tag,
                    name = repositoryName,
                    order = order,
                    deps = deps,
                    templateNodes = defaultTemplateNodes(),
                    context = resultContext,
                    exportTypes = mapOf(repositoryName to fullName),
                )
            )
        }

        return units
    }

    context(ctx: AggregateContext)
    private fun entityFullName(
        table: Map<String, Any?>,
        entityType: String,
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage["entity"] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(entityType)}"
    }

    private fun repositoryFullName(ctx: AggregateContext, repositoryName: String): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        return "$basePackage${templatePackage}${refPackage(repositoryName)}"
    }
}
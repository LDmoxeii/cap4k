package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.QueryHandlerImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toLowerCamelCase
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class UniqueQueryHandlerUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "query_handler"
    override val order: Int = 20

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueQueryHandlerUnitGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/unique_query_handler.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun collect(): List<GenerationUnit> {
        val units = mutableListOf<GenerationUnit>()
        val seen = mutableSetOf<String>()

        ctx.tableMap.values.forEach { table ->
            if (SqlSchemaUtils.isIgnore(table)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val entityTypeRaw = ctx.entityTypeMap[tableName] ?: return@forEach
            val entityType = AggregateNaming.entityName(entityTypeRaw)
            val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
            if (constraints.isEmpty()) return@forEach

            val columns = ctx.columnsMap[tableName] ?: return@forEach
            val deletedField = ctx.getString("deletedField")
            val aggregate = ctx.resolveAggregateWithModule(tableName)

            constraints.forEach { cons ->
                val suffix = AggregateNaming.uniqueConstraintSuffix(cons, deletedField)
                val queryName = AggregateNaming.uniqueQueryName(entityType, suffix)
                if (queryName.isBlank()) return@forEach
                val handlerName = AggregateNaming.uniqueQueryHandlerName(queryName)
                val key = "$aggregate:$handlerName"
                if (!seen.add(key)) return@forEach
                if (ctx.typeMapping.containsKey(handlerName)) return@forEach

                val queryFullName = ctx.typeMapping[queryName] ?: queryFullName(ctx, aggregate, queryName)

                val whereProps = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
                    .map { it["columnName"].toString() }
                    .filter { !it.equals(deletedField, ignoreCase = true) }
                    .map { colName -> toLowerCamelCase(colName) ?: colName }

                val idColumn = columns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
                val idPropName = idColumn?.let { toLowerCamelCase(SqlSchemaUtils.getColumnName(it)) } ?: "id"
                val excludeIdParamName = "exclude${entityType}Id"

                val importManager = QueryHandlerImportManager(QueryHandlerImportManager.QueryType.SINGLE).apply {
                    addBaseImports()
                    add(queryFullName)
                }
                importManager.add(
                    "org.babyfish.jimmer.sql.kt.KSqlClient",
                    "org.babyfish.jimmer.sql.kt.ast.expression.eq",
                    "org.babyfish.jimmer.sql.kt.ast.expression.`ne?`",
                    "org.babyfish.jimmer.sql.kt.exists",
                )

                val basePackage = ctx.getString("basePackage")
                val shareModelPkg = basePackage + refPackage(ctx.templatePackage["query"] ?: "") + "._share.model"
                importManager.add("$shareModelPkg.$entityType")
                whereProps.forEach { prop -> importManager.add("$shareModelPkg.$prop") }
                importManager.add("$shareModelPkg.$idPropName")

                val resultContext = ctx.baseMap.toMutableMap()
                with(ctx) {
                    resultContext.putContext(tag, "modulePath", adapterPath)
                    resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                    resultContext.putContext(tag, "package", refPackage(aggregate))

                    resultContext.putContext(tag, "QueryHandler", handlerName)
                    resultContext.putContext(tag, "Query", queryName)
                    resultContext.putContext(tag, "Entity", entityType)
                    resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
                    resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

                    resultContext.putContext(tag, "imports", importManager.toImportLines())

                    resultContext.putContext(tag, "WhereProps", whereProps)
                    resultContext.putContext(tag, "IdPropName", idPropName)
                    resultContext.putContext(tag, "ExcludeIdParamName", excludeIdParamName)
                }

                val deps = if (ctx.typeMapping.containsKey(queryName)) {
                    emptyList()
                } else {
                    listOf("uniqueQuery:$aggregate:$queryName")
                }

                val fullName = handlerFullName(ctx, aggregate, handlerName)
                units.add(
                    GenerationUnit(
                        id = "uniqueQueryHandler:$aggregate:$handlerName",
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

    private fun queryFullName(
        ctx: AggregateContext,
        aggregate: String,
        queryName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage["query"] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(queryName)}"
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
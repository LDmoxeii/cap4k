package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.QueryImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toLowerCamelCase
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class UniqueQueryUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "query"
    override val order: Int = 20

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueQueryUnitGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/unique_query.kt.peb"
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
                val key = "$aggregate:$queryName"
                if (!seen.add(key)) return@forEach
                if (ctx.typeMapping.containsKey(queryName)) return@forEach

                val resultContext = buildContext(table, cons, columns, entityType, aggregate, queryName)
                val fullName = queryFullName(ctx, aggregate, queryName)

                units.add(
                    GenerationUnit(
                        id = "uniqueQuery:$aggregate:$queryName",
                        tag = tag,
                        name = queryName,
                        order = order,
                        templateNodes = defaultTemplateNodes(),
                        context = resultContext,
                        exportTypes = mapOf(queryName to fullName),
                    )
                )
            }
        }

        return units
    }

    context(ctx: AggregateContext)
    private fun buildContext(
        table: Map<String, Any?>,
        constraint: Map<String, Any?>,
        columns: List<Map<String, Any?>>,
        entityType: String,
        aggregate: String,
        queryName: String,
    ): Map<String, Any?> {
        val resultContext = ctx.baseMap.toMutableMap()
        val deletedField = ctx.getString("deletedField")

        val extraImports = mutableSetOf<String>()

        fun addTypeImportIfNeeded(colMeta: Map<String, Any?>, typeName: String) {
            val simple = typeName.removeSuffix("?")
            if (SqlSchemaUtils.hasType(colMeta)) {
                val mapped = ctx.typeMapping[simple]
                if (!mapped.isNullOrBlank()) {
                    extraImports += mapped
                } else {
                    val enumPkg = ctx.enumPackageMap[simple]
                    if (!enumPkg.isNullOrBlank()) extraImports += "$enumPkg.$simple"
                }
            }
        }

        val requestParams = (constraint["columns"] as? List<Map<String, Any?>>).orEmpty()
            .map { it["columnName"].toString() }
            .filter { !it.equals(deletedField, ignoreCase = true) }
            .map { colName ->
                val colMeta = columns.first { SqlSchemaUtils.getColumnName(it).equals(colName, ignoreCase = true) }
                val type = SqlSchemaUtils.getColumnType(colMeta)
                addTypeImportIfNeeded(colMeta, type)
                mapOf(
                    "name" to (toLowerCamelCase(colName) ?: colName),
                    "type" to type,
                    "isString" to (type.removeSuffix("?") == "String"),
                )
            }

        val idColumn = columns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val idTypeRaw = idColumn?.let { SqlSchemaUtils.getColumnType(it) } ?: "Long"
        val idType = idTypeRaw.removeSuffix("?")
        val excludeIdParamName = "exclude${entityType}Id"

        val importManager = QueryImportManager(QueryImportManager.QueryType.SINGLE).apply { addBaseImports() }
        extraImports.forEach { importManager.add(it) }

        with(ctx) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "Query", queryName)
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            resultContext.putContext(tag, "imports", importManager.toImportLines())
            resultContext.putContext(tag, "RequestParams", requestParams)
            resultContext.putContext(tag, "ExcludeIdParamName", excludeIdParamName)
            resultContext.putContext(tag, "IdType", idType)
        }

        return resultContext
    }

    private fun queryFullName(
        ctx: AggregateContext,
        aggregate: String,
        queryName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(queryName)}"
    }
}
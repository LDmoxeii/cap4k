package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toLowerCamelCase
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class UniqueQueryHandlerGenerator : AggregateGenerator {
    override val tag: String = "query_handler"
    override val order: Int = 20

    @Volatile
    private lateinit var currentType: String
    @Volatile
    private lateinit var currentFullName: String

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName]!!
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        val deletedField = ctx.getString("deletedField")

        val qryHandlerType = constraints.map { cons ->
            val suffix = computeSuffix(cons, deletedField)
            val q = "Unique${entityType}${suffix}Qry"
            "${q}Handler"
        }.firstOrNull { currentQryHandlerType ->
            currentQryHandlerType.isNotBlank() && !ctx.typeMapping.containsKey(currentQryHandlerType)
        }

        if (qryHandlerType == null) return false

        val aggregate = ctx.resolveAggregateWithModule(tableName)

        currentType = qryHandlerType
        currentFullName = resolveFullName(ctx, aggregate)
        return true
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val resultContext = ctx.baseMap.toMutableMap()

        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)
        val entityType = ctx.entityTypeMap[tableName]!!

        val selected = resolveSelectedConstraint(table)
        val deletedField = ctx.getString("deletedField")
        val allColumns = ctx.columnsMap[tableName]!!

        val whereProps = selected?.get("columns")
            .let { it as? List<Map<String, Any?>> ?: emptyList() }
            .map { it["columnName"].toString() }
            .filter { !it.equals(deletedField, ignoreCase = true) }
            .map { colName -> toLowerCamelCase(colName) ?: colName }

        val idColumn = allColumns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val idPropName = idColumn?.let { toLowerCamelCase(SqlSchemaUtils.getColumnName(it)) } ?: "id"
        val excludeIdParamName = "exclude${entityType}Id"

        val basePackage = ctx.getString("basePackage")
        val shareModelPkg = basePackage + refPackage(ctx.templatePackage["query"] ?: "") + "._share.model"
        val shareModelImports = (listOf(entityType) + whereProps + idPropName)
            .distinct()
            .map { "$shareModelPkg.$it" }

        with(ctx) {
            resultContext.putContext(tag, "modulePath", adapterPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "QueryHandler", currentType)
            resultContext.putContext(tag, "Query", getQueryName(table))
            resultContext.putContext(tag, "QueryType", ctx.typeMapping[getQueryName(table)]!!)
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
            resultContext.putContext(tag, "shareModelImports", shareModelImports)

            resultContext.putContext(tag, "WhereProps", whereProps)
            resultContext.putContext(tag, "IdPropName", idPropName)
            resultContext.putContext(tag, "ExcludeIdParamName", excludeIdParamName)
        }

        return resultContext
    }

    override fun generatorFullName(): String = currentFullName

    override fun generatorName(): String = currentType

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueQueryHandlerGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ QueryHandler }}.kt"
                format = "resource"
                data = "templates/unique_query_handler.kt.peb"
                conflict = "overwrite"
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

    context(ctx: AggregateContext)
    private fun getQueryName(table: Map<String, Any?>): String {
        val handlerName = currentType
        return handlerName.removeSuffix("Handler")
    }

    context(ctx: AggregateContext)
    private fun resolveSelectedConstraint(table: Map<String, Any?>): Map<String, Any?>? {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName] ?: return null
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        val deletedField = ctx.getString("deletedField")
        val targetHandler = currentType
        return constraints.firstOrNull { cons ->
            val suffix = computeSuffix(cons, deletedField)
            val q = "Unique${entityType}${suffix}Qry"
            val h = toUpperCamelCase("${q}Handler")!!
            h == targetHandler
        }
    }

    private fun computeSuffix(cons: Map<String, Any?>, deletedField: String): String {
        // 1) Prefer custom suffix from constraint name: uk_v_xxx -> Xxx
        val cName = cons["constraintName"].toString()
        if ("uk_i" == cName) return ""
        val regex = Regex("^uk_v_(.+)$", RegexOption.IGNORE_CASE)
        val m = regex.find(cName)
        if (m != null) {
            val token = m.groupValues[1]
            return toUpperCamelCase(token) ?: token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        // 2) Fallback to concatenated column names (excluding deleted field)
        val cols = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
        val filtered = cols.filter { c ->
            !c["columnName"].toString().equals(deletedField, ignoreCase = true)
        }
        if (filtered.isEmpty()) return ""
        return filtered.sortedBy { (it["ordinal"] as Number).toInt() }
            .joinToString("") { toUpperCamelCase(it["columnName"].toString()) ?: it["columnName"].toString() }
    }
}

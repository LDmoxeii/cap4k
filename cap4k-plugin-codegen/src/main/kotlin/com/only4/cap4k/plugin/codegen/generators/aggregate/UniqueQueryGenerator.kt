package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.QueryImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toLowerCamelCase
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class UniqueQueryGenerator : AggregateGenerator {
    override val tag: String = "query"
    override val order: Int = 20

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        val name = generatorName(table)
        return name.isNotBlank() && !ctx.typeMapping.containsKey(name)
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

        val extraImports = mutableSetOf<String>()

        fun addTypeImportIfNeeded(colMeta: Map<String, Any?>, typeName: String) {
            val simple = typeName.removeSuffix("?")
            if (SqlSchemaUtils.hasType(colMeta)) {
                // Prefer typeMapping (enums and custom types registered earlier)
                val mapped = ctx.typeMapping[simple]
                if (!mapped.isNullOrBlank()) {
                    extraImports += mapped
                } else {
                    // Fallback to enumPackageMap if available
                    val enumPkg = ctx.enumPackageMap[simple]
                    if (!enumPkg.isNullOrBlank()) extraImports += "$enumPkg.$simple"
                }
            }
        }

        val requestParams = selected?.get("columns")
            .let { it as? List<Map<String, Any?>> ?: emptyList() }
            .map { it["columnName"].toString() }
            .filter { !it.equals(deletedField, ignoreCase = true) }
            .map { colName ->
                val colMeta = allColumns.first { SqlSchemaUtils.getColumnName(it).equals(colName, ignoreCase = true) }
                val type = SqlSchemaUtils.getColumnType(colMeta)
                addTypeImportIfNeeded(colMeta, type)
                mapOf(
                    "name" to (toLowerCamelCase(colName) ?: colName),
                    "type" to type,
                    "isString" to (type.removeSuffix("?") == "String")
                )
            }

        val idColumn = allColumns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val idTypeRaw = idColumn?.let { SqlSchemaUtils.getColumnType(it) } ?: "Long"
        val idType = idTypeRaw.removeSuffix("?")
        val excludeIdParamName = "exclude${entityType}Id"

        // imports
        val importManager = QueryImportManager(QueryImportManager.QueryType.SINGLE).apply { addBaseImports() }

        with(ctx) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "Query", generatorName(table))
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            // merge imports: base + extra type imports
            extraImports.forEach { importManager.add(it) }
            resultContext.putContext(tag, "imports", importManager.toImportLines())

            resultContext.putContext(tag, "RequestParams", requestParams)
            resultContext.putContext(tag, "ExcludeIdParamName", excludeIdParamName)
            resultContext.putContext(tag, "IdType", idType)
        }

        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(table: Map<String, Any?>): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage(aggregate)
        return "$basePackage${templatePackage}${`package`}${refPackage(generatorName(table))}"
    }

    context(ctx: AggregateContext)
    override fun generatorName(table: Map<String, Any?>): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName] ?: return ""
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        val deletedField = ctx.getString("deletedField")

        constraints.forEach { cons ->
            val suffix = computeSuffix(cons, deletedField)
            val q = "Unique${entityType}${suffix}Qry"
            if (!ctx.typeMapping.containsKey(q)) {
                return q
            }
        }
        return ""
    }

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueQueryGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ Query }}.kt"
                format = "resource"
                data = "templates/unique_query.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }

    context(ctx: AggregateContext)
    private fun resolveSelectedConstraint(table: Map<String, Any?>): Map<String, Any?>? {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName] ?: return null
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        val target = generatorName(table)
        val deletedField = ctx.getString("deletedField")
        return constraints.firstOrNull { cons ->
            val suffix = computeSuffix(cons, deletedField)
            val q = "Unique${entityType}${suffix}Qry"
            q == target
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

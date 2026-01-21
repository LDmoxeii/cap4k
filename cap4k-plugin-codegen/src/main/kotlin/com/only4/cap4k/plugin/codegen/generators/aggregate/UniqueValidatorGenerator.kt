package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.ValidatorImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toLowerCamelCase
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class UniqueValidatorGenerator : AggregateGenerator {
    override val tag: String = "validator"
    override val order: Int = 20

    @Volatile
    private lateinit var currentType: String

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        // 仅依据当前生成名是否可用（生成名内部已确保前置条件满足）
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName]!!
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        val deletedField = ctx.getString("deletedField")
        val validatorType = constraints.map { cons ->
            val suffix = computeSuffix(cons, deletedField)
            "Unique${entityType}${suffix}"
        }.firstOrNull { currentValidatorType ->
            currentValidatorType.isNotBlank() && !(ctx.typeMapping.containsKey(currentValidatorType))
        }

        if (validatorType == null) return false

        currentType = validatorType
        return true
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val resultContext = ctx.baseMap.toMutableMap()

        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName]!!

        val selected = resolveSelectedConstraint(table)
        val deletedField = ctx.getString("deletedField")
        val allColumns = ctx.columnsMap[tableName]!!

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

        val requestProps = selected?.get("columns")
            .let { it as? List<Map<String, Any?>> ?: emptyList() }
            .map { it["columnName"].toString() }
            .filter { !it.equals(deletedField, ignoreCase = true) }
            .map { colName ->
                val colMeta = allColumns.first { SqlSchemaUtils.getColumnName(it).equals(colName, ignoreCase = true) }
                val type = SqlSchemaUtils.getColumnType(colMeta).removeSuffix("?")
                addTypeImportIfNeeded(colMeta, type)
                val camel = toLowerCamelCase(colName) ?: colName
                mapOf(
                    "name" to camel,
                    "type" to type,
                    "isString" to (type.removeSuffix("?") == "String"),
                    // 注解参数名，如 codeField / nameField
                    "param" to "${camel}Field",
                    // 初始化后用于 props[...] 的变量名，如 codeProperty
                    "varName" to "${camel}Property"
                )
            }

        val idColumn = allColumns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val idTypeRaw = idColumn?.let { SqlSchemaUtils.getColumnType(it) } ?: "Long"
        val idType = idTypeRaw.removeSuffix("?")
        val entityCamel = toLowerCamelCase(entityType) ?: entityType
        val entityIdParam = "${entityCamel}IdField"
        val entityIdPropDefault = "${entityCamel}Id"
        val entityIdVar = "${entityCamel}IdProperty"

        // imports
        val importManager = ValidatorImportManager().apply { addBaseImports() }
        importManager.add(
            "com.only4.cap4k.ddd.core.Mediator",
            // kotlin reflect
            "kotlin.reflect.full.memberProperties",
            // unique query type
            ctx.typeMapping[getQueryName(table)]!!
        )

        with(ctx) {
            resultContext.putContext(tag, "modulePath", applicationPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(""))

            resultContext.putContext(tag, "Validator", currentType)
            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

            extraImports.forEach { importManager.add(it) }
            resultContext.putContext(tag, "imports", importManager.toImportLines())

            resultContext.putContext(tag, "FieldParams", requestProps.map {
                mapOf(
                    "param" to it["param"]!!,
                    "default" to it["name"]!!
                )
            })
            resultContext.putContext(tag, "RequestProps", requestProps)
            resultContext.putContext(tag, "EntityIdParam", entityIdParam)
            resultContext.putContext(tag, "EntityIdDefault", entityIdPropDefault)
            resultContext.putContext(tag, "EntityIdVar", entityIdVar)
            resultContext.putContext(tag, "IdType", idType)
            resultContext.putContext(tag, "ExcludeIdParamName", "exclude${entityType}Id")
            resultContext.putContext(tag, "Query", getQueryName(table))
        }

        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(table: Map<String, Any?>): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val `package` = refPackage("")
        return "$basePackage${templatePackage}${`package`}${refPackage(currentType)}"
    }

    override fun generatorName(): String = currentType

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueValidatorGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ Validator }}.kt"
                format = "resource"
                data = "templates/unique_validator.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[currentType] = generatorFullName(table)
    }

    context(ctx: AggregateContext)
    private fun getQueryName(table: Map<String, Any?>): String {
        val validatorName = currentType
        return if (validatorName.isBlank()) "" else "${validatorName}Qry"
    }

    context(ctx: AggregateContext)
    private fun resolveSelectedConstraint(table: Map<String, Any?>): Map<String, Any?>? {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName] ?: return null
        val constraints = ctx.uniqueConstraintsMap[tableName].orEmpty()
        val deletedField = ctx.getString("deletedField")
        val targetName = currentType
        return constraints.firstOrNull { cons ->
            val suffix = computeSuffix(cons, deletedField)
            val name = "Unique${entityType}${suffix}"
            name == targetName
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

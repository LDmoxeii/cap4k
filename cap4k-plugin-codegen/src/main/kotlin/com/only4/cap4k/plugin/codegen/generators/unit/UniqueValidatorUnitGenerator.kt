package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.ValidatorImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toLowerCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class UniqueValidatorUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "validator"
    override val order: Int = 20

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@UniqueValidatorUnitGenerator.tag
                pattern = "^Unique.*$"
                name = "{{ Validator }}.kt"
                format = "resource"
                data = "templates/unique_validator.kt.peb"
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
                val validatorName = AggregateNaming.uniqueValidatorName(entityType, suffix)
                if (validatorName.isBlank()) return@forEach
                val key = "$aggregate:$validatorName"
                if (!seen.add(key)) return@forEach
                if (ctx.typeMapping.containsKey(validatorName)) return@forEach

                val queryName = AggregateNaming.uniqueQueryName(entityType, suffix)
                val queryFullName = ctx.typeMapping[queryName] ?: queryFullName(table, queryName)

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

                val requestProps = (cons["columns"] as? List<Map<String, Any?>>).orEmpty()
                    .map { it["columnName"].toString() }
                    .filter { !it.equals(deletedField, ignoreCase = true) }
                    .map { colName ->
                        val colMeta = columns.first { SqlSchemaUtils.getColumnName(it).equals(colName, ignoreCase = true) }
                        val type = SqlSchemaUtils.getColumnType(colMeta).removeSuffix("?")
                        addTypeImportIfNeeded(colMeta, type)
                        val camel = toLowerCamelCase(colName) ?: colName
                        mapOf(
                            "name" to camel,
                            "type" to type,
                            "isString" to (type.removeSuffix("?") == "String"),
                            "param" to "${camel}Field",
                            "varName" to "${camel}Property",
                        )
                    }

                val idColumn = columns.firstOrNull { SqlSchemaUtils.isColumnPrimaryKey(it) }
                val idTypeRaw = idColumn?.let { SqlSchemaUtils.getColumnType(it) } ?: "Long"
                val idType = idTypeRaw.removeSuffix("?")
                val entityCamel = toLowerCamelCase(entityType) ?: entityType
                val entityIdParam = "${entityCamel}IdField"
                val entityIdPropDefault = "${entityCamel}Id"
                val entityIdVar = "${entityCamel}IdProperty"

                val importManager = ValidatorImportManager().apply { addBaseImports() }
                importManager.add(
                    "com.only4.cap4k.ddd.core.Mediator",
                    "kotlin.reflect.full.memberProperties",
                    queryFullName,
                )
                extraImports.forEach { importManager.add(it) }

                val resultContext = ctx.baseMap.toMutableMap()
                with(ctx) {
                    resultContext.putContext(tag, "modulePath", applicationPath)
                    resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                    resultContext.putContext(tag, "package", refPackage(""))

                    resultContext.putContext(tag, "Validator", validatorName)
                    resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))

                    resultContext.putContext(tag, "imports", importManager.toImportLines())

                    resultContext.putContext(tag, "FieldParams", requestProps.map {
                        mapOf(
                            "param" to it["param"]!!,
                            "default" to it["name"]!!,
                        )
                    })
                    resultContext.putContext(tag, "RequestProps", requestProps)
                    resultContext.putContext(tag, "EntityIdParam", entityIdParam)
                    resultContext.putContext(tag, "EntityIdDefault", entityIdPropDefault)
                    resultContext.putContext(tag, "EntityIdVar", entityIdVar)
                    resultContext.putContext(tag, "IdType", idType)
                    resultContext.putContext(tag, "ExcludeIdParamName", "exclude${entityType}Id")
                    resultContext.putContext(tag, "Query", queryName)
                }

                val deps = if (ctx.typeMapping.containsKey(queryName)) {
                    emptyList()
                } else {
                    listOf("uniqueQuery:$aggregate:$queryName")
                }

                val fullName = validatorFullName(ctx, validatorName)
                units.add(
                    GenerationUnit(
                        id = "uniqueValidator:$aggregate:$validatorName",
                        tag = tag,
                        name = validatorName,
                        order = order,
                        deps = deps,
                        templateNodes = defaultTemplateNodes(),
                        context = resultContext,
                        exportTypes = mapOf(validatorName to fullName),
                    )
                )
            }
        }

        return units
    }

    context(ctx: AggregateContext)
    private fun queryFullName(
        table: Map<String, Any?>,
        queryName: String,
    ): String {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage["query"] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(queryName)}"
    }

    private fun validatorFullName(ctx: AggregateContext, validatorName: String): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        return "$basePackage${templatePackage}${refPackage(validatorName)}"
    }
}
package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.SchemaImportManager
import com.only4.cap4k.plugin.codegen.misc.Inflector
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.getPackageFromClassName
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.misc.toLowerCamelCase
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase
import com.only4.cap4k.plugin.codegen.template.TemplateNode

class SchemaUnitGenerator : AggregateUnitGenerator {
    override val tag: String = "schema"
    override val order: Int = 50

    private fun defaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SchemaUnitGenerator.tag
                name = "{{ Schema }}.kt"
                format = "resource"
                data = "templates/schema.kt.peb"
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
            if (!ctx.getBoolean("generateSchema", false)) return@forEach

            val tableName = SqlSchemaUtils.getTableName(table)
            val entityTypeRaw = ctx.entityTypeMap[tableName] ?: return@forEach
            val entityType = AggregateNaming.entityName(entityTypeRaw)
            val schemaName = AggregateNaming.schemaName(entityType)
            if (ctx.typeMapping.containsKey(schemaName)) return@forEach

            val columns = ctx.columnsMap[tableName] ?: return@forEach
            val aggregate = ctx.resolveAggregateWithModule(tableName)

            val isAggregateRoot = SqlSchemaUtils.isAggregateRoot(table)
            val generateAggregate = ctx.getBoolean("generateAggregate")
            val repositorySupportQuerydsl = ctx.getBoolean("repositorySupportQuerydsl")

            val schemaBaseName = AggregateNaming.schemaBaseName()
            val schemaBaseFullName = ctx.typeMapping[schemaBaseName] ?: schemaBaseFullName(ctx, schemaBaseName)

            val importManager = SchemaImportManager(getPackageFromClassName(schemaBaseFullName))
            importManager.addBaseImports()

            val fullEntityType = ctx.typeMapping[entityType] ?: entityFullName(ctx, aggregate, entityType)
            importManager.add(fullEntityType)

            importManager.addIfNeeded(
                isAggregateRoot,
                "com.only4.cap4k.ddd.domain.repo.JpaPredicate"
            )

            val querydslEnabled = isAggregateRoot && repositorySupportQuerydsl
            importManager.addIfNeeded(
                querydslEnabled,
                "com.querydsl.core.types.OrderSpecifier",
                "com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate",
                "com.only4.cap4k.ddd.domain.repo.querydsl.QuerydslPredicate"
            )

            if (querydslEnabled) {
                val qType = AggregateNaming.querydslName(entityType)
                val fullQType = ctx.typeMapping[qType] ?: entityFullName(ctx, aggregate, qType)
                val aggregateType = AggregateNaming.aggregateName(ctx, entityType)
                val fullAggregateType = ctx.typeMapping[aggregateType] ?: aggregateFullName(ctx, aggregate, aggregateType)
                importManager.add(fullQType)
                importManager.add(fullAggregateType)
            }

            val fields = columns
                .filter { !SqlSchemaUtils.isIgnore(it) }
                .map { column ->
                    val columnName = SqlSchemaUtils.getColumnName(column)
                    val columnType = SqlSchemaUtils.getColumnType(column)
                    val simpleType = columnType.removeSuffix("?")
                    val fieldName = toLowerCamelCase(columnName) ?: columnName
                    val comment = SqlSchemaUtils.getComment(column)

                    if (SqlSchemaUtils.hasType(column)) {
                        val mapped = ctx.typeMapping[simpleType]
                            ?: ctx.enumPackageMap[simpleType]?.let { "$it.$simpleType" }
                        if (!mapped.isNullOrBlank()) {
                            importManager.add(mapped)
                        }
                    }

                    mapOf(
                        "fieldName" to fieldName,
                        "columnName" to columnName,
                        "fieldType" to columnType,
                        "comment" to comment,
                    )
                }

            val relationFields = mutableListOf<Map<String, Any?>>()
            ctx.relationsMap[tableName]?.forEach { (refTableName, relationInfo) ->
                val refInfos = relationInfo.split(";")

                val refEntityType = ctx.entityTypeMap[refTableName] ?: return@forEach
                val relation = refInfos[0].replace("*", "")
                val fieldName = when (relation) {
                    "OneToMany", "ManyToMany" -> Inflector.pluralize(toLowerCamelCase(refEntityType) ?: refEntityType)
                    else -> toLowerCamelCase(refEntityType) ?: refEntityType
                }

                relationFields.add(
                    mapOf(
                        "fieldName" to fieldName,
                        "refEntityType" to refEntityType,
                        "relation" to relation,
                    )
                )
            }

            val resultContext = ctx.baseMap.toMutableMap()
            with(ctx) {
                resultContext.putContext(tag, "modulePath", domainPath)
                resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
                resultContext.putContext(tag, "package", refPackage(aggregate))

                resultContext.putContext(tag, "Schema", schemaName)
                resultContext.putContext(tag, "imports", importManager.toImportLines())

                resultContext.putContext(tag, "EntityVar", toLowerCamelCase(entityType) ?: entityType)
                resultContext.putContext(tag, "Entity", entityType)
                resultContext.putContext(tag, "SchemaBase", schemaBaseName)
                resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
                resultContext.putContext(tag, "fields", fields)
                resultContext.putContext(tag, "relationFields", relationFields)

                resultContext.putContext(tag, "isAggregateRoot", isAggregateRoot)
                resultContext.putContext(tag, "generateAggregate", generateAggregate)
                resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
            }

            val deps = buildList {
                if (!ctx.typeMapping.containsKey(schemaBaseName)) {
                    add("schemaBase")
                }
                if (!ctx.typeMapping.containsKey(entityType)) {
                    add("entity:$aggregate:$entityType")
                }
            }

            val fullName = schemaFullName(ctx, aggregate, schemaName)
            units.add(
                GenerationUnit(
                    id = "schema:$aggregate:$schemaName",
                    tag = tag,
                    name = schemaName,
                    order = order,
                    deps = deps,
                    templateNodes = defaultTemplateNodes(),
                    context = resultContext,
                    exportTypes = mapOf(schemaName to fullName),
                )
            )
        }

        return units
    }

    private fun schemaBaseFullName(ctx: AggregateContext, schemaBaseName: String): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage["schema_base"] ?: "")
        return "$basePackage${templatePackage}${refPackage(schemaBaseName)}"
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

    private fun aggregateFullName(
        ctx: AggregateContext,
        aggregate: String,
        aggregateType: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage["aggregate"] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(aggregateType)}"
    }

    private fun schemaFullName(
        ctx: AggregateContext,
        aggregate: String,
        schemaName: String,
    ): String {
        val basePackage = ctx.getString("basePackage")
        val templatePackage = refPackage(ctx.templatePackage[tag] ?: "")
        val pkg = refPackage(aggregate)
        return "$basePackage${templatePackage}${pkg}${refPackage(schemaName)}"
    }
}
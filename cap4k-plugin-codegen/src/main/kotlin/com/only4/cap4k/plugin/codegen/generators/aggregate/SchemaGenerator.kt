package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.SchemaImportManager
import com.only4.cap4k.plugin.codegen.misc.*
import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * Schema 文件生成器
 * 为每个实体生成对应的 Schema 类（类似 JPA Metamodel）
 */
class SchemaGenerator : EntityGenerator() {
    override val tag = "schema"
    override val order = 50

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false
        if (!ctx.getBoolean("generateSchema", false)) return false

        return !ctx.typeMapping.containsKey(generatorName(table))
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val aggregate = ctx.resolveAggregateWithModule(tableName)
        val columns = ctx.columnsMap[tableName]!!

        val aggregateTypeTemplate = ctx.getString("aggregateTypeTemplate")

        val entityType = ctx.entityTypeMap[tableName]!!
        val aggregateType = renderString(aggregateTypeTemplate, mapOf("Entity" to entityType))

        val isAggregateRoot = SqlSchemaUtils.isAggregateRoot(table)
        val generateAggregate = ctx.getBoolean("generateAggregate")
        val repositorySupportQuerydsl = ctx.getBoolean("repositorySupportQuerydsl")

        // 创建 ImportManager
        val importManager = SchemaImportManager(getPackageFromClassName(ctx.typeMapping["Schema"]!!))
        importManager.addBaseImports()
        importManager.add(
            ctx.typeMapping[entityType]!!,
        )
        importManager.addIfNeeded(
            isAggregateRoot,
            "com.only4.cap4k.ddd.domain.repo.JpaPredicate"
        )
        importManager.addIfNeeded(
            isAggregateRoot && repositorySupportQuerydsl,
            "com.querydsl.core.types.OrderSpecifier",
            "com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate",
            "com.only4.cap4k.ddd.domain.repo.querydsl.QuerydslPredicate"
        )
        importManager.addIfNeeded(isAggregateRoot && repositorySupportQuerydsl) { ctx.typeMapping["Q$entityType"]!! }
        importManager.addIfNeeded(isAggregateRoot && repositorySupportQuerydsl) { ctx.typeMapping[aggregateType]!! }

        // 准备列字段数据
        val fields = columns
            .filter { !SqlSchemaUtils.isIgnore(it) }
            .map { column ->
                val columnName = SqlSchemaUtils.getColumnName(column)
                val columnType = SqlSchemaUtils.getColumnType(column)
                val simpleType = columnType.removeSuffix("?")
                val fieldName = toLowerCamelCase(columnName) ?: columnName
                val comment = SqlSchemaUtils.getComment(column)

                if (SqlSchemaUtils.hasType(column)) {
                    importManager.add(ctx.typeMapping[simpleType]!!)
                }

                mapOf(
                    "fieldName" to fieldName,
                    "columnName" to columnName,
                    "fieldType" to columnType,
                    "comment" to comment
                )
            }

        // 准备关系字段数据
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
                    "relation" to relation
                )
            )
        }

        val resultContext = ctx.baseMap.toMutableMap()

        with(ctx) {
            resultContext.putContext(tag, "modulePath", domainPath)
            resultContext.putContext(tag, "templatePackage", refPackage(templatePackage[tag] ?: ""))
            resultContext.putContext(tag, "package", refPackage(aggregate))

            resultContext.putContext(tag, "Schema", "S$entityType")

            resultContext.putContext(tag, "imports", importManager.toImportLines())

            resultContext.putContext(tag, "EntityVar", toLowerCamelCase(entityType) ?: entityType)
            resultContext.putContext(tag, "Entity", entityType)
            resultContext.putContext(tag, "SchemaBase", "Schema")
            resultContext.putContext(tag, "Aggregate", toUpperCamelCase(aggregate) ?: aggregate)
            resultContext.putContext(tag, "fields", fields)
            resultContext.putContext(tag, "relationFields", relationFields)

            resultContext.putContext(tag, "isAggregateRoot", isAggregateRoot)
            resultContext.putContext(tag, "generateAggregate", generateAggregate)

            resultContext.putContext(tag, "Comment", SqlSchemaUtils.getComment(table))
        }


        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(table: Map<String, Any?>): String {
        with(ctx) {
            val tableName = SqlSchemaUtils.getTableName(table)
            val aggregate = resolveAggregateWithModule(tableName)
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = refPackage(aggregate)

            return "$basePackage${templatePackage}${`package`}${refPackage(generatorName(table))}"
        }
    }

    context(ctx: AggregateContext)
    override fun generatorName(table: Map<String, Any?>): String = "S${super.generatorName(table)}"

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@SchemaGenerator.tag
                name = "{{ Schema }}.kt"
                format = "resource"
                data = "templates/schema.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[generatorName(table)] = generatorFullName(table)
    }
}


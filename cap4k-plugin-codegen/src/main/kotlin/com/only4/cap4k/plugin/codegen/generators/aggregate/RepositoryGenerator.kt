package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.imports.RepositoryImportManager
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.refPackage
import com.only4.cap4k.plugin.codegen.pebble.PebbleTemplateRenderer.renderString
import com.only4.cap4k.plugin.codegen.template.TemplateNode

/**
 * Repository 生成器
 * 为聚合根生成 JPA Repository 接口及其适配器实现
 */
class RepositoryGenerator : AggregateGenerator {

    override val tag = "repository"
    override val order = 30

    @Volatile
    private lateinit var currentType: String

    context(ctx: AggregateContext)
    override fun shouldGenerate(table: Map<String, Any?>): Boolean {
        if (SqlSchemaUtils.isIgnore(table)) return false
        if (SqlSchemaUtils.hasRelation(table)) return false

        if (!SqlSchemaUtils.isAggregateRoot(table)) return false

        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName]!!
        val repositoryNameTemplate = ctx.getString("repositoryNameTemplate")

        val repositoryType =  renderString(repositoryNameTemplate, mapOf("Aggregate" to entityType))

        if (ctx.typeMapping.containsKey(repositoryType)) return false

        currentType = repositoryType
        return true
    }

    context(ctx: AggregateContext)
    override fun buildContext(table: Map<String, Any?>): Map<String, Any?> {
        val tableName = SqlSchemaUtils.getTableName(table)
        val entityType = ctx.entityTypeMap[tableName]!!
        val fullRootEntityType = ctx.typeMapping[entityType]!!

        val columns = ctx.columnsMap[tableName]!!
        val ids = columns.filter { SqlSchemaUtils.isColumnPrimaryKey(it) }
        val identityType = if (ids.size != 1) "Long" else SqlSchemaUtils.getColumnType(ids[0])

        val imports = RepositoryImportManager()
        imports.addBaseImports()
        imports.add(fullRootEntityType)

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

            resultContext.putContext(tag, "Repository", currentType)

            val comment = "Repository for $entityType aggregate"
            resultContext.putContext(tag, "Comment", comment)
        }

        return resultContext
    }

    context(ctx: AggregateContext)
    override fun generatorFullName(
        table: Map<String, Any?>
    ): String {
        with(ctx) {
            val basePackage = getString("basePackage")
            val templatePackage = refPackage(templatePackage[tag] ?: "")
            val `package` = ""

            return "$basePackage${templatePackage}${`package`}${refPackage(currentType)}"
        }
    }

    override fun generatorName(): String = currentType

    override fun getDefaultTemplateNodes(): List<TemplateNode> {
        return listOf(
            TemplateNode().apply {
                type = "file"
                tag = this@RepositoryGenerator.tag
                name = "{{ Repository }}.kt"
                format = "resource"
                data = "templates/repository.kt.peb"
                conflict = "overwrite"
            }
        )
    }

    context(ctx: AggregateContext)
    override fun onGenerated(table: Map<String, Any?>) {
        ctx.typeMapping[currentType] = generatorFullName(table)
    }
}


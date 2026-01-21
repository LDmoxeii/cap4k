package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.plugin.codegen.context.aggregate.builders.*
import com.only4.cap4k.plugin.codegen.core.TagAliasResolver
import com.only4.cap4k.plugin.codegen.generators.aggregate.*
import com.only4.cap4k.plugin.codegen.generators.aggregate.UniqueQueryGenerator
import com.only4.cap4k.plugin.codegen.generators.aggregate.UniqueQueryHandlerGenerator
import com.only4.cap4k.plugin.codegen.generators.aggregate.UniqueValidatorGenerator
import com.only4.cap4k.plugin.codegen.misc.SqlSchemaUtils
import com.only4.cap4k.plugin.codegen.misc.concatPackage
import com.only4.cap4k.plugin.codegen.misc.resolvePackageDirectory
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * 基于数据库实体的聚合生成任务
 */
open class GenAggregateTask : GenArchTask(), MutableAggregateContext {

    companion object {
        private val DEFAULT_ENTITY_IMPORTS = listOf(
            "com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate",
            "jakarta.persistence.*",
            "org.hibernate.annotations.DynamicInsert",
            "org.hibernate.annotations.DynamicUpdate",
            "org.hibernate.annotations.Fetch",
            "org.hibernate.annotations.FetchMode",
            "org.hibernate.annotations.GenericGenerator",
            "org.hibernate.annotations.SQLDelete",
            "org.hibernate.annotations.Where",
        )
    }

    @Internal
    override val dbType: String = "dbType"

    @get:Internal
    override val entityClassExtraImports: List<String> by lazy {
        buildList {
            addAll(DEFAULT_ENTITY_IMPORTS)
            val extraImports = getString("entityClassExtraImports")

            if (extraImports.isNotEmpty()) {
                addAll(
                    extraImports.split(";")
                        .asSequence()
                        .map { it.trim().replace(Regex(PATTERN_LINE_BREAK), "") }
                        .map { if (it.startsWith("import ")) it.substring(6).trim() else it }
                        .filter { it.isNotBlank() }
                        .toList()
                )
            }
        }.distinct()
    }

    @Internal
    override val tableMap: MutableMap<String, Map<String, Any?>> = mutableMapOf()

    @Internal
    override val columnsMap: MutableMap<String, List<Map<String, Any?>>> = mutableMapOf()

    @Internal
    override val relationsMap: MutableMap<String, Map<String, String>> = mutableMapOf()

    @Internal
    override val tablePackageMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val entityTypeMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val tableModuleMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val tableAggregateMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val annotationsMap: MutableMap<String, Map<String, String>> = mutableMapOf()

    @Internal
    override val enumConfigMap: MutableMap<String, Map<Int, Array<String>>> = mutableMapOf()

    @Internal
    override val enumPackageMap: MutableMap<String, String> = mutableMapOf()

    @Internal
    override val uniqueConstraintsMap: MutableMap<String, List<Map<String, Any?>>> = mutableMapOf()

    override fun resolveAggregateWithModule(tableName: String): String {
        val module = tableModuleMap[tableName]
        return if (!(module.isNullOrBlank())) {
            concatPackage(module, tableAggregateMap[tableName]!!)
        } else {
            tableAggregateMap[tableName]!!
        }
    }

    override fun renderTemplate(
        templateNodes: List<TemplateNode>,
        parentPath: String,
    ) {
        super.renderTemplate(templateNodes, parentPath)
        templateNodes.forEach { templateNode ->
            val tag = templateNode.tag?.let { TagAliasResolver.normalizeAggregateTag(it) } ?: return@forEach
            templateNodeMap.computeIfAbsent(tag) { mutableListOf() }.add(templateNode)
        }
    }

    @TaskAction
    override fun generate() {
        renderFileSwitch = false
        super.generate()
        SqlSchemaUtils.context = this

        genEntity()
    }

    private fun genEntity() {
        val context = buildGenerationContext()

        if (context.tableMap.isEmpty()) {
            logger.warn("No tables found in database")
            return
        }
        with(context) {
            generateFiles()
        }

    }

    private fun buildGenerationContext(): AggregateContext {
        val contextBuilders = listOf(
            TableContextBuilder(),           // order=10  - 表/列基础信息
            EntityTypeContextBuilder(),      // order=20  - 实体类型
            AnnotationContextBuilder(),      // order=20  - 注释/注解信息
            ModuleContextBuilder(),          // order=20  - 模块信息
            RelationContextBuilder(),        // order=20  - 关联关系
            UniqueConstraintContextBuilder(),// order=20  - 唯一约束
            EnumContextBuilder(),            // order=20  - 枚举信息
            AggregateContextBuilder(),       // order=30  - 聚合信息
            TablePackageContextBuilder(),    // order=40  - 包信息
        )

        contextBuilders.sortedBy { it.order }.forEach { builder ->
            logger.lifecycle("Building context: ${builder.javaClass.simpleName}")
            builder.build(this)
        }
        return this
    }

    context(ctx: AggregateContext)
    private fun generateFiles() {
        val generators = listOf(
            SchemaBaseGenerator(),           // order=10 - Schema 基类
            EnumGenerator(),                 // order=10 - 枚举
            EnumTranslationGenerator(),      // order=20 - 枚举翻译
            EntityGenerator(),               // order=20 - 实体
            UniqueQueryGenerator(),          // order=20 - 唯一约束查询
            UniqueQueryHandlerGenerator(),   // order=20 - 唯一约束查询处理器
            UniqueValidatorGenerator(),      // order=20 - 唯一约束校验器
            SpecificationGenerator(),        // order=30 - 规范
            FactoryGenerator(),              // order=30 - 工厂
            DomainEventGenerator(),          // order=30 - 领域事件
            DomainEventHandlerGenerator(),   // order=30 - 领域事件处理器
            RepositoryGenerator(),           // order=30 - Repository 接口及实现
            AggregateWrapperGenerator(),            // order=40 - 聚合封装
            SchemaGenerator(),               // order=50 - Schema 类
        )

        generators.sortedBy { it.order }
            .forEach { generator ->
                logger.lifecycle("Generating files: ${generator.tag}")
                generateForTables(generator)
            }
    }

    context(ctx: AggregateContext)
    private fun generateForTables(
        generator: AggregateGenerator,
    ) {
        val tables = ctx.tableMap.values.toList()
        tables.forEach { table ->
            while (generator.shouldGenerate(table)) {
                val tableContext = generator.buildContext(table)

                // 合并模板节点（上下文配置合并默认，再根据 pattern 选择）：
                // - 同一 dir/file 类型节点去重；每个唯一 (name+pattern) 保留一个模板节点
                // - context 优先级高于 defaults；目录和文件层级也遵循优先级合并
                val genName = generator.generatorName()

                val ctxTop = ctx.templateNodeMap.getOrDefault(generator.tag, emptyList())
                val defTop = generator.getDefaultTemplateNodes()

                val selected = TemplateNode.mergeAndSelect(ctxTop, defTop, genName)

                selected.forEach { templateNode ->
                    val pathNode = templateNode.resolve(tableContext)
                    forceRender(
                        pathNode,
                        resolvePackageDirectory(
                            tableContext["modulePath"].toString(),
                            concatPackage(
                                getString("basePackage"),
                                tableContext["templatePackage"].toString(),
                                tableContext["package"].toString()
                            )
                        )
                    )
                }
                generator.onGenerated(table)
            }
        }
    }
}


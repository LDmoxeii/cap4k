package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.plugin.codegen.context.aggregate.builders.*
import com.only4.cap4k.plugin.codegen.core.TagAliasResolver
import com.only4.cap4k.plugin.codegen.generators.aggregate.*
import com.only4.cap4k.plugin.codegen.generators.aggregate.UniqueQueryGenerator
import com.only4.cap4k.plugin.codegen.generators.aggregate.UniqueQueryHandlerGenerator
import com.only4.cap4k.plugin.codegen.generators.aggregate.UniqueValidatorGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.AggregateUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.DomainEventHandlerUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.DomainEventUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.EnumTranslationUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.EnumUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.GenerationPlan
import com.only4.cap4k.plugin.codegen.generators.unit.GenerationUnit
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

        generateFiles(context)
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

    private fun generateFiles(context: AggregateContext) {
        val steps: List<GenerationStep> = listOf(
            TableStep(SchemaBaseGenerator()), // order=10
            UnitStep(
                listOf(
                    EnumUnitGenerator(),
                    EnumTranslationUnitGenerator(),
                ),
                order = 10,
            ),
            TableStep(EntityGenerator()), // order=20
            TableStep(UniqueQueryGenerator()), // order=20
            TableStep(UniqueQueryHandlerGenerator()), // order=20
            TableStep(UniqueValidatorGenerator()), // order=20
            TableStep(SpecificationGenerator()), // order=30
            TableStep(FactoryGenerator()), // order=30
            UnitStep(
                listOf(
                    DomainEventUnitGenerator(),
                    DomainEventHandlerUnitGenerator(),
                ),
                order = 30,
            ),
            TableStep(RepositoryGenerator()), // order=30
            TableStep(AggregateGenerator()), // order=40
            TableStep(SchemaGenerator()), // order=50
        )

        steps.sortedBy { it.order }.forEach { step ->
            logger.lifecycle("Generating: ${step.label}")
            step.execute(context)
        }
    }

    private fun generateForTables(
        generator: AggregateTemplateGenerator,
        context: AggregateContext,
    ) {
        val tables = context.tableMap.values.toMutableList()

        with(context) {
            while (tables.isNotEmpty()) {
                val table = tables.first()

                if (!generator.shouldGenerate(table)) {
                    tables.removeFirst()
                    continue
                }

                val tableContext = generator.buildContext(table)

                // 合并模板节点（上下文配置合并默认，再根据 pattern 选择）：
                // - 同一 dir/file 类型节点去重；每个唯一 (name+pattern) 保留一个模板节点
                // - context 优先级高于 defaults；目录和文件层级也遵循优先级合并
                val genName = generator.generatorName(table)

                val ctxTop = context.templateNodeMap.getOrDefault(generator.tag, emptyList())
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

    private fun generateUnits(
        generators: List<AggregateUnitGenerator>,
        context: AggregateContext,
    ) {
        val units = generators.flatMap { generator ->
            with(context) { generator.collect() }
        }
        if (units.isEmpty()) return

        val plan = GenerationPlan(logAdapter)
        val ordered = plan.order(units)
        ordered.forEach { unit ->
            renderUnit(unit, context)
            applyExports(unit, context)
        }
    }

    private fun renderUnit(
        unit: GenerationUnit,
        context: AggregateContext,
    ) {
        val genName = unit.name
        val ctxTop = context.templateNodeMap.getOrDefault(unit.tag, emptyList())
        val defTop = unit.templateNodes
        val selected = TemplateNode.mergeAndSelect(ctxTop, defTop, genName)

        selected.forEach { templateNode ->
            val pathNode = templateNode.resolve(unit.context)
            forceRender(
                pathNode,
                resolvePackageDirectory(
                    unit.context["modulePath"].toString(),
                    concatPackage(
                        getString("basePackage"),
                        unit.context["templatePackage"].toString(),
                        unit.context["package"].toString(),
                    )
                )
            )
        }
    }

    private fun applyExports(
        unit: GenerationUnit,
        context: AggregateContext,
    ) {
        unit.exportTypes.forEach { (simple, full) ->
            context.typeMapping[simple] = full
        }
    }

    private sealed class GenerationStep(
        val order: Int,
        val label: String,
    ) {
        abstract fun execute(context: AggregateContext)
    }

    private inner class TableStep(
        private val generator: AggregateTemplateGenerator,
    ) : GenerationStep(generator.order, generator.tag) {
        override fun execute(context: AggregateContext) {
            generateForTables(generator, context)
        }
    }

    private inner class UnitStep(
        private val generators: List<AggregateUnitGenerator>,
        order: Int,
    ) : GenerationStep(
        order = order,
        label = generators.joinToString(",") { it.tag },
    ) {
        override fun execute(context: AggregateContext) {
            generateUnits(generators, context)
        }
    }
}


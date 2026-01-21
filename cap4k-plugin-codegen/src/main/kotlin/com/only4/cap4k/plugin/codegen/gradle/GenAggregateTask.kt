package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.context.aggregate.MutableAggregateContext
import com.only4.cap4k.plugin.codegen.context.aggregate.builders.*
import com.only4.cap4k.plugin.codegen.core.TagAliasResolver
import com.only4.cap4k.plugin.codegen.generators.unit.AggregateUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.AggregateWrapperUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.DomainEventHandlerUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.DomainEventUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.EntityUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.EnumTranslationUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.EnumUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.FactoryUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.GenerationPlan
import com.only4.cap4k.plugin.codegen.generators.unit.GenerationUnit
import com.only4.cap4k.plugin.codegen.generators.unit.RepositoryUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.SchemaBaseUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.SchemaUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.SpecificationUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.UniqueQueryHandlerUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.UniqueQueryUnitGenerator
import com.only4.cap4k.plugin.codegen.generators.unit.UniqueValidatorUnitGenerator
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
        val generators = listOf(
            SchemaBaseUnitGenerator(),
            EnumUnitGenerator(),
            EnumTranslationUnitGenerator(),
            EntityUnitGenerator(),
            UniqueQueryUnitGenerator(),
            UniqueQueryHandlerUnitGenerator(),
            UniqueValidatorUnitGenerator(),
            SpecificationUnitGenerator(),
            FactoryUnitGenerator(),
            DomainEventUnitGenerator(),
            DomainEventHandlerUnitGenerator(),
            RepositoryUnitGenerator(),
            AggregateWrapperUnitGenerator(),
            SchemaUnitGenerator(),
        )

        generateUnits(generators, context)
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

}


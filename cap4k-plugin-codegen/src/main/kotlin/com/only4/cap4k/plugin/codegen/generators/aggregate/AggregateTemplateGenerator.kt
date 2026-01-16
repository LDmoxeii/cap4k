package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.template.TemplateNode

interface AggregateTemplateGenerator {
    val tag: String

    val order: Int

    context(ctx: AggregateContext)
    fun shouldGenerate(table: Map<String, Any?>): Boolean

    context(ctx: AggregateContext)
    fun buildContext(table: Map<String, Any?>): Map<String, Any?>

    context(ctx: AggregateContext)
    fun generatorFullName(table: Map<String, Any?>): String

    context(ctx: AggregateContext)
    fun generatorName(table: Map<String, Any?>): String

    /**
     * 获取默认模板节点列表
     *
     * 一个表可能需要生成多个文件，例如：
     * - Entity 可能需要生成 Entity.kt + EntityRepository.kt + EntityMapper.kt
     * - Aggregate 可能需要生成 Aggregate.kt + AggregateService.kt
     *
     * 使用方可以通过 TemplateNode.pattern 属性来过滤需要生成的模板
     *
     * @return 模板节点列表
     */
    fun getDefaultTemplateNodes(): List<TemplateNode>

    context(ctx: AggregateContext)
    fun onGenerated(table: Map<String, Any?>) {}
}

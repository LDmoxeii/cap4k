package com.only4.cap4k.plugin.codegen.generators.aggregate

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.template.TemplateNode

interface AggregateGenerator {

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

    fun getDefaultTemplateNodes(): List<TemplateNode>

    context(ctx: AggregateContext)
    fun onGenerated(table: Map<String, Any?>) {}
}

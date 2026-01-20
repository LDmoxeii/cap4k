package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext

interface AggregateUnitGenerator {
    val tag: String
    val order: Int

    context(ctx: AggregateContext)
    fun collect(): List<GenerationUnit>
}

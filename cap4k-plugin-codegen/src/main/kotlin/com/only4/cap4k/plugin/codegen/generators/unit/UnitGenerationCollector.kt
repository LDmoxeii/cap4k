package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.context.aggregate.AggregateContext
import com.only4.cap4k.plugin.codegen.core.LoggerAdapter

class UnitGenerationCollector @JvmOverloads constructor(
    private val log: LoggerAdapter? = null,
) {
    fun collect(
        generators: List<AggregateUnitGenerator>,
        context: AggregateContext,
    ): List<GenerationUnit> {
        if (generators.isEmpty()) return emptyList()

        val plan = GenerationPlan(log)
        val units = mutableListOf<GenerationUnit>()
        val applied = mutableSetOf<String>()

        generators.forEach { generator ->
            val collected = with(context) { generator.collect() }
            if (collected.isEmpty()) return@forEach

            units.addAll(collected)

            val ordered = plan.addAll(collected)
            ordered.forEach { unit ->
                if (applied.add(unit.id)) {
                    unit.exportTypes.forEach { (simple, full) ->
                        context.typeMapping[simple] = full
                    }
                }
            }
        }

        return units
    }
}

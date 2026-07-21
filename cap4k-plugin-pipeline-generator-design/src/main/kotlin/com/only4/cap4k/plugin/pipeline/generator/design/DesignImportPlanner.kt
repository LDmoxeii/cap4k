package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.design.types.ImportResolver

internal object DesignImportPlanner {

    fun plan(
        types: List<DesignResolvedTypeModel>,
        innerTypeNames: Set<String> = emptySet(),
        symbolRegistry: TypeSymbolRegistry = TypeSymbolRegistry(),
        aggregateContext: List<String> = emptyList(),
    ): DesignImportPlan {
        return ImportResolver.plan(
            types = types,
            innerTypeNames = innerTypeNames,
            symbolRegistry = symbolRegistry,
            aggregateContext = aggregateContext,
        )
    }
}

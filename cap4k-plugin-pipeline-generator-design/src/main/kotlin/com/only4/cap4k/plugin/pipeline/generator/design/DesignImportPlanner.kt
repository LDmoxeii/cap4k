package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.generator.design.types.DesignSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.design.types.ImportResolver

internal object DesignImportPlanner {

    fun plan(
        types: List<DesignResolvedTypeModel>,
        innerTypeNames: Set<String> = emptySet(),
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    ): DesignImportPlan {
        return ImportResolver.plan(
            types = types,
            innerTypeNames = innerTypeNames,
            symbolRegistry = symbolRegistry,
        )
    }
}

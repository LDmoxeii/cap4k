package com.only4.cap4k.plugin.codegen.imports

/**
 * Aggregate 生成器的 Import 管理器
 */
class AggregateImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.Aggregate")
    }
}

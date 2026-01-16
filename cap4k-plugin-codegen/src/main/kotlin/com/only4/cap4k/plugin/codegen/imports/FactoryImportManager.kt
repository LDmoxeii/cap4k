package com.only4.cap4k.plugin.codegen.imports

/**
 * Factory 生成器的 Import 管理器
 */
class FactoryImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory")
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload")
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("org.springframework.stereotype.Service")
    }
}

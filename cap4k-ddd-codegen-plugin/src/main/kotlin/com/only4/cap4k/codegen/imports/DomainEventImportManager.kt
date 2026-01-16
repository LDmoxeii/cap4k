package com.only4.cap4k.codegen.imports

/**
 * DomainEvent 生成器的 Import 管理器
 */
class DomainEventImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent")
    }
}

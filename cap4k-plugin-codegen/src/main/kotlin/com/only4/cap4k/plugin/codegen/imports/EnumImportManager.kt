package com.only4.cap4k.plugin.codegen.imports

/**
 * Enum 生成器的 Import 管理器
 */
class EnumImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("jakarta.persistence.AttributeConverter")
        requiredImports.add("com.only.engine.exception.KnownException")
    }
}

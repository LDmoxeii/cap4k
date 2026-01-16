package com.only4.cap4k.plugin.codegen.imports

/**
 * IntegrationEvent 生成器的 Import 管理器
 */
class IntegrationEventImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.application.event.annotation.AutoRelease")
        requiredImports.add("com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent")
    }
}

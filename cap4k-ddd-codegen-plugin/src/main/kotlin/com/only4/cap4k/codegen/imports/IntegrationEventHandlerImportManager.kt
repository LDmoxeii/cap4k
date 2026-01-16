package com.only4.cap4k.codegen.imports

/**
 * IntegrationEventHandler 生成器的 Import 管理器
 */
class IntegrationEventHandlerImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("org.springframework.context.event.EventListener")
        requiredImports.add("org.springframework.stereotype.Service")
    }
}

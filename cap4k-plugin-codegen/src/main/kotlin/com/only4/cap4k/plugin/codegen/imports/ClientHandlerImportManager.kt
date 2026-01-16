package com.only4.cap4k.plugin.codegen.imports

/**
 * ClientHandler 生成器的 Import 管理器
 */
class ClientHandlerImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("org.springframework.stereotype.Service")
        requiredImports.add("com.only4.cap4k.ddd.core.application.RequestHandler")
    }
}

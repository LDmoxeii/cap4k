package com.only4.cap4k.plugin.codegen.imports

/**
 * Command 生成器的 Import 管理器
 */
class CommandImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.Mediator")
        requiredImports.add("com.only4.cap4k.ddd.core.application.RequestParam")
        requiredImports.add("com.only4.cap4k.ddd.core.application.command.Command")
        requiredImports.add("org.springframework.stereotype.Service")
    }
}

package com.only4.cap4k.plugin.codegen.imports

/**
 * DomainService 生成器的 Import 管理器
 */
class DomainServiceImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("org.slf4j.LoggerFactory")
        requiredImports.add("org.springframework.stereotype.Service")
        requiredImports.add("com.only4.cap4k.ddd.annotation.DomainService")
        requiredImports.add("com.only4.cap4k.ddd.core.share.X")
    }
}

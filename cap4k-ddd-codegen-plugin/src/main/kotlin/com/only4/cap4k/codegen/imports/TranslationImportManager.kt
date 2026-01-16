package com.only4.cap4k.codegen.imports

/**
 * 翻译器类的 Import 管理器
 */
class TranslationImportManager : BaseImportManager() {
    override fun addBaseImports() {
        requiredImports.add("com.only.engine.translation.annotation.TranslationType")
        requiredImports.add("com.only.engine.translation.core.TranslationInterface")
        requiredImports.add("com.only.engine.translation.core.BatchTranslationInterface")
        requiredImports.add("org.springframework.stereotype.Component")
    }
}


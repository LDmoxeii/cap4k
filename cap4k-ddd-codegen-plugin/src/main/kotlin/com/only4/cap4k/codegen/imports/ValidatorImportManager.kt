package com.only4.cap4k.codegen.imports

/**
 * Validator 模板的 Import 管理器
 * - 统一输出 jakarta.validation 与 Kotlin 相关依赖
 */
class ValidatorImportManager : BaseImportManager() {

    override fun addBaseImports() {
        requiredImports.add("jakarta.validation.Constraint")
        requiredImports.add("jakarta.validation.ConstraintValidator")
        requiredImports.add("jakarta.validation.ConstraintValidatorContext")
        requiredImports.add("jakarta.validation.Payload")
        requiredImports.add("kotlin.reflect.KClass")
    }
}


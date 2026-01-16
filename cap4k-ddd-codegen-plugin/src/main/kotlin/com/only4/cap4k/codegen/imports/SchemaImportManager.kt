package com.only4.cap4k.codegen.imports

class SchemaImportManager(private val schemaBasePackage: String) : BaseImportManager() {

    override fun addBaseImports() {
        requiredImports.add("org.springframework.data.jpa.domain.Specification")
        requiredImports.add("jakarta.persistence.criteria.*")

        // 导入 schema_base.kt 中定义的顶层类型
        requiredImports.add("$schemaBasePackage.SchemaSpecification")
        requiredImports.add("$schemaBasePackage.PredicateBuilder")
        requiredImports.add("$schemaBasePackage.OrderBuilder")
        requiredImports.add("$schemaBasePackage.ExpressionBuilder")
        requiredImports.add("$schemaBasePackage.SubqueryConfigure")
        requiredImports.add("$schemaBasePackage.Field")
    }

}

package com.only4.cap4k.plugin.codegen.imports

class EntityImportManager : BaseImportManager()  {
    override fun addBaseImports() {
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("jakarta.persistence.*")
        requiredImports.add("org.hibernate.annotations.DynamicInsert")
        requiredImports.add("org.hibernate.annotations.DynamicUpdate")
    }

}

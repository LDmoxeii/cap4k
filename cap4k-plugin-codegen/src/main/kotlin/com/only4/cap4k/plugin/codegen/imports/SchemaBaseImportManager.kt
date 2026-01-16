package com.only4.cap4k.plugin.codegen.imports

class SchemaBaseImportManager: BaseImportManager() {

    override fun addBaseImports() {
        requiredImports.add("jakarta.persistence.criteria.*")
        requiredImports.add("org.hibernate.query.NullPrecedence")
        requiredImports.add("org.hibernate.query.SortDirection")
        requiredImports.add("org.hibernate.query.sqm.tree.domain.SqmBasicValuedSimplePath")
        requiredImports.add("org.hibernate.query.sqm.tree.select.SqmSortSpecification")
    }

}

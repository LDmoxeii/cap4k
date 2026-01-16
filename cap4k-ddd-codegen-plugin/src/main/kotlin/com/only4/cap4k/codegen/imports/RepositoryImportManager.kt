package com.only4.cap4k.codegen.imports

class RepositoryImportManager : BaseImportManager() {

    override fun addBaseImports() {
        requiredImports.add("org.springframework.data.jpa.repository.JpaRepository")
        requiredImports.add("org.springframework.data.jpa.repository.JpaSpecificationExecutor")
        requiredImports.add("org.springframework.stereotype.Repository")
        requiredImports.add("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")
        requiredImports.add("com.only4.cap4k.ddd.domain.repo.AbstractJpaRepository")
        requiredImports.add("org.springframework.stereotype.Component")
    }

}

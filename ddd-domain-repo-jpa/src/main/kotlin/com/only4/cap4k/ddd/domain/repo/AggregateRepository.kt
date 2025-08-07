package com.only4.cap4k.ddd.domain.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.NoRepositoryBean

@NoRepositoryBean
interface AggregateRepository<T : Any, ID : Any> : JpaRepository<T, ID>, JpaSpecificationExecutor<T>

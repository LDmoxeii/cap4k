package com.only4.cap4k.ddd.application.saga.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * Saga仓储
 *
 * @author LD_moxeii
 * @date 2025/08/01
 */
interface SagaJpaRepository : JpaRepository<Saga, Long>, JpaSpecificationExecutor<Saga>
package com.only4.cap4k.ddd.application.saga.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * 归档Saga仓储
 *
 * @author LD_moxeii
 * @date 2025/08/01
 */
interface ArchivedSagaJpaRepository : JpaRepository<ArchivedSaga, Long>, JpaSpecificationExecutor<ArchivedSaga>
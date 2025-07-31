package com.only4.cap4k.ddd.application.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * 归档请求实体仓储
 *
 * @author binking338
 * @date 2025/5/16
 */
interface ArchivedRequestJpaRepository : JpaRepository<ArchivedRequest, Long>, JpaSpecificationExecutor<ArchivedRequest>
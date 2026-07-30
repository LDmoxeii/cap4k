package com.only4.cap4k.ddd.application.command.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * 归档命令实体仓储
 *
 * @author binking338
 * @date 2025/5/16
 */
interface ArchivedCommandRecordJpaRepository : JpaRepository<ArchivedCommandRecordEntity, Long>, JpaSpecificationExecutor<ArchivedCommandRecordEntity>

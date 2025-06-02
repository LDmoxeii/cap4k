package com.only4.cap4k.ddd.domain.event.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * 归档事件仓储
 */
interface ArchivedEventJpaRepository : JpaRepository<ArchivedEvent, Long>, JpaSpecificationExecutor<ArchivedEvent>

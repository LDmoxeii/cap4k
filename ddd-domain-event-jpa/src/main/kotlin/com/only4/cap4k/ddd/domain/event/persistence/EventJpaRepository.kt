package com.only4.cap4k.ddd.domain.event.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * 事件仓储
 */
interface EventJpaRepository : JpaRepository<Event, Long>, JpaSpecificationExecutor<Event>

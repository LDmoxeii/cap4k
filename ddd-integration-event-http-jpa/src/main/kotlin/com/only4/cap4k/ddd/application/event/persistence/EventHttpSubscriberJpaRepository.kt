package com.only4.cap4k.ddd.application.event.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * 集成事件HTTP订阅仓储
 *
 * @author binking338
 * @date 2025/5/23
 */
interface EventHttpSubscriberJpaRepository : JpaRepository<EventHttpSubscriber, Long>,
    JpaSpecificationExecutor<EventHttpSubscriber>

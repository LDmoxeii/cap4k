package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 基于Jpa的事件记录仓储实现
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
open class JpaEventRecordRepository(
    private val eventJpaRepository: EventJpaRepository,
) : EventRecordRepository {

    override fun create(): EventRecord = EventRecordImpl()

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(eventRecord: EventRecord) {
        val record = eventRecord as EventRecordImpl
        val event = eventJpaRepository.save(record.event)
        record.resume(event)
    }

    override fun getById(id: String): EventRecord {
        val event = eventJpaRepository.findOne { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<String>(Event.F_EVENT_UUID), id)
        }.orElseThrow { DomainException("EventRecord not found") }

        return EventRecordImpl().apply {
            resume(event)
        }
    }

}

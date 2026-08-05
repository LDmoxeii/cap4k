package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

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

    override fun getByNextTryTime(svcName: String, maxNextTryTime: LocalDateTime, limit: Int): List<EventRecord> {
        val events = eventJpaRepository.findAll({ root, cq, cb ->
            cq.where(
                cb.or(
                    cb.and(
                        // 【初始状态】
                        cb.equal(root.get<Event.EventState>(Event.F_EVENT_STATE), Event.EventState.INIT),
                        cb.lessThan(root.get(Event.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Event.F_SVC_NAME), svcName)
                    ),
                    cb.and(
                        // 【发送中状态】
                        cb.equal(root.get<Event.EventState>(Event.F_EVENT_STATE), Event.EventState.DELIVERING),
                        cb.lessThan(root.get(Event.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Event.F_SVC_NAME), svcName)
                    ),
                    cb.and(
                        // 【异常状态】
                        cb.equal(root.get<Event.EventState>(Event.F_EVENT_STATE), Event.EventState.EXCEPTION),
                        cb.lessThan(root.get(Event.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Event.F_SVC_NAME), svcName)
                    )
                )
            )
            null
        }, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Event.F_NEXT_TRY_TIME)))

        return events.map { event ->
            EventRecordImpl().apply {
                resume(event)
            }
        }.toList()
    }

}

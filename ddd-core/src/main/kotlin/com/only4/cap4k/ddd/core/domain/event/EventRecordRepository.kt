package com.only4.cap4k.ddd.core.domain.event

/**
 * 事件记录仓储
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface EventRecordRepository {
    fun create(): EventRecord
    fun save(eventRecord: EventRecord)
    fun getById(id: String): EventRecord
}

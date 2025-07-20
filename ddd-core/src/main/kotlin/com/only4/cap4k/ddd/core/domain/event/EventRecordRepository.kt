package com.only4.cap4k.ddd.core.domain.event

import java.time.LocalDateTime


/**
 * 事件记录仓储
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface EventRecordRepository {
    fun create(): EventRecord
    fun save(event: EventRecord)
    fun getById(id: String): EventRecord
    fun getByNextTryTime(svcName: String, maxNextTryTime: LocalDateTime, limit: Int): List<EventRecord>
    fun archiveByExpireAt(svcName: String, maxExpireAt: LocalDateTime, limit: Int): Int
}

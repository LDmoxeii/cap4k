package com.only4.cap4k.ddd.core.application.command

import java.time.LocalDateTime

interface CommandRecordRepository {
    fun create(): CommandRecord
    fun save(commandRecord: CommandRecord)
    fun getById(id: String): CommandRecord
    fun getByNextTryTime(serviceName: String, maxNextTryTime: LocalDateTime, limit: Int): List<CommandRecord>
    fun archiveByExpireAt(serviceName: String, maxExpireAt: LocalDateTime, limit: Int): Int
}

package com.only4.cap4k.ddd.core.application.command

interface CommandRecordRepository {
    fun create(): CommandRecord
    fun save(commandRecord: CommandRecord)
}

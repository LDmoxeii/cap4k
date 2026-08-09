package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.TestCommand
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class CommandRecordImplTest {
    @Test
    fun `registration carrier initializes immutable execution input`() {
        val scheduleAt = LocalDateTime.of(2026, 8, 7, 10, 0)
        val context = listOf(EncodedExecutionContextElement("actor", 1, "user-7"))
        val command = TestCommand("work")
        val record = CommandRecordImpl()

        record.init(command, "service", "test-command", scheduleAt, Duration.ofHours(1), 3, context)

        assertTrue(record.id.isNotBlank())
        assertEquals(CommandRecordEntity.CommandState.INIT, record.entity.commandState)
        assertEquals(scheduleAt, record.entity.nextTryTime)
        assertEquals(command, record.entity.commandParam)
        assertEquals(context, JpaExecutionContextEnvelope.decode(record.entity.executionContext))
        assertNotEquals(record.entity.param, record.entity.executionContext)
    }

    @Test
    fun `save resume replaces the carrier entity`() {
        val persisted = CommandRecordEntity(commandUuid = "persisted")
        val record = CommandRecordImpl()

        record.resume(persisted)

        assertEquals("persisted", record.id)
        assertEquals(persisted, record.entity)
    }
}

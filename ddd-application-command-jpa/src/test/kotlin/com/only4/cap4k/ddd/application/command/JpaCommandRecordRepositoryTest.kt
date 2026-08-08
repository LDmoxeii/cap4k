package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.application.command.persistence.TestCommand
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class JpaCommandRecordRepositoryTest {
    private val records = mockk<CommandRecordJpaRepository>()
    private val repository = JpaCommandRecordRepository(records)

    @Test
    fun `create returns independent registration carriers`() {
        val first = repository.create()
        val second = repository.create()
        assertTrue(first is CommandRecordImpl)
        assertNotSame(first, second)
    }

    @Test
    fun `save persists carrier and resumes generated identity`() {
        val carrier = repository.create() as CommandRecordImpl
        carrier.init(TestCommand("work"), "service", "test-command", LocalDateTime.now(), Duration.ofHours(1), 3)
        val persisted = CommandRecordEntity(commandUuid = carrier.id, id = 7)
        every { records.save(carrier.entity) } returns persisted

        repository.save(carrier)

        verify(exactly = 1) { records.save(any()) }
        assertEquals(persisted, carrier.entity)
    }
}

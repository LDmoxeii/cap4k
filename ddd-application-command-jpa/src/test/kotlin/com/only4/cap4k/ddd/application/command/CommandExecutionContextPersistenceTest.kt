package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.application.command.persistence.ArchivedCommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextDecodingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

class CommandExecutionContextPersistenceTest {
    @Test
    fun `payload and context are stored separately and archive preserves the original envelope`() {
        val context = listOf(EncodedExecutionContextElement("actor", 2, "user-7"))
        val record = CommandRecordImpl().apply {
            init(
                TestCommand("work"),
                "service",
                TestCommand::class.java.name,
                LocalDateTime.now(),
                Duration.ofMinutes(5),
                3,
                context,
            )
        }

        assertEquals(context, record.executionContext)
        assertNotEquals(record.entity.param, record.entity.executionContext)

        val archived = ArchivedCommandRecordEntity().archiveFrom(record.entity)
        assertEquals(record.entity.executionContext, archived.executionContext)
    }

    @Test
    fun `legacy null context decodes as empty`() {
        val record = CommandRecordImpl().apply {
            resume(CommandRecordEntity(executionContext = null))
        }

        assertEquals(emptyList<EncodedExecutionContextElement>(), record.executionContext)
    }

    @Test
    fun `duplicate persisted element fails before command execution`() {
        val record = CommandRecordImpl().apply {
            resume(
                CommandRecordEntity(
                    executionContext = """[{"name":"actor","version":1,"value":"a"},{"name":"actor","version":1,"value":"b"}]""",
                ),
            )
        }

        assertThrows<ExecutionContextDecodingException> { record.executionContext }
    }

    private data class TestCommand(val value: String) : Command<String>
}

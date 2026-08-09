package com.only4.cap4k.ddd.application.command

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
    fun `payload and context are stored separately`() {
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

        assertEquals(context, JpaExecutionContextEnvelope.decode(record.entity.executionContext))
        assertNotEquals(record.entity.param, record.entity.executionContext)

    }

    @Test
    fun `legacy null context decodes as empty`() {
        assertEquals(
            emptyList<EncodedExecutionContextElement>(),
            JpaExecutionContextEnvelope.decode(CommandRecordEntity(executionContext = null).executionContext),
        )
    }

    @Test
    fun `duplicate persisted element fails before command execution`() {
        val persisted = CommandRecordEntity(
            executionContext = """[{"name":"actor","version":1,"value":"a"},{"name":"actor","version":1,"value":"b"}]""",
        )

        assertThrows<ExecutionContextDecodingException> {
            JpaExecutionContextEnvelope.decode(persisted.executionContext)
        }
    }

    private data class TestCommand(val value: String) : Command<String>
}

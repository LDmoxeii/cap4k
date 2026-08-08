package com.only4.cap4k.ddd.application.command.persistence

import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.share.annotation.Retry
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class CommandRetryPolicySnapshotTest {
    private val createdAt = LocalDateTime.of(2025, 1, 15, 10, 30)

    @Test
    fun `stored policy survives active reload and ignores the currently loaded command annotation`() {
        val original = CommandRecordEntity().init(
            commandParam = OriginalPolicyCommand("business-secret"),
            svcName = "test-service",
            commandType = "ORIGINAL_POLICY",
            scheduleAt = createdAt,
            expireAfter = Duration.ofHours(1),
            retryTimes = 99,
        )
        val snapshot = RuntimeJson.read(original.retryPolicy, ReliableRetryPolicySnapshot::class.java)
        assertEquals(4, snapshot.retryLimit)
        assertFalse(original.retryPolicy.contains("business-secret"))

        val reloaded = CommandRecordEntity(
            commandUuid = original.commandUuid,
            svcName = original.svcName,
            commandType = original.commandType,
            param = RuntimeJson.write(CurrentPolicyCommand("changed")),
            paramType = CurrentPolicyCommand::class.java.name,
            expireAt = original.expireAt,
            createAt = original.createAt,
            lastTryTime = createdAt,
            nextTryTime = createdAt,
            triedTimes = 1,
            tryTimes = original.tryTimes,
            retryPolicy = original.retryPolicy,
        )
        assertEquals(original.retryPolicy, reloaded.retryPolicy)
        val persistedPolicy = RuntimeJson.read(reloaded.retryPolicy, ReliableRetryPolicySnapshot::class.java)
        assertEquals(7, persistedPolicy.delayMinutesFor(2))
    }

    @Retry(retryTimes = 4, retryIntervals = [2, 7], expireAfter = 30)
    private data class OriginalPolicyCommand(val secret: String) : Command<String>

    @Retry(retryTimes = 40, retryIntervals = [99], expireAfter = 300)
    private data class CurrentPolicyCommand(val value: String) : Command<String>
}

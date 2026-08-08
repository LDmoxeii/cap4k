package com.only4.cap4k.ddd.domain.event.persistence

import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.annotation.Retry
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class EventRetryPolicySnapshotTest {
    private val createdAt = LocalDateTime.of(2025, 1, 15, 10, 30)

    @Test
    fun `stored policy survives reload and ignores the currently loaded event annotation`() {
        val original = Event().init(
            payload = OriginalPolicyEvent("business-secret"),
            svcName = "test-service",
            scheduleAt = createdAt,
            expireAfter = Duration.ofHours(1),
            retryTimes = 99,
        )
        val snapshot = RuntimeJson.read(original.retryPolicy, ReliableRetryPolicySnapshot::class.java)

        assertEquals(4, snapshot.retryLimit)
        assertEquals(listOf(2L, 7L), snapshot.delaySteps.map { it.delayMinutes })
        assertFalse(original.retryPolicy.contains("business-secret"))

        val reloaded = Event(
            eventUuid = original.eventUuid,
            svcName = original.svcName,
            eventType = original.eventType,
            data = RuntimeJson.write(CurrentPolicyEvent("changed")),
            dataType = CurrentPolicyEvent::class.java.name,
            expireAt = original.expireAt,
            createAt = original.createAt,
            publishedAt = original.publishedAt,
            lastTryTime = createdAt,
            nextTryTime = createdAt,
            triedTimes = 1,
            tryTimes = original.tryTimes,
            retryPolicy = original.retryPolicy,
        )
        val reloadedSnapshot = RuntimeJson.read(reloaded.retryPolicy, ReliableRetryPolicySnapshot::class.java)

        assertEquals(original.retryPolicy, reloaded.retryPolicy)
        assertEquals(snapshot, reloadedSnapshot)
        assertEquals(1, reloaded.triedTimes)
        assertEquals(createdAt, reloaded.nextTryTime)
    }

    @DomainEvent("retry.original")
    @Retry(retryTimes = 4, retryIntervals = [2, 7], expireAfter = 30)
    private data class OriginalPolicyEvent(val secret: String)

    @DomainEvent("retry.current")
    @Retry(retryTimes = 40, retryIntervals = [99], expireAfter = 300)
    private data class CurrentPolicyEvent(val value: String)
}

package com.only4.cap4k.ddd.core.share.retry

import com.only4.cap4k.ddd.core.share.annotation.Retry
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ReliableRetryPolicySnapshotTest {

    @Test
    fun `captures the current default curve as immutable facts`() {
        val snapshot = ReliableRetryPolicySnapshot.capture(retry = null, fallbackRetryLimit = 30)

        assertEquals(ReliableRetryPolicySnapshot.CURRENT_POLICY_VERSION, snapshot.policyVersion)
        assertEquals(30, snapshot.retryLimit)
        assertEquals(RetryableClassification.ANY_EXCEPTION, snapshot.retryableClassification)
        assertEquals(1, snapshot.delayMinutesFor(0))
        assertEquals(1, snapshot.delayMinutesFor(10))
        assertEquals(5, snapshot.delayMinutesFor(11))
        assertEquals(5, snapshot.delayMinutesFor(20))
        assertEquals(10, snapshot.delayMinutesFor(21))
    }

    @Test
    fun `captures annotation overrides and repeats the final interval`() {
        val retry = CustomRetryPolicy::class.java.getAnnotation(Retry::class.java)

        val snapshot = ReliableRetryPolicySnapshot.capture(retry, fallbackRetryLimit = 99)

        assertEquals(4, snapshot.retryLimit)
        assertEquals(2, snapshot.delayMinutesFor(0))
        assertEquals(2, snapshot.delayMinutesFor(1))
        assertEquals(7, snapshot.delayMinutesFor(2))
        assertEquals(7, snapshot.delayMinutesFor(200))
    }

    @Test
    fun `round trips through RuntimeJson without business payload facts`() {
        val snapshot = ReliableRetryPolicySnapshot.capture(
            CustomRetryPolicy::class.java.getAnnotation(Retry::class.java),
            fallbackRetryLimit = 99,
        )

        val json = RuntimeJson.write(snapshot)
        val restored = RuntimeJson.read(json, ReliableRetryPolicySnapshot::class.java)

        assertEquals(snapshot, restored)
        assertFalse(json.contains("business-secret"))
        assertFalse(json.contains(CustomRetryPolicy::class.java.name))
    }

    @Retry(retryTimes = 4, retryIntervals = [2, 7])
    private class CustomRetryPolicy
}

package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.domain.event.EventRecord
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class IntegrationEventPublishCompletionTest {
    private val event = mockk<EventRecord>()
    private val callback = mockk<IntegrationEventPublisher.PublishCallback>()

    @Test
    fun `first success wins and duplicate terminal transitions are ignored`() {
        every { callback.onSuccess(event) } just runs

        val completion = IntegrationEventPublishCompletion(event, callback)
        completion.success()
        completion.success()
        completion.failure(IllegalStateException("late failure"))

        verify(exactly = 1) { callback.onSuccess(event) }
        verify(exactly = 0) { callback.onException(any(), any()) }
    }

    @Test
    fun `first failure wins and duplicate terminal transitions are ignored`() {
        val failure = IllegalStateException("send failed")
        every { callback.onException(event, failure) } just runs

        val completion = IntegrationEventPublishCompletion(event, callback)
        completion.failure(failure)
        completion.failure(IllegalStateException("duplicate failure"))
        completion.success()

        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 1) { callback.onException(event, failure) }
    }

    @Test
    fun `success callback exception does not invoke failure callback`() {
        every { callback.onSuccess(event) } throws IllegalStateException("callback failed")

        val completion = IntegrationEventPublishCompletion(event, callback)
        completion.success()
        completion.failure(IllegalStateException("late failure"))

        verify(exactly = 1) { callback.onSuccess(event) }
        verify(exactly = 0) { callback.onException(any(), any()) }
    }

    @Test
    fun `failure callback exception does not invoke success callback`() {
        val failure = IllegalStateException("send failed")
        every { callback.onException(event, failure) } throws IllegalStateException("callback failed")

        val completion = IntegrationEventPublishCompletion(event, callback)
        completion.failure(failure)
        completion.success()

        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 1) { callback.onException(event, failure) }
    }
}

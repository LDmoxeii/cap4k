package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.domain.event.EventRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the provider-level terminal boundary for an Integration Event publish.
 *
 * The first terminal transition wins. The guard is marked before user callback
 * code runs, so a callback exception cannot reopen the attempt or invoke the
 * opposite callback.
 */
class IntegrationEventPublishCompletion(
    private val event: EventRecord,
    private val callback: IntegrationEventPublisher.PublishCallback,
) {
    private val completed = AtomicBoolean(false)

    fun success() {
        if (!completed.compareAndSet(false, true)) return
        runCatching { callback.onSuccess(event) }
            .onFailure { log.warn("Integration Event success callback failed after terminal completion") }
    }

    fun failure(throwable: Throwable) {
        if (!completed.compareAndSet(false, true)) return
        runCatching { callback.onException(event, throwable) }
            .onFailure { log.warn("Integration Event failure callback failed after terminal completion") }
    }

    companion object {
        private val log = LoggerFactory.getLogger(IntegrationEventPublishCompletion::class.java)
    }
}

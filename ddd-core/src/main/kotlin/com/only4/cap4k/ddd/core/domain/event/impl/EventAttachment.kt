package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import java.time.LocalDateTime

internal class EventAttachment<out EVENT : Any> private constructor(
    private val payload: EVENT?,
    private val supplier: (() -> EVENT)?,
    val schedule: LocalDateTime,
    val executionContext: ExecutionContextSnapshot,
) {
    private object Unresolved

    private var resolvedPayload: Any? = Unresolved

    fun resolve(): EVENT {
        payload?.let { return it }

        if (resolvedPayload === Unresolved) {
            resolvedPayload = supplier!!.invoke()
        }

        @Suppress("UNCHECKED_CAST")
        return resolvedPayload as EVENT
    }

    fun matches(candidate: Any): Boolean {
        payload?.let { return it === candidate || it == candidate }

        if (resolvedPayload !== Unresolved) {
            return resolvedPayload === candidate || resolvedPayload == candidate
        }

        return false
    }

    fun matchesIdentity(candidate: Any): Boolean {
        payload?.let { return it === candidate }

        if (resolvedPayload !== Unresolved) {
            return resolvedPayload === candidate
        }

        return false
    }

    companion object {
        fun <EVENT : Any> eager(
            payload: EVENT,
            schedule: LocalDateTime = LocalDateTime.now(),
            executionContext: ExecutionContextSnapshot = ExecutionContextSnapshot.EMPTY,
        ): EventAttachment<EVENT> = EventAttachment(payload, null, schedule, executionContext)

        fun <EVENT : Any> lazy(
            schedule: LocalDateTime = LocalDateTime.now(),
            executionContext: ExecutionContextSnapshot = ExecutionContextSnapshot.EMPTY,
            supplier: () -> EVENT,
        ): EventAttachment<EVENT> = EventAttachment(null, supplier, schedule, executionContext)
    }
}

package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextAccessor
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager

class DefaultReliableEventDeliveryContextManager(
    private val executionContextAccessor: ExecutionContextAccessor,
    private val executionContextScopeManager: ExecutionContextScopeManager,
) : ReliableEventDeliveryContextAccessor, ReliableEventDeliveryContextScopeManager {
    override fun currentOrNull(): ReliableEventDeliveryContext? = executionContextAccessor.current()[CONTEXT_KEY]

    override fun install(context: ReliableEventDeliveryContext): AutoCloseable =
        executionContextScopeManager.install(
            executionContextAccessor.current()
                .toBuilder()
                .replace(CONTEXT_KEY, context)
                .build(),
        )

    override fun suppress(): AutoCloseable =
        executionContextScopeManager.install(
            executionContextAccessor.current()
                .toBuilder()
                .remove(CONTEXT_KEY)
                .build(),
        )

    private companion object {
        val CONTEXT_KEY = ExecutionContextKey(
            "cap4k.reliable-event-delivery",
            ReliableEventDeliveryContext::class.java,
        )
    }
}

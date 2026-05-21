package com.only4.cap4k.ddd.core.domain.event.impl

import java.util.ArrayDeque

internal object EventRuntimeContext {
    private val scopes = ThreadLocal<ArrayDeque<EventRuntimeScope>>()

    fun push(type: EventRuntimeScopeType): EventRuntimeScope {
        val stack = scopes.get() ?: ArrayDeque<EventRuntimeScope>().also(scopes::set)
        val scope = EventRuntimeScope(type)
        stack.addLast(scope)
        return scope
    }

    fun pop(scope: EventRuntimeScope) {
        val stack = scopes.get() ?: throw IllegalStateException("No event runtime scope is active")
        if (stack.peekLast() !== scope) {
            throw IllegalStateException("Event runtime scope can only pop the current scope")
        }

        stack.removeLast()
        if (stack.isEmpty()) {
            scopes.remove()
        }
    }

    fun current(): EventRuntimeScope =
        currentOrNull() ?: throw IllegalStateException("No event runtime scope is active")

    fun currentOrNull(): EventRuntimeScope? = scopes.get()?.peekLast()

    fun currentOrCreateAmbient(): EventRuntimeScope = currentOrNull() ?: push(EventRuntimeScopeType.AMBIENT)

    fun hasScope(): Boolean = currentOrNull() != null

    fun discard(scope: EventRuntimeScope) {
        scope.clearAttachments()
    }

    fun reset() {
        scopes.get()?.forEach(EventRuntimeScope::clearAttachments)
        scopes.remove()
    }
}

package com.only4.cap4k.ddd.core.domain.event.impl

import java.util.ArrayDeque

internal object EventRuntimeContext {
    private val scopes = ThreadLocal<ArrayDeque<EventRuntimeScope>>()
    private val causalFrames = ThreadLocal<ArrayDeque<String>>()
    private val lastCausalPath = ThreadLocal<List<String>>()

    fun <RESULT> withCausalFrame(frame: String, block: () -> RESULT): RESULT {
        val stack = causalFrames.get() ?: ArrayDeque<String>().also(causalFrames::set)
        if (stack.isEmpty()) lastCausalPath.remove()
        stack.addLast(frame)
        lastCausalPath.set(stack.toList())
        var completed = false
        return try {
            block().also { completed = true }
        } finally {
            check(stack.peekLast() == frame) {
                "Application execution causal frames must unwind in stack order"
            }
            stack.removeLast()
            if (stack.isEmpty()) {
                causalFrames.remove()
                if (completed) lastCausalPath.remove()
            }
        }
    }

    fun diagnosticCausalPath(): List<String> = lastCausalPath.get().orEmpty()

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

    fun restoreTo(scope: EventRuntimeScope?) {
        val stack = scopes.get() ?: return
        while (stack.isNotEmpty() && stack.peekLast() !== scope) {
            stack.removeLast().clearAttachments()
        }
        if (stack.isEmpty()) {
            scopes.remove()
        }
    }

    fun current(): EventRuntimeScope =
        currentOrNull() ?: throw IllegalStateException("No event runtime scope is active")

    fun currentOrNull(): EventRuntimeScope? = scopes.get()?.peekLast()

    fun currentUnitOfWorkOrNull(): EventRuntimeScope? {
        val iterator = scopes.get()?.descendingIterator() ?: return null
        while (iterator.hasNext()) {
            val scope = iterator.next()
            if (scope.type == EventRuntimeScopeType.UNIT_OF_WORK) return scope
        }
        return null
    }

    fun attachmentScope(): EventRuntimeScope = currentUnitOfWorkOrNull() ?: currentOrCreateAmbient()

    fun beginUnitOfWork() {
        check(currentUnitOfWorkOrNull() == null) { "A Unit of Work event scope is already active" }
        push(EventRuntimeScopeType.UNIT_OF_WORK)
    }

    fun endUnitOfWork() {
        val scope = currentUnitOfWorkOrNull() ?: return
        restoreTo(scope)
        discard(scope)
        pop(scope)
    }

    fun currentOrCreateAmbient(): EventRuntimeScope = currentOrNull() ?: push(EventRuntimeScopeType.AMBIENT)

    fun hasScope(): Boolean = currentOrNull() != null

    fun discard(scope: EventRuntimeScope) {
        scope.clearAttachments()
    }

    fun reset() {
        scopes.get()?.forEach(EventRuntimeScope::clearAttachments)
        scopes.remove()
        causalFrames.remove()
        lastCausalPath.remove()
    }
}

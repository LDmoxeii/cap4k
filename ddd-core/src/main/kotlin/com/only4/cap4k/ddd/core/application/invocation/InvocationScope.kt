package com.only4.cap4k.ddd.core.application.invocation

import java.util.ArrayDeque

enum class InvocationKind {
    COMMAND,
    QUERY,
    CAPABILITY,
    DOMAIN_EVENT_HANDLER,
}

fun interface InvocationScopeAccessor {
    fun current(): InvocationKind?
}

fun interface InvocationScopeManager {
    fun enter(kind: InvocationKind): AutoCloseable
}

class DefaultInvocationScopeManager : InvocationScopeAccessor, InvocationScopeManager {
    private val scopes = ThreadLocal<ArrayDeque<Scope>>()

    override fun current(): InvocationKind? = scopes.get()?.peekLast()?.kind

    override fun enter(kind: InvocationKind): AutoCloseable {
        val stack = scopes.get() ?: ArrayDeque<Scope>().also(scopes::set)
        val scope = Scope(kind)
        stack.addLast(scope)
        return AutoCloseable { close(scope) }
    }

    private fun close(scope: Scope) {
        check(!scope.closed) { "InvocationScope is already closed" }
        val stack = scopes.get() ?: error("No InvocationScope is active")
        check(stack.peekLast() === scope) { "InvocationScope must close in LIFO order" }
        scope.closed = true
        stack.removeLast()
        if (stack.isEmpty()) scopes.remove()
    }

    private class Scope(
        val kind: InvocationKind,
        var closed: Boolean = false,
    )
}

class InvocationNotAllowedException(
    val currentKind: InvocationKind,
    val requestedKind: InvocationKind,
    val asynchronous: Boolean,
) : IllegalStateException(
    "Invocation $requestedKind${if (asynchronous) " (async)" else ""} is not allowed from $currentKind",
)

class InvocationPolicy(
    private val scopeAccessor: InvocationScopeAccessor,
) {
    fun check(requestedKind: InvocationKind, asynchronous: Boolean = false) {
        val current = scopeAccessor.current() ?: return
        val allowed = when (current) {
            InvocationKind.COMMAND -> requestedKind == InvocationKind.COMMAND ||
                requestedKind == InvocationKind.CAPABILITY
            InvocationKind.QUERY -> requestedKind == InvocationKind.CAPABILITY ||
                (requestedKind == InvocationKind.QUERY && !asynchronous)
            InvocationKind.CAPABILITY -> requestedKind == InvocationKind.CAPABILITY
            InvocationKind.DOMAIN_EVENT_HANDLER -> requestedKind == InvocationKind.COMMAND ||
                requestedKind == InvocationKind.CAPABILITY
        }
        if (!allowed) throw InvocationNotAllowedException(current, requestedKind, asynchronous)
    }
}

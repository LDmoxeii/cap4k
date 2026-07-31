package com.only4.cap4k.ddd.core.application.context

import java.util.ArrayDeque

class DefaultExecutionContextManager : ExecutionContextAccessor, ExecutionContextScopeManager {
    private val scopes = ThreadLocal<ArrayDeque<Scope>>()

    override fun current(): ExecutionContextSnapshot =
        scopes.get()?.peekLast()?.snapshot ?: ExecutionContextSnapshot.EMPTY

    override fun install(snapshot: ExecutionContextSnapshot): AutoCloseable {
        val stack = scopes.get() ?: ArrayDeque<Scope>().also(scopes::set)
        val scope = Scope(snapshot)
        stack.addLast(scope)
        return AutoCloseable { close(scope) }
    }

    private fun close(scope: Scope) {
        check(!scope.closed) { "ExecutionContext scope is already closed" }
        val stack = scopes.get() ?: error("No ExecutionContext scope is active")
        check(stack.peekLast() === scope) { "ExecutionContext scopes must close in LIFO order" }
        scope.closed = true
        stack.removeLast()
        if (stack.isEmpty()) scopes.remove()
    }

    private class Scope(
        val snapshot: ExecutionContextSnapshot,
        var closed: Boolean = false,
    )
}

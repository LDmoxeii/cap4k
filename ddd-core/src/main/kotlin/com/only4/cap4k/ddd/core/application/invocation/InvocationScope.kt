package com.only4.cap4k.ddd.core.application.invocation

import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

enum class InvocationKind {
    COMMAND,
    QUERY,
    CAPABILITY,
    DOMAIN_EVENT_HANDLER,
}

fun interface InvocationScopeAccessor {
    fun current(): InvocationKind?
}

interface InvocationScope : AutoCloseable {
    fun <RESULT> complete(block: () -> RESULT): RESULT
}

interface InvocationScopeManager {
    fun enter(kind: InvocationKind): InvocationScope

    fun <RESULT : Any> track(stage: CompletionStage<RESULT>): CompletionStage<RESULT>
}

class DefaultInvocationScopeManager : InvocationScopeAccessor, InvocationScopeManager {
    private val scopes = ThreadLocal<ArrayDeque<Scope>>()

    override fun current(): InvocationKind? = scopes.get()?.peekLast()?.kind

    override fun enter(kind: InvocationKind): InvocationScope {
        val stack = scopes.get() ?: ArrayDeque<Scope>().also(scopes::set)
        val scope = Scope(kind, closeAction = { close(it) })
        stack.addLast(scope)
        return scope
    }

    override fun <RESULT : Any> track(stage: CompletionStage<RESULT>): CompletionStage<RESULT> {
        scopes.get()?.peekLast()?.track(stage)
        return stage
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
        private val closeAction: (Scope) -> Unit,
        var closed: Boolean = false,
    ) : InvocationScope {
        private val tasks = mutableListOf<CompletableFuture<Unit>>()
        private var completed = false

        fun track(stage: CompletionStage<*>) {
            check(!completed) { "Cannot register an async task after InvocationScope completion" }
            val normalized = CompletableFuture<Unit>()
            try {
                stage.whenComplete { _, error ->
                    if (error == null) {
                        normalized.complete(Unit)
                    } else {
                        normalized.completeExceptionally(unwrap(error))
                    }
                }
            } catch (error: Throwable) {
                normalized.completeExceptionally(unwrap(error))
            }
            tasks += normalized
        }

        override fun <RESULT> complete(block: () -> RESULT): RESULT {
            check(!completed) { "InvocationScope can only complete once" }
            var result: Any? = null
            var bodyFailure: Throwable? = null
            try {
                result = block()
            } catch (error: Throwable) {
                bodyFailure = unwrap(error)
            }

            completed = true
            val taskFailures = tasks.mapNotNull { task ->
                try {
                    task.join()
                    null
                } catch (error: Throwable) {
                    unwrap(error)
                }
            }
            val primaryFailure = bodyFailure ?: taskFailures.firstOrNull()
            if (primaryFailure != null) {
                taskFailures
                    .asSequence()
                    .filter { failure -> failure !== primaryFailure }
                    .distinctBy { failure -> System.identityHashCode(failure) }
                    .forEach(primaryFailure::addSuppressed)
                throw primaryFailure
            }

            @Suppress("UNCHECKED_CAST")
            return result as RESULT
        }

        override fun close() = closeAction(this)
    }

    companion object {
        private fun unwrap(error: Throwable): Throwable = when (error) {
            is CompletionException -> error.cause?.let(::unwrap) ?: error
            is ExecutionException -> error.cause?.let(::unwrap) ?: error
            else -> error
        }
    }
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
                requestedKind == InvocationKind.QUERY ||
                requestedKind == InvocationKind.CAPABILITY
        }
        if (!allowed) throw InvocationNotAllowedException(current, requestedKind, asynchronous)
    }
}

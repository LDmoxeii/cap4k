package com.only4.cap4k.ddd.core.application.context

import java.util.concurrent.Callable
import java.util.concurrent.Executor

class ExecutionContextPropagation(
    private val accessor: ExecutionContextAccessor,
    private val scopeManager: ExecutionContextScopeManager,
) {
    fun wrap(task: Runnable): Runnable {
        val snapshot = accessor.current()
        return Runnable { withSnapshot(snapshot) { task.run() } }
    }

    fun <RESULT> wrap(task: Callable<RESULT>): Callable<RESULT> {
        val snapshot = accessor.current()
        return Callable { withSnapshot(snapshot) { task.call() } }
    }

    fun decorate(executor: Executor): Executor = Executor { task -> executor.execute(wrap(task)) }

    fun <RESULT> withSnapshot(snapshot: ExecutionContextSnapshot, block: () -> RESULT): RESULT {
        val scope = scopeManager.install(snapshot)
        return try {
            block()
        } finally {
            scope.close()
        }
    }
}

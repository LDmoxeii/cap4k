package com.only4.cap4k.ddd.core.application.async

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class AsyncOverloadStrategy {
    CALLER_RUNS,
    REJECT,
}

interface ApplicationAsyncExecutor : AutoCloseable {
    fun <RESULT : Any> submit(task: () -> RESULT): CompletionStage<RESULT>
}

class BoundedApplicationAsyncExecutor(
    workerCount: Int,
    queueCapacity: Int,
    overloadStrategy: AsyncOverloadStrategy = AsyncOverloadStrategy.CALLER_RUNS,
    threadNamePrefix: String = "cap4k-application-",
    threadFactory: ThreadFactory = namedThreadFactory(threadNamePrefix),
) : ApplicationAsyncExecutor, AutoCloseable {
    private val overloadStrategy = overloadStrategy
    private val executor: ThreadPoolExecutor

    init {
        require(workerCount > 0) { "Async executor workerCount must be positive" }
        require(queueCapacity > 0) { "Async executor queueCapacity must be positive" }
        executor = ThreadPoolExecutor(
            workerCount,
            workerCount,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(queueCapacity),
            threadFactory,
            ThreadPoolExecutor.AbortPolicy(),
        )
    }

    override fun <RESULT : Any> submit(task: () -> RESULT): CompletionStage<RESULT> {
        val result = CompletableFuture<RESULT>()
        val command = Runnable {
            try {
                result.complete(task())
            } catch (ex: Throwable) {
                result.completeExceptionally(ex)
            }
        }
        try {
            executor.execute(command)
        } catch (ex: RejectedExecutionException) {
            when {
                executor.isShutdown -> result.completeExceptionally(
                    RejectedExecutionException("Cap4k async executor is shut down", ex),
                )
                overloadStrategy == AsyncOverloadStrategy.CALLER_RUNS -> command.run()
                else -> result.completeExceptionally(ex)
            }
        } catch (ex: Throwable) {
            result.completeExceptionally(ex)
        }
        return result
    }

    override fun close() {
        executor.shutdown()
    }

    companion object {
        private fun namedThreadFactory(prefix: String): ThreadFactory {
            require(prefix.isNotBlank()) { "Async executor threadNamePrefix must not be blank" }
            val sequence = AtomicLong()
            return ThreadFactory { task -> Thread(task, "$prefix${sequence.incrementAndGet()}") }
        }
    }
}

internal fun <RESULT : Any> failedStage(error: Throwable): CompletionStage<RESULT> =
    CompletableFuture<RESULT>().also { it.completeExceptionally(error) }

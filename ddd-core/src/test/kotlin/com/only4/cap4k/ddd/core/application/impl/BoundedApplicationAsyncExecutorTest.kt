package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.application.async.AsyncOverloadStrategy
import com.only4.cap4k.ddd.core.application.async.BoundedApplicationAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class BoundedApplicationAsyncExecutorTest {
    @Test
    fun `caller runs only when active executor is saturated`() {
        val executor = BoundedApplicationAsyncExecutor(
            workerCount = 1,
            queueCapacity = 1,
            overloadStrategy = AsyncOverloadStrategy.CALLER_RUNS,
            threadNamePrefix = "caller-runs-test-",
        )
        try {
            val workerStarted = CountDownLatch(1)
            val releaseWorker = CountDownLatch(1)
            val worker = executor.submit {
                workerStarted.countDown()
                releaseWorker.await(5, TimeUnit.SECONDS)
                "worker"
            }
            workerStarted.await(5, TimeUnit.SECONDS)
            val queued = executor.submit { "queued" }
            val callerThread = Thread.currentThread()
            val caller = executor.submit { Thread.currentThread() }

            assertSame(callerThread, caller.toCompletableFuture().get())
            releaseWorker.countDown()
            assertEquals("worker", worker.toCompletableFuture().get())
            assertEquals("queued", queued.toCompletableFuture().get())
        } finally {
            executor.close()
        }
    }

    @Test
    fun `reject strategy and shutdown report failures through stages`() {
        val executor = BoundedApplicationAsyncExecutor(
            workerCount = 1,
            queueCapacity = 1,
            overloadStrategy = AsyncOverloadStrategy.REJECT,
            threadNamePrefix = "reject-test-",
        )
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        try {
            val worker = executor.submit {
                workerStarted.countDown()
                releaseWorker.await(5, TimeUnit.SECONDS)
                "worker"
            }
            workerStarted.await(5, TimeUnit.SECONDS)
            val queued = executor.submit { "queued" }
            val rejected = executor.submit { "rejected" }

            assertThrows<ExecutionException> { rejected.toCompletableFuture().get() }.also {
                assertEquals(RejectedExecutionException::class.java, it.cause?.javaClass)
            }
            releaseWorker.countDown()
            worker.toCompletableFuture().get()
            queued.toCompletableFuture().get()
        } finally {
            releaseWorker.countDown()
            executor.close()
        }

        val afterShutdown = executor.submit { "never" }
        assertThrows<ExecutionException> { afterShutdown.toCompletableFuture().get() }.also {
            assertEquals(RejectedExecutionException::class.java, it.cause?.javaClass)
        }
    }

    @Test
    fun `task failures complete the stage exceptionally`() {
        val executor = BoundedApplicationAsyncExecutor(1, 1, threadNamePrefix = "failure-test-")
        try {
            val stage = executor.submit<String> { error("boom") }
            val failure = assertThrows<ExecutionException> { stage.toCompletableFuture().get() }
            assertEquals("boom", failure.cause?.message)
        } finally {
            executor.close()
        }
    }
}

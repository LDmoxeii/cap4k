package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.application.JpaOwnershipClaim
import com.only4.cap4k.ddd.application.JpaRedriveResult
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandWakeUp
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.share.misc.createScheduledThreadPool
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Private reliable Command worker.
 *
 * Durable ownership remains entirely in [JpaCommandExecutionSubstrate]. This
 * worker only wakes, claims, invokes the synchronous [CommandSupervisor], and
 * completes the claimed attempt through token-fenced acknowledge/fail calls.
 */
class JpaReliableCommandWorker(
    private val substrate: JpaCommandExecutionSubstrate,
    private val commandSupervisor: CommandSupervisor,
    private val executionContextScopeManager: ExecutionContextScopeManager,
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry,
    private val serviceName: String,
    private val workerCount: Int,
    private val batchSize: Int,
    private val pollInterval: Duration,
    private val leaseDuration: Duration,
    private val renewInterval: Duration,
    private val threadFactoryClassName: String,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ReliableCommandWakeUp, AutoCloseable {
    private val log = LoggerFactory.getLogger(JpaReliableCommandWorker::class.java)
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private val workerExecutor: ScheduledExecutorService by lazy {
        createScheduledThreadPool(workerCount, threadFactoryClassName, javaClass.classLoader)
    }
    private val renewalExecutor: ScheduledExecutorService by lazy {
        createScheduledThreadPool(1, threadFactoryClassName, javaClass.classLoader)
    }
    private var sweep: ScheduledFuture<*>? = null

    init {
        require(workerCount > 0) { "reliable Command worker count must be positive" }
        require(batchSize > 0) { "reliable Command batch size must be positive" }
        require(pollInterval > Duration.ZERO) { "reliable Command poll interval must be positive" }
        require(leaseDuration > Duration.ZERO) { "reliable Command lease duration must be positive" }
        require(renewInterval > Duration.ZERO && renewInterval < leaseDuration) {
            "reliable Command renew interval must be positive and shorter than the lease duration"
        }
    }

    fun init() {
        if (!started.compareAndSet(false, true)) return
        workerExecutor
        renewalExecutor
        sweep = workerExecutor.scheduleWithFixedDelay(
            { poll() },
            0,
            pollInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    override fun wakeUp(@Suppress("UNUSED_PARAMETER") scheduleAt: LocalDateTime) {
        if (closed.get()) return
        // The database nextTryTime is the durable schedule. A commit notification
        // only prompts one immediate scan; future Commands are recovered by the
        // periodic sweep without mirroring every row into an unbounded timer queue.
        submitWorker()
    }

    /**
     * Performs one explicit operator redrive and wakes the worker only after the
     * durable CAS reset is accepted; duplicate replays do not schedule another wake.
     */
    fun redrive(
        recordId: Long,
        expectedVersion: Int,
        expectedState: CommandRecordEntity.CommandState,
        requestToken: String,
        now: LocalDateTime = clock(),
    ): JpaRedriveResult {
        val result = substrate.redrive(
            recordId = recordId,
            serviceName = serviceName,
            expectedVersion = expectedVersion,
            expectedState = expectedState,
            requestToken = requestToken,
            now = now,
        )
        if (result == JpaRedriveResult.REDRIVEN) wakeUp(now)
        return result
    }

    fun poll() {
        if (closed.get()) return
        repeat(workerCount) {
            if (closed.get()) return
            submitWorker()
        }
    }

    internal fun processAvailable(): Int {
        var processed = 0
        repeat(batchSize) {
            if (closed.get()) return processed
            val ownership = substrate.claim(
                serviceName = serviceName,
                now = clock(),
                leaseDuration = leaseDuration,
                candidateLimit = batchSize,
            ) ?: return processed
            process(ownership)
            processed += 1
        }
        return processed
    }

    private fun process(ownership: JpaOwnershipClaim) {
        var renewal: ScheduledFuture<*>? = null
        try {
            if (closed.get()) return
            val claimed = substrate.load(ownership, clock()) ?: return
            if (closed.get()) return
            renewal = scheduleRenewal(ownership) ?: return
            val snapshot = executionContextCodecRegistry.decodeReliable(
                claimed.executionContext,
                ExecutionContextBoundary.RELIABLE_COMMAND,
            )
            executionContextScopeManager.install(snapshot).use {
                @Suppress("UNCHECKED_CAST")
                commandSupervisor.send(claimed.command as Command<Any>)
            }
            if (!substrate.acknowledge(ownership, clock())) {
                log.warn("Reliable Command acknowledgement lost ownership recordId={}", ownership.recordId)
            }
        } catch (failure: Throwable) {
            if (closed.get()) {
                log.info(
                    "Reliable Command attempt interrupted by worker shutdown recordId={} failureType={}",
                    ownership.recordId,
                    failure.javaClass.name,
                )
                return
            }
            val transitioned = runCatching { substrate.fail(ownership, clock(), failure) }
                .onFailure { transitionFailure ->
                    log.error(
                        "Reliable Command failure transition failed recordId={} failureType={}",
                        ownership.recordId,
                        transitionFailure.javaClass.name,
                    )
                }
                .getOrDefault(false)
            if (!transitioned) {
                log.warn(
                    "Reliable Command failure lost ownership recordId={} failureType={}",
                    ownership.recordId,
                    failure.javaClass.name,
                )
            }
        } finally {
            renewal?.cancel(false)
        }
    }

    private fun scheduleRenewal(ownership: JpaOwnershipClaim): ScheduledFuture<*>? = try {
        renewalExecutor.scheduleWithFixedDelay(
            { renew(ownership) },
            renewInterval.toMillis(),
            renewInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    } catch (rejected: RejectedExecutionException) {
        if (!closed.get()) {
            log.error(
                "Reliable Command lease renewal could not start recordId={} failureType={}",
                ownership.recordId,
                rejected.javaClass.name,
            )
        }
        null
    }

    private fun submitWorker() {
        try {
            workerExecutor.execute { processAvailable() }
        } catch (rejected: RejectedExecutionException) {
            if (!closed.get()) throw rejected
        }
    }

    private fun renew(ownership: JpaOwnershipClaim) {
        runCatching { substrate.renew(ownership, clock(), leaseDuration) }
            .onFailure { failure ->
                log.warn(
                    "Reliable Command lease renewal failed recordId={} failureType={}",
                    ownership.recordId,
                    failure.javaClass.name,
                )
            }
            .onSuccess { renewed ->
                if (!renewed) {
                    log.warn("Reliable Command lease renewal lost ownership recordId={}", ownership.recordId)
                }
            }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        sweep?.cancel(false)
        workerExecutor.shutdownNow()
        renewalExecutor.shutdownNow()
    }
}

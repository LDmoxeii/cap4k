package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.application.JpaOwnershipClaim
import com.only4.cap4k.ddd.application.JpaRedriveResult
import com.only4.cap4k.contract.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Private ownership-driven reliable Event coordinator. */
class JpaEventScheduleService(
    private val eventPublisher: EventPublisher,
    private val executionSubstrate: JpaEventExecutionSubstrate,
    private val eventJpaRepository: EventJpaRepository,
    private val serviceName: String,
    private val batchSize: Int,
    private val leaseDuration: Duration,
    private val leaseRenewInterval: Duration,
    workerThreads: Int,
    private val enableAddPartition: Boolean,
    private val jdbcTemplate: JdbcTemplate,
) : ReliableEventCoordinator {
    private val worker = Executors.newFixedThreadPool(workerThreads.coerceAtLeast(1))
    private val leaseRenewer = Executors.newSingleThreadScheduledExecutor()
    private val drainRunning = AtomicBoolean(false)
    private val wakeRequested = AtomicBoolean(false)

    init {
        require(batchSize > 0) { "Event delivery batch size must be positive" }
        require(!leaseDuration.isZero && !leaseDuration.isNegative) {
            "Event delivery lease duration must be positive"
        }
        require(!leaseRenewInterval.isZero && !leaseRenewInterval.isNegative) {
            "Event delivery lease renewal interval must be positive"
        }
        require(leaseRenewInterval < leaseDuration) {
            "Event delivery lease renewal interval must be shorter than the lease duration"
        }
    }

    fun init() {
        addPartition()
    }

    override fun wake() {
        wakeRequested.set(true)
        startDrainIfNecessary()
    }
    /**
     * Performs one explicit operator redrive and wakes the coordinator only
     * after the durable CAS reset is accepted; duplicate replays do not schedule another wake.
     */
    fun redrive(
        recordId: Long,
        expectedVersion: Int,
        expectedState: Event.EventState,
        requestToken: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): JpaRedriveResult {
        val result = executionSubstrate.redrive(
            recordId = recordId,
            serviceName = serviceName,
            expectedVersion = expectedVersion,
            expectedState = expectedState,
            requestToken = requestToken,
            now = now,
        )
        if (result == JpaRedriveResult.REDRIVEN) wake()
        return result
    }

    /** Scheduled recovery signal. Ownership remains database-coordinated. */
    fun retry() = wake()

    internal fun drainNow(): Int = drainBatch()

    fun shutdown() {
        worker.shutdown()
        leaseRenewer.shutdown()
    }

    private fun startDrainIfNecessary() {
        if (!drainRunning.compareAndSet(false, true)) return
        worker.execute {
            try {
                do {
                    wakeRequested.set(false)
                    val processed = drainBatch()
                } while (wakeRequested.get() || processed == batchSize)
            } catch (throwable: Throwable) {
                log.error("Reliable Event drain failed", throwable)
            } finally {
                drainRunning.set(false)
                if (wakeRequested.get()) startDrainIfNecessary()
            }
        }
    }

    private fun drainBatch(): Int {
        var processed = 0
        repeat(batchSize) {
            val ownership = executionSubstrate.claim(
                serviceName = serviceName,
                now = LocalDateTime.now(),
                leaseDuration = leaseDuration,
                candidateLimit = batchSize,
            ) ?: return processed
            process(ownership)
            processed += 1
        }
        return processed
    }

    private fun process(ownership: JpaOwnershipClaim) {
        val entity = runCatching {
            eventJpaRepository.findById(ownership.recordId).orElse(null)
        }.getOrElse { throwable ->
            failOwnership(ownership, throwable)
            return
        }
        if (entity == null) {
            failOwnership(
                ownership,
                IllegalStateException("Claimed reliable Event record disappeared: ${ownership.recordId}"),
            )
            return
        }
        if (!owns(entity, ownership)) {
            log.warn("Claimed Event ownership could not be loaded: recordId={}", ownership.recordId)
            return
        }
        val event = runCatching {
            EventRecordImpl().apply {
                resume(entity)
                markPersist(true)
            }
        }.getOrElse { throwable ->
            failOwnership(ownership, throwable)
            return
        }
        val integrationEvent = runCatching {
            event.payload.javaClass.isAnnotationPresent(IntegrationEvent::class.java)
        }.getOrElse { throwable ->
            failOwnership(ownership, throwable)
            return
        }
        val completed = AtomicBoolean(false)
        val completionMonitor = Any()
        var renewal: ScheduledFuture<*>? = null

        fun finish(action: () -> Boolean, outcome: String) {
            synchronized(completionMonitor) {
                if (completed.get()) return
                val transitioned = runCatching { action() }
                    .onFailure { throwable ->
                        log.warn(
                            "Reliable Event {} transition failed: eventId={}, recordId={}",
                            outcome,
                            event.id,
                            ownership.recordId,
                            throwable,
                        )
                    }.getOrDefault(false)
                if (transitioned) {
                    completed.set(true)
                    renewal?.cancel(false)
                } else {
                    log.warn(
                        "Reliable Event {} lost ownership before transition: eventId={}, recordId={}",
                        outcome,
                        event.id,
                        ownership.recordId,
                    )
                }
            }
        }

        renewal = leaseRenewer.scheduleAtFixedRate(
            {
                runCatching {
                    executionSubstrate.renew(ownership, LocalDateTime.now(), leaseDuration)
                }.onFailure { throwable ->
                    log.warn("Reliable Event lease renewal failed: eventId={}", event.id, throwable)
                }.onSuccess { renewed ->
                    if (!renewed) renewal?.cancel(false)
                }
            },
            leaseRenewInterval.toMillis(),
            leaseRenewInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )

        val completion = object : EventPublisher.Completion {
            override fun onSuccess(event: EventRecord) = finish(
                action = { executionSubstrate.acknowledge(ownership, LocalDateTime.now()) },
                outcome = "acknowledgement",
            )

            override fun onFailure(event: EventRecord, throwable: Throwable) = finish(
                action = { executionSubstrate.fail(ownership, LocalDateTime.now(), throwable) },
                outcome = "failure",
            )
        }

        runCatching { eventPublisher.publish(event, completion) }
            .onFailure { throwable -> completion.onFailure(event, throwable) }

        if (!integrationEvent && !completed.get()) {
            completion.onFailure(
                event,
                IllegalStateException("Reliable Domain Event publisher returned without synchronous completion"),
            )
        }
    }

    private fun failOwnership(ownership: JpaOwnershipClaim, throwable: Throwable) {
        runCatching {
            executionSubstrate.fail(ownership, LocalDateTime.now(), throwable)
        }.onFailure { failure ->
            log.warn(
                "Reliable Event failure transition threw: recordId={}, failureType={}",
                ownership.recordId,
                failure.javaClass.name,
                failure,
            )
        }.onSuccess { transitioned ->
            if (!transitioned) {
                log.warn(
                    "Reliable Event failure transition lost ownership: recordId={}",
                    ownership.recordId,
                )
            }
        }
    }

    private fun owns(entity: Event?, ownership: JpaOwnershipClaim): Boolean =
        entity != null &&
            entity.eventState == Event.EventState.DELIVERING &&
            entity.deliveryToken?.contentEquals(ownership.token.toByteArray()) == true &&
            entity.leaseUntil?.isAfter(LocalDateTime.now()) == true

    fun addPartition() {
        if (!enableAddPartition) return
        val now = LocalDateTime.now()
        addPartition("__event", now.plusMonths(1))
    }

    private fun addPartition(table: String, date: LocalDateTime) {
        val sql =
            "alter table $table add partition (partition p${date.format(DateTimeFormatter.ofPattern("yyyyMM"))} " +
                "values less than (to_days('${
                    date.plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                }-01')) ENGINE=InnoDB)"

        try {
            jdbcTemplate.execute(sql)
        } catch (ex: Exception) {
            if (ex.message?.contains("Duplicate partition") != true) {
                log.error(
                    "Event partition creation failed: table={}, partition={}, failureType={}",
                    table,
                    "p${date.format(DateTimeFormatter.ofPattern("yyyyMM"))}",
                    ex.javaClass.name,
                )
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(JpaEventScheduleService::class.java)
    }
}

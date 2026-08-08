package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.application.JpaOwnershipClaim
import com.only4.cap4k.ddd.application.ReliableJpaOwnership
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.share.ReliableFailureFacts
import com.only4.cap4k.ddd.core.share.ReliableFailureOperation
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

/** Private reliable Command ownership substrate. It deliberately does not execute Commands. */
open class JpaCommandExecutionSubstrate(
    private val records: CommandRecordJpaRepository,
) {
    /** Loads immutable execution input only while the supplied ownership is still live. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    open fun load(ownership: JpaOwnershipClaim, now: LocalDateTime): JpaClaimedCommand? {
        val effectiveNow = ReliableJpaOwnership.normalize(now)
        val record = records.findById(ownership.recordId).orElse(null) ?: return null
        if (record.commandState != CommandRecordEntity.CommandState.EXECUTING) return null
        if (record.deliveryToken?.contentEquals(ownership.token.toByteArray()) != true) return null
        if (record.leaseUntil?.isAfter(effectiveNow) != true) return null
        return JpaClaimedCommand(
            command = requireNotNull(record.commandParam) {
                "claimed Command record ${ownership.recordId} has no payload"
            },
            executionContext = JpaExecutionContextEnvelope.decode(record.executionContext),
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun claim(
        serviceName: String,
        now: LocalDateTime,
        leaseDuration: Duration,
        candidateLimit: Int = DEFAULT_CANDIDATE_LIMIT,
    ): JpaOwnershipClaim? {
        require(candidateLimit > 0) { "candidate limit must be positive" }
        val effectiveNow = ReliableJpaOwnership.normalize(now)
        val leaseUntil = ReliableJpaOwnership.leaseUntil(effectiveNow, leaseDuration)
        records.findExpiredCandidates(
            serviceName = serviceName,
            readyStates = READY_STATES,
            ownedState = CommandRecordEntity.CommandState.EXECUTING,
            now = effectiveNow,
            pageable = PageRequest.of(0, candidateLimit),
        ).forEach { expired ->
            val recordId = requireNotNull(expired.id) { "persisted Command record must have an id" }
            records.terminalizeExpired(
                recordId = recordId,
                version = expired.version,
                serviceName = serviceName,
                readyStates = READY_STATES,
                ownedState = CommandRecordEntity.CommandState.EXECUTING,
                expiredState = CommandRecordEntity.CommandState.EXPIRED,
                failureFacts = terminalFailureFacts(
                    existing = expired.failureFacts,
                    operation = ReliableFailureOperation.COMMAND_EXECUTION,
                    occurredAt = effectiveNow,
                    attempt = expired.triedTimes,
                    correlationId = expired.commandUuid.ifBlank { "command-$recordId" },
                ),
                now = effectiveNow,
            )
        }
        val candidates = records.findClaimCandidates(
            serviceName = serviceName,
            now = effectiveNow,
            readyStates = READY_STATES,
            ownedState = CommandRecordEntity.CommandState.EXECUTING,
            pageable = PageRequest.of(0, candidateLimit),
        )

        for (candidate in candidates) {
            val recordId = requireNotNull(candidate.id) { "persisted Command record must have an id" }
            val nextAttempt = candidate.triedTimes + 1
            val retryPolicy = RuntimeJson.read(
                candidate.retryPolicy,
                ReliableRetryPolicySnapshot::class.java,
            )
            if (candidate.triedTimes >= retryPolicy.retryLimit) {
                records.terminalizeExhausted(
                    recordId = recordId,
                    version = candidate.version,
                    serviceName = serviceName,
                    readyStates = READY_STATES,
                    ownedState = CommandRecordEntity.CommandState.EXECUTING,
                    exhaustedState = CommandRecordEntity.CommandState.EXHAUSTED,
                    retryLimit = retryPolicy.retryLimit,
                    failureFacts = terminalFailureFacts(
                        existing = candidate.failureFacts,
                        operation = ReliableFailureOperation.COMMAND_EXECUTION,
                        occurredAt = effectiveNow,
                        attempt = candidate.triedTimes,
                        correlationId = candidate.commandUuid.ifBlank { "command-$recordId" },
                    ),
                    now = effectiveNow,
                )
                continue
            }
            val nextTryTime = effectiveNow.plusMinutes(retryPolicy.delayMinutesFor(nextAttempt))
            val token = ReliableJpaOwnership.issueToken()
            val updated = records.claim(
                recordId = recordId,
                serviceName = serviceName,
                readyStates = READY_STATES,
                ownedState = CommandRecordEntity.CommandState.EXECUTING,
                now = effectiveNow,
                nextTryTime = nextTryTime,
                token = token.toByteArray(),
                leaseUntil = leaseUntil,
                retryLimit = retryPolicy.retryLimit,
            )
            if (updated == 1) {
                return JpaOwnershipClaim(recordId, token, leaseUntil)
            }
        }
        return null
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun renew(
        ownership: JpaOwnershipClaim,
        now: LocalDateTime,
        leaseDuration: Duration,
    ): Boolean {
        val effectiveNow = ReliableJpaOwnership.normalize(now)
        return records.renew(
            recordId = ownership.recordId,
            token = ownership.token.toByteArray(),
            ownedState = CommandRecordEntity.CommandState.EXECUTING,
            now = effectiveNow,
            leaseUntil = ReliableJpaOwnership.leaseUntil(effectiveNow, leaseDuration),
        ) == 1
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun acknowledge(ownership: JpaOwnershipClaim, now: LocalDateTime): Boolean = records.acknowledge(
        recordId = ownership.recordId,
        token = ownership.token.toByteArray(),
        ownedState = CommandRecordEntity.CommandState.EXECUTING,
        successState = CommandRecordEntity.CommandState.EXECUTED,
        now = ReliableJpaOwnership.normalize(now),
    ) == 1

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun fail(ownership: JpaOwnershipClaim, now: LocalDateTime, throwable: Throwable): Boolean {
        val effectiveNow = ReliableJpaOwnership.normalize(now)
        val record = records.findById(ownership.recordId).orElse(null) ?: return false
        val retryPolicy = RuntimeJson.read(record.retryPolicy, ReliableRetryPolicySnapshot::class.java)
        val failureState = when {
            !record.expireAt.isAfter(effectiveNow) -> CommandRecordEntity.CommandState.EXPIRED
            record.triedTimes >= retryPolicy.retryLimit -> CommandRecordEntity.CommandState.EXHAUSTED
            else -> CommandRecordEntity.CommandState.EXCEPTION
        }
        val retryable = failureState == CommandRecordEntity.CommandState.EXCEPTION
        val facts = ReliableFailureFacts.capture(
            operation = ReliableFailureOperation.COMMAND_EXECUTION,
            throwable = throwable,
            occurredAt = effectiveNow,
            attempt = record.triedTimes.coerceAtLeast(1),
            correlationId = record.commandUuid,
            retryable = retryable,
        )
        val nextTryTime = if (retryable) {
            effectiveNow.plusMinutes(retryPolicy.delayMinutesFor(record.triedTimes))
        } else {
            record.nextTryTime
        }
        return records.transitionFailure(
            recordId = ownership.recordId,
            token = ownership.token.toByteArray(),
            ownedState = CommandRecordEntity.CommandState.EXECUTING,
            failureState = failureState,
            failureFacts = RuntimeJson.write(facts),
            nextTryTime = nextTryTime,
            now = effectiveNow,
        ) == 1
    }

    private fun terminalFailureFacts(
        existing: ReliableFailureFacts?,
        operation: ReliableFailureOperation,
        occurredAt: LocalDateTime,
        attempt: Int,
        correlationId: String,
    ): String = RuntimeJson.write(
        existing?.copy(retryable = false, terminal = true)
            ?: ReliableFailureFacts(
                type = "cap4k.runtime.ReliableTerminalization",
                message = operation.safeMessage,
                occurredAt = occurredAt,
                attempt = attempt.coerceAtLeast(1),
                correlationId = correlationId,
                retryable = false,
                terminal = true,
            )
    )

    private companion object {
        const val DEFAULT_CANDIDATE_LIMIT = 32
        val READY_STATES = setOf(
            CommandRecordEntity.CommandState.INIT,
            CommandRecordEntity.CommandState.EXCEPTION,
        )
    }
}

/** Immutable input exposed only to the private reliable Command worker. */
data class JpaClaimedCommand(
    val command: Command<*>,
    val executionContext: List<EncodedExecutionContextElement>,
)

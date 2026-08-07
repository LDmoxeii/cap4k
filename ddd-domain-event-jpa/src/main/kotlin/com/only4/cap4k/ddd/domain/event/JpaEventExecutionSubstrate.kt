package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.application.JpaOwnershipClaim
import com.only4.cap4k.ddd.application.ReliableJpaOwnership
import com.only4.cap4k.ddd.core.share.ReliableFailureFacts
import com.only4.cap4k.ddd.core.share.ReliableFailureOperation
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.share.retry.ReliableRetryPolicySnapshot
import com.only4.cap4k.ddd.domain.event.persistence.Event
import com.only4.cap4k.ddd.domain.event.persistence.EventJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

/** Private reliable Event ownership substrate. It deliberately does not publish events. */
open class JpaEventExecutionSubstrate(
    private val records: EventJpaRepository,
) {
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
        val candidates = records.findClaimCandidates(
            serviceName = serviceName,
            now = effectiveNow,
            readyStates = READY_STATES,
            ownedState = Event.EventState.DELIVERING,
            pageable = PageRequest.of(0, candidateLimit),
        )

        for (candidate in candidates) {
            val recordId = requireNotNull(candidate.id) { "persisted Event record must have an id" }
            val nextAttempt = candidate.triedTimes + 1
            val retryPolicy = RuntimeJson.read(
                candidate.retryPolicy,
                ReliableRetryPolicySnapshot::class.java,
            )
            val nextTryTime = effectiveNow.plusMinutes(retryPolicy.delayMinutesFor(nextAttempt))
            val token = ReliableJpaOwnership.issueToken()
            val updated = records.claim(
                recordId = recordId,
                serviceName = serviceName,
                readyStates = READY_STATES,
                ownedState = Event.EventState.DELIVERING,
                now = effectiveNow,
                nextTryTime = nextTryTime,
                token = token,
                leaseUntil = leaseUntil,
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
            token = ownership.token,
            ownedState = Event.EventState.DELIVERING,
            now = effectiveNow,
            leaseUntil = ReliableJpaOwnership.leaseUntil(effectiveNow, leaseDuration),
        ) == 1
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun acknowledge(ownership: JpaOwnershipClaim, now: LocalDateTime): Boolean = records.acknowledge(
        recordId = ownership.recordId,
        token = ownership.token,
        ownedState = Event.EventState.DELIVERING,
        successState = Event.EventState.DELIVERED,
        now = ReliableJpaOwnership.normalize(now),
    ) == 1

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun fail(ownership: JpaOwnershipClaim, now: LocalDateTime, throwable: Throwable): Boolean {
        val effectiveNow = ReliableJpaOwnership.normalize(now)
        val record = records.findById(ownership.recordId).orElse(null) ?: return false
        val retryPolicy = RuntimeJson.read(record.retryPolicy, ReliableRetryPolicySnapshot::class.java)
        val failureState = when {
            !record.expireAt.isAfter(effectiveNow) -> Event.EventState.EXPIRED
            record.triedTimes >= retryPolicy.retryLimit -> Event.EventState.EXHAUSTED
            else -> Event.EventState.EXCEPTION
        }
        val retryable = failureState == Event.EventState.EXCEPTION
        val facts = ReliableFailureFacts.capture(
            operation = ReliableFailureOperation.EVENT_DELIVERY,
            throwable = throwable,
            occurredAt = effectiveNow,
            attempt = record.triedTimes.coerceAtLeast(1),
            correlationId = record.eventUuid,
            retryable = retryable,
        )
        val nextTryTime = if (retryable) {
            effectiveNow.plusMinutes(retryPolicy.delayMinutesFor(record.triedTimes))
        } else {
            record.nextTryTime
        }
        return records.transitionFailure(
            recordId = ownership.recordId,
            token = ownership.token,
            ownedState = Event.EventState.DELIVERING,
            failureState = failureState,
            failureFacts = RuntimeJson.write(facts),
            nextTryTime = nextTryTime,
            now = effectiveNow,
        ) == 1
    }

    private companion object {
        const val DEFAULT_CANDIDATE_LIMIT = 32
        val READY_STATES = setOf(
            Event.EventState.INIT,
            Event.EventState.EXCEPTION,
        )
    }
}
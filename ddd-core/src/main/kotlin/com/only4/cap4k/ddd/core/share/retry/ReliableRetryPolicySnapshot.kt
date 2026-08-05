package com.only4.cap4k.ddd.core.share.retry

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.only4.cap4k.ddd.core.share.annotation.Retry

/**
 * Immutable retry-policy facts captured when a reliable Command/Event record is created.
 *
 * This is deliberately a persistence contract rather than a generic scheduling API. Reliable
 * state machines use it to keep historical retry decisions independent from later annotation or
 * runtime-default changes.
 */
@JsonPropertyOrder("policyVersion", "retryLimit", "retryableClassification", "delaySteps")
data class ReliableRetryPolicySnapshot(
    val policyVersion: Int,
    val retryLimit: Int,
    val retryableClassification: RetryableClassification,
    val delaySteps: List<RetryDelayStep>,
) {
    init {
        require(policyVersion > 0) { "Retry policy version must be positive" }
        require(delaySteps.isNotEmpty()) { "Retry policy delay steps must not be empty" }
        require(delaySteps.last().throughAttempt == null) { "Retry policy delay steps must end with a fallback" }
    }

    fun delayMinutesFor(attempt: Int): Long = delaySteps
        .first { step -> step.throughAttempt == null || attempt <= step.throughAttempt }
        .delayMinutes

    companion object {
        const val CURRENT_POLICY_VERSION: Int = 1

        @JvmStatic
        fun capture(retry: Retry?, fallbackRetryLimit: Int): ReliableRetryPolicySnapshot {
            val retryLimit = retry?.retryTimes ?: fallbackRetryLimit
            val intervals = retry?.retryIntervals ?: intArrayOf()
            val delaySteps = if (intervals.isEmpty()) {
                listOf(
                    RetryDelayStep(throughAttempt = 10, delayMinutes = 1),
                    RetryDelayStep(throughAttempt = 20, delayMinutes = 5),
                    RetryDelayStep(throughAttempt = null, delayMinutes = 10),
                )
            } else {
                intervals.mapIndexed { index, minutes ->
                    RetryDelayStep(
                        throughAttempt = if (index == intervals.lastIndex) null else index + 1,
                        delayMinutes = minutes.toLong(),
                    )
                }
            }
            return ReliableRetryPolicySnapshot(
                policyVersion = CURRENT_POLICY_VERSION,
                retryLimit = retryLimit,
                retryableClassification = RetryableClassification.ANY_EXCEPTION,
                delaySteps = delaySteps,
            )
        }
    }
}

@JsonPropertyOrder("throughAttempt", "delayMinutes")
data class RetryDelayStep(
    val throughAttempt: Int?,
    val delayMinutes: Long,
)

enum class RetryableClassification {
    ANY_EXCEPTION,
}

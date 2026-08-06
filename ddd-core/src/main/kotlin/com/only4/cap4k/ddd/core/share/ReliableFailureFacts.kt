package com.only4.cap4k.ddd.core.share

import java.time.LocalDateTime

/**
 * Structured diagnostics retained for a failed reliable delivery attempt.
 *
 * Arbitrary exception messages, causes, stack traces, and business payloads are
 * intentionally excluded. The framework owns [message] so persisted facts and
 * record diagnostics are safe to expose to operators and tooling.
 */
data class ReliableFailureFacts(
    val type: String,
    val message: String,
    val occurredAt: LocalDateTime,
    val attempt: Int,
    val correlationId: String,
    val retryable: Boolean,
    val terminal: Boolean,
) {
    init {
        require(type.isNotBlank()) { "Failure type must not be blank" }
        require(message in ReliableFailureOperation.entries.map { it.safeMessage }) {
            "Failure message must be framework-owned"
        }
        require(attempt > 0) { "Failure attempt must be positive" }
        require(correlationId.isNotBlank()) { "Failure correlationId must not be blank" }
        require(retryable != terminal) { "Failure must be exactly retryable or terminal" }
    }

    companion object {
        fun capture(
            operation: ReliableFailureOperation,
            throwable: Throwable,
            occurredAt: LocalDateTime,
            attempt: Int,
            correlationId: String,
            retryable: Boolean,
        ): ReliableFailureFacts = ReliableFailureFacts(
            type = throwable.javaClass.name,
            message = operation.safeMessage,
            occurredAt = occurredAt,
            attempt = attempt,
            correlationId = correlationId,
            retryable = retryable,
            terminal = !retryable,
        )
    }
}

enum class ReliableFailureOperation(val safeMessage: String) {
    COMMAND_EXECUTION("Reliable Command execution failed"),
    EVENT_DELIVERY("Reliable Event delivery failed"),
}
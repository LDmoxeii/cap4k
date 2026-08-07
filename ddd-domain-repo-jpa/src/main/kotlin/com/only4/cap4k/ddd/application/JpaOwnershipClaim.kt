package com.only4.cap4k.ddd.application

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Private runtime ownership value shared by reliable JPA Command and Event substrates.
 *
 * This is deliberately not a scheduler or a user-facing task abstraction. The row id,
 * opaque token, and lease expiry are the complete durable ownership facts returned by a claim.
 */
data class JpaOwnershipClaim(
    val recordId: Long,
    val token: String,
    val leaseUntil: LocalDateTime,
) {
    init {
        require(token.isNotBlank()) { "ownership token must not be blank" }
    }
}

object ReliableJpaOwnership {
    fun issueToken(): String = UUID.randomUUID().toString().replace("-", "")

    fun normalize(time: LocalDateTime): LocalDateTime = time.truncatedTo(ChronoUnit.MILLIS)

    fun leaseUntil(now: LocalDateTime, leaseDuration: Duration): LocalDateTime {
        require(!leaseDuration.isZero && !leaseDuration.isNegative) {
            "lease duration must be positive"
        }
        return normalize(now).plus(leaseDuration).truncatedTo(ChronoUnit.MILLIS)
    }
}
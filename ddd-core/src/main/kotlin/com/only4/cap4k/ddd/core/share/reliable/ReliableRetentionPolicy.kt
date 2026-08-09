package com.only4.cap4k.ddd.core.share.reliable

import java.time.Duration

/**
 * Explicit retention policy for Runtime-owned reliable records.
 *
 * The policy is intentionally a small value contract shared by the JPA Command and Event
 * substrates. It is not a scheduler configuration or a generic persistence API.
 */
data class ReliableRetentionPolicy(
    val successful: Duration,
    val exhausted: Duration,
    val expired: Duration,
    val batchLimit: Int,
) {
    init {
        require(!successful.isNegative) { "Successful retention must not be negative" }
        require(!exhausted.isNegative) { "Exhausted retention must not be negative" }
        require(!expired.isNegative) { "Expired retention must not be negative" }
        require(batchLimit > 0) { "Retention batch limit must be positive" }
    }
}

/** Safe aggregate facts returned by one bounded cleanup attempt. */
data class ReliableCleanupResult(
    val examined: Int,
    val deleted: Int,
) {
    init {
        require(examined >= 0) { "Examined count must not be negative" }
        require(deleted >= 0) { "Deleted count must not be negative" }
        require(deleted <= examined) { "Deleted count must not exceed examined count" }
    }
}

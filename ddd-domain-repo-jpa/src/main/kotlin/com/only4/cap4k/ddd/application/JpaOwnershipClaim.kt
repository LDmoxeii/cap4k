package com.only4.cap4k.ddd.application

import java.time.Duration
import java.time.LocalDateTime
import java.nio.charset.StandardCharsets
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Immutable private ownership token whose equality is the exact stored byte sequence.
 *
 * Tokens are fixed-width ASCII hexadecimal values. Parsing deliberately preserves case so
 * database fencing cannot silently inherit text collation or padding semantics.
 */
class JpaOwnershipToken private constructor(value: ByteArray) {
    private val bytes: ByteArray = value.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    fun asText(): String = String(bytes, StandardCharsets.US_ASCII)

    override fun equals(other: Any?): Boolean =
        this === other || (other is JpaOwnershipToken && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "JpaOwnershipToken(**redacted**)"

    companion object {
        const val BYTE_LENGTH = 32

        @JvmStatic
        fun fromText(value: String): JpaOwnershipToken = fromBytes(
            value.toByteArray(StandardCharsets.US_ASCII),
        )

        @JvmStatic
        fun fromBytes(value: ByteArray): JpaOwnershipToken {
            val snapshot = value.copyOf()
            require(snapshot.size == BYTE_LENGTH) {
                "ownership token must contain exactly $BYTE_LENGTH bytes"
            }
            require(snapshot.all(::isAsciiHex)) {
                "ownership token must contain only ASCII hexadecimal bytes"
            }
            return JpaOwnershipToken(snapshot)
        }

        private fun isAsciiHex(value: Byte): Boolean = value.toInt() in '0'.code..'9'.code ||
            value.toInt() in 'a'.code..'f'.code ||
            value.toInt() in 'A'.code..'F'.code
    }
}

/**
 * Private runtime ownership value shared by reliable JPA Command and Event substrates.
 *
 * This is deliberately not a scheduler or a user-facing task abstraction. The row id,
 * opaque token, and lease expiry are the complete durable ownership facts returned by a claim.
 */
data class JpaOwnershipClaim(
    val recordId: Long,
    val token: JpaOwnershipToken,
    val leaseUntil: LocalDateTime,
)

object ReliableJpaOwnership {
    fun issueToken(): JpaOwnershipToken = JpaOwnershipToken.fromText(
        UUID.randomUUID().toString().replace("-", ""),
    )

    fun normalize(time: LocalDateTime): LocalDateTime = time.truncatedTo(ChronoUnit.MILLIS)

    fun leaseUntil(now: LocalDateTime, leaseDuration: Duration): LocalDateTime {
        require(!leaseDuration.isZero && !leaseDuration.isNegative) {
            "lease duration must be positive"
        }
        return normalize(now).plus(leaseDuration).truncatedTo(ChronoUnit.MILLIS)
    }
}
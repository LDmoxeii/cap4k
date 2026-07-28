package com.only4.cap4k.ddd.core

/**
 * Raised when an optional runtime capability is invoked without a configured provider.
 */
class CapabilityUnavailableException(
    val capability: String,
    val suggestedStarter: String? = null,
) : IllegalStateException(
    buildString {
        append("cap4k capability '")
        append(capability)
        append("' is unavailable")
        suggestedStarter?.let {
            append("; add or configure ")
            append(it)
        }
    }
)

internal class CapabilitySlot<T : Any>(
    private val capability: String,
    private val suggestedStarter: String? = null,
) {
    @Volatile
    private var provider: T? = null

    fun get(): T = provider ?: throw CapabilityUnavailableException(capability, suggestedStarter)

    fun getOrNull(): T? = provider

    fun configure(provider: T) {
        this.provider = provider
    }

    fun reset() {
        provider = null
    }
}

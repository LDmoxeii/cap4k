package com.only4.cap4k.ddd.core

/**
 * Raised when an optional runtime provider is invoked without an implementation.
 */
class ProviderUnavailableException(
    val providerName: String,
    val suggestedStarter: String? = null,
) : IllegalStateException(
    buildString {
        append("cap4k provider '")
        append(providerName)
        append("' is unavailable")
        suggestedStarter?.let {
            append("; add or configure ")
            append(it)
        }
    }
)

internal class ProviderSlot<T : Any>(
    private val providerName: String,
    private val suggestedStarter: String? = null,
) {
    @Volatile
    private var provider: T? = null

    fun get(): T = provider ?: throw ProviderUnavailableException(providerName, suggestedStarter)

    fun getOrNull(): T? = provider

    fun configure(provider: T) {
        this.provider = provider
    }

    fun reset() {
        provider = null
    }
}

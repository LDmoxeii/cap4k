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

    @Synchronized
    fun configure(provider: T) {
        val configured = this.provider
        if (configured != null) {
            error(
                "cap4k provider '$providerName' is already configured by ${configured.javaClass.name}; " +
                    "cannot register ${provider.javaClass.name}"
            )
        }
        this.provider = provider
    }

    @Synchronized
    fun release(provider: T) {
        if (this.provider === provider) {
            this.provider = null
        }
    }

    @Synchronized
    fun reset() {
        provider = null
    }
}

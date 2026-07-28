package com.only4.cap4k.ddd.core.domain.id

object GeneratedOwnId {
    fun <ID : Any> assignIfMissing(
        current: () -> ID?,
        assign: (ID) -> Unit,
        next: () -> ID,
        label: String,
    ): ID {
        current()?.let { return it }
        val generated = next()
        assign(generated)
        return current() ?: error("generated own ID assignment failed: $label")
    }
}

inline fun <ID : Any> readInitializedOrNull(read: () -> ID): ID? =
    try {
        read()
    } catch (_: UninitializedPropertyAccessException) {
        null
    }

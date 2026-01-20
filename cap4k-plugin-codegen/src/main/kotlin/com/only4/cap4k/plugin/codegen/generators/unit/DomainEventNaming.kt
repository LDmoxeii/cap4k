package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase

object DomainEventNaming {
    fun eventClassName(raw: String): String {
        val base = toUpperCamelCase(raw) ?: raw
        return if (base.endsWith("Event") || base.endsWith("Evt")) {
            base
        } else {
            "${base}DomainEvent"
        }
    }

    fun handlerClassName(eventClass: String): String = "${eventClass}Subscriber"
}

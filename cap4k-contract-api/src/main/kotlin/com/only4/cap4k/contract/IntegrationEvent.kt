package com.only4.cap4k.contract

/** Marks a payload as a transport-neutral Integration Event. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntegrationEvent(
    /** Stable logical event name used by the active transport route. */
    val value: String = "",
)

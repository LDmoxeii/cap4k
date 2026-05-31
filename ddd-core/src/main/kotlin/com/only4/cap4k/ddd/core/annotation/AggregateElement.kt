package com.only4.cap4k.ddd.core.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AggregateElement(
    val aggregate: String = "",
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val type: String,
    val root: Boolean = false,
)

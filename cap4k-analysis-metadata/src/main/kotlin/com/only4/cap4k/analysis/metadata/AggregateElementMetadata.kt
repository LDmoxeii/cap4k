package com.only4.cap4k.analysis.metadata

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AggregateElementMetadata(
    val aggregate: String,
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val type: String,
    val root: Boolean = false,
)

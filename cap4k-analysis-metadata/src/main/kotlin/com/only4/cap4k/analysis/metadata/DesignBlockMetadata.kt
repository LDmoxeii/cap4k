package com.only4.cap4k.analysis.metadata

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class DesignBlockMetadata(
    val tag: String,
    val name: String,
    val packageName: String = "",
    val description: String = "",
    val aggregates: Array<String> = [],
    val eventName: String = "",
    val operationName: String = "",
    val family: String,
    val variant: String = "",
)

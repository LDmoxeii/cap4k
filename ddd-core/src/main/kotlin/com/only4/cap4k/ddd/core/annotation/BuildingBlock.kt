package com.only4.cap4k.ddd.core.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class BuildingBlock(
    val tag: String,
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val aggregates: Array<String> = [],
    val eventName: String = "",
    val family: String,
    val variant: String = "",
)

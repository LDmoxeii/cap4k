package com.only4.cap4k.plugin.codegen.context.design.models.common

data class PayloadField(
    val name: String,
    val type: String? = null,
    val defaultValue: String? = null,
    val nullable: Boolean = false,
)

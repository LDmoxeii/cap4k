package com.only4.cap4k.plugin.codeanalysis.core.model

data class DesignElement(
    val tag: String,
    val `package`: String,
    val name: String,
    val desc: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DesignField> = emptyList(),
    val responseFields: List<DesignField> = emptyList()
)

data class DesignField(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null
)

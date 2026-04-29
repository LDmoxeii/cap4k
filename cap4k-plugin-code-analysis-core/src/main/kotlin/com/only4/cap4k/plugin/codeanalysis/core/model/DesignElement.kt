package com.only4.cap4k.plugin.codeanalysis.core.model

data class DesignElement(
    val tag: String,
    val `package`: String,
    val name: String,
    val desc: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val traits: List<String> = emptyList(),
    val requestFields: List<DesignField> = emptyList(),
    val responseFields: List<DesignField> = emptyList(),
    val message: String? = null,
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<DesignParameter> = emptyList(),
)

data class DesignField(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DesignParameter(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

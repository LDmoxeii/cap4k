package com.only4.cap4k.plugin.codeanalysis.core.model

data class DesignElement(
    val tag: String,
    val `package`: String,
    val name: String,
    val description: String = "",
    val aggregates: List<String> = emptyList(),
    val eventName: String = "",
    val persist: Boolean? = null,
    val artifacts: List<DesignArtifact> = emptyList(),
    val fields: List<DesignField> = emptyList(),
    val resultFields: List<DesignField> = emptyList(),
)

data class DesignArtifact(
    val family: String,
    val variant: String = "",
)

data class DesignField(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

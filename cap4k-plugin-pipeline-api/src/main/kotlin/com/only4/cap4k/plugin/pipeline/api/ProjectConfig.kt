package com.only4.cap4k.plugin.pipeline.api

data class ProjectConfig(
    val basePackage: String,
    val layout: ProjectLayout,
    // Module role -> repository-relative filesystem path.
    val modules: Map<String, String>,
    val typeRegistry: Map<String, String> = emptyMap(),
    val sources: Map<String, SourceConfig>,
    val generators: Map<String, GeneratorConfig>,
    val templates: TemplateConfig,
) {
    fun enabledSourceIds(): Set<String> = sources.filterValues { it.enabled }.keys

    fun enabledGeneratorIds(): Set<String> = generators.filterValues { it.enabled }.keys
}

enum class ProjectLayout {
    SINGLE_MODULE,
    MULTI_MODULE,
}

data class SourceConfig(
    val enabled: Boolean,
    val options: Map<String, Any?> = emptyMap(),
)

data class GeneratorConfig(
    val enabled: Boolean,
    val options: Map<String, Any?> = emptyMap(),
)

data class TemplateConfig(
    val preset: String,
    val overrideDirs: List<String>,
    val conflictPolicy: ConflictPolicy,
)

enum class ConflictPolicy {
    SKIP,
    OVERWRITE,
    FAIL,
}

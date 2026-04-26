package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal fun aggregatePackageKey(config: ProjectConfig, aggregatePackageName: String): String {
    val normalizedPackage = aggregatePackageName.trim('.')
    if (normalizedPackage.isBlank()) {
        return ""
    }

    val aggregatePrefix = ArtifactLayoutResolver.joinPackage(
        config.basePackage,
        config.artifactLayout.aggregate.packageRoot,
    )
    val packageKey = when {
        aggregatePrefix.isNotBlank() && normalizedPackage == aggregatePrefix -> ""
        aggregatePrefix.isNotBlank() && normalizedPackage.startsWith("$aggregatePrefix.") ->
            normalizedPackage.removePrefix("$aggregatePrefix.")
        config.basePackage.isNotBlank() && normalizedPackage.startsWith("${config.basePackage}.") ->
            normalizedPackage.removePrefix("${config.basePackage}.")
        else -> normalizedPackage.substringAfterLast('.')
    }

    val aggregateSuffix = config.artifactLayout.aggregate.packageSuffix.trim('.')
    return when {
        aggregateSuffix.isBlank() -> packageKey
        packageKey == aggregateSuffix -> ""
        packageKey.endsWith(".$aggregateSuffix") -> packageKey.removeSuffix(".$aggregateSuffix")
        else -> packageKey
    }
}

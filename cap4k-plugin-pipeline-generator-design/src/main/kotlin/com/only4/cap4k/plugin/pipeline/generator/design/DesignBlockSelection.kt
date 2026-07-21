package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.util.Locale

internal fun DesignBlockModel.selects(family: String): Boolean =
    artifacts.any { artifact -> artifact.family == family }

internal fun DesignBlockModel.selection(family: String): ArtifactSelectionModel? =
    artifacts.firstOrNull { artifact -> artifact.family == family }

internal fun DesignBlockModel.commandTypeName(): String = "${name}Cmd"

internal fun DesignBlockModel.queryTypeName(): String = "${name}Qry"

internal fun DesignBlockModel.clientTypeName(): String = "${name}Cli"

internal fun DesignBlockModel.apiPayloadTypeName(): String = name.normalizeUpperCamelTypeName()

internal fun DesignBlockModel.domainEventTypeName(): String = name.toDomainEventTypeName()

internal fun DesignBlockModel.integrationEventTypeName(): String = name.toIntegrationEventTypeName()

internal fun DesignBlockModel.pageVariantSelected(family: String): Boolean =
    selection(family)?.variant == "page"

internal fun DesignBlockModel.integrationEventVariant(): String =
    requireNotNull(selection("integration-event")) {
        "integration_event $name must select integration-event."
    }.variant.also { variant ->
        require(variant == "inbound" || variant == "outbound") {
            "integration_event $name must select integration-event variant inbound or outbound."
        }
    }

internal fun DesignBlockModel.ownerAggregateEntity(model: CanonicalModel): EntityModel {
    val aggregateCount = aggregates.size
    require(aggregateCount == 1) {
        "domain_event $name must declare exactly one aggregate, but found $aggregateCount."
    }
    val aggregateName = aggregates.first()
    return model.entities.firstOrNull { entity -> entity.aggregateRoot && entity.name == aggregateName }
        ?: throw IllegalArgumentException("domain_event $name references missing aggregate metadata: $aggregateName")
}

internal fun DesignBlockModel.domainEventPackageKey(
    config: ProjectConfig,
    model: CanonicalModel,
): String = resolveDomainEventPackageKey(ownerAggregateEntity(model).packageName, config)

internal fun CanonicalModel.designInteractionSiblingTypeNames(
    packageName: String,
    currentTypeName: String,
): Set<String> = buildSet {
    designBlocks.forEach { block ->
        block.interactionSiblingTypeNames()
            .forEach { candidateTypeName ->
                addSiblingTypeName(
                    candidatePackageName = block.packageName,
                    candidateTypeName = candidateTypeName,
                    packageName = packageName,
                    currentTypeName = currentTypeName,
                )
            }
    }
}

private fun DesignBlockModel.interactionSiblingTypeNames(): List<String> = buildList {
    if (selects("command")) {
        add(commandTypeName())
    }
    if (selects("query")) {
        add(queryTypeName())
    }
    if (selects("client")) {
        add(clientTypeName())
    }
}

private fun MutableSet<String>.addSiblingTypeName(
    candidatePackageName: String,
    candidateTypeName: String,
    packageName: String,
    currentTypeName: String,
) {
    if (candidatePackageName == packageName && candidateTypeName != currentTypeName) {
        add(candidateTypeName)
    }
}

private fun String.normalizeUpperCamelTypeName(): String {
    val parts = trim()
        .split(UpperCamelSplitRegex)
        .filter { it.isNotEmpty() }
    if (parts.isEmpty()) {
        return ""
    }
    return parts.joinToString("") { part ->
        part.lowercase(Locale.ROOT).replaceFirstChar { character -> character.titlecase(Locale.ROOT) }
    }
}

private val UpperCamelSplitRegex = Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")

private fun String.toDomainEventTypeName(): String {
    val rawName = trim()
    val candidate = when {
        rawName.endsWith("Evt") || rawName.endsWith("Event") -> rawName
        else -> "${rawName}DomainEvent"
    }
    return candidate.normalizeUpperCamelTypeName()
}

private fun String.toIntegrationEventTypeName(): String {
    val rawName = trim()
    val candidate = when {
        rawName.endsWith("Evt") || rawName.endsWith("Event") -> rawName
        else -> "${rawName}IntegrationEvent"
    }
    return candidate.normalizeUpperCamelTypeName()
}

private fun resolveDomainEventPackageKey(
    aggregateRootPackageName: String,
    config: ProjectConfig,
): String {
    val normalizedRootPackage = aggregateRootPackageName.trim('.')
    if (normalizedRootPackage.isBlank()) {
        return ""
    }

    val aggregateRootPrefix = com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver.joinPackage(
        config.basePackage,
        config.artifactLayout.aggregate.packageRoot,
    )
    val packageKey = when {
        aggregateRootPrefix.isNotBlank() && normalizedRootPackage == aggregateRootPrefix -> ""
        aggregateRootPrefix.isNotBlank() && normalizedRootPackage.startsWith("$aggregateRootPrefix.") ->
            normalizedRootPackage.removePrefix("$aggregateRootPrefix.")
        else -> normalizedRootPackage.substringAfterLast('.')
    }
    val aggregateSuffix = config.artifactLayout.aggregate.packageSuffix.trim('.')
    return when {
        aggregateSuffix.isBlank() -> packageKey
        packageKey == aggregateSuffix -> ""
        packageKey.endsWith(".$aggregateSuffix") -> packageKey.removeSuffix(".$aggregateSuffix")
        else -> packageKey
    }
}

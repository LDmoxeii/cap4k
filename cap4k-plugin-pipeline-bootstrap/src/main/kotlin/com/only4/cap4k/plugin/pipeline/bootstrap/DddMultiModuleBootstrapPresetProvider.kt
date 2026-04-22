package com.only4.cap4k.plugin.pipeline.bootstrap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanItem
import com.only4.cap4k.plugin.pipeline.api.BootstrapPresetProvider
import java.io.File
import java.nio.charset.StandardCharsets

class DddMultiModuleBootstrapPresetProvider : BootstrapPresetProvider {
    override val presetId: String = "ddd-multi-module"

    override fun plan(config: BootstrapConfig): List<BootstrapPlanItem> {
        validateBootstrapPathSegments(config)
        val context = bootstrapContext(config)
        return buildList {
            add(fixed("bootstrap/root/settings.gradle.kts.peb", rebaseOutputPath("settings.gradle.kts", config), config, context))
            add(fixed("bootstrap/root/build.gradle.kts.peb", rebaseOutputPath("build.gradle.kts", config), config, context))
            add(
                fixed(
                    templateId = "bootstrap/module/domain-build.gradle.kts.peb",
                    outputPath = rebaseOutputPath("${config.modules.domainModuleName}/build.gradle.kts", config),
                    config = config,
                    context = context + mapOf("moduleRole" to "domain"),
                )
            )
            add(
                fixed(
                    templateId = "bootstrap/module/application-build.gradle.kts.peb",
                    outputPath = rebaseOutputPath("${config.modules.applicationModuleName}/build.gradle.kts", config),
                    config = config,
                    context = context + mapOf("moduleRole" to "application"),
                )
            )
            add(
                fixed(
                    templateId = "bootstrap/module/adapter-build.gradle.kts.peb",
                    outputPath = rebaseOutputPath("${config.modules.adapterModuleName}/build.gradle.kts", config),
                    config = config,
                    context = context + mapOf("moduleRole" to "adapter"),
                )
            )
            add(
                fixed(
                    templateId = "bootstrap/module/start-build.gradle.kts.peb",
                    outputPath = rebaseOutputPath("${config.modules.startModuleName}/build.gradle.kts", config),
                    config = config,
                    context = context + mapOf("moduleRole" to "start"),
                )
            )
            add(
                fixed(
                    templateId = "bootstrap/module/start-application.kt.peb",
                    outputPath = rebaseOutputPath(
                        "${config.modules.startModuleName}/src/main/kotlin/${config.basePackagePath()}/StartApplication.kt",
                        config
                    ),
                    config = config,
                    context = context + mapOf("moduleRole" to "start"),
                )
            )
            addAll(BootstrapSlotPlanner.plan(config))
        }
    }
}

internal fun bootstrapContext(config: BootstrapConfig): Map<String, Any?> =
    mapOf(
        "projectName" to config.projectName,
        "basePackage" to config.basePackage,
        "basePackagePath" to config.basePackagePath(),
        "domainModuleName" to config.modules.domainModuleName,
        "applicationModuleName" to config.modules.applicationModuleName,
        "adapterModuleName" to config.modules.adapterModuleName,
        "startModuleName" to config.modules.startModuleName,
        "templatePreset" to config.templates.preset,
        "templateOverrideDirs" to config.templates.overrideDirs.map(::normalizeDslPathLiteral),
        "slotBindings" to config.slots.map(::toRenderModel),
        "conflictPolicy" to config.conflictPolicy.name,
        "mode" to config.mode.name,
        "previewDir" to config.previewDir?.let(::normalizeDslPathLiteral),
    )

internal fun fixed(
    templateId: String,
    outputPath: String,
    config: BootstrapConfig,
    context: Map<String, Any?>,
): BootstrapPlanItem =
    BootstrapPlanItem(
        presetId = config.preset,
        templateId = templateId,
        outputPath = outputPath,
        conflictPolicy = config.conflictPolicy,
        context = context,
    )

internal fun renderRelativePath(relativePath: String, config: BootstrapConfig): String {
    val replacements = mapOf(
        "{{ projectName }}" to config.projectName,
        "{{ basePackage }}" to config.basePackage,
        "{{ basePackagePath }}" to config.basePackagePath(),
        "__projectName__" to config.projectName,
        "__basePackage__" to config.basePackage,
        "__basePackagePath__" to config.basePackagePath(),
    )
    val rendered = replacements.entries.fold(relativePath.replace('\\', '/')) { acc, entry ->
        acc.replace(entry.key, entry.value)
    }
    return trimPebExtension(normalizeRelativePath(rendered))
}

internal fun resolveSlotOutputPath(
    binding: com.only4.cap4k.plugin.pipeline.api.BootstrapSlotBinding,
    renderedRelativePath: String,
    config: BootstrapConfig,
): String {
    val boundedRelative = normalizeRelativePath(renderedRelativePath)
    val moduleName = binding.role?.let { resolveModuleName(it, config) }
    return when (binding.kind) {
        com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind.ROOT ->
            rebaseOutputPath(boundedRelative, config)

        com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind.BUILD_LOGIC ->
            rebaseOutputPath("build-logic/$boundedRelative", config)

        com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind.MODULE_ROOT ->
            rebaseOutputPath("${requireNotNull(moduleName)}/$boundedRelative", config)

        com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind.MODULE_RESOURCES ->
            rebaseOutputPath("${requireNotNull(moduleName)}/src/main/resources/$boundedRelative", config)

        com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind.MODULE_PACKAGE -> {
            val role = requireNotNull(binding.role)
            val packageRoot = resolveRolePackageRoot(role, config)
            val packageRelative = resolveModulePackageRelativePath(role, boundedRelative, config)
            val packageOutputPath = when {
                packageRelative.isEmpty() -> packageRoot
                else -> "$packageRoot/$packageRelative"
            }
            rebaseOutputPath("${requireNotNull(moduleName)}/src/main/kotlin/$packageOutputPath", config)
        }
    }
}

internal fun resolveModuleName(role: String, config: BootstrapConfig): String =
    when (role) {
        "domain" -> config.modules.domainModuleName
        "application" -> config.modules.applicationModuleName
        "adapter" -> config.modules.adapterModuleName
        "start" -> config.modules.startModuleName
        else -> throw IllegalArgumentException("unsupported bootstrap slot role: $role")
    }

private fun toRenderModel(binding: com.only4.cap4k.plugin.pipeline.api.BootstrapSlotBinding): BootstrapSlotBindingRenderModel =
    BootstrapSlotBindingRenderModel(
        kind = binding.kind.name,
        role = binding.role,
        sourceDir = normalizeDslPathLiteral(binding.sourceDir),
    )

private fun BootstrapConfig.basePackagePath(): String = basePackage.replace('.', '/')

private fun trimPebExtension(path: String): String = path.removeSuffix(".peb")

private fun normalizeDslPathLiteral(path: String): String = path
    .replace('\\', '/')
    .replace("$", "\\$")
    .replace("\"", "\\\"")

internal fun rebaseOutputPath(relativePath: String, config: BootstrapConfig): String {
    val normalizedRelativePath = normalizeRelativePath(relativePath)
    return when (config.mode) {
        BootstrapMode.IN_PLACE -> normalizedRelativePath
        BootstrapMode.PREVIEW_SUBTREE -> "${requireNotNull(config.previewDir)}/$normalizedRelativePath"
    }
}

private fun resolveModulePackageRelativePath(
    role: String,
    boundedRelativePath: String,
    config: BootstrapConfig,
): String {
    val basePackagePath = config.basePackagePath()
    val rolePackageRoot = resolveRolePackageRoot(role, config)
    return when {
        boundedRelativePath == rolePackageRoot -> ""
        boundedRelativePath.startsWith("$rolePackageRoot/") -> boundedRelativePath.removePrefix("$rolePackageRoot/")
        boundedRelativePath == basePackagePath -> ""
        boundedRelativePath.startsWith("$basePackagePath/") -> boundedRelativePath.removePrefix("$basePackagePath/")
        else -> boundedRelativePath
    }
}

internal fun resolveRolePackageRoot(role: String, config: BootstrapConfig): String =
    when (role) {
        "domain" -> "${config.basePackagePath()}/domain"
        "application" -> "${config.basePackagePath()}/application"
        "adapter" -> "${config.basePackagePath()}/adapter"
        "start" -> config.basePackagePath()
        else -> throw IllegalArgumentException("unsupported bootstrap slot role: $role")
    }

internal fun validateBootstrapPathSegments(config: BootstrapConfig) {
    requireSafePathSegment("bootstrap.projectName", config.projectName)
    requireSafePathSegment("bootstrap.modules.domainModuleName", config.modules.domainModuleName)
    requireSafePathSegment("bootstrap.modules.applicationModuleName", config.modules.applicationModuleName)
    requireSafePathSegment("bootstrap.modules.adapterModuleName", config.modules.adapterModuleName)
    requireSafePathSegment("bootstrap.modules.startModuleName", config.modules.startModuleName)
}

private fun requireSafePathSegment(fieldName: String, value: String) {
    val trimmed = value.trim()
    require(trimmed.isNotEmpty()) { "$fieldName must be a safe path segment: $value" }
    require(!trimmed.contains('/') && !trimmed.contains('\\')) { "$fieldName must be a safe path segment: $value" }
    require(trimmed != "." && trimmed != "..") { "$fieldName must be a safe path segment: $value" }
    require(!trimmed.contains("..")) { "$fieldName must be a safe path segment: $value" }
    require(!trimmed.contains(':')) { "$fieldName must be a safe path segment: $value" }
}

private fun normalizeRelativePath(value: String): String {
    val normalized = value.replace('\\', '/').trim().trimStart('/')
    require(normalized.isNotBlank()) { "bootstrap slot path must not be blank." }
    require(!normalized.split('/').contains("..")) { "bootstrap slot path must stay within project subtree: $value" }
    return normalized
}

internal data class LegacyArchTemplateSample(
    val raw: JsonObject,
)

internal data class LegacyArchTemplateMapping(
    val structuralNodes: Set<String>,
    val fixedTemplateFiles: Set<String>,
    val routingTags: Set<String>,
)

internal object LegacyArchTemplateMappingSamples {
    fun load(resourcePath: String): LegacyArchTemplateSample {
        val directFile = File(resourcePath)
        val content = when {
            directFile.exists() -> directFile.readText()
            else -> {
                val stream = LegacyArchTemplateMappingSamples::class.java.classLoader.getResourceAsStream(resourcePath)
                    ?: error("legacy sample not found: $resourcePath")
                stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
        }
        return LegacyArchTemplateSample(
            raw = JsonParser.parseString(content).asJsonObject
        )
    }
}

internal object LegacyArchTemplateMapper {
    fun classify(sample: LegacyArchTemplateSample): LegacyArchTemplateMapping {
        val nodes = sample.raw.getAsJsonArray("nodes")
            ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
            ?.toSet()
            .orEmpty()

        val routing = sample.raw.getAsJsonObject("routing")
        val routingTags = routing
            ?.entrySet()
            ?.map { it.key }
            ?.toSet()
            .orEmpty()
        val routedTemplateFiles = routing
            ?.entrySet()
            ?.mapNotNull { entry -> entry.value.takeIf { it.isJsonPrimitive }?.asString }
            ?.toSet()
            .orEmpty()

        val fixedTemplateFiles = sample.raw.getAsJsonArray("templates")
            ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
            ?.filter { candidate ->
                candidate.endsWith(".peb") &&
                    candidate !in routedTemplateFiles &&
                    !candidate.contains("/_tpl/")
            }
            ?.toSet()
            .orEmpty()

        return LegacyArchTemplateMapping(
            structuralNodes = nodes,
            fixedTemplateFiles = fixedTemplateFiles,
            routingTags = routingTags,
        )
    }
}

internal data class BootstrapSlotBindingRenderModel(
    val kind: String,
    val role: String?,
    val sourceDir: String,
)

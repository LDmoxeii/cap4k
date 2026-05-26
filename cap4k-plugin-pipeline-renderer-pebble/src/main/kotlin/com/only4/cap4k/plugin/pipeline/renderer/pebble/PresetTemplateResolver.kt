package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import java.io.File

class PresetTemplateResolver(
    private val preset: String,
    private val overrideDirs: List<String>,
    private val addonTemplateClassLoaders: Map<String, ClassLoader> = emptyMap(),
) : TemplateResolver {

    constructor(preset: String, overrideDirs: List<String>) : this(preset, overrideDirs, emptyMap())

    override fun resolve(templateId: String): String {
        if (templateId.isNotBlank()) {
            val directFile = File(templateId)
            if (directFile.isAbsolute && directFile.exists()) {
                return directFile.readText()
            }
        }

        for (dir in overrideDirs) {
            val file = File(dir, templateId)
            if (file.exists()) {
                return file.readText()
            }
        }

        val addonId = addonId(templateId)
        if (addonId != null) {
            val classLoader = addonTemplateClassLoaders[addonId]
                ?: throw IllegalArgumentException("Template references addon '$addonId' but no addon provider is loaded.")
            val resourcePath = "cap4k/$templateId"
            val resource = classLoader.getResource(resourcePath)
                ?: throw IllegalArgumentException("Addon template not found: $resourcePath")
            return resource.readText()
        }

        val resourcePath = "presets/$preset/$templateId"
        val aliasedResourcePath = presetTemplateAlias(templateId)?.let { alias -> "presets/$preset/$alias" }
        val resource = javaClass.classLoader.getResource(resourcePath)
            ?: aliasedResourcePath?.let { javaClass.classLoader.getResource(it) }
            ?: error("Template not found: $resourcePath")
        return resource.readText()
    }
}

private fun presetTemplateAlias(templateId: String): String? =
    when (templateId) {
        "types/value-object" -> "types/value_object.kt.peb"
        else -> null
    }

private fun addonId(templateId: String): String? {
    val prefix = "addons/"
    if (!templateId.startsWith(prefix)) {
        return null
    }
    return templateId.removePrefix(prefix).substringBefore('/').takeIf { it.isNotBlank() }
}

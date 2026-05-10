package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import java.io.File

class PresetTemplateResolver(
    private val preset: String,
    private val overrideDirs: List<String>,
    private val addonClassLoaders: List<ClassLoader> = emptyList(),
) : TemplateResolver {

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

        if (templateId.startsWith("addons/")) {
            val resourcePath = "cap4k/$templateId"
            for (classLoader in addonClassLoaders) {
                val resource = classLoader.getResource(resourcePath)
                if (resource != null) {
                    return resource.readText()
                }
            }
            error("Addon template not found: $resourcePath")
        }

        val resourcePath = "presets/$preset/$templateId"
        val resource = javaClass.classLoader.getResource(resourcePath)
            ?: error("Template not found: $resourcePath")
        return resource.readText()
    }
}

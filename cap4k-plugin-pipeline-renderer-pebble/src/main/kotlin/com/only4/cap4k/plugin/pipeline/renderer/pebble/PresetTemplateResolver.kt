package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import java.io.File

class PresetTemplateResolver(
    private val preset: String,
    private val overrideDirs: List<String>,
) : TemplateResolver {

    override fun resolve(templateId: String): String {
        for (dir in overrideDirs) {
            val file = File(dir, templateId)
            if (file.exists()) {
                return file.readText()
            }
        }

        val resourcePath = "presets/$preset/$templateId"
        val resource = javaClass.classLoader.getResource(resourcePath)
            ?: throw IllegalArgumentException("Template not found: $resourcePath")
        return resource.readText()
    }
}

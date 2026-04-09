package com.only4.cap4k.plugin.pipeline.renderer.api

interface TemplateResolver {
    fun resolve(templateId: String): String
}

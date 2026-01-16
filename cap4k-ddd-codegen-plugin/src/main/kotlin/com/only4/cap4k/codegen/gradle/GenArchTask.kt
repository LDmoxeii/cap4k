package com.only4.cap4k.codegen.gradle

import com.google.gson.Gson
import com.only4.cap4k.codegen.gradle.extension.CodegenExtension
import com.only4.cap4k.codegen.misc.loadFileContent
import com.only4.cap4k.codegen.misc.resolveDirectory
import com.only4.cap4k.codegen.pebble.PebbleConfig
import com.only4.cap4k.codegen.pebble.PebbleInitializer
import com.only4.cap4k.codegen.template.PathNode
import com.only4.cap4k.codegen.template.Template
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

open class GenArchTask : AbstractCodegenTask() {

    @get:Input
    override val extension: Property<CodegenExtension> =
        project.objects.property(CodegenExtension::class.java)

    @get:Input
    override val projectName: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectGroup: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    override val projectDir: Property<String> = project.objects.property(String::class.java)

    @TaskAction
    open fun generate() = genArch()

    private fun genArch() {
        val ext = extension.get()
        val config = PebbleConfig(
            encoding = ext.archTemplateEncoding.get()
        )
        PebbleInitializer.initPebble(config)

        val archTemplate = validateAndGetArchTemplate(ext) ?: return

        template = loadTemplate(archTemplate, ext)
        render(template!!, projectDir.get())

    }

    private fun loadTemplate(templatePath: String, ext: CodegenExtension): Template {
        val templateContent = loadFileContent(templatePath, ext.archTemplateEncoding.get())

        PathNode.setDirectory(resolveDirectory(templatePath, projectDir.get()))

        return Gson().fromJson(templateContent, Template::class.java).apply {
            resolve(baseMap)
        }
    }

    private fun validateAndGetArchTemplate(ext: CodegenExtension): String? {
        val archTemplate = ext.archTemplate.orNull?.takeIf { it.isNotBlank() }
            ?: run {
                logger.error("请设置(archTemplate)参数")
                return null
            }

        if (ext.basePackage.get().isBlank()) {
            logger.warn("请设置(basePackage)参数")
            return null
        }

        return archTemplate
    }
}


package com.only4.cap4k.gradle.codegen

import com.alibaba.fastjson.JSON
import com.only4.cap4k.gradle.codegen.misc.loadFileContent
import com.only4.cap4k.gradle.codegen.misc.resolveDirectory
import com.only4.cap4k.gradle.codegen.template.PathNode
import com.only4.cap4k.gradle.codegen.template.Template
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.nio.charset.Charset

open class GenArchTask : AbstractCodegenTask() {

    @get:Input
    override val extension: Property<Cap4kCodegenExtension> =
        project.objects.property(Cap4kCodegenExtension::class.java)

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

        fun loadTemplate(templatePath: String): Template {
            val ext = extension.get()
            val templateContent =
                loadFileContent(templatePath, ext.archTemplateEncoding.get(), projectDir.get())
            logger.debug("模板内容: $templateContent")

            PathNode.setDirectory(resolveDirectory(templatePath, projectDir.get()))
            val template = JSON.parseObject(templateContent, Template::class.java)
            template.resolve(getEscapeContext())

            return template
        }

        val ext = extension.get()
        logger.info("生成项目架构结构...")

        // 基础日志
        logger.info("当前系统默认编码：${Charset.defaultCharset().name()}")
        with(ext) {
            logger.info("设置模板读取编码：${archTemplateEncoding.get()} (from archTemplateEncoding)")
            logger.info("设置输出文件编码：${outputEncoding.get()} (from outputEncoding)")
            logger.info("基础包名：${basePackage.get()}")
            logger.info(if (multiModule.get()) "多模块项目" else "单模块项目")
        }
        logger.info("项目目录：${projectDir.get()}")
        logger.info("适配层目录：${getAdapterModulePath()}")
        logger.info("应用层目录：${getApplicationModulePath()}")
        logger.info("领域层目录：${getDomainModulePath()}")

        val archTemplate = ext.archTemplate.orNull?.takeIf { it.isNotBlank() }
            ?: run {
                logger.error("请设置(archTemplate)参数")
                return
            }
        if (ext.basePackage.get().isBlank()) {
            logger.warn("请设置(basePackage)参数")
            return
        }

        runCatching {
            template = loadTemplate(archTemplate)
            render(template!!, projectDir.get())
        }.onSuccess {
            logger.info("项目架构生成完成")
        }.onFailure {
            logger.error("生成架构结构失败", it)
            throw it
        }
    }
}


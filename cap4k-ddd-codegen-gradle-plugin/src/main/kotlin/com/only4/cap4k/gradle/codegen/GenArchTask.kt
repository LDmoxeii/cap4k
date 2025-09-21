package com.only4.cap4k.gradle.codegen

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * 生成项目架构结构任务
 */
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
    fun generate() {
        logger.info("生成项目架构结构...")

        val ext = getExtension()

        logger.info("当前系统默认编码：${java.nio.charset.Charset.defaultCharset().name()}")
        logger.info("设置模板读取编码：${ext.archTemplateEncoding.get()} (from archTemplateEncoding)")
        logger.info("设置输出文件编码：${ext.outputEncoding.get()} (from outputEncoding)")
        logger.info("基础包名：${ext.basePackage.get()}")
        logger.info(if (ext.multiModule.get()) "多模块项目" else "单模块项目")
        logger.info("项目目录：${getProjectDir()}")
        logger.info("适配层目录：${getAdapterModulePath()}")
        logger.info("应用层目录：${getApplicationModulePath()}")
        logger.info("领域层目录：${getDomainModulePath()}")

        val archTemplate = ext.archTemplate.orNull
        if (archTemplate.isNullOrEmpty()) {
            logger.error("请设置(archTemplate)参数")
            return
        }

        if (ext.basePackage.get().isEmpty()) {
            logger.warn("请设置(basePackage)参数")
            return
        }

        try {
            template = loadTemplate(archTemplate)
            render(template!!, getProjectDir())
        } catch (e: Exception) {
            logger.error("生成架构结构失败", e)
            throw e
        }
    }
}

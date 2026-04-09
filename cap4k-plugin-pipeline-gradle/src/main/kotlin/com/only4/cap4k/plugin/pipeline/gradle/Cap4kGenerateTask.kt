package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kGenerateTask : DefaultTask() {
    @get:Internal
    lateinit var extension: PipelineExtension

    @TaskAction
    fun generate() {
        val config = buildConfig(project, extension)
        buildRunner(project, config, exportEnabled = true).run(config)
    }
}

package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kAnalysisGenerateTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @TaskAction
    fun generate() {
        val config = analysisTaskConfig(configFactory.build(project, extension))
        buildAnalysisRunner(project, config, exportEnabled = true).run(config)
    }
}

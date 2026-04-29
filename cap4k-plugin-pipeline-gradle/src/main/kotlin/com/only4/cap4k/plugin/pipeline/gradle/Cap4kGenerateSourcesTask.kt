package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kGenerateSourcesTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @TaskAction
    fun generateSources() {
        val config = generatedSourceTaskConfig(configFactory.build(project, extension))
        buildGeneratedSourceRunner(project, config).run(config)
    }
}

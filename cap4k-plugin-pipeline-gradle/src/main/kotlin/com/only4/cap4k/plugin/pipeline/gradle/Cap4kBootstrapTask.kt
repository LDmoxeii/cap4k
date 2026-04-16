package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kBootstrapTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kBootstrapConfigFactory

    @TaskAction
    fun generate() {
        val config = configFactory.build(project, extension)
        buildBootstrapRunner(project, exportEnabled = true).run(config)
    }
}

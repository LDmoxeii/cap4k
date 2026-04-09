package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kPlanTask : DefaultTask() {
    @get:Internal
    lateinit var extension: PipelineExtension

    @TaskAction
    fun runPlan() {
        val config = buildConfig(project, extension)
        val result = buildRunner(project, config, exportEnabled = false).run(config)
        val outputFile = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(result.planItems)
        )
    }
}

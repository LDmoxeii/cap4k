package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanReport
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kBootstrapPlanTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kBootstrapConfigFactory

    @TaskAction
    fun runPlan() {
        val config = configFactory.build(project, extension)
        val outputFile = project.layout.buildDirectory.file("cap4k/bootstrap-plan.json").get().asFile
        outputFile.parentFile.mkdirs()
        val result = buildBootstrapRunner(project, exportEnabled = false).run(config)
        outputFile.writeText(
            GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create()
                .toJson(BootstrapPlanReport(result.planItems))
        )
    }
}

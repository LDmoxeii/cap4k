package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.api.PlanReport
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kPlanTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @TaskAction
    fun runPlan() {
        val config = configFactory.build(project, extension)
        val result = buildRunner(project, config, exportEnabled = false).run(config)
        val outputFile = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create()
                .toJson(
                    PlanReport(
                        items = result.planItems,
                        diagnostics = result.diagnostics,
                    )
                )
        )
    }
}

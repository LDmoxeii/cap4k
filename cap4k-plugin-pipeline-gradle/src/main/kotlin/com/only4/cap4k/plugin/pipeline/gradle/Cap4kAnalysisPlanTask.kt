package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.api.PlanReport
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnosticsException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kAnalysisPlanTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @TaskAction
    fun runPlan() {
        val config = configFactory.build(project, extension)
        val outputFile = project.layout.buildDirectory.file("cap4k/analysis-plan.json").get().asFile
        outputFile.parentFile.mkdirs()
        try {
            val result = buildRunner(project, config, exportEnabled = false).run(config)
            writePlanReport(
                outputFile = outputFile,
                report = PlanReport(
                    items = result.planItems,
                    diagnostics = result.diagnostics,
                )
            )
        } catch (error: PipelineDiagnosticsException) {
            writePlanReport(
                outputFile = outputFile,
                report = PlanReport(
                    items = emptyList(),
                    diagnostics = error.diagnostics,
                )
            )
            throw error
        }
    }

    private fun writePlanReport(outputFile: java.io.File, report: PlanReport) {
        outputFile.writeText(
            GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create()
                .toJson(report)
        )
    }
}

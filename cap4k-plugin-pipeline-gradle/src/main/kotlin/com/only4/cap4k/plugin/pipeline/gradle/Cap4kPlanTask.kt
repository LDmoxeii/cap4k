package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import com.only4.cap4k.plugin.pipeline.api.PlanOutcome
import com.only4.cap4k.plugin.pipeline.api.PlanReport
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnosticsException
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class Cap4kPlanTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @get:Classpath
    val pipelineExtensionClasspath: FileCollection
        get() = pipelineExtensionClasspath(project)

    @TaskAction
    fun runPlan() {
        val config = sourceTaskConfig(configFactory.build(project, extension))
        val outputFile = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        outputFile.parentFile.mkdirs()
        try {
            val result = buildSourceRunner(project, config, exportEnabled = false).run(config)
            writePlanReport(
                outputFile = outputFile,
                report = PlanReport(
                    items = result.planItems,
                    outcome = PlanOutcome.SUCCEEDED,
                    diagnostics = result.diagnostics,
                    managedFieldDefaults = config.managedFields,
                    managedFieldPolicies = result.managedFieldPolicies,
                    evidence = planEvidence(project, config),
                )
            )
        } catch (error: PipelineDiagnosticsException) {
            writePlanReport(
                outputFile = outputFile,
                report = PlanReport(
                    items = emptyList(),
                    outcome = PlanOutcome.FAILED,
                    diagnostics = error.diagnostics,
                    managedFieldDefaults = config.managedFields,
                    managedFieldPolicies = emptyList(),
                    evidence = planEvidence(project, config),
                )
            )
            throw error
        }
    }

    private fun writePlanReport(outputFile: java.io.File, report: PlanReport) {
        val mapper = PipelineJson.newMapper(includeNulls = true)
        outputFile.writeText(PipelineJson.prettyWriter(mapper).writeValueAsString(report))
    }
}

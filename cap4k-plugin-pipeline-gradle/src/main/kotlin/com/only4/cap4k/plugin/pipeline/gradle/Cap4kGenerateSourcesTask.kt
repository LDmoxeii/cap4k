package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class Cap4kGenerateSourcesTask : DefaultTask() {
    init {
        outputs.upToDateWhen {
            !hasUntrackedLiveDbInput
        }
    }

    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @get:Input
    val inputSnapshot: String
        get() = generatedSourceTaskInputSnapshot(
            rootProject = project.rootProject,
            config = generatedSourceTaskConfig(configFactory.build(project, extension)),
        )

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputFiles: FileCollection
        get() = generatedSourceTaskInputFiles(
            project = project,
            extension = extension,
            config = generatedSourceTaskConfig(configFactory.build(project, extension)),
        )

    @get:OutputDirectories
    val outputDirectories: FileCollection
        get() = project.files(
            generatedSourceOutputDirectories(
                rootProject = project.rootProject,
                config = generatedSourceTaskConfig(configFactory.build(project, extension)),
            )
        )

    @get:Internal
    val hasUntrackedLiveDbInput: Boolean
        get() = generatedSourceTaskHasUntrackedLiveDbInput(
            project = project,
            config = generatedSourceTaskConfig(configFactory.build(project, extension)),
        )

    @TaskAction
    fun generateSources() {
        val config = generatedSourceTaskConfig(configFactory.build(project, extension))
        buildGeneratedSourceRunner(project, config).run(config)
    }
}

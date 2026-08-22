package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Generates and merges author-owned source files with conflict-policy side effects")
abstract class Cap4kGenerateTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @get:Classpath
    val pipelineExtensionClasspath: FileCollection
        get() = pipelineExtensionClasspath(project)

    @TaskAction
    fun generate() {
        val config = sourceTaskConfig(configFactory.build(project, extension))
        cleanGeneratedSourceOutputDirectories(project.rootProject, config)
        buildSourceRunner(project, config, exportEnabled = true).run(config)
        recordManagedGeneratedSourceOutputDirectories(project.rootProject, config)
    }
}

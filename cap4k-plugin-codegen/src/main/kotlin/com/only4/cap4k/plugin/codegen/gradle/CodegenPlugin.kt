package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.gradle.extension.CodegenExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 代码生成 Gradle 插件
 */
class CodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("cap4kCodegen", CodegenExtension::class.java)

        project.tasks.register("cap4kGenArch", GenArchTask::class.java) { task ->
            task.description = "Generate project architecture structure"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }

        project.tasks.register("cap4kGenAggregate", GenAggregateTask::class.java) { task ->
            task.description = "Generate Aggregate from database schema"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }


        project.tasks.register("cap4kGenDesign", GenDesignTask::class.java) { task ->
            task.description = "Generate design elements (commands, queries, events)"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }

        // Configure genDesign to depend on kspKotlin after project evaluation
        project.afterEvaluate {
            val genDesignTask = project.tasks.findByName("genDesign") ?: return@afterEvaluate

            // Try to find kspKotlin in current project first
            val currentKspTask = project.tasks.findByName("kspKotlin")
            if (currentKspTask != null) {
                genDesignTask.dependsOn(currentKspTask)
                return@afterEvaluate
            }

            // Try to find kspKotlin in domain submodule
            val domainModuleName = project.name + extension.moduleNameSuffix4Domain.getOrElse("-domain")
            val domainProject = project.rootProject.allprojects.find { it.name == domainModuleName }

            if (domainProject != null) {
                // Use task path dependency to allow Gradle to resolve the task even if it's created later
                val kspTaskPath = "${domainProject.path}:kspKotlin"
                try {
                    genDesignTask.dependsOn(kspTaskPath)
                } catch (e: Exception) {
                    project.logger.warn("Could not add dependency on $kspTaskPath: ${e.message}")
                }
            }
        }
    }
}

package com.only4.cap4k.gradle.codegen

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Cap4k DDD 代码生成 Gradle 插件
 */
class Cap4kDddCodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // 创建插件扩展
        val extension = project.extensions.create("cap4kCodegen", Cap4kCodegenExtension::class.java)

        // 注册任务
        project.tasks.register("genArch", GenArchTask::class.java) { task ->
            task.group = "cap4k codegen"
            task.description = "Generate project architecture structure"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }

        project.tasks.register("genEntity", GenEntityTask::class.java) { task ->
            task.group = "cap4k codegen"
            task.description = "Generate entity classes from database schema"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }

        project.tasks.register("genRepository", GenRepositoryTask::class.java) { task ->
            task.group = "cap4k codegen"
            task.description = "Generate repository classes"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }

        project.tasks.register("genDesign", GenDesignTask::class.java) { task ->
            task.group = "cap4k codegen"
            task.description = "Generate design elements (commands, queries, events)"
            task.extension.set(extension)
            task.projectName.set(project.name)
            task.projectGroup.set(project.group.toString())
            task.projectVersion.set(project.version.toString())
            task.projectDir.set(project.projectDir.absolutePath)
        }

        // 创建一个主任务来执行所有生成任务
        project.tasks.register("genAll") { task ->
            task.group = "cap4k codegen"
            task.description = "Generate all DDD code"
            task.dependsOn("genArch", "genEntity", "genRepository", "genDesign")
        }
    }
}
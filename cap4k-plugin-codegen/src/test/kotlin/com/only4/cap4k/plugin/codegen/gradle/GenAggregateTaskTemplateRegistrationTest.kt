package com.only4.cap4k.plugin.codegen.gradle

import com.only4.cap4k.plugin.codegen.gradle.extension.CodegenExtension
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class GenAggregateTaskTemplateRegistrationTest {

    @Test
    fun `registers aggregate templates with original tags and skips package lookup for plain dirs`() {
        val projectDir = Files.createTempDirectory("cap4k-aggregate-task").toFile()
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()

        CodegenPlugin().apply(project)

        val extension = project.extensions.getByType(CodegenExtension::class.java).apply {
            archTemplate.set(File(projectDir, "dummy-template.json").absolutePath)
            basePackage.set("com.acme")
            database.url.set("")
            database.username.set("")
            database.password.set("")
            database.schema.set("")
        }

        val task = project.tasks.create("testCap4kGenAggregate", TestGenAggregateTask::class.java)
        task.extension.set(extension)
        task.projectName.set(project.name)
        task.projectGroup.set(project.group.toString())
        task.projectVersion.set(project.version.toString())
        task.projectDir.set(project.projectDir.absolutePath)

        val entityDir = File(projectDir, "module/src/main/kotlin/com/acme/domain/aggregates").apply { mkdirs() }
        val designDir = File(projectDir, "design").apply { mkdirs() }

        task.exposeRenderTemplate(
            listOf(TemplateNode().apply {
                type = "package"
                tag = "entity"
            }),
            entityDir.absolutePath
        )
        task.exposeRenderTemplate(
            listOf(TemplateNode().apply {
                type = "dir"
                tag = "drawing_board_cli"
            }),
            designDir.absolutePath
        )

        assertEquals("domain.aggregates", task.templatePackage["entity"])
        assertFalse(task.templatePackage.containsKey("drawing_board_cli"))
    }

    abstract class TestGenAggregateTask : GenAggregateTask() {
        fun exposeRenderTemplate(templateNodes: List<TemplateNode>, parentPath: String) {
            renderTemplate(templateNodes, parentPath)
        }
    }
}

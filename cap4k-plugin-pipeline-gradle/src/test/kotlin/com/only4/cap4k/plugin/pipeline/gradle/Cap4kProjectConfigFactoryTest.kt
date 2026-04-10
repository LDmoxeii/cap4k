package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class Cap4kProjectConfigFactoryTest {

    @Test
    fun `nested cap4k extension exposes explicit disabled defaults`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        assertFalse(extension.sources.designJson.enabled.get())
        assertFalse(extension.sources.kspMetadata.enabled.get())
        assertFalse(extension.sources.db.enabled.get())
        assertFalse(extension.sources.irAnalysis.enabled.get())
        assertFalse(extension.generators.design.enabled.get())
        assertFalse(extension.generators.aggregate.enabled.get())
        assertFalse(extension.generators.drawingBoard.enabled.get())
        assertFalse(extension.generators.flow.enabled.get())
        assertEquals("ddd-default", extension.templates.preset.get())
        assertEquals("SKIP", extension.templates.conflictPolicy.get())
    }

    @Test
    fun `project and nested blocks can be configured with typed properties`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
            domainModulePath.set("demo-domain")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
            kspMetadata {
                enabled.set(true)
                inputDir.set("domain/build/generated/ksp/main/resources/metadata")
            }
            db {
                enabled.set(true)
                url.set("jdbc:h2:mem:test")
                username.set("sa")
                password.set("")
                schema.set("PUBLIC")
            }
            irAnalysis {
                enabled.set(true)
                inputDirs.from(project.file("analysis/app/build/cap4k-code-analysis"))
            }
        }
        extension.generators {
            design { enabled.set(true) }
            aggregate { enabled.set(true) }
            drawingBoard {
                enabled.set(true)
                outputDir.set("design")
            }
            flow {
                enabled.set(true)
                outputDir.set("flows")
            }
        }
        extension.templates {
            preset.set("ddd-default")
            overrideDirs.from("codegen/templates")
            conflictPolicy.set("SKIP")
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals("com.acme.demo", config.basePackage)
        assertEquals("demo-application", config.modules["application"])
        assertEquals("demo-domain", config.modules["domain"])
        assertEquals("demo-adapter", config.modules["adapter"])
    }
}

package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
        assertEquals("FAIL", extension.generators.aggregate.unsupportedTablePolicy.get())
        assertFalse(extension.generators.drawingBoard.enabled.get())
        assertFalse(extension.generators.flow.enabled.get())
        assertEquals("ddd-default", extension.templates.preset.get())
        assertEquals("SKIP", extension.templates.conflictPolicy.get())
    }

    @Test
    fun `factory preserves template override dir order and keeps only enabled blocks`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
            db {
                enabled.set(false)
                url.set("jdbc:h2:mem:test")
                username.set("sa")
                password.set("secret")
            }
        }
        extension.generators {
            design { enabled.set(true) }
            aggregate { enabled.set(false) }
        }
        extension.templates {
            overrideDirs.from("z-templates", project.file("a-templates"))
            templateOverrideDir.set("bridge/templates")
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals("com.acme.demo", config.basePackage)
        assertEquals(mapOf("application" to "demo-application"), config.modules)
        assertEquals(setOf("design-json"), config.enabledSourceIds())
        assertEquals(setOf("design"), config.enabledGeneratorIds())
        assertEquals("ddd-default", config.templates.preset)
        assertEquals(ConflictPolicy.SKIP, config.templates.conflictPolicy)
        assertEquals(
            listOf(
                project.file("z-templates").absolutePath,
                project.file("a-templates").absolutePath,
                project.file("bridge/templates").absolutePath,
            ),
            config.templates.overrideDirs
        )
    }

    @Test
    fun `aggregate unsupported table policy maps into generator options`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            db {
                enabled.set(true)
                url.set("jdbc:h2:mem:test")
                username.set("sa")
                password.set("secret")
            }
        }
        extension.generators {
            aggregate {
                enabled.set(true)
                unsupportedTablePolicy.set("SKIP")
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            "SKIP",
            config.generators.getValue("aggregate").options["unsupportedTablePolicy"]
        )
    }

    @Test
    fun `design json prefers manifest file when configured`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val manifest = project.file("design/manifest.json")

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                manifestFile.set(manifest.path)
                files.from(project.file("design/ignored-1.json"), project.file("design/ignored-2.json"))
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)
        val options = config.sources.getValue("design-json").options

        assertEquals(manifest.absolutePath, options["manifestFile"])
        assertEquals(project.projectDir.absolutePath, options["projectDir"])
        assertFalse(options.containsKey("files"))
    }

    @Test
    fun `disabled blocks do not trigger validation`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            designJson { enabled.set(false) }
            kspMetadata { enabled.set(false) }
            db {
                enabled.set(false)
                url.set("jdbc:h2:mem:test")
            }
            irAnalysis { enabled.set(false) }
        }
        extension.generators {
            design { enabled.set(false) }
            aggregate { enabled.set(false) }
            flow { enabled.set(false) }
            drawingBoard { enabled.set(false) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(emptyMap<String, String>(), config.modules)
        assertEquals(emptySet<String>(), config.enabledSourceIds())
        assertEquals(emptySet<String>(), config.enabledGeneratorIds())
    }

    @Test
    fun `project base package is required always`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.basePackage is required.", error.message)
    }

    @Test
    fun `design generator requires application module path`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
        }
        extension.generators {
            design { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.applicationModulePath is required when design is enabled.", error.message)
    }

    @Test
    fun `aggregate generator requires aggregate modules`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            db {
                enabled.set(true)
                url.set("jdbc:h2:mem:test")
                username.set("sa")
                password.set("secret")
            }
        }
        extension.generators {
            aggregate { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "project.domainModulePath and project.adapterModulePath are required when aggregate is enabled.",
            error.message
        )
    }

    @Test
    fun `enabled design json source requires files`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            designJson { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("sources.designJson.files must not be empty when designJson is enabled.", error.message)
    }

    @Test
    fun `enabled ksp metadata source requires input dir`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            kspMetadata { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("sources.kspMetadata.inputDir is required when kspMetadata is enabled.", error.message)
    }

    @Test
    fun `enabled db source requires url username and password`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            db {
                enabled.set(true)
                url.set("jdbc:h2:mem:test")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("sources.db.username is required when db is enabled.", error.message)
    }

    @Test
    fun `enabled ir analysis source requires input dirs`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            irAnalysis { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("sources.irAnalysis.inputDirs must not be empty when irAnalysis is enabled.", error.message)
    }

    @Test
    fun `design generator requires enabled design json source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
        }
        extension.generators {
            design { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("design generator requires enabled designJson source.", error.message)
    }

    @Test
    fun `aggregate generator requires enabled db source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            adapterModulePath.set("demo-adapter")
        }
        extension.generators {
            aggregate { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("aggregate generator requires enabled db source.", error.message)
    }

    @Test
    fun `flow generator requires enabled ir analysis source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.generators {
            flow {
                enabled.set(true)
                outputDir.set("flows")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("flow generator requires enabled irAnalysis source.", error.message)
    }

    @Test
    fun `drawing board generator requires enabled ir analysis source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.generators {
            drawingBoard {
                enabled.set(true)
                outputDir.set("design")
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("drawingBoard generator requires enabled irAnalysis source.", error.message)
    }
}

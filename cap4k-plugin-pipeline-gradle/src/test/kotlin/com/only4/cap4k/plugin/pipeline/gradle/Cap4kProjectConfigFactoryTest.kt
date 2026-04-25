package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertFalse(extension.generators.designCommand.enabled.get())
        assertFalse(extension.generators.designQuery.enabled.get())
        assertFalse(extension.generators.designQueryHandler.enabled.get())
        assertFalse(extension.generators.designClient.enabled.get())
        assertFalse(extension.generators.designClientHandler.enabled.get())
        assertFalse(extension.generators.designValidator.enabled.get())
        assertFalse(extension.generators.designApiPayload.enabled.get())
        assertFalse(extension.generators.designDomainEvent.enabled.get())
        assertFalse(extension.generators.designDomainEventHandler.enabled.get())
        assertFalse(extension.generators.aggregate.enabled.get())
        assertEquals("FAIL", extension.generators.aggregate.unsupportedTablePolicy.get())
        assertFalse(extension.generators.aggregate.artifacts.factory.get())
        assertFalse(extension.generators.aggregate.artifacts.specification.get())
        assertFalse(extension.generators.aggregate.artifacts.wrapper.get())
        assertFalse(extension.generators.aggregate.artifacts.unique.get())
        assertFalse(extension.generators.aggregate.artifacts.enumTranslation.get())
        assertFalse(extension.generators.drawingBoard.enabled.get())
        assertFalse(extension.generators.flow.enabled.get())
        assertEquals("ddd-default", extension.templates.preset.get())
        assertEquals("SKIP", extension.templates.conflictPolicy.get())
        assertEquals(null, extension.types.registryFile.orNull)
    }

    @Test
    fun `nested cap4k extension exposes artifact layout defaults`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        assertEquals("domain.aggregates", extension.layout.aggregate.packageRoot.get())
        assertEquals("domain._share.meta", extension.layout.aggregateSchema.packageRoot.get())
        assertEquals("adapter.domain.repositories", extension.layout.aggregateRepository.packageRoot.get())
        assertEquals("flows", extension.layout.flow.outputRoot.get())
        assertEquals("design", extension.layout.drawingBoard.outputRoot.get())
        assertEquals("domain.aggregates", extension.layout.designDomainEvent.packageRoot.get())
        assertEquals("events", extension.layout.designDomainEvent.packageSuffix.get())
    }

    @Test
    fun `factory artifact layout defaults match api defaults`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(ArtifactLayoutConfig(), config.artifactLayout)
    }

    @Test
    fun `factory copies custom artifact layout into project config`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.layout {
            aggregate {
                packageRoot.set("domain.model")
                packageSuffix.set("aggregates")
            }
            aggregateSchema {
                packageRoot.set("domain.model")
                packageSuffix.set("schema")
            }
            aggregateRepository {
                packageRoot.set("adapter.persistence")
                packageSuffix.set("repositories")
            }
            flow {
                outputRoot.set("build/cap4k/flows")
            }
            drawingBoard {
                outputRoot.set("build/cap4k/design")
            }
            designDomainEvent {
                packageRoot.set("domain.model")
                packageSuffix.set("events")
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals("domain.model", config.artifactLayout.aggregate.packageRoot)
        assertEquals("aggregates", config.artifactLayout.aggregate.packageSuffix)
        assertEquals("domain.model", config.artifactLayout.aggregateSchema.packageRoot)
        assertEquals("schema", config.artifactLayout.aggregateSchema.packageSuffix)
        assertEquals("adapter.persistence", config.artifactLayout.aggregateRepository.packageRoot)
        assertEquals("repositories", config.artifactLayout.aggregateRepository.packageSuffix)
        assertEquals("build/cap4k/flows", config.artifactLayout.flow.outputRoot)
        assertEquals("build/cap4k/design", config.artifactLayout.drawingBoard.outputRoot)
        assertEquals("domain.model", config.artifactLayout.designDomainEvent.packageRoot)
        assertEquals("events", config.artifactLayout.designDomainEvent.packageSuffix)
    }

    @Test
    fun `factory rejects invalid layout package root`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.layout.aggregate.packageRoot.set("domain/aggregates")

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "layout.aggregate.packageRoot must be a valid relative Kotlin package fragment: domain/aggregates",
            error.message,
        )
    }

    @Test
    fun `factory rejects layout package root with surrounding whitespace`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.layout.aggregate.packageRoot.set(" domain.aggregates ")

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "layout.aggregate.packageRoot must be a valid relative Kotlin package fragment:  domain.aggregates ",
            error.message,
        )
    }

    @Test
    fun `factory rejects invalid layout output root`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.layout.flow.outputRoot.set("../flows")

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "flow outputRoot must be a valid relative filesystem path: ../flows",
            error.message,
        )
    }

    @Test
    fun `factory rejects layout output root with surrounding whitespace`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.layout.flow.outputRoot.set(" flows ")

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "flow outputRoot must be a valid relative filesystem path:  flows ",
            error.message,
        )
    }

    @Test
    fun `factory includes application and adapter modules and design client generators when enabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
        }
        extension.generators {
            designClient { enabled.set(true) }
            designClientHandler { enabled.set(true) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            mapOf(
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            config.modules,
        )
        assertEquals(setOf("design-client", "design-client-handler"), config.enabledGeneratorIds())
    }

    @Test
    fun `factory includes application module and design validator generator when enabled`() {
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
        }
        extension.generators {
            designValidator { enabled.set(true) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(mapOf("application" to "demo-application"), config.modules)
        assertEquals(setOf("design-validator"), config.enabledGeneratorIds())
    }

    @Test
    fun `factory includes adapter module and design api payload generator when enabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
        }
        extension.generators {
            designApiPayload { enabled.set(true) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(mapOf("adapter" to "demo-adapter"), config.modules)
        assertEquals(setOf("design-api-payload"), config.enabledGeneratorIds())
    }

    @Test
    fun `factory includes domain and application modules and domain event family generators when enabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
            kspMetadata {
                enabled.set(true)
                inputDir.set("build/generated/ksp/main/resources/metadata")
            }
        }
        extension.generators {
            designDomainEvent { enabled.set(true) }
            designDomainEventHandler { enabled.set(true) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
            ),
            config.modules,
        )
        assertEquals(setOf("design-domain-event", "design-domain-event-handler"), config.enabledGeneratorIds())
    }

    @Test
    fun `design validator generator requires application module path`() {
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
            designValidator { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.applicationModulePath is required when designValidator is enabled.", error.message)
    }

    @Test
    fun `design validator generator requires enabled design json source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
        }
        extension.generators {
            designValidator { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("designValidator generator requires enabled designJson source.", error.message)
    }

    @Test
    fun `design api payload generator requires adapter module path`() {
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
            designApiPayload { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.adapterModulePath is required when designApiPayload is enabled.", error.message)
    }

    @Test
    fun `design api payload generator requires enabled design json source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            adapterModulePath.set("demo-adapter")
        }
        extension.generators {
            designApiPayload { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("designApiPayload generator requires enabled designJson source.", error.message)
    }

    @Test
    fun `design domain event generator requires domain module path`() {
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
            designDomainEvent { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.domainModulePath is required when designDomainEvent is enabled.", error.message)
    }

    @Test
    fun `design domain event handler generator requires application module path`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
        }
        extension.generators {
            designDomainEvent { enabled.set(true) }
            designDomainEventHandler { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.applicationModulePath is required when designDomainEventHandler is enabled.", error.message)
    }

    @Test
    fun `design domain event generator requires enabled design json source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
        }
        extension.generators {
            designDomainEvent { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("designDomainEvent generator requires enabled designJson source.", error.message)
    }

    @Test
    fun `design domain event generator does not require enabled ksp metadata source`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)

        extension.project.basePackage.set("com.acme.demo")
        extension.project.domainModulePath.set("demo-domain")
        extension.sources.designJson.enabled.set(true)
        extension.sources.designJson.files.from(project.file("design/design.json"))
        extension.generators.designDomainEvent.enabled.set(true)
        extension.sources.kspMetadata.enabled.set(false)

        val config = Cap4kProjectConfigFactory().build(project, extension)
        assertTrue(config.generators.containsKey("design-domain-event"))
        assertFalse(config.sources.containsKey("ksp-metadata"))
    }

    @Test
    fun `design domain event handler generator requires enabled design domain event generator`() {
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
        }
        extension.generators {
            designDomainEventHandler { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("designDomainEventHandler generator requires enabled designDomainEvent generator.", error.message)
    }

    @Test
    fun `design client generator requires application module path`() {
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
            designClient { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.applicationModulePath is required when designClient is enabled.", error.message)
    }

    @Test
    fun `design client handler generator requires adapter module path`() {
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
        }
        extension.generators {
            designClient { enabled.set(true) }
            designClientHandler { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.adapterModulePath is required when designClientHandler is enabled.", error.message)
    }

    @Test
    fun `design client generator requires enabled design json source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
        }
        extension.generators {
            designClient { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("designClient generator requires enabled designJson source.", error.message)
    }

    @Test
    fun `design client handler generator requires enabled design client generator`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
        }
        extension.generators {
            designClientHandler { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("designClientHandler generator requires enabled designClient generator.", error.message)
    }

    @Test
    fun `factory includes adapter module and design query handler generator when enabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
        }
        extension.generators {
            designCommand { enabled.set(true) }
            designQuery { enabled.set(true) }
            designQueryHandler { enabled.set(true) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            mapOf(
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            config.modules,
        )
        assertEquals(setOf("design-command", "design-query", "design-query-handler"), config.enabledGeneratorIds())
    }

    @Test
    fun `design query handler generator requires adapter module path`() {
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
        }
        extension.generators {
            designQuery { enabled.set(true) }
            designQueryHandler { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.adapterModulePath is required when designQueryHandler is enabled.", error.message)
    }

    @Test
    fun `design query handler generator requires enabled design query generator`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
        }
        extension.generators {
            designQueryHandler { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("designQueryHandler generator requires enabled designQuery generator.", error.message)
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
            designCommand { enabled.set(true) }
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
        assertEquals(setOf("design-command"), config.enabledGeneratorIds())
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
    fun `factory loads project type registry from json file`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "OrderId": "com.acme.order.OrderId",
              "Customer": "com.acme.customer.Customer"
            }
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/project-types.json")
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            linkedMapOf(
                "OrderId" to "com.acme.order.OrderId",
                "Customer" to "com.acme.customer.Customer",
            ),
            config.typeRegistry
        )
    }

    @Test
    fun `factory rejects missing project type registry file`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/missing-project-types.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "types.registryFile does not exist: ${project.file("config/missing-project-types.json").absoluteFile.path}",
            error.message
        )
    }

    @Test
    fun `factory rejects registry root that is not a json object`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText("""["not", "an", "object"]""")

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/project-types.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("types.registryFile must contain a JSON object.", error.message)
    }

    @Test
    fun `factory rejects registry keys that are not simple names`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "bad.name": "com.acme.Bad"
            }
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/project-types.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("types.registryFile type name must be a simple name: bad.name", error.message)
    }

    @Test
    fun `factory rejects duplicate registry keys after trimming`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "OrderId": "com.acme.order.OrderId",
              " OrderId ": "com.acme.order.LegacyOrderId"
            }
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/project-types.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "types.registryFile contains duplicate type name after normalization: OrderId",
            error.message
        )
    }

    @Test
    fun `factory rejects exact duplicate registry keys`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "OrderId": "com.acme.order.OrderId",
              "OrderId": "com.acme.order.LegacyOrderId"
            }
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/project-types.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("types.registryFile contains duplicate type name: OrderId", error.message)
    }

    @Test
    fun `factory rejects registry values that are not fqns`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "Customer": "Customer"
            }
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/project-types.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "types.registryFile value for Customer must be a fully qualified name.",
            error.message
        )
    }

    @Test
    fun `factory rejects fqcn values with surrounding whitespace`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "Customer": " com.acme.Customer "
            }
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/project-types.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "types.registryFile value for Customer must be a fully qualified name.",
            error.message
        )
    }

    @Test
    fun `factory rejects malformed fqcn forms`() {
        val malformedValues = listOf("com..Foo", "Foo.", ".Foo")

        malformedValues.forEach { malformedValue ->
            val project = ProjectBuilder.builder().build()
            val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
            val registryFile = project.file("config/project-types-$malformedValue.json")
            registryFile.parentFile.mkdirs()
            registryFile.writeText(
                """
                {
                  "Customer": "$malformedValue"
                }
                """.trimIndent()
            )

            extension.project {
                basePackage.set("com.acme.demo")
            }
            extension.types {
                this.registryFile.set("config/project-types-$malformedValue.json")
            }

            val error = assertThrows(IllegalArgumentException::class.java) {
                Cap4kProjectConfigFactory().build(project, extension)
            }

            assertEquals(
                "types.registryFile value for Customer must be a fully qualified name.",
                error.message
            )
        }
    }

    @Test
    fun `factory rejects fqcn segments with whitespace or illegal characters`() {
        val malformedValues = listOf("com.acme.Bad Name", "com.acme.Bad#Name")

        malformedValues.forEach { malformedValue ->
            val project = ProjectBuilder.builder().build()
            val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
            val registryFile = project.file("config/project-types-$malformedValue.json")
            registryFile.parentFile.mkdirs()
            registryFile.writeText(
                """
                {
                  "Customer": "$malformedValue"
                }
                """.trimIndent()
            )

            extension.project {
                basePackage.set("com.acme.demo")
            }
            extension.types {
                this.registryFile.set("config/project-types-$malformedValue.json")
            }

            val error = assertThrows(IllegalArgumentException::class.java) {
                Cap4kProjectConfigFactory().build(project, extension)
            }

            assertEquals(
                "types.registryFile value for Customer must be a fully qualified name.",
                error.message
            )
        }
    }

    @Test
    fun `factory rejects built in type overrides`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "String": "com.acme.text.StringAlias"
            }
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.types {
            this.registryFile.set("config/project-types.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("types.registryFile cannot override built-in type: String", error.message)
    }

    @Test
    fun `aggregate unsupported table policy maps into generator options`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
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
    fun `aggregate artifact selection maps into generator options`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
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
                artifacts {
                    factory.set(true)
                    specification.set(true)
                    wrapper.set(true)
                    unique.set(true)
                    enumTranslation.set(true)
                }
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)
        val options = config.generators.getValue("aggregate").options

        assertEquals(true, options["artifact.factory"])
        assertEquals(true, options["artifact.specification"])
        assertEquals(true, options["artifact.wrapper"])
        assertEquals(true, options["artifact.unique"])
        assertEquals(true, options["artifact.enumTranslation"])
    }

    @Test
    fun `aggregate wrapper artifact requires factory artifact`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
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
                artifacts {
                    wrapper.set(true)
                }
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("aggregate wrapper artifact requires enabled aggregate factory artifact.", error.message)
    }

    @Test
    fun `factory includes domain application and adapter modules when aggregate is enabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
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
            aggregate { enabled.set(true) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            config.modules
        )
        assertEquals(setOf("aggregate"), config.enabledGeneratorIds())
    }

    @Test
    fun `factory includes enum manifest source when enabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val manifest = project.file("shared-enums.json")
        manifest.writeText(
            """
            [
              { "name": "Status", "package": "shared", "items": [ { "value": 0, "name": "DRAFT", "desc": "Draft" } ] }
            ]
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            db {
                enabled.set(true)
                url.set("jdbc:h2:mem:test")
                username.set("sa")
                password.set("secret")
            }
            enumManifest {
                enabled.set(true)
                files.from(manifest)
            }
        }
        extension.generators {
            aggregate { enabled.set(true) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(setOf("db", "enum-manifest"), config.enabledSourceIds())
        assertEquals(
            listOf(manifest.absolutePath),
            config.sources.getValue("enum-manifest").options["files"]
        )
    }

    @Test
    fun `runner collects enum manifest source when enabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val manifest = project.file("shared-enums.json")
        manifest.writeText(
            """
            [
              { "name": "Status", "package": "shared", "items": [ { "value": 0, "name": "DRAFT", "desc": "Draft" } ] },
              { "name": "Status", "package": "shared", "items": [ { "value": 1, "name": "PUBLISHED", "desc": "Published" } ] }
            ]
            """.trimIndent()
        )

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            enumManifest {
                enabled.set(true)
                files.from(manifest)
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)
        val error = assertThrows(IllegalArgumentException::class.java) {
            buildRunner(project, config, exportEnabled = false).run(config)
        }

        assertEquals("duplicate shared enum definition: Status", error.message)
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
            designCommand { enabled.set(false) }
            designQuery { enabled.set(false) }
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
    fun `design command generator requires application module path`() {
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
            designCommand { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.applicationModulePath is required when designCommand is enabled.", error.message)
    }

    @Test
    fun `aggregate generator requires domain application and adapter modules`() {
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
            "project.domainModulePath, project.applicationModulePath, and project.adapterModulePath are required when aggregate is enabled.",
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
    fun `design command generator requires enabled design json source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
        }
        extension.generators {
            designCommand { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("designCommand generator requires enabled designJson source.", error.message)
    }

    @Test
    fun `aggregate generator requires enabled db source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
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
            flow { enabled.set(true) }
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
            drawingBoard { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("drawingBoard generator requires enabled irAnalysis source.", error.message)
    }
}

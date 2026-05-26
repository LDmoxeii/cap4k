package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConverter
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
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
        assertFalse(extension.generators.aggregate.enabled.get())
        assertFalse(extension.generators.aggregateProjection.enabled.get())
        assertEquals("FAIL", extension.generators.aggregate.unsupportedTablePolicy.get())
        assertEquals("uuid7", extension.generators.aggregate.specialFields.idDefaultStrategy.get())
        assertEquals("", extension.generators.aggregate.specialFields.deletedDefaultColumn.get())
        assertEquals("", extension.generators.aggregate.specialFields.versionDefaultColumn.get())
        assertFalse(extension.generators.aggregate.artifacts.factory.get())
        assertFalse(extension.generators.aggregate.artifacts.specification.get())
        assertFalse(extension.generators.aggregate.artifacts.unique.get())
        assertFalse(extension.generators.drawingBoard.enabled.get())
        assertFalse(extension.generators.flow.enabled.get())
        assertEquals("ddd-default", extension.templates.preset.get())
        assertEquals("SKIP", extension.templates.conflictPolicy.get())
        assertTrue(extension.templates.templateConflictPolicies.get().isEmpty())
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
        assertEquals("application.subscribers.domain", extension.layout.designDomainEventHandler.packageRoot.get())
        assertEquals("", extension.layout.designDomainEventHandler.packageSuffix.get())
        assertEquals("application.subscribers.integration", extension.layout.designIntegrationEvent.packageRoot.get())
        assertEquals("", extension.layout.designIntegrationEvent.packageSuffix.get())
        assertEquals("application.subscribers.integration", extension.layout.designIntegrationEventSubscriber.packageRoot.get())
        assertEquals("", extension.layout.designIntegrationEventSubscriber.packageSuffix.get())
        assertEquals("adapter.application.queries", extension.layout.designQueryHandler.packageRoot.get())
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
    fun `factory copies normalized aggregate special field defaults into project config`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.generators {
            aggregate {
                specialFields {
                    idDefaultStrategy.set("   ")
                    deletedDefaultColumn.set(" deleted ")
                    versionDefaultColumn.set(" version ")
                }
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals("uuid7", config.aggregateSpecialFieldDefaults.idDefaultStrategy)
        assertEquals("deleted", config.aggregateSpecialFieldDefaults.deletedDefaultColumn)
        assertEquals("version", config.aggregateSpecialFieldDefaults.versionDefaultColumn)
    }

    @Test
    fun `types block owns enum and value object manifests`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("design/type-registry.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText("{}")

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
        }
        extension.types {
            this.registryFile.set("design/type-registry.json")
            enumManifest {
                files.from("design/enums.json")
            }
            valueObjectManifest {
                files.from("design/value-objects.json")
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals("design/type-registry.json", config.typeRegistry.registryFile)
        assertEquals(listOf("design/enums.json"), config.typeRegistry.enumManifestFiles)
        assertEquals(listOf("design/value-objects.json"), config.typeRegistry.valueObjectManifestFiles)
        assertEquals(true, config.sources["value-object-manifest"]?.enabled)
        assertEquals(true, config.generators["types-value-object"]?.enabled)
    }

    @Test
    fun `value object manifest only config carries domain module and generator`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
        }
        extension.types {
            valueObjectManifest {
                files.from("design/value-objects.json")
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(mapOf("domain" to "demo-domain"), config.modules)
        assertEquals(true, config.sources["value-object-manifest"]?.enabled)
        assertEquals(true, config.generators["types-value-object"]?.enabled)
    }

    @Test
    fun `addons block maps provider scoped options`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
        }
        extension.addons {
            provider("only-engine-validator") {
                option("manifestFile", "validation/validators.json")
                option("strict", "true")
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            mapOf("manifestFile" to "validation/validators.json", "strict" to "true"),
            config.addons.getValue("only-engine-validator").options,
        )
    }

    @Test
    fun `generators extension exposes only explicit non design switches`() {
        val methodNames = Cap4kGeneratorsExtension::class.java.methods.map { it.name }.toSet()

        assertTrue("aggregate" in methodNames)
        assertTrue("aggregateProjection" in methodNames)
        assertTrue("flow" in methodNames)
        assertTrue("drawingBoard" in methodNames)
        listOf(
            "designCommand",
            "designQuery",
            "designQueryHandler",
            "designClient",
            "designClientHandler",
            "designValidator",
            "designApiPayload",
            "designDomainEvent",
            "designDomainEventHandler",
            "designIntegrationEvent",
            "designIntegrationEventSubscriber",
        ).forEach { removedMethod ->
            assertFalse(removedMethod in methodNames)
        }
    }

    @Test
    fun `factory maps managed default columns from aggregate special fields with trimming and filtering`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.generators.aggregate.specialFields.managedDefaultColumns.set(
            listOf(" created_at ", "", "  ", "updated_at  ")
        )

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            listOf("created_at", "updated_at"),
            config.aggregateSpecialFieldDefaults.managedDefaultColumns,
        )
    }

    @Test
    fun `factory rejects legacy aggregate entity id override dsl`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        val error = assertThrows(IllegalArgumentException::class.java) {
            extension.generators {
                aggregate {
                    idPolicy {
                        aggregate("message.UserMessage", "uuid7")
                    }
                }
            }
        }

        assertEquals(
            "generators.aggregate.idPolicy is removed. Use generators.aggregate.specialFields { idDefaultStrategy, deletedDefaultColumn, versionDefaultColumn }.",
            error.message,
        )
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
            designIntegrationEvent {
                packageRoot.set("application.integration")
                packageSuffix.set("events")
            }
            designIntegrationEventSubscriber {
                packageRoot.set("application.integration")
                packageSuffix.set("subscribers")
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
        assertEquals("application.integration", config.artifactLayout.designIntegrationEvent.packageRoot)
        assertEquals("events", config.artifactLayout.designIntegrationEvent.packageSuffix)
        assertEquals("application.integration", config.artifactLayout.designIntegrationEventSubscriber.packageRoot)
        assertEquals("subscribers", config.artifactLayout.designIntegrationEventSubscriber.packageSuffix)
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
    fun `factory preserves template override dir order and keeps only enabled blocks`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
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
            aggregate { enabled.set(false) }
        }
        extension.templates {
            overrideDirs.from("z-templates", project.file("a-templates"))
            templateOverrideDir.set("bridge/templates")
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals("com.acme.demo", config.basePackage)
        assertEquals(
            mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            config.modules,
        )
        assertEquals(setOf("design-json"), config.enabledSourceIds())
        assertEquals(
            setOf(
                "design-command",
                "design-query",
                "design-query-handler",
                "design-client",
                "design-client-handler",
                "design-api-payload",
                "design-domain-event",
                "design-domain-event-handler",
                "design-domain-service",
                "design-saga",
                "design-integration-event",
                "design-integration-event-subscriber",
            ),
            config.enabledGeneratorIds(),
        )
        assertFalse(config.enabledGeneratorIds().any { it.contains("validator") })
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
        assertTrue(config.templates.templateConflictPolicies.isEmpty())
    }

    @Test
    fun `factory trims and maps template conflict policies`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.templates {
            templateConflictPolicies.put(" aggregate/factory.kt.peb ", " overwrite ")
            templateConflictPolicies.put("aggregate/behavior.kt.peb", "FAIL")
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(
            mapOf(
                "aggregate/factory.kt.peb" to ConflictPolicy.OVERWRITE,
                "aggregate/behavior.kt.peb" to ConflictPolicy.FAIL,
            ),
            config.templates.templateConflictPolicies,
        )
    }

    @Test
    fun `factory rejects blank template id in template conflict policies`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.templates {
            templateConflictPolicies.put("   ", "OVERWRITE")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("templates.templateConflictPolicies contains a blank template id.", error.message)
    }

    @Test
    fun `factory rejects duplicate template ids after normalization in template conflict policies`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.templates {
            templateConflictPolicies.put(" aggregate/factory.kt.peb ", "OVERWRITE")
            templateConflictPolicies.put("aggregate/factory.kt.peb", "FAIL")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals(
            "templates.templateConflictPolicies contains duplicate template id after normalization: aggregate/factory.kt.peb",
            error.message,
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
              "OrderId": { "fqn": "com.acme.order.OrderId" },
              "Customer": { "fqn": "com.acme.customer.Customer", "converter": false },
              "External": {
                "fqn": "com.acme.external.ExternalValue",
                "converter": "com.acme.external.ExternalValueConverter"
              }
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
                "OrderId" to TypeRegistryEntry(
                    fqn = "com.acme.order.OrderId",
                    converter = TypeRegistryConverter.nested(),
                ),
                "Customer" to TypeRegistryEntry(
                    fqn = "com.acme.customer.Customer",
                    converter = TypeRegistryConverter.none(),
                ),
                "External" to TypeRegistryEntry(
                    fqn = "com.acme.external.ExternalValue",
                    converter = TypeRegistryConverter.explicit("com.acme.external.ExternalValueConverter"),
                ),
            ),
            config.typeRegistry.entries
        )
        assertEquals("config/project-types.json", config.typeRegistry.registryFile)
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
            error.message,
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
              "bad.name": { "fqn": "com.acme.Bad" }
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
              "OrderId": { "fqn": "com.acme.order.OrderId" },
              " OrderId ": { "fqn": "com.acme.order.LegacyOrderId" }
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
              "OrderId": { "fqn": "com.acme.order.OrderId" },
              "OrderId": { "fqn": "com.acme.order.LegacyOrderId" }
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
    fun `factory rejects registry values that are not objects`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "Customer": "com.acme.Customer"
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
            "types.registryFile value for Customer must be an object.",
            error.message
        )
    }

    @Test
    fun `factory rejects registry fqns that are not fqns`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val registryFile = project.file("config/project-types.json")
        registryFile.parentFile.mkdirs()
        registryFile.writeText(
            """
            {
              "Customer": { "fqn": "Customer" }
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
            "types.registryFile value for Customer.fqn must be a fully qualified name.",
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
              "Customer": { "fqn": " com.acme.Customer " }
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
            "types.registryFile value for Customer.fqn must be a fully qualified name.",
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
                  "Customer": { "fqn": "$malformedValue" }
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
                "types.registryFile value for Customer.fqn must be a fully qualified name.",
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
                  "Customer": { "fqn": "$malformedValue" }
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
                "types.registryFile value for Customer.fqn must be a fully qualified name.",
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
              "String": { "fqn": "com.acme.text.StringAlias" }
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
                    unique.set(true)
                }
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)
        val options = config.generators.getValue("aggregate").options

        assertEquals(true, options["artifact.factory"])
        assertEquals(true, options["artifact.specification"])
        assertEquals(true, options["artifact.unique"])
        assertFalse(options.containsKey("artifact.enum" + "Translation"))
        assertFalse(options.containsKey("artifact.wrapper"))
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
    fun `factory includes adapter module and aggregate projection generator when enabled`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
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
            aggregateProjection { enabled.set(true) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(mapOf("adapter" to "demo-adapter"), config.modules)
        assertEquals(setOf("aggregate-projection"), config.enabledGeneratorIds())
    }

    @Test
    fun `aggregate projection generator requires adapter module path`() {
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
            aggregateProjection { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("project.adapterModulePath is required when aggregateProjection is enabled.", error.message)
    }

    @Test
    fun `aggregate projection generator requires enabled db source`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            adapterModulePath.set("demo-adapter")
        }
        extension.generators {
            aggregateProjection { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("aggregateProjection generator requires enabled db source.", error.message)
    }

    @Test
    fun `factory maps enum manifest from types block`() {
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
        }
        extension.types {
            enumManifest {
                files.from(manifest)
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(setOf("enum-manifest"), config.enabledSourceIds())
        assertEquals(listOf("shared-enums.json"), config.typeRegistry.enumManifestFiles)
    }

    @Test
    fun `design json prefers manifest file when configured`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val manifest = project.file("design/manifest.json")

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
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
    fun `design json source keeps only explicitly configured module paths`() {
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

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(mapOf("domain" to "demo-domain"), config.modules)
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
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
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
    fun `enabled db source requires password to be configured`() {
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
            }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("sources.db.password is required when db is enabled.", error.message)
    }

    @Test
    fun `enabled db source accepts explicit blank password`() {
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
                password.set("")
            }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals("", config.sources.getValue("db").options["password"])
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

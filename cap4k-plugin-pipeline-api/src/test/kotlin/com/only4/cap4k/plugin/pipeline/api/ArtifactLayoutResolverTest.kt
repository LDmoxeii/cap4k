package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ArtifactLayoutResolverTest {

    private val resolver = ArtifactLayoutResolver(basePackage = "com.acme.demo")

    @Test
    fun `resolves default aggregate packages`() {
        assertEquals("com.acme.demo.domain.aggregates.user_message", resolver.aggregateEntityPackage("user_message"))
        assertEquals("com.acme.demo.domain._share.meta.user_message", resolver.aggregateSchemaPackage("user_message"))
        assertEquals("com.acme.demo.domain._share.meta", resolver.aggregateSchemaBasePackage())
        assertEquals("com.acme.demo.adapter.domain.repositories", resolver.aggregateRepositoryPackage())
        assertEquals("com.acme.demo.domain.shared.enums", resolver.aggregateSharedEnumPackage(""))
        assertEquals("com.acme.demo.domain.quality.enums", resolver.aggregateSharedEnumPackage("quality"))
        assertEquals("com.acme.demo.domain.translation.shared", resolver.aggregateEnumTranslationPackage("shared"))
        assertEquals("com.acme.demo.application.queries.user_message.unique", resolver.aggregateUniqueQueryPackage("user_message"))
        assertEquals("com.acme.demo.adapter.queries.user_message.unique", resolver.aggregateUniqueQueryHandlerPackage("user_message"))
        assertEquals("com.acme.demo.application.validators.user_message.unique", resolver.aggregateUniqueValidatorPackage("user_message"))
    }

    @Test
    fun `resolves aggregate helper packages from resolved entity package`() {
        val entityPackage = "com.acme.demo.domain.aggregates.user_message"

        assertEquals(entityPackage, resolver.aggregateWrapperPackage(entityPackage))
        assertEquals("com.acme.demo.domain.aggregates.user_message.factory", resolver.aggregateFactoryPackage(entityPackage))
        assertEquals(
            "com.acme.demo.domain.aggregates.user_message.specification",
            resolver.aggregateSpecificationPackage(entityPackage),
        )
        assertEquals("com.acme.demo.domain.aggregates.user_message.enums", resolver.aggregateLocalEnumPackage(entityPackage))
    }

    @Test
    fun `resolves default design packages`() {
        assertEquals("com.acme.demo.application.commands.message.create", resolver.designCommandPackage("message.create"))
        assertEquals("com.acme.demo.application.queries.message.read", resolver.designQueryPackage("message.read"))
        assertEquals("com.acme.demo.application.distributed.clients.message.delivery", resolver.designClientPackage("message.delivery"))
        assertEquals(
            "com.acme.demo.adapter.application.queries.message.read",
            resolver.designQueryHandlerPackage("message.read"),
        )
        assertEquals(
            "com.acme.demo.adapter.application.distributed.clients.message.delivery",
            resolver.designClientHandlerPackage("message.delivery"),
        )
        assertEquals("com.acme.demo.application.validators.message", resolver.designValidatorPackage("message"))
        assertEquals("com.acme.demo.adapter.portal.api.payload.message", resolver.designApiPayloadPackage("message"))
        assertEquals("com.acme.demo.domain.aggregates.message.events", resolver.designDomainEventPackage("message"))
        assertEquals("com.acme.demo.application.message.events", resolver.designDomainEventHandlerPackage("message"))
    }

    @Test
    fun `resolves default output roots`() {
        assertEquals("flows", resolver.flowOutputRoot())
        assertEquals("design", resolver.drawingBoardOutputRoot())
    }

    @Test
    fun `normalizes configured output roots to slash separated paths`() {
        val resolver = ArtifactLayoutResolver(
            basePackage = "com.acme.demo",
            artifactLayout = ArtifactLayoutConfig(
                flow = OutputRootLayout(outputRoot = "build\\cap4k\\flows"),
            ),
        )

        assertEquals("build/cap4k/flows", resolver.flowOutputRoot())
    }

    @Test
    fun `resolves package against base package when package root is blank`() {
        val resolver = ArtifactLayoutResolver(
            basePackage = "com.acme.demo",
            artifactLayout = ArtifactLayoutConfig(
                aggregate = PackageLayout(packageRoot = ""),
            ),
        )

        assertEquals("com.acme.demo.user_message", resolver.aggregateEntityPackage("user_message"))
    }

    @Test
    fun `resolves source and resource paths`() {
        assertEquals("demo-domain/src/main/kotlin", resolver.kotlinSourceRoot("demo-domain"))
        assertEquals(
            "demo-domain/build/generated/cap4k/main/kotlin",
            resolver.generatedKotlinSourceRoot("demo-domain"),
        )
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_message/UserMessage.kt",
            resolver.kotlinSourcePath(
                moduleRoot = "demo-domain",
                packageName = "com.acme.demo.domain.aggregates.user_message",
                typeName = "UserMessage",
            ),
        )
        assertEquals(
            "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/user_message/UserMessage.kt",
            resolver.generatedKotlinSourcePath(
                moduleRoot = "demo-domain",
                packageName = "com.acme.demo.domain.aggregates.user_message",
                typeName = "UserMessage",
            ),
        )
        assertEquals("flows/user_message/create.yaml", resolver.projectResourcePath("flows", "user_message/create.yaml"))
    }

    @Test
    fun `public helpers use value first signatures`() {
        assertEquals(
            "domain.aggregates",
            ArtifactLayoutResolver.validatePackageFragment("domain.aggregates", "layout.aggregate.packageRoot"),
        )
        assertEquals("build/cap4k/flows", ArtifactLayoutResolver.normalizeOutputRoot("build\\cap4k\\flows", "flow"))
    }

    @Test
    fun `rejects invalid base package fragments`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ArtifactLayoutResolver(basePackage = "")
        }

        assertEquals(
            "basePackage must be a valid relative Kotlin package fragment: ",
            exception.message,
        )
    }

    @Test
    fun `rejects invalid aggregate package root fragments`() {
        listOf(
            "domain/aggregates",
            "domain\\aggregates",
            ".domain",
            "domain.",
            "domain..aggregates",
            "domain.*",
        ).forEach { packageRoot ->
            val exception = assertThrows(IllegalArgumentException::class.java) {
                ArtifactLayoutResolver(
                    basePackage = "com.acme.demo",
                    artifactLayout = ArtifactLayoutConfig(
                        aggregate = PackageLayout(packageRoot = packageRoot),
                    ),
                )
            }

            assertEquals(
                "layout.aggregate.packageRoot must be a valid relative Kotlin package fragment: $packageRoot",
                exception.message,
            )
        }
    }

    @Test
    fun `rejects invalid flow output roots`() {
        listOf(
            "",
            " ",
            " flows",
            "../flows",
            "flows/..",
            "/flows",
            "\\flows",
            java.io.File("/flows").absolutePath,
        ).forEach { outputRoot ->
            val exception = assertThrows(IllegalArgumentException::class.java) {
                ArtifactLayoutResolver(
                    basePackage = "com.acme.demo",
                    artifactLayout = ArtifactLayoutConfig(
                        flow = OutputRootLayout(outputRoot = outputRoot),
                    ),
                )
            }

            assertEquals(
                "flow outputRoot must be a valid relative filesystem path: $outputRoot",
                exception.message,
            )
        }
    }

    @Test
    fun `rejects invalid drawing board output root with drawing board label`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ArtifactLayoutResolver(
                basePackage = "com.acme.demo",
                artifactLayout = ArtifactLayoutConfig(
                    drawingBoard = OutputRootLayout(outputRoot = "../design"),
                ),
            )
        }

        assertEquals(
            "drawing-board outputRoot must be a valid relative filesystem path: ../design",
            exception.message,
        )
    }
}

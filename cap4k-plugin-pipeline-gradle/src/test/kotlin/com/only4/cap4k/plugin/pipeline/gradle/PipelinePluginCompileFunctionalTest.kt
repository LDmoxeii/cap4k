package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PipelinePluginCompileFunctionalTest {

    @Test
    fun `validator generation participates in application compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-validator-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-validator-compile-sample")
        val designFile = projectDir.resolve("design/design.json")
        designFile.writeText(
            designFile.readText().replace(
                "\"desc\": \"order id validator\"",
                "\"desc\": \"order */ validator\"",
            )
        )

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-application:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("OrderIdValid"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .build()
        val generatedValidator = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/order/OrderIdValid.kt"
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/order/OrderIdValid.kt",
        )
        assertTrue(generatedValidator.contains("* order * / validator"))
        assertFalse(generatedValidator.contains("* order */ validator"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `request and query variants compile in the application module`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-compile-sample")
        disableHandlerGenerators(projectDir)

        val settingsContent = projectDir.resolve("settings.gradle.kts").readText()
        assertFalse(settingsContent.contains("__CAP4K_REPO_ROOT__"))
        assertTrue(settingsContent.contains("includeBuild(\""))

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-application:compileKotlin")?.outcome
        )

        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-application:compileKotlin"
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertGeneratedFilesExist(
            projectDir,
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt",
        )
    }

    @Test
    fun `query-handler and client-handler variants compile in the adapter module`() {
        val redProjectDir = Files.createTempDirectory("pipeline-functional-design-compile-adapter-red")
        FunctionalFixtureSupport.copyCompileFixture(redProjectDir, "design-compile-sample")
        removeApplicationCompileSmokeSource(redProjectDir)

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(redProjectDir, ":demo-adapter:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-adapter:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("FindOrderQryHandler"))

        val projectDir = Files.createTempDirectory("pipeline-functional-design-compile-adapter")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-compile-sample")
        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-adapter:compileKotlin"
        )

        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertGeneratedFilesExist(
            projectDir,
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt",
        )
    }

    @Test
    fun `api payload generation participates in adapter compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-api-payload-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-api-payload-compile-sample")

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-adapter:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("SubmitOrderPayload"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/order/SubmitOrderPayload.kt",
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `domain event generation participates in domain and application compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-domain-event-compile-sample")
        val designFile = projectDir.resolve("design/design.json")
        designFile.writeText(
            designFile.readText().replace(
                "\"desc\": \"order \\\"created\\\" event\"",
                "\"desc\": \"order */ created\"",
            )
        )

        val beforeGenerateDomainCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateDomainCompileResult.task(":demo-domain:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateDomainCompileResult.output.contains("OrderCreatedDomainEvent"))

        val beforeGenerateApplicationCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin", "-x", ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateApplicationCompileResult.task(":demo-application:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateApplicationCompileResult.output.contains("OrderCreatedDomainEventSubscriber"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val domainCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val applicationCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .build()
        val generatedEvent = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt"
        ).readText()
        val generatedHandler = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt"
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt",
        )
        assertTrue(generatedEvent.contains("* order * / created"))
        assertFalse(generatedEvent.contains("* order */ created"))
        assertTrue(generatedHandler.contains("* order * / created"))
        assertFalse(generatedHandler.contains("* order */ created"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(domainCompileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(applicationCompileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate factory and specification generation participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("VideoPostFactory"))
        assertTrue(beforeGenerateCompileResult.output.contains("AggVideoPost"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val generatedEntity = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).toFile().readText()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt",
        )
        assertTrue(generatedEntity.contains("import jakarta.persistence.Column"))
        assertTrue(generatedEntity.contains("import jakarta.persistence.Entity"))
        assertTrue(generatedEntity.contains("import jakarta.persistence.Id"))
        assertTrue(generatedEntity.contains("import jakarta.persistence.Table"))
        assertTrue(generatedEntity.contains("@Entity"))
        assertTrue(generatedEntity.contains("@Table(name = \"video_post\")"))
        assertTrue(generatedEntity.contains("@Id"))
        assertTrue(generatedEntity.contains("@Column(name = \"id\")"))
        assertFalse(generatedEntity.contains("@GeneratedValue"))
        assertFalse(generatedEntity.contains("@Version"))
        assertFalse(generatedEntity.contains("@DynamicInsert"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate relation generation participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-relation-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts")
        val domainBuildFileContent = domainBuildFile.readText()

        assertFalse(domainBuildFileContent.contains("jakarta.persistence:jakarta.persistence-api"))

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("VideoPost"))
        assertTrue(beforeGenerateCompileResult.output.contains("VideoPostItem"))

        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-domain:compileKotlin"
        )

        assertGeneratedFilesExist(
            projectDir,
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_profile/UserProfile.kt",
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate persistence field behavior generation participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-persistence-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-persistence-compile-sample")
        val applicationBuildFile = projectDir.resolve("demo-application/build.gradle.kts").readText().trim()
        val adapterBuildFile = projectDir.resolve("demo-adapter/build.gradle.kts").readText().trim()
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts").readText()

        assertTrue(applicationBuildFile == "// Functional fixture module.")
        assertTrue(adapterBuildFile == "// Functional fixture module.")
        assertTrue(domainBuildFile.contains("org.springframework:spring-context"))
        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(TaskOutcome.FAILED, beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome)

        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-domain:compileKotlin"
        )

        val generatedEntity = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).readText()

        assertTrue(generatedEntity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedEntity.contains("@Version"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate provider specific persistence generation participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-provider-persistence-compile-sample")
        val applicationBuildFile = projectDir.resolve("demo-application/build.gradle.kts").readText().trim()
        val adapterBuildFile = projectDir.resolve("demo-adapter/build.gradle.kts").readText().trim()
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts").readText()

        assertTrue(applicationBuildFile == "// Functional fixture module.")
        assertTrue(adapterBuildFile == "// Functional fixture module.")
        assertTrue(domainBuildFile.contains("org.hibernate.orm:hibernate-core"))
        assertTrue(domainBuildFile.contains("jakarta.persistence:jakarta.persistence-api"))
        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(TaskOutcome.FAILED, beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome)

        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-domain:compileKotlin"
        )

        val generatedVideoPost = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).readText()
        val generatedAuditLog = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/audit_log/AuditLog.kt"
        ).readText()

        assertTrue(generatedVideoPost.contains("@DynamicInsert"))
        assertTrue(generatedVideoPost.contains("@DynamicUpdate"))
        assertTrue(generatedVideoPost.contains("@SQLDelete"))
        assertTrue(generatedVideoPost.contains("@Where"))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertTrue(generatedAuditLog.contains("@SQLDelete"))
        assertTrue(generatedAuditLog.contains("@Where"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate enum generation participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum-domain-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-enum-compile-sample")

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("VideoPost"))
        assertTrue(beforeGenerateCompileResult.output.contains("Status"))
        assertTrue(beforeGenerateCompileResult.output.contains("VideoPostVisibility"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val generatedEntity = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        ).readText()
        val generatedSharedEnum = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt"
        ).readText()
        val generatedLocalEnum = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/enums/VideoPostVisibility.kt"
        ).readText()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/enums/VideoPostVisibility.kt",
        )
        assertTrue(generatedEntity.contains("@Entity"))
        assertTrue(generatedEntity.contains("@Table(name = \"video_post\")"))
        assertTrue(generatedEntity.contains("@Id"))
        assertTrue(generatedEntity.contains("@Column(name = \"id\")"))
        assertTrue(generatedEntity.contains("@Column(name = \"status\")"))
        assertTrue(
            generatedEntity.contains(
                "@Convert(converter = com.acme.demo.domain.shared.enums.Status.Converter::class)"
            )
        )
        assertTrue(generatedSharedEnum.contains("class Converter : AttributeConverter<Status, Int>"))
        assertTrue(generatedLocalEnum.contains("class Converter : AttributeConverter<VideoPostVisibility, Int>"))
        assertFalse(generatedEntity.contains("@GeneratedValue"))
        assertFalse(generatedEntity.contains("@Version"))
        assertFalse(generatedEntity.contains("@DynamicInsert"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate enum translation generation participates in adapter compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum-adapter-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-enum-compile-sample")

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin", "-x", ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-adapter:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("StatusTranslation"))
        assertTrue(beforeGenerateCompileResult.output.contains("VideoPostVisibilityTranslation"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            "demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/StatusTranslation.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/video_post/VideoPostVisibilityTranslation.kt",
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate unique query and validator generation participates in application compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-unique-application-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-application:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("UniqueVideoPostSlug"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .build()
        val queryContent = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt"
        ).readText()
        val validatorContent = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt"
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt",
        )
        assertTrue(queryContent.contains("data class Request("))
        assertTrue(queryContent.contains("val excludeVideoPostId: Long?"))
        assertTrue(queryContent.contains(") : RequestParam<Response>"))
        assertTrue(validatorContent.contains("annotation class UniqueVideoPostSlug"))
        assertTrue(
            validatorContent.contains("import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry")
        )
        assertTrue(
            validatorContent.contains(
                "class Validator : ConstraintValidator<UniqueVideoPostSlug, UniqueVideoPostSlugQry.Request>"
            )
        )
        assertTrue(validatorContent.contains("request.slug"))
        assertTrue(validatorContent.contains("request.excludeVideoPostId"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate unique query handler generation participates in adapter compileKotlin`() {
        val redProjectDir = Files.createTempDirectory("pipeline-functional-aggregate-unique-adapter-compile-red")
        FunctionalFixtureSupport.copyCompileFixture(redProjectDir, "aggregate-compile-sample")
        removeAggregateUniqueApplicationCompileSmokeSource(redProjectDir)

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(redProjectDir, ":demo-adapter:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-adapter:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateCompileResult.output.contains("UniqueVideoPostSlugQryHandler"))

        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-unique-adapter-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")
        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-adapter:compileKotlin"
        )
        val handlerContent = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt"
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt",
        )
        assertTrue(
            handlerContent.contains(
                "class UniqueVideoPostSlugQryHandler : Query<UniqueVideoPostSlugQry.Request, UniqueVideoPostSlugQry.Response>"
            )
        )
        assertTrue(
            handlerContent.contains(
                "override fun exec(request: UniqueVideoPostSlugQry.Request): UniqueVideoPostSlugQry.Response"
            )
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `integrated compile sample keeps migrated design families compile-safe together`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-integrated-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-integrated-compile-sample")

        val beforeGenerateDomainCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateDomainCompileResult.task(":demo-domain:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateDomainCompileResult.output.contains("OrderCreatedDomainEvent"))

        val beforeGenerateApplicationCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin", "-x", ":demo-domain:compileKotlin")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateApplicationCompileResult.task(":demo-application:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateApplicationCompileResult.output.contains("FindOrderQry"))

        val beforeGenerateAdapterCompileResult = FunctionalFixtureSupport
            .runner(
                projectDir,
                ":demo-adapter:compileKotlin",
                "-x",
                ":demo-domain:compileKotlin",
                "-x",
                ":demo-application:compileKotlin"
            )
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateAdapterCompileResult.task(":demo-adapter:compileKotlin")?.outcome
        )
        assertTrue(beforeGenerateAdapterCompileResult.output.contains("FindOrderQryHandler"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val domainCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val applicationCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .build()
        val adapterCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/order/OrderIdValid.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/order/SubmitOrderPayload.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt",
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(domainCompileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(applicationCompileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(adapterCompileResult.output.contains("BUILD SUCCESSFUL"))
    }

    private fun assertGeneratedFilesExist(projectDir: Path, vararg relativePaths: String) {
        relativePaths.forEach { relativePath ->
            assertTrue(
                projectDir.resolve(relativePath).toFile().exists(),
                "Expected generated file to exist: $relativePath"
            )
        }
    }

    private fun disableHandlerGenerators(projectDir: Path) {
        val buildFile = projectDir.resolve("build.gradle.kts")
        val designQueryHandlerBlock = Regex(
            """designQueryHandler\s*\{\s*enabled\.set\(true\)\s*}""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
        )
        val designClientHandlerBlock = Regex(
            """designClientHandler\s*\{\s*enabled\.set\(true\)\s*}""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
        )
        val patchedContent = buildFile.readText()
            .replace(designQueryHandlerBlock, "designQueryHandler {\n            enabled.set(false)\n        }")
            .replace(designClientHandlerBlock, "designClientHandler {\n            enabled.set(false)\n        }")
        buildFile.writeText(patchedContent)
        assertTrue(patchedContent.contains("designQueryHandler {\n            enabled.set(false)\n        }"))
        assertTrue(patchedContent.contains("designClientHandler {\n            enabled.set(false)\n        }"))
    }

    private fun removeApplicationCompileSmokeSource(projectDir: Path) {
        val applicationCompileSmokePath = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/smoke/CompileSmoke.kt"
        )
        Files.deleteIfExists(applicationCompileSmokePath)
    }

    private fun removeAggregateUniqueApplicationCompileSmokeSource(projectDir: Path) {
        val applicationCompileSmokePath = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/AggregateUniqueApplicationCompileSmoke.kt"
        )
        Files.deleteIfExists(applicationCompileSmokePath)
    }

}

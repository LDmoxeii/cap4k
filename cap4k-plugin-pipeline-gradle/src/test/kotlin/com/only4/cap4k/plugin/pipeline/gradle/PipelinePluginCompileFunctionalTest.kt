package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
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
    fun `empty configured project keeps generation in the compile lifecycle as a no-op`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-empty-project-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "empty-project-compile-sample")

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertEquals(TaskOutcome.NO_SOURCE, compileResult.task(":demo-domain:compileKotlin")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `request and query variants compile in the application module`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-compile-sample")

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
        val listQueryContent = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt",
        ).readText()
        val pageQueryContent = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt",
        ).readText()
        assertTrue(listQueryContent.contains("val items: List<Item>"))
        assertTrue(pageQueryContent.contains(") : PageRequest, RequestParam<Response>"))
        assertTrue(pageQueryContent.contains("val page: PageData<Item>"))
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
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderQryHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderListQryHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderPageQryHandler.kt",
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
    fun `nested recursive design payload generation participates in adapter compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-nested-recursion-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-nested-recursion-compile-sample")

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin")
            .build()

        val payloadFile = projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/video/SyncVideoPostProcessStatus.kt",
        )
        val content = payloadFile.readText()

        assertGeneratedFilesExist(
            projectDir,
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/video/SyncVideoPostProcessStatus.kt",
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertContainsNormalized(
            content,
            """
            data class Request(
                val fileList: List<FileItem>,
                val itemList: List<Item>,
                val externalItem: com.acme.shared.Item
            ) {
            """.trimIndent(),
        )
        assertContainsNormalized(
            content,
            """
            data class FileItem(
                val fileIndex: Int,
                val variants: List<VariantItem>
            )
            """.trimIndent(),
        )
        assertContainsNormalized(
            content,
            """
            data class VariantItem(
                val quality: String = "",
                val width: Int = 0,
                val children: List<VariantItem>
            )
            """.trimIndent(),
        )
        assertContainsNormalized(
            content,
            """
            data class Item(
                val requestValue: String
            )
                }
            """.trimIndent(),
        )
        assertContainsNormalized(
            content,
            """
            data class Response(
                val nodes: List<Node>,
                val list: List<Item>
            ) {
            """.trimIndent(),
        )
        assertContainsNormalized(
            content,
            """
            data class Node(
                val categoryId: Long,
                val children: List<Node>
            )
            """.trimIndent(),
        )
        assertContainsNormalized(
            content,
            """
            data class Item(
                val messageType: Int,
                val count: Int
            )
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `domain event generation participates in domain and application compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-domain-event-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-domain-event-compile-sample")
        val designFile = projectDir.resolve("design/design.json")
        designFile.writeText(
            designFile.readText().replace(
                "\"description\": \"order \\\"created\\\" event\"",
                "\"description\": \"order */ created\"",
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
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt"
        ).readText()
        val generatedHandler = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt"
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt",
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
    fun `integration event generation participates in application compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-integration-event-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-integration-event-compile-sample")

        val beforeGenerateApplicationCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .buildAndFail()
        assertTrue(beforeGenerateApplicationCompileResult.output.contains("MediaProcessingCallbackIntegrationEvent"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val applicationCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .build()
        val inboundEvent = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/inbound/media/processing/MediaProcessingCallbackIntegrationEvent.kt"
        ).readText()
        val inboundSubscriber = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/MediaProcessingCallbackIntegrationEventSubscriber.kt"
        ).readText()
        val outboundEvent = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/outbound/content/ContentPublishedIntegrationEvent.kt"
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/inbound/media/processing/MediaProcessingCallbackIntegrationEvent.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/MediaProcessingCallbackIntegrationEventSubscriber.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/outbound/content/ContentPublishedIntegrationEvent.kt",
        )
        assertFalse(
            projectDir.resolve(
                "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/outbound/content/ContentPublishedIntegrationEventSubscriber.kt"
            ).toFile().exists()
        )
        assertTrue(inboundEvent.contains("value = \"cap4k.reference.contentstudio.media-processing.succeeded\""))
        assertTrue(inboundEvent.contains("subscriber = \"\\${'$'}{spring.application.name:}\""))
        assertTrue(inboundEvent.contains("val externalTaskId: String"))
        assertTrue(inboundEvent.contains("data class FileInfo("))
        assertTrue(inboundSubscriber.contains("@EventListener(MediaProcessingCallbackIntegrationEvent::class)"))
        assertTrue(outboundEvent.contains("value = \"cap4k.reference.content.published\""))
        assertTrue(outboundEvent.contains("subscriber = IntegrationEvent.NONE_SUBSCRIBER"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(applicationCompileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate factory generation participates in domain compileKotlin`() {
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
        assertFalse(beforeGenerateCompileResult.output.contains("AggVideoPost"))

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val generatedEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).toFile().readText()
        val generatedContentEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/Content.kt")
        ).toFile().readText()
        val generatedMediaProcessingTaskEntity = projectDir.resolve(
            generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/media_processing_task/MediaProcessingTask.kt"
            )
        ).toFile().readText()
        val checkedInMediaProcessingResultSnapshot = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/media_processing_task/values/MediaProcessingResultSnapshot.kt"
        ).toFile().readText()
        val checkedInEntity = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
        )
        val behaviorFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt"
        )
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostId.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/Content.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/ContentId.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/media_processing_task/MediaProcessingTask.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/media_processing_task/MediaProcessingTaskId.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/ids/AuthorId.kt"),
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/media_processing_task/values/MediaProcessingResultSnapshot.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt",
        )
        assertFalse(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt"
            ).toFile().exists()
        )
        assertFalse(checkedInEntity.toFile().exists())
        assertTrue(behaviorFile.readText().contains("Place behavior for VideoPost and its owned entities here."))
        assertTrue(generatedEntity.contains("import jakarta.persistence.Entity"))
        assertTrue(generatedEntity.contains("import jakarta.persistence.EmbeddedId"))
        assertTrue(generatedEntity.contains("import jakarta.persistence.Table"))
        assertTrue(generatedEntity.contains("@Entity"))
        assertTrue(generatedEntity.contains("@Table(name = \"video_post\")"))
        assertTrue(generatedEntity.contains("@EmbeddedId"))
        assertGeneratedOwnIdShape(generatedEntity, "VideoPostId")
        assertFalse(generatedEntity.contains("@GeneratedValue"))
        assertFalse(generatedEntity.contains("@Version"))
        assertFalse(generatedEntity.contains("@DynamicInsert"))
        assertTrue(generatedContentEntity.contains("import com.acme.demo.domain.shared.ids.AuthorId"))
        assertTrue(generatedContentEntity.contains("import com.acme.demo.domain.aggregates.media_processing_task.MediaProcessingTaskId"))
        assertGeneratedOwnIdShape(generatedContentEntity, "ContentId")
        assertTrue(generatedContentEntity.contains("var authorId: AuthorId = authorId"))
        assertTrue(generatedContentEntity.contains("var mediaProcessingTaskId: MediaProcessingTaskId? = mediaProcessingTaskId"))
        assertTrue(
            generatedMediaProcessingTaskEntity.contains(
                "import com.acme.demo.domain.aggregates.media_processing_task.values.MediaProcessingResultSnapshot"
            )
        )
        assertTrue(
            generatedMediaProcessingTaskEntity.contains(
                "@Convert(converter = MediaProcessingResultSnapshot.Converter::class)"
            )
        )
        assertTrue(
            generatedMediaProcessingTaskEntity.contains(
                "var resultSnapshot: MediaProcessingResultSnapshot? = resultSnapshot"
            )
        )
        assertTrue(checkedInMediaProcessingResultSnapshot.contains("data class MediaProcessingResultSnapshot("))
        assertTrue(
            checkedInMediaProcessingResultSnapshot.contains(
                "class Converter : AttributeConverter<MediaProcessingResultSnapshot, String>"
            )
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate relation generation keeps owned parent bindings forward only`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-relation-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts")
        val domainBuildFileContent = domainBuildFile.readText()

        assertFalse(domainBuildFileContent.contains("jakarta.persistence:jakarta.persistence-api"))

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val generatedRootEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedChildEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt")
        ).readText()
        val generatedOneChildEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt")
        ).readText()
        val generatedVariantEntity = projectDir.resolve(
            generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFileVariant.kt"
            )
        ).readText()
        val generatedContentEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/Content.kt")
        ).readText()
        val generatedRootSchema = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt")
        ).readText()
        val generatedChildSchema = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPostItem.kt")
        ).readText()
        val generatedFileSchema = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPostFile.kt")
        ).readText()
        val generatedVariantSchema = projectDir.resolve(
            generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPostFileVariant.kt"
            )
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFileVariant.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_profile/UserProfile.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/Content.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/ContentId.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/media_processing_task/MediaProcessingTask.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/media_processing_task/MediaProcessingTaskId.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/ids/AuthorId.kt"),
        )
        assertFalse(
            projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt")
                .toFile()
                .exists()
        )
        assertTrue(generatedRootEntity.contains("import jakarta.persistence.CascadeType"))
        assertTrue(generatedRootEntity.contains("import com.acme.demo.domain.aggregates.user_profile.UserProfileId"))
        assertTrue(generatedRootEntity.contains("var authorId: UserProfileId = authorId"))
        assertTrue(generatedRootEntity.contains("var coverProfileId: UserProfileId? = coverProfileId"))
        assertFalse(generatedRootEntity.contains("@JoinColumn(name = \"author_id\""))
        assertFalse(generatedRootEntity.contains("@OneToOne(fetch = FetchType.EAGER)"))
        assertFalse(generatedRootEntity.contains("@JoinColumn(name = \"cover_profile_id\""))
        assertTrue(
            generatedRootEntity.contains(
                "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"
            )
        )
        assertFalse(generatedRootEntity.contains("CascadeType.ALL"))
        assertTrue(generatedRootEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertFalse(generatedRootEntity.contains("mappedBy ="))
        assertTrue(generatedRootEntity.contains("class VideoPost internal constructor("))
        assertTrue(generatedRootEntity.contains("import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
        assertTrue(generatedRootEntity.contains("private var _items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(generatedRootEntity.contains("val items: OwnedEntityList<VideoPostItem>"))
        assertTrue(generatedRootEntity.contains("get() = OwnedEntityList.of(_items, VideoPostItem::class, \"VideoPost.items\")"))
        assertFalse(generatedRootEntity.replace("\r\n", "\n").contains("\n    val items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(generatedRootEntity.contains("private var _files: MutableList<VideoPostFile> = mutableListOf()"))
        assertTrue(generatedRootEntity.contains("var file: VideoPostFile?"))
        assertTrue(generatedRootEntity.contains("@get:Transient"))
        assertFalse(generatedRootEntity.replace("\r\n", "\n").contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertTrue(generatedRootEntity.contains("get() = OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
        assertTrue(generatedRootEntity.contains(".singleOrNull()"))
        assertTrue(generatedRootEntity.contains("OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
        assertTrue(generatedRootEntity.contains(".replace(value)"))
        assertFalse(generatedRootEntity.contains("_files.clear()"))
        assertFalse(generatedRootEntity.contains("_files.add(value)"))
        assertFalse(generatedChildEntity.contains("videoPostId"))
        assertFalse(generatedChildEntity.contains("@ManyToOne"))
        assertFalse(generatedChildEntity.contains("import jakarta.persistence.ManyToOne"))
        assertFalse(generatedChildSchema.contains("videoPostId"))
        assertFalse(generatedChildEntity.contains("mappedBy ="))
        assertFalse(generatedOneChildEntity.contains("videoPostId"))
        assertFalse(generatedOneChildEntity.contains("@ManyToOne"))
        assertFalse(generatedOneChildEntity.contains("import jakarta.persistence.ManyToOne"))
        assertFalse(generatedFileSchema.contains("videoPostId"))
        assertFalse(generatedVariantEntity.contains("videoPostFileId"))
        assertFalse(generatedVariantEntity.contains("@ManyToOne"))
        assertFalse(generatedVariantEntity.contains("import jakarta.persistence.ManyToOne"))
        assertFalse(generatedVariantSchema.contains("videoPostFileId"))
        assertTrue(generatedRootSchema.contains("fun joinItems()"))
        assertTrue(generatedRootSchema.contains("fun joinFile("))
        assertTrue(generatedFileSchema.contains("fun joinVariants()"))
        listOf(generatedRootEntity, generatedChildEntity, generatedOneChildEntity, generatedVariantEntity).forEach {
            assertTrue(it.contains("var id: Long? = null"))
            assertTrue(it.contains("var version: Long? = null"))
            assertFalse(internalConstructorParameters(it).contains("id"))
            assertFalse(internalConstructorParameters(it).contains("version"))
        }
        listOf(generatedRootSchema, generatedChildSchema, generatedFileSchema, generatedVariantSchema).forEach {
            assertTrue(it.contains("val version: Field<Long>"))
            assertFalse(it.contains("val version: Field<Long?>"))
        }
        assertTrue(generatedContentEntity.contains("import com.acme.demo.domain.shared.ids.AuthorId"))
        assertTrue(generatedContentEntity.contains("import com.acme.demo.domain.aggregates.media_processing_task.MediaProcessingTaskId"))
        assertTrue(generatedContentEntity.contains("var authorId: AuthorId = authorId"))
        assertTrue(generatedContentEntity.contains("var mediaProcessingTaskId: MediaProcessingTaskId? = mediaProcessingTaskId"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate schema owned relation joins compile for owned many owned one and chained children`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-schema-relation-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")
        val smokeFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/SchemaRelationCompileSmoke.kt"
        )
        smokeFile.writeText(
            """
            package com.acme.demo.domain.aggregates.video_post

            import com.acme.demo.domain._share.meta.video_post.SVideoPost
            import com.only4.cap4k.ddd.domain.repo.schema.JoinType

            class SchemaRelationCompileSmoke {
                fun compileOwnedRelationQueries(label: String, storageKey: String, variantKey: String) {
                    SVideoPost.predicate(distinct = true) { post ->
                        val item = post.joinItems()
                        val file = post.joinFile(JoinType.LEFT)
                        val variant = file.joinVariants()

                        post.all(
                            post.items.isNotEmpty(),
                            post.file.isNotNull(),
                            item.label eq label,
                            file.storageKey eq storageKey,
                            variant.variantKey eq variantKey,
                        )
                    }
                }
            }
            """.trimIndent()
        )

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val rootSchema = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt")
        ).readText()
        val fileSchema = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPostFile.kt")
        ).readText()

        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(rootSchema.contains("fun predicate(distinct: Boolean, builder: PredicateBuilder<SVideoPost>): JpaPredicate<VideoPost>"))
        assertTrue(rootSchema.contains("val items: RelationCollectionField<VideoPostItem>"))
        assertTrue(rootSchema.contains("val file: RelationOptionalField<VideoPostFile>"))
        assertTrue(rootSchema.contains("fun joinItems(): SVideoPostItem = joinItems(JoinType.INNER)"))
        assertTrue(rootSchema.contains("fun joinFile(joinType: JoinType): SVideoPostFile"))
        assertTrue(rootSchema.contains("root.join<VideoPost, T>(persistencePathName, joinType.toJpaJoinType())"))
        assertTrue(fileSchema.contains("fun joinVariants(): SVideoPostFileVariant = joinVariants(JoinType.INNER)"))
        assertFalse(rootSchema.contains("val _items: RelationCollectionField"))
        assertFalse(rootSchema.contains("val _files: RelationOptionalField"))
        assertFalse(rootSchema.contains("fun join_items"))
        assertFalse(rootSchema.contains("fun join_files"))
    }

    @Test
    fun `aggregate behavior source compiles against generated entities when module build dir is customized`() {
        val planProjectDir = Files.createTempDirectory("pipeline-functional-aggregate-custom-build-dir-plan")
        FunctionalFixtureSupport.copyCompileFixture(planProjectDir, "aggregate-relation-compile-sample")
        val planDomainBuildFile = planProjectDir.resolve("demo-domain/build.gradle.kts")
        planDomainBuildFile.writeText(
            planDomainBuildFile.readText() +
                "\nlayout.buildDirectory.set(layout.projectDirectory.dir(\"out/build\"))\n"
        )
        val planResult = FunctionalFixtureSupport
            .runner(planProjectDir, "cap4kPlan")
            .build()
        val planJson = planProjectDir.resolve("build/cap4k/plan.json").readText()
        assertTrue(planResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planJson.contains("demo-domain/out/build/generated/cap4k/main/kotlin"))
        assertFalse(planJson.contains("demo-domain/build/generated/cap4k/main/kotlin"))

        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-custom-build-dir-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts")
        domainBuildFile.writeText(
            domainBuildFile.readText() +
                "\nlayout.buildDirectory.set(layout.projectDirectory.dir(\"out/build\"))\n"
        )
        val behaviorFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt"
        )
        Files.createDirectories(behaviorFile.parent)
        behaviorFile.writeText(
            """
            package com.acme.demo.domain.aggregates.video_post

            fun VideoPost.renameForCompile(name: String) {
                this.title = name
            }

            fun VideoPost.attachForCompile(item: VideoPostItem) {
                this.items.add(item)
            }

            fun VideoPost.replaceFileForCompile(file: VideoPostFile?) {
                this.file = file
            }
            """.trimIndent()
        )

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            "demo-domain/out/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            "demo-domain/out/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt",
            "demo-domain/out/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt",
        )
        assertFalse(
            projectDir.resolve(
                generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
            ).toFile().exists()
        )
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))

        val secondCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        assertEquals(TaskOutcome.UP_TO_DATE, secondCompileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(secondCompileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate parent without parent ref fails fast during domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-parent-without-parent-ref-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            """
            create table video_post (id bigint primary key comment '@IdStrategy=db_identity;');
            create table video_post_item (id bigint primary key comment '@IdStrategy=db_identity;', video_post_id bigint not null);
            comment on table video_post_item is '@Parent=video_post;';
            """.trimIndent()
        )
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                """includeTables.set(listOf("video_post", "video_post_item", "user_profile", "content", "media_processing_task"))""",
                """includeTables.set(listOf("video_post", "video_post_item"))""",
            )
        )

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .buildAndFail()
        assertTrue(
            compileResult.output.contains("table VIDEO_POST_ITEM declares @Parent=video_post but has no @ParentRef column."),
            compileResult.output,
        )
    }

    @Test
    fun `aggregate inherited persistence fields omitted entity participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-persistence-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-persistence-compile-sample")
        val applicationBuildFile = projectDir.resolve("demo-application/build.gradle.kts").readText().trim()
        val adapterBuildFile = projectDir.resolve("demo-adapter/build.gradle.kts").readText().trim()
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts").readText()
        assertTrue(applicationBuildFile == "// Functional fixture module.")
        assertTrue(adapterBuildFile == "// Functional fixture module.")
        assertTrue(domainBuildFile.contains("org.springframework:spring-context"))
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        val generatedEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()

        assertTrue(generatedEntity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedEntity.contains("@Version"))
        assertFalse(generatedEntity.contains("createdBy"))
        assertFalse(generatedEntity.contains("updatedBy"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate provider specific persistence generation participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-provider-persistence-compile-sample")
        val applicationBuildFile = projectDir.resolve("demo-application/build.gradle.kts").readText().trim()
        val adapterBuildFile = projectDir.resolve("demo-adapter/build.gradle.kts").readText().trim()
        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts").readText()
        val fixtureBuildFile = projectDir.resolve("build.gradle.kts")

        assertTrue(applicationBuildFile == "// Functional fixture module.")
        assertTrue(adapterBuildFile == "// Functional fixture module.")
        assertTrue(domainBuildFile.contains("org.hibernate.orm:hibernate-core"))
        assertTrue(domainBuildFile.contains("jakarta.persistence:jakarta.persistence-api"))

        data class ApplicationSideCell(
            val tableName: String,
            val entityName: String,
            val backingType: String,
            val deletedProperty: String,
            val activeSqlLiteral: String,
            val strategy: String,
        ) {
            val packageName: String = "com.acme.demo.domain.aggregates.$tableName"
            val idType: String = "${entityName}Id"
            val accessorType: String = "${entityName}GeneratedOwnIdAccessor"
            val factoryType: String = "${entityName}Factory"
        }

        val nilUuid = "00000000-0000-0000-0000-000000000000"
        val applicationSideCells = listOf(
            ApplicationSideCell(
                tableName = "snowflake_long_record",
                entityName = "SnowflakeLongRecord",
                backingType = "Long",
                deletedProperty = "var deleted: Long = 0L",
                activeSqlLiteral = "0",
                strategy = "snowflake",
            ),
            ApplicationSideCell(
                tableName = "snowflake_string_record",
                entityName = "SnowflakeStringRecord",
                backingType = "String",
                deletedProperty = "var deleted: String = \"0\"",
                activeSqlLiteral = "'0'",
                strategy = "snowflake",
            ),
            ApplicationSideCell(
                tableName = "uuid_string_record",
                entityName = "UuidStringRecord",
                backingType = "String",
                deletedProperty = "var deleted: String = \"$nilUuid\"",
                activeSqlLiteral = "'$nilUuid'",
                strategy = "uuid7",
            ),
            ApplicationSideCell(
                tableName = "uuid_native_record",
                entityName = "UuidNativeRecord",
                backingType = "UUID",
                deletedProperty = "var deleted: UUID = UUID(0L, 0L)",
                activeSqlLiteral = "CAST('$nilUuid' AS UUID)",
                strategy = "uuid7",
            ),
        )

        val planResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kPlan")
            .build()
        val planContent = projectDir.resolve("build/cap4k/plan.json").readText()
        fixtureBuildFile.writeText(fixtureBuildFile.readText().replace("h2/demo", "h2/generate"))
        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()

        val entityPaths = listOf(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ) + applicationSideCells.map { cell ->
            generatedSource(
                "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.entityName}.kt"
            )
        }
        val strongIdPaths = applicationSideCells.map { cell ->
            generatedSource(
                "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.idType}.kt"
            )
        }
        val accessorPaths = applicationSideCells.map { cell ->
            generatedSource(
                "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.accessorType}.kt"
            )
        }
        val catalogPath = generatedSource(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/identity/GeneratedOwnIdCatalogContribution.kt"
        )
        val applicationFactoryPaths = applicationSideCells.map { cell ->
            "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/factory/${cell.factoryType}.kt"
        }

        assertGeneratedFilesExist(
            projectDir,
            *(entityPaths + strongIdPaths + accessorPaths + catalogPath + applicationFactoryPaths).toTypedArray(),
        )

        val generatedVideoPost = projectDir.resolve(entityPaths.first()).readText()
        val generatedEntities = applicationSideCells.associateWith { cell ->
            projectDir.resolve(
                generatedSource(
                    "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.entityName}.kt"
                )
            ).readText()
        }
        val generatedStrongIds = applicationSideCells.associateWith { cell ->
            projectDir.resolve(
                generatedSource(
                    "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.idType}.kt"
                )
            ).readText()
        }
        val generatedAccessors = applicationSideCells.associateWith { cell ->
            projectDir.resolve(
                generatedSource(
                    "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.accessorType}.kt"
                )
            ).readText()
        }
        val generatedCatalog = projectDir.resolve(catalogPath).readText()
        val generatedFactories = applicationSideCells.associateWith { cell ->
            projectDir.resolve(
                "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/factory/${cell.factoryType}.kt"
            ).readText()
        }
        val identityFactoryPath =
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt"
        assertTrue(
            projectDir.resolve(identityFactoryPath).toFile().exists(),
            "Expected generated identity factory boundary file to exist: $identityFactoryPath",
        )
        val generatedIdentityFactory = projectDir.resolve(identityFactoryPath).readText()
        val factoryContexts = JsonParser.parseString(planContent)
            .asJsonObject
            .getAsJsonArray("items")
            .map { it.asJsonObject }
            .filter { it.get("templateId").asString == "aggregate/factory.kt.peb" }
            .associate { item ->
                val context = item.getAsJsonObject("context")
                context.get("entityName").asString to context
            }

        assertTrue(planResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertFalse(generatedVideoPost.contains("@DynamicInsert"))
        assertFalse(generatedVideoPost.contains("@DynamicUpdate"))
        assertTrue(generatedVideoPost.contains("import org.hibernate.annotations.SQLDelete"))
        assertTrue(generatedVideoPost.contains("import org.hibernate.annotations.Where"))
        assertTrue(generatedVideoPost.contains("""@SQLDelete(sql = "update `video_post` set `deleted` = `id` where `id` = ? and `version` = ?")"""))
        assertTrue(generatedVideoPost.contains("""@Where(clause = "`deleted` = 0")"""))
        assertTrue(generatedVideoPost.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedVideoPost.contains("@Version"))
        assertFalse(internalConstructorParameters(generatedVideoPost).contains("id"))
        assertFalse(internalConstructorParameters(generatedVideoPost).contains("version"))
        assertTrue(generatedVideoPost.contains("var id: Long? = null"))
        assertTrue(generatedVideoPost.contains("var version: Long? = null"))
        assertTrue(generatedVideoPost.contains("var deleted: Long = 0L"))
        assertFalse(internalConstructorParameters(generatedVideoPost).contains("deleted"))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))

        applicationSideCells.forEach { cell ->
            val entity = generatedEntities.getValue(cell)
            val strongId = generatedStrongIds.getValue(cell)
            val accessor = generatedAccessors.getValue(cell)
            val factory = generatedFactories.getValue(cell)
            val constructorParameters = internalConstructorParameters(entity)

            assertTrue(entity.contains("@EmbeddedId"), cell.entityName)
            assertGeneratedOwnIdShape(entity, cell.idType)
            assertFalse(Regex("""\bid\s*:""").containsMatchIn(constructorParameters), cell.entityName)
            assertFalse(constructorParameters.contains("deleted"), cell.entityName)
            assertTrue(entity.contains(cell.deletedProperty), cell.entityName)
            assertFalse(entity.contains("var deleted: ${cell.idType}"), cell.entityName)
            assertTrue(
                entity.contains(
                    """@SQLDelete(sql = "update `${cell.tableName}` set `deleted` = `id` where `id` = ?")"""
                ),
                cell.entityName,
            )
            assertTrue(
                entity.contains("""@Where(clause = "`deleted` = ${cell.activeSqlLiteral}")"""),
                cell.entityName,
            )
            assertFalse(entity.contains("and `version` = ?"), cell.entityName)
            assertFalse(entity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"), cell.entityName)
            assertTrue(strongId.contains("StrongId<${cell.backingType}>"), cell.idType)
            assertTrue(
                accessor.contains(
                    "Mediator.identifiers.next(\"${cell.strategy}\", ${cell.backingType}::class)"
                ),
                cell.accessorType,
            )
            assertTrue(accessor.contains("${cell.idType}.of("), cell.accessorType)
            assertTrue(
                generatedCatalog.contains("${cell.packageName}.${cell.accessorType}"),
                cell.accessorType,
            )
            assertEquals(true, factoryContexts.getValue(cell.entityName).get("constructorMappingResolved").asBoolean)
            assertTrue(factory.contains("${cell.entityName}("), cell.factoryType)
            assertTrue(factory.contains("title = entityPayload.title"), cell.factoryType)
            assertTrue(factory.contains("val title: String"), cell.factoryType)
            assertFalse(factory.contains("TODO(\"Implement aggregate construction\")"), cell.factoryType)
            assertFalse(factory.contains("deleted"), cell.factoryType)
            assertFalse(factory.contains("val id:"), cell.factoryType)
            assertFalse(factory.contains(cell.idType), cell.factoryType)
        }

        assertEquals(true, factoryContexts.getValue("VideoPost").get("constructorMappingResolved").asBoolean)
        assertFalse(generatedIdentityFactory.contains("TODO(\"Implement aggregate construction\")"))
        assertFalse(generatedIdentityFactory.contains("deleted"))

        val allGeneratedEvidence = buildString {
            append(generatedVideoPost)
            generatedEntities.values.forEach { append(it) }
            generatedStrongIds.values.forEach { append(it) }
            generatedAccessors.values.forEach { append(it) }
            append(generatedCatalog)
            generatedFactories.values.forEach { append(it) }
            append(generatedIdentityFactory)
        }
        assertFalse(allGeneratedEvidence.contains("ApplicationSideId"))
        assertFalse(allGeneratedEvidence.contains("snowflake-long"))

        fixtureBuildFile.writeText(fixtureBuildFile.readText().replace("h2/generate", "h2/compile"))
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":demo-domain:compileKotlin")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `generated quoted mixed case entity completes hibernate soft delete lifecycle`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-quoted-mixed-case-runtime")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-provider-persistence-compile-sample")
        Files.delete(
            projectDir.resolve(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/" +
                    "AggregateProviderPersistenceCompileSmoke.kt"
            )
        )

        projectDir.resolve("schema.sql").writeText(
            """
            create table "MixedCaseOwner" (
                "Id" bigint generated by default as identity primary key comment '@IdStrategy=db_identity;',
                "Deleted" bigint not null default 0 comment '@Managed=deleted;',
                "Name" varchar(128) not null
            );

            create table "MixedCaseRecord" (
                "Id" bigint generated by default as identity primary key comment '@IdStrategy=db_identity;',
                "Deleted" bigint not null default 0 comment '@Managed=deleted;',
                "OwnerId" bigint not null,
                "Title" varchar(128) not null,
                constraint "FkMixedCaseRecordOwner" foreign key ("OwnerId") references "MixedCaseOwner" ("Id")
            );
            """.trimIndent()
        )

        val rootBuildFile = projectDir.resolve("build.gradle.kts")
        rootBuildFile.writeText(
            rootBuildFile.readText()
                .replace(";MODE=MySQL", "")
                .replace(";DB_CLOSE_DELAY=-1", "")
                .replace(";DATABASE_TO_UPPER=false", "")
                .replace(
                    Regex("""(?s)includeTables\.set\(\s*listOf\(.*?\)\s*\)"""),
                    """includeTables.set(listOf("MixedCaseOwner", "MixedCaseRecord"))""",
                )
        )

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerateSources")
            .build()
        val generatedEntityPath = projectDir.resolve(
            generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/mixedcaserecord/MixedCaseRecord.kt"
            )
        )
        val generatedOwnerPath = projectDir.resolve(
            generatedSource(
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/mixedcaseowner/MixedCaseOwner.kt"
            )
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertFalse(generatedEntityPath.readText().contains("@JoinColumn("))

        // DB references intentionally remain ID-only, so model this JPA relation explicitly.
        val dbFilePath = projectDir.resolve("build/h2/demo")
            .toAbsolutePath()
            .toString()
            .replace("\\", "/")
        val providerConfig = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            sources = mapOf(
                "db" to SourceConfig(
                    options = mapOf(
                        "url" to "jdbc:h2:file:$dbFilePath",
                        "username" to "sa",
                        "password" to "secret",
                        "schema" to "PUBLIC",
                        "includeTables" to listOf("MixedCaseOwner", "MixedCaseRecord"),
                        "excludeTables" to emptyList<String>(),
                    )
                )
            ),
            generators = mapOf(
                "aggregate" to GeneratorConfig(
                    options = mapOf(
                    )
                )
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
        val snapshot = DbSchemaSourceProvider().collect(providerConfig)
        val canonical = DefaultCanonicalAssembler().assemble(providerConfig, listOf(snapshot)).model
        val recordEntity = canonical.entities.single { it.name == "MixedCaseRecord" }
        val ownerEntity = canonical.entities.single { it.name == "MixedCaseOwner" }
        val canonicalWithReference = canonical.copy(
            aggregateRelations = canonical.aggregateRelations + AggregateRelationModel(
                ownerEntityName = recordEntity.name,
                ownerEntityPackageName = recordEntity.packageName,
                fieldName = "owner",
                targetEntityName = ownerEntity.name,
                targetEntityPackageName = ownerEntity.packageName,
                relationType = AggregateRelationType.MANY_TO_ONE,
                joinColumn = "OwnerId",
                fetchType = AggregateFetchType.LAZY,
                nullable = false,
                owned = false,
            )
        )
        val planItems = AggregateArtifactPlanner().plan(providerConfig, canonicalWithReference)
        val entityPlans = planItems.filter { it.templateId == "aggregate/entity.kt.peb" }
        val renderedByPath = PebbleArtifactRenderer(
            PresetTemplateResolver("ddd-default", emptyList())
        ).render(entityPlans, providerConfig).associateBy { it.outputPath }
        fun writeGeneratedEntity(typeName: String, target: Path) {
            val planItem = entityPlans.single { it.context["typeName"] == typeName }
            target.writeText(renderedByPath.getValue(planItem.outputPath).content)
        }
        writeGeneratedEntity("MixedCaseOwner", generatedOwnerPath)
        writeGeneratedEntity("MixedCaseRecord", generatedEntityPath)
        assertTrue(
            generatedEntityPath.readText().contains(
                """@JoinColumn(name = "\"OwnerId\"", nullable = false)"""
            )
        )

        val domainBuildFile = projectDir.resolve("demo-domain/build.gradle.kts")
        domainBuildFile.writeText(
            domainBuildFile.readText()
                .replace(
                    """kotlin("jvm") version "2.2.20"""",
                    """kotlin("jvm") version "2.2.20"
    kotlin("plugin.jpa") version "2.2.20"""",
                ) +
                """

                dependencies {
                    testImplementation(kotlin("test-junit5"))
                    testImplementation("com.h2database:h2:2.3.232")
                }

                val runtimeDbFilePath = rootProject.layout.buildDirectory
                    .file("h2/demo")
                    .get()
                    .asFile
                    .absolutePath
                    .replace("\\", "/")

                tasks.test {
                    useJUnitPlatform()
                    systemProperty(
                        "cap4k.test.jdbcUrl",
                        "jdbc:h2:file:${'$'}runtimeDbFilePath"
                    )
                }
                """.trimIndent()
        )

        val runtimeTestFile = projectDir.resolve(
            "demo-domain/src/test/kotlin/com/acme/demo/domain/aggregates/mixedcaserecord/" +
                "MixedCaseRecordGeneratedRuntimeTest.kt"
        )
        Files.createDirectories(runtimeTestFile.parent)
        runtimeTestFile.writeText(
            """
            package com.acme.demo.domain.aggregates.mixedcaserecord

            import com.acme.demo.domain.aggregates.mixedcaseowner.MixedCaseOwner
            import kotlin.test.Test
            import kotlin.test.assertEquals
            import kotlin.test.assertTrue
            import org.hibernate.boot.MetadataSources
            import org.hibernate.boot.registry.StandardServiceRegistryBuilder
            import org.hibernate.cfg.AvailableSettings
            import java.sql.DriverManager

            class MixedCaseRecordGeneratedRuntimeTest {
                @Test
                fun `generated mapping persists queries and soft deletes exact quoted identifiers`() {
                    val jdbcUrl = checkNotNull(System.getProperty("cap4k.test.jdbcUrl"))
                    val registry = StandardServiceRegistryBuilder()
                        .applySetting(AvailableSettings.DRIVER, "org.h2.Driver")
                        .applySetting(AvailableSettings.URL, jdbcUrl)
                        .applySetting(AvailableSettings.USER, "sa")
                        .applySetting(AvailableSettings.PASS, "secret")
                        .applySetting(AvailableSettings.HBM2DDL_AUTO, "none")
                        .build()
                    try {
                        MetadataSources(registry)
                            .addAnnotatedClass(MixedCaseOwner::class.java)
                            .addAnnotatedClass(MixedCaseRecord::class.java)
                            .buildMetadata()
                            .buildSessionFactory()
                            .use { sessionFactory ->
                                var id = 0L
                                var ownerId = 0L
                                sessionFactory.openSession().use { session ->
                                    val transaction = session.beginTransaction()
                                    val owner = MixedCaseOwner(name = "owner")
                                    session.persist(owner)
                                    session.flush()
                                    ownerId = checkNotNull(owner.id)
                                    val entity = MixedCaseRecord(
                                        title = "active",
                                    )
                                    entity.owner = owner
                                    session.persist(entity)
                                    session.flush()
                                    id = checkNotNull(entity.id)
                                    transaction.commit()
                                }

                                sessionFactory.openSession().use { session ->
                                    val transaction = session.beginTransaction()
                                    val active = session.createQuery(
                                            "from MixedCaseRecord",
                                            MixedCaseRecord::class.java,
                                        ).singleResult
                                    assertEquals(ownerId, active.owner.id)
                                    assertEquals("owner", active.owner.name)
                                    session.remove(active)
                                    session.flush()
                                    transaction.commit()
                                }

                                sessionFactory.openSession().use { session ->
                                    assertTrue(
                                        session.createQuery(
                                            "from MixedCaseRecord",
                                            MixedCaseRecord::class.java,
                                        ).resultList.isEmpty()
                                    )
                                }

                                DriverManager.getConnection(jdbcUrl, "sa", "secret").use { connection ->
                                    connection.createStatement().use { statement ->
                                        statement.executeQuery(
                                            "select \"Id\", \"Deleted\", \"OwnerId\" from \"MixedCaseRecord\""
                                        ).use { rows ->
                                            assertTrue(rows.next())
                                            assertEquals(id, rows.getLong("Id"))
                                            assertEquals(id, rows.getLong("Deleted"))
                                            assertEquals(ownerId, rows.getLong("OwnerId"))
                                            assertTrue(!rows.next())
                                        }
                                    }
                                }
                            }
                    } finally {
                        StandardServiceRegistryBuilder.destroy(registry)
                    }
                }
            }
            """.trimIndent()
        )

        val runtimeResult = FunctionalFixtureSupport
            .runner(
                projectDir,
                ":demo-domain:test",
                "--tests",
                "com.acme.demo.domain.aggregates.mixedcaserecord.MixedCaseRecordGeneratedRuntimeTest",
                "-x",
                "cap4kGenerateSources",
            )
            .build()
        val generatedEntity = generatedEntityPath.readText()

        assertTrue(generatedEntity.contains("""@Table(name = "\"MixedCaseRecord\"")"""))
        assertTrue(generatedEntity.contains("""@Column(name = "\"Title\"")"""))
        assertTrue(generatedEntity.contains("""@Column(name = "\"Deleted\"")"""))
        assertTrue(
            generatedEntity.contains(
                """@JoinColumn(name = "\"OwnerId\"", nullable = false)"""
            )
        )
        assertEquals(TaskOutcome.SUCCESS, runtimeResult.task(":demo-domain:test")?.outcome)
        assertTrue(runtimeResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate provider persistence generation keeps provider identity id policies compile-safe together`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-mixed-id-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-provider-persistence-compile-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replaceFirst(
                "id bigint primary key comment '@IdStrategy=db_identity;',",
                "id varchar(36) primary key comment '@IdStrategy=uuid7;',",
            ).replaceFirst("@Managed=deleted;", "") +
                "\n\n" +
                """
                create table audit_log (
                    id bigint primary key comment '@IdStrategy=db_identity;',
                    deleted bigint not null default 0 comment '@Managed=deleted;',
                    content varchar(128) not null
                );
                """.trimIndent()
        )
        val buildFile = projectDir.resolve("build.gradle.kts")
        val patchedBuildFile = buildFile.readText().replace("\r\n", "\n")
            .replace(
                """
                |                    "uuid_native_record",
                |                )
                """.trimMargin(),
                """
                |                    "uuid_native_record",
                |                    "audit_log",
                |                )
                """.trimMargin(),
            )
            .replace(
                """
                |        aggregate { }
                """.trimMargin(),
                """
                |        aggregate {
                |            specialFields {
                |                idDefaultStrategy.set("identity")
                |            }
                |        }
                """.trimMargin(),
            )
        buildFile.writeText(patchedBuildFile)
        assertTrue(patchedBuildFile.contains("""idDefaultStrategy.set("identity")"""))
        assertTrue(patchedBuildFile.contains("\"audit_log\""))
        projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/" +
                "AggregateProviderPersistenceCompileSmoke.kt"
        ).writeText(
            """
            package com.acme.demo.domain.aggregates.video_post

            import com.acme.demo.domain.aggregates.audit_log.AuditLog

            object AggregateProviderPersistenceCompileSmoke {
                fun verify(videoPost: VideoPost, auditLog: AuditLog): List<Any> =
                    listOf(videoPost, auditLog)
            }
            """.trimIndent()
        )

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedAuditLog = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/audit_log/AuditLog.kt")
        ).readText()

        assertFalse(generatedVideoPost.contains("ApplicationSideId"))
        assertFalse(generatedVideoPost.contains("id: Long = 0L"))
        assertFalse(generatedVideoPost.contains("@GeneratedValue(generator ="))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertTrue(generatedVideoPost.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostId"))
        assertTrue(generatedVideoPost.contains("@EmbeddedId"))
        assertGeneratedOwnIdShape(generatedVideoPost, "VideoPostId")
        assertFalse(generatedVideoPost.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedAuditLog.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertFalse(generatedAuditLog.contains("GenericGenerator"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate provider persistence generation keeps native uuid ids compile-safe without save-time assignment`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-uuid-id-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-provider-persistence-compile-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replaceFirst(
                "id bigint primary key comment '@IdStrategy=db_identity;',",
                "id varchar(36) primary key comment '@IdStrategy=uuid7;',",
            ).replaceFirst("@Managed=deleted;", "")
        )
        projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/" +
                "AggregateProviderPersistenceCompileSmoke.kt"
        ).writeText(
            """
            package com.acme.demo.domain.aggregates.video_post

            object AggregateProviderPersistenceCompileSmoke {
                fun verify(videoPost: VideoPost): VideoPost = videoPost
            }
            """.trimIndent()
        )

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()

        assertFalse(generatedVideoPost.contains("ApplicationSideId"))
        assertFalse(generatedVideoPost.contains("UUID(" + "0L, 0L)"))
        assertTrue(generatedVideoPost.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostId"))
        assertTrue(generatedVideoPost.contains("@EmbeddedId"))
        assertGeneratedOwnIdShape(generatedVideoPost, "VideoPostId")
        assertFalse(generatedVideoPost.contains("id: UUID"))
        assertFalse(generatedVideoPost.contains("@GeneratedValue(generator ="))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertFalse(generatedVideoPost.contains("@SQLDelete"))
        assertFalse(generatedVideoPost.contains("@Where"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate enum generation participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum-domain-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-enum-compile-sample")

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val generatedEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedSharedEnum = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt")
        ).readText()
        val generatedLocalEnum = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/enums/VideoPostVisibility.kt")
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/enums/VideoPostVisibility.kt"),
        )
        assertTrue(generatedEntity.contains("@Entity"))
        assertTrue(generatedEntity.contains("@Table(name = \"video_post\")"))
        assertTrue(generatedEntity.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostId"))
        assertTrue(generatedEntity.contains("import com.acme.demo.domain.shared.enums.Status"))
        assertTrue(generatedEntity.contains("@EmbeddedId"))
        assertGeneratedOwnIdShape(generatedEntity, "VideoPostId")
        assertFalse(generatedEntity.contains("@Id"))
        assertFalse(generatedEntity.contains("@Column(name = \"id\""))
        assertTrue(generatedEntity.contains("@Column(name = \"status\")"))
        assertTrue(
            generatedEntity.contains(
                "@Convert(converter = Status.Converter::class)"
            )
        )
        assertTrue(generatedSharedEnum.contains("class Converter : AttributeConverter<Status, Int>"))
        assertTrue(generatedLocalEnum.contains("class Converter : AttributeConverter<VideoPostVisibility, Int>"))
        assertFalse(generatedEntity.contains("@GeneratedValue"))
        assertFalse(generatedEntity.contains("@Version"))
        assertFalse(generatedEntity.contains("@DynamicInsert"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `enum manifest only generation participates in domain compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-enum-manifest-domain-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "enum-manifest-compile-sample")

        val beforeGenerateCompileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin", "-x", "cap4kGenerateSources")
            .buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome,
        )
        assertTrue(beforeGenerateCompileResult.output.contains("Status"))

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val generatedEnum = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt")
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt"),
        )
        assertTrue(generatedEnum.contains("enum class Status"))
        assertTrue(generatedEnum.contains("class Converter : AttributeConverter<Status, Int>"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
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
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderQryHandler.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/order/SubmitOrderPayload.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/values/OrderAddress.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/services/order/pricing/CalculateOrderTotal.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/sagas/order/fulfillment/FulfillOrderSaga.kt",
        )
        assertGeneratedFilesDoNotExist(
            projectDir,
            "demo-application/src/main/kotlin/com/acme/demo/application/sagas/order/fulfillment/FulfillOrderSagaParam.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/sagas/order/fulfillment/FulfillOrderSagaResult.kt",
            "demo-application/src/main/kotlin/com/acme/demo/application/sagas/order/fulfillment/FulfillOrderSagaHandler.kt",
        )
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(domainCompileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(applicationCompileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(adapterCompileResult.output.contains("BUILD SUCCESSFUL"))
    }

    private fun assertGeneratedOwnIdShape(generatedEntity: String, idType: String) {
        val normalizedEntity = generatedEntity.replace("\r\n", "\n")
        val expectedPropertyBlock = "    lateinit var id: $idType\n        internal set"
        assertTrue(
            normalizedEntity.contains(expectedPropertyBlock),
            "Expected generated own ID property block:\n$expectedPropertyBlock",
        )
        assertFalse(normalizedEntity.contains("var id: $idType = id"))

        val constructorParameters = internalConstructorParameters(normalizedEntity)
        assertFalse(
            Regex("""\bid\s*:\s*${Regex.escape(idType)}\b""").containsMatchIn(constructorParameters),
            "Expected internal constructor to exclude id: $idType, but parameters were:\n$constructorParameters",
        )
    }

    private fun internalConstructorParameters(generatedEntity: String): String {
        val constructorMarker = "internal constructor("
        val constructorStart = generatedEntity.indexOf(constructorMarker)
        if (constructorStart < 0) {
            throw AssertionError("Expected generated entity to declare an internal constructor")
        }

        val parameterStart = constructorStart + constructorMarker.length
        var depth = 1
        for (index in parameterStart until generatedEntity.length) {
            when (generatedEntity[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        return generatedEntity.substring(parameterStart, index)
                    }
                }
            }
        }

        throw AssertionError("Expected internal constructor parameters to have a matching closing parenthesis")
    }

    private fun assertGeneratedFilesExist(projectDir: Path, vararg relativePaths: String) {
        relativePaths.forEach { relativePath ->
            assertTrue(
                projectDir.resolve(relativePath).toFile().exists(),
                "Expected generated file to exist: $relativePath"
            )
        }
    }

    private fun assertGeneratedFilesDoNotExist(projectDir: Path, vararg relativePaths: String) {
        relativePaths.forEach { relativePath ->
            assertFalse(
                projectDir.resolve(relativePath).toFile().exists(),
                "Expected generated file not to exist: $relativePath"
            )
        }
    }

    private fun generatedSource(relativePath: String): String =
        relativePath.replace("/src/main/kotlin/", "/build/generated/cap4k/main/kotlin/")

    private fun assertContainsNormalized(content: String, expectedSnippet: String) {
        val normalizedContent = content.normalizedSnippetText()
        val normalizedSnippet = expectedSnippet.normalizedSnippetText()
        assertTrue(
            normalizedContent.contains(normalizedSnippet),
            "Expected generated content to contain:\n$normalizedSnippet\n\nActual content:\n$normalizedContent",
        )
    }

    private fun String.normalizedSnippetText(): String =
        replace("\r\n", "\n")
            .lines()
            .joinToString("\n") { it.trimStart().trimEnd() }

    private fun removeApplicationCompileSmokeSource(projectDir: Path) {
        val applicationCompileSmokePath = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/smoke/CompileSmoke.kt"
        )
        Files.deleteIfExists(applicationCompileSmokePath)
    }

}

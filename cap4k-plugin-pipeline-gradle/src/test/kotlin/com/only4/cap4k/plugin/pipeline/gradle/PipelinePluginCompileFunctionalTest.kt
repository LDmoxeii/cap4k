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
        assertTrue(beforeGenerateCompileResult.output.contains("VideoPostSpecification"))
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
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt",
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
        assertTrue(generatedEntity.contains("var id: VideoPostId = id"))
        assertFalse(generatedEntity.contains("@GeneratedValue"))
        assertFalse(generatedEntity.contains("@Version"))
        assertFalse(generatedEntity.contains("@DynamicInsert"))
        assertTrue(generatedContentEntity.contains("import com.acme.demo.domain.shared.ids.AuthorId"))
        assertTrue(generatedContentEntity.contains("import com.acme.demo.domain.aggregates.media_processing_task.MediaProcessingTaskId"))
        assertTrue(generatedContentEntity.contains("var id: ContentId = id"))
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
    fun `aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation`() {
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
        val generatedContentEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/Content.kt")
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt"),
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
        assertTrue(generatedChildEntity.contains("@Column(name = \"video_post_id\", insertable = false, updatable = false)"))
        assertTrue(generatedChildEntity.contains("var videoPostId: Long = videoPostId"))
        assertTrue(generatedChildEntity.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(
            generatedChildEntity.contains(
                "@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"
            )
        )
        assertFalse(generatedChildEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false)\n    lateinit var videoPost: VideoPost"))
        assertFalse(generatedChildEntity.contains("@Column(name = \"video_post_id\")\n    var videoPostId: Long = videoPostId"))
        assertFalse(generatedChildEntity.contains("mappedBy ="))
        assertTrue(generatedOneChildEntity.contains("@Column(name = \"video_post_id\", insertable = false, updatable = false)"))
        assertTrue(generatedOneChildEntity.contains("var videoPostId: Long = videoPostId"))
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
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                """includeTables.set(listOf("video_post", "video_post_item", "video_post_file", "user_profile", "content", "media_processing_task"))""",
                """includeTables.set(listOf("video_post", "video_post_item", "video_post_file", "video_post_item_adjustment", "user_profile", "content", "media_processing_task"))""",
            )
        )
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText() +
                """

                create table video_post_item_adjustment (
                    id bigint primary key comment '@IdStrategy=db_identity;',
                    video_post_item_id bigint not null comment '@ParentRef;',
                    reason varchar(64) not null
                );

                comment on table video_post_item_adjustment is '@Parent=video_post_item;';
                """.trimIndent()
        )
        val smokeFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/SchemaRelationCompileSmoke.kt"
        )
        smokeFile.writeText(
            """
            package com.acme.demo.domain.aggregates.video_post

            import com.acme.demo.domain._share.meta.video_post.SVideoPost
            import com.only4.cap4k.ddd.domain.repo.schema.JoinType

            class SchemaRelationCompileSmoke {
                fun compileOwnedRelationQueries(label: String, storageKey: String, reason: String) {
                    SVideoPost.predicate(distinct = true) { post ->
                        val item = post.joinItems()
                        val file = post.joinFile(JoinType.LEFT)
                        val adjustment = item.joinAdjustments()

                        post.all(
                            post.items.isNotEmpty(),
                            post.file.isNotNull(),
                            item.label eq label,
                            file.storageKey eq storageKey,
                            adjustment.reason eq reason,
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
        val itemSchema = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPostItem.kt")
        ).readText()

        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(rootSchema.contains("fun predicate(distinct: Boolean, builder: PredicateBuilder<SVideoPost>): JpaPredicate<VideoPost>"))
        assertTrue(rootSchema.contains("val items: RelationCollectionField<VideoPostItem>"))
        assertTrue(rootSchema.contains("val file: RelationOptionalField<VideoPostFile>"))
        assertTrue(rootSchema.contains("fun joinItems(): SVideoPostItem = joinItems(JoinType.INNER)"))
        assertTrue(rootSchema.contains("fun joinFile(joinType: JoinType): SVideoPostFile"))
        assertTrue(rootSchema.contains("root.join<VideoPostItem>(\"_items\", joinType.toJpaJoinType())"))
        assertTrue(rootSchema.contains("root.join<VideoPostFile>(\"_files\", joinType.toJpaJoinType())"))
        assertTrue(itemSchema.contains("fun joinAdjustments(): SVideoPostItemAdjustment = joinAdjustments(JoinType.INNER)"))
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

        assertTrue(applicationBuildFile == "// Functional fixture module.")
        assertTrue(adapterBuildFile == "// Functional fixture module.")
        assertTrue(domainBuildFile.contains("org.hibernate.orm:hibernate-core"))
        assertTrue(domainBuildFile.contains("jakarta.persistence:jakarta.persistence-api"))
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        val generatedVideoPost = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedAuditLog = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/audit_log/AuditLog.kt")
        ).readText()

        assertFalse(generatedVideoPost.contains("@DynamicInsert"))
        assertFalse(generatedVideoPost.contains("@DynamicUpdate"))
        assertTrue(generatedVideoPost.contains("import org.hibernate.annotations.SQLDelete"))
        assertTrue(generatedVideoPost.contains("import org.hibernate.annotations.Where"))
        assertTrue(generatedVideoPost.contains("""@SQLDelete(sql = "update `video_post` set `deleted` = `id` where `id` = ? and `version` = ?")"""))
        assertTrue(generatedVideoPost.contains("""@Where(clause = "`deleted` = 0")"""))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertTrue(generatedAuditLog.contains("import org.hibernate.annotations.SQLDelete"))
        assertTrue(generatedAuditLog.contains("import org.hibernate.annotations.Where"))
        assertTrue(generatedAuditLog.contains("""@SQLDelete(sql = "update `audit_log` set `deleted` = `id` where `id` = ?")"""))
        assertTrue(generatedAuditLog.contains("""@Where(clause = "`deleted` = 0")"""))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
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
            ).replaceFirst("@Managed=deleted;", "")
        )
        val buildFile = projectDir.resolve("build.gradle.kts")
        val patchedBuildFile = buildFile.readText().replace(
            Regex("""aggregate\s*\{\s*}"""),
            """
            |aggregate {
            |            specialFields {
            |                idDefaultStrategy.set("identity")
            |            }
            |        }
            """.trimMargin(),
        )
        buildFile.writeText(patchedBuildFile)
        assertTrue(patchedBuildFile.contains("""idDefaultStrategy.set("identity")"""))

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
        assertTrue(generatedVideoPost.contains("var id: VideoPostId = id"))
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
        assertTrue(generatedVideoPost.contains("var id: VideoPostId = id"))
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
        assertTrue(generatedEntity.contains("var id: VideoPostId = id"))
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
    fun `aggregate unique query and validator generation participates in application compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-unique-application-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-application:compileKotlin")
            .build()
        val queryContent = projectDir.resolve(
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt"
            )
        ).readText()
        val validatorContent = projectDir.resolve(
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt"
            )
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt"
            ),
            generatedSource(
                "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt"
            ),
        )
        assertTrue(queryContent.contains("data class Request("))
        assertTrue(queryContent.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostId"))
        assertTrue(queryContent.contains("val excludeVideoPostId: VideoPostId?"))
        assertTrue(queryContent.contains(") : RequestParam<Response>"))
        assertTrue(validatorContent.contains("annotation class UniqueVideoPostSlug"))
        assertTrue(
            validatorContent.contains("import com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry")
        )
        assertTrue(
            validatorContent.contains("class Validator : ConstraintValidator<UniqueVideoPostSlug, Any>")
        )
        assertTrue(validatorContent.contains("value::class.memberProperties.associateBy"))
        assertTrue(validatorContent.contains("Mediator.queries.send("))
        assertTrue(validatorContent.contains("return !result.exists"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate unique query handler generation participates in adapter compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-unique-adapter-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")
        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin")
            .build()
        val handlerContent = projectDir.resolve(
            generatedSource(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt"
            )
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt"
            ),
        )
        assertTrue(handlerContent.contains("class UniqueVideoPostSlugQryHandler("))
        assertTrue(
            handlerContent.contains(
                "override fun exec(request: UniqueVideoPostSlugQry.Request): UniqueVideoPostSlugQry.Response"
            )
        )
        assertTrue(handlerContent.contains("private val repository: VideoPostRepository"))
        assertTrue(handlerContent.contains("repository.exists("))
        assertTrue(handlerContent.contains("SVideoPost.specify"))
        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate child unique query handler generation participates in adapter compileKotlin without child repository`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-child-unique-adapter-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                """includeTables.set(listOf("video_post", "content", "media_processing_task"))""",
                """includeTables.set(listOf("video_post", "content", "media_processing_task", "video_file"))""",
            )
        )
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText() +
                """

                create table if not exists video_file (
                    id bigint primary key comment '@IdStrategy=db_identity;',
                    video_post_id bigint not null comment '@ParentRef;',
                    file_index int not null,
                    constraint uq_video_file_parent_index unique (video_post_id, file_index)
                );

                comment on table video_file is '@Parent=video_post;';
                """.trimIndent()
        )

        val generateResult = FunctionalFixtureSupport
            .runner(projectDir, "cap4kGenerate")
            .build()
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin")
            .build()
        val childEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoFile.kt")
        )
        val childSchemaFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoFile.kt")
        )
        val handlerFile = projectDir.resolve(
            generatedSource(
                "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoFileVideoPostIdFileIndexQryHandler.kt"
            )
        )
        val handlerContent = handlerFile.readText()

        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(childEntityFile.toFile().exists())
        assertTrue(childSchemaFile.toFile().exists())
        assertTrue(handlerFile.toFile().exists())
        assertTrue(handlerContent.contains("private val entityManager: EntityManager"))
        assertTrue(handlerContent.contains("criteriaQuery.from(VideoFile::class.java)"))
        assertTrue(handlerContent.contains("SVideoFile.specify"))
        assertFalse(handlerContent.contains("private val repository"))
        assertFalse(handlerContent.contains("VideoFileRepository"))
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

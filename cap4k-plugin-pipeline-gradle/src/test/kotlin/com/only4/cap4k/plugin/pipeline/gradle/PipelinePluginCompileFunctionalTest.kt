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
    fun `expanded validator generation compiles class and field skeletons`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-design-validator-expanded")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "design-validator-expanded-sample")

        val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
            projectDir,
            ":demo-application:compileKotlin",
        )
        val classValidator = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/danmuku/DanmukuDeletePermission.kt"
        ).readText()
        val fieldValidator = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/category/CategoryMustExist.kt"
        ).readText()

        assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(classValidator.contains("@Target(AnnotationTarget.CLASS)"))
        assertTrue(classValidator.contains("val message: String = \"no delete \\${'$'}permission\""))
        assertTrue(classValidator.contains("val danmukuIdField: String = \"danmukuId\""))
        assertTrue(classValidator.contains("val operatorIdField: String = \"operator\\${'$'}id\""))
        assertTrue(classValidator.contains("ConstraintValidator<DanmukuDeletePermission, Any>"))
        assertTrue(classValidator.contains("override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean = true"))
        assertTrue(fieldValidator.contains("@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)"))
        assertTrue(fieldValidator.contains("ConstraintValidator<CategoryMustExist, Long>"))
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
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt"
        ).readText()
        val generatedHandler = projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt"
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt",
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
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
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
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt",
        )
        assertFalse(checkedInEntity.toFile().exists())
        assertTrue(behaviorFile.readText().contains("Place behavior for VideoPost and its owned entities here."))
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
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replace(
                "@Reference=video_post;@Lazy=true;",
                "@Reference=video_post;@Relation=ManyToOne;@Lazy=true;",
            )
        )

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

        assertGeneratedFilesExist(
            projectDir,
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_profile/UserProfile.kt"),
        )
        assertFalse(
            projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostBehavior.kt")
                .toFile()
                .exists()
        )
        assertTrue(generatedRootEntity.contains("import jakarta.persistence.CascadeType"))
        assertTrue(generatedRootEntity.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(generatedRootEntity.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(generatedRootEntity.contains("@OneToOne(fetch = FetchType.EAGER)"))
        assertTrue(generatedRootEntity.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
        assertTrue(
            generatedRootEntity.contains(
                "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"
            )
        )
        assertFalse(generatedRootEntity.contains("CascadeType.ALL"))
        assertTrue(generatedRootEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertFalse(generatedRootEntity.contains("mappedBy ="))
        assertTrue(generatedChildEntity.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(generatedChildEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertFalse(
            generatedChildEntity.contains(
                "@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"
            )
        )
        assertFalse(generatedChildEntity.contains("insertable = false"))
        assertFalse(generatedChildEntity.contains("updatable = false"))
        assertFalse(generatedChildEntity.contains("mappedBy ="))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
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
            """.trimIndent()
        )

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            "demo-domain/out/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            "demo-domain/out/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt",
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
    fun `aggregate inverse read only relation generation participates in domain compileKotlin with scalar fk preserved`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-inverse-relation-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText().replace(
                "video_post_id bigint not null comment '@Reference=video_post;@Lazy=true;',",
                "video_post_id bigint not null,",
            )
        )

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val generatedRootEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()
        val generatedChildEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt")
        ).readText()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostItem.kt"),
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_profile/UserProfile.kt"),
        )
        assertTrue(generatedRootEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertTrue(generatedRootEntity.contains("val items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(generatedChildEntity.contains("@Column(name = \"video_post_id\")"))
        assertTrue(generatedChildEntity.contains("var videoPostId: Long = videoPostId"))
        assertTrue(generatedChildEntity.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(
            generatedChildEntity.contains(
                "@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"
            )
        )
        assertTrue(generatedChildEntity.contains("lateinit var videoPost: VideoPost"))
        assertFalse(generatedChildEntity.contains("mappedBy ="))
        assertFalse(generatedChildEntity.contains("JoinTable"))
        assertFalse(generatedChildEntity.contains("ManyToMany"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
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
        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()

        val generatedEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt")
        ).readText()

        assertTrue(generatedEntity.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedEntity.contains("@Version"))
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

        assertTrue(generatedVideoPost.contains("@DynamicInsert"))
        assertTrue(generatedVideoPost.contains("@DynamicUpdate"))
        assertTrue(generatedVideoPost.contains("@SQLDelete"))
        assertTrue(generatedVideoPost.contains("@Where"))
        assertFalse(generatedVideoPost.contains("@GenericGenerator"))
        assertTrue(generatedAuditLog.contains("@SQLDelete"))
        assertTrue(generatedAuditLog.contains("@Where"))
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate provider persistence generation keeps custom generator and identity compile-safe together`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-provider-persistence-mixed-id-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-provider-persistence-compile-sample")
        val schemaFile = projectDir.resolve("schema.sql")
        val patchedSchema = schemaFile.readText().replace(
            "@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;@SoftDeleteColumn=deleted;",
            "@AggregateRoot=true;@IdGenerator=snowflakeIdGenerator;@DynamicInsert=true;@DynamicUpdate=true;@SoftDeleteColumn=deleted;",
        )
        schemaFile.writeText(patchedSchema)
        val persistedPatchedSchema = schemaFile.readText()
        assertTrue(
            persistedPatchedSchema.contains("@IdGenerator=snowflakeIdGenerator;"),
            "Expected patched schema to contain @IdGenerator=snowflakeIdGenerator; but was:\n$persistedPatchedSchema"
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

        assertTrue(generatedVideoPost.contains("@GeneratedValue(generator = \"snowflakeIdGenerator\")"))
        assertTrue(
            generatedVideoPost.contains(
                "@GenericGenerator(name = \"snowflakeIdGenerator\", strategy = \"snowflakeIdGenerator\")"
            )
        )
        assertFalse(generatedVideoPost.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(generatedAuditLog.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertFalse(generatedAuditLog.contains("GenericGenerator"))
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
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate enum translation generation participates in adapter compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-enum-adapter-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-enum-compile-sample")

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-adapter:compileKotlin")
            .build()

        assertGeneratedFilesExist(
            projectDir,
            generatedSource("demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/StatusTranslation.kt"),
            generatedSource("demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/video_post/VideoPostVisibilityTranslation.kt"),
        )
        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    }

    @Test
    fun `aggregate unique query and validator generation participates in application compileKotlin`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-unique-application-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-compile-sample")

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
        assertTrue(queryContent.contains("val excludeVideoPostId: Long?"))
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
                """includeTables.set(listOf("video_post"))""",
                """includeTables.set(listOf("video_post", "video_file"))""",
            )
        )
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText() +
                """

                create table if not exists video_file (
                    id bigint primary key,
                    video_post_id bigint not null comment '@Reference=video_post;@Lazy=true;',
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
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/order/OrderIdValid.kt",
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/order/SubmitOrderPayload.kt",
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/events/OrderCreatedDomainEvent.kt",
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

}

@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.codeanalysis.compiler.Cap4kCodeAnalysisCompilerRegistrar
import com.only4.cap4k.plugin.codeanalysis.core.config.OptionsKeys
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SemanticArrayTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueEnvelope
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.copyCompileFixture
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.repositoryRoot
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.runner
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
import com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProvider
import com.only4.cap4k.plugin.pipeline.source.enummanifest.EnumManifestSourceProvider
import com.only4.cap4k.plugin.pipeline.source.valueobject.ValueObjectManifestSourceProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.TreeMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DesignRoundTripFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `real compiler analysis and drawing board preserve normalized tactical semantics`() {
        val projectA = Files.createTempDirectory("cap4k-roundtrip-project-a")
        val projectB = Files.createTempDirectory("cap4k-roundtrip-project-b")
        copyCompileFixture(projectA, FixtureName)
        copyCompileFixture(projectB, FixtureName)

        val originalDesignFile = projectA.resolve("design/design.json")
        val originalDesignBytes = Files.readAllBytes(originalDesignFile)
        val originalDesignHash = sha256(originalDesignBytes)
        val originalCanonical = canonicalTacticalProjection(
            projectDir = projectA,
            designFiles = listOf(originalDesignFile),
        )
        assertRichFixtureCoverage(originalCanonical)

        generateAndCompile(projectA)
        val firstGenerationSkeleton = frameworkOwnedSkeleton(projectA)

        val analysisModules = analyzeWithRealCompiler(projectA)
        assertEquals(listOf("domain", "application", "adapter"), analysisModules.map { it.role })
        analysisModules.forEach(::assertRealAnalysisOutput)

        configureDrawingBoard(projectA, analysisModules.map { it.analysisDir })
        val drawingBoardResult = runner(
            projectA,
            "cap4kAnalysisPlan",
            "cap4kAnalysisGenerate",
            "--stacktrace",
        ).build()
        assertBuildSucceeded(drawingBoardResult)

        val drawingBoardFiles = DrawingBoardTags.map { tag ->
            projectA.resolve("analysis-design/drawing_board_$tag.json").also { output ->
                assertTrue(Files.isRegularFile(output), "Missing Drawing Board output: $output")
                val entries = JsonParser.parseString(output.readText()).asJsonArray
                assertTrue(entries.size() > 0, "Drawing Board output must not be partial or empty: $output")
            }
        }

        val projectABytesAfterRoundTrip = Files.readAllBytes(originalDesignFile)
        assertArrayEquals(
            originalDesignBytes,
            projectABytesAfterRoundTrip,
            "Project A operations must not mutate the original Design JSON bytes",
        )
        assertEquals(
            originalDesignHash,
            sha256(projectABytesAfterRoundTrip),
            "Project A operations must not mutate the original Design JSON hash",
        )

        val recoveredDesignFiles = registerDrawingBoardAsDesignJson(projectB, drawingBoardFiles)
        val recoveredCanonical = canonicalTacticalProjection(
            projectDir = projectB,
            designFiles = recoveredDesignFiles,
        )

        assertEquals(DrawingBoardTags.toSet(), originalCanonical.blocks.map { it.tag }.toSet())
        assertEquals(originalCanonical, recoveredCanonical)

        generateAndCompile(projectB)
        val secondGenerationSkeleton = frameworkOwnedSkeleton(projectB)
        assertEquals(firstGenerationSkeleton, secondGenerationSkeleton)

        val firstRuntimeAnnotations = runtimeAnnotationProjection(firstGenerationSkeleton)
        val secondRuntimeAnnotations = runtimeAnnotationProjection(secondGenerationSkeleton)
        assertEquals(firstRuntimeAnnotations, secondRuntimeAnnotations)
        assertExpectedRuntimeAnnotations(firstRuntimeAnnotations)
    }

    private fun assertRichFixtureCoverage(projection: TacticalProjection) {
        val blocks = projection.blocks
        assertEquals(DrawingBoardTags.toSet(), blocks.map { it.tag }.toSet())

        val ordinaryQuery = blocks.single { it.tag == "query" && it.name == "FindOrderSummary" }
        assertEquals(listOf(ArtifactProjection("query", "")), ordinaryQuery.artifacts)
        assertTrue(ordinaryQuery.request.fields.isNotEmpty())
        assertTrue(ordinaryQuery.response?.fields?.isNotEmpty() == true)

        val pageQuery = blocks.single { it.tag == "query" && it.name == "FindOrders" }
        assertTrue(pageQuery.artifacts.contains(ArtifactProjection("query", "page")))
        assertTrue(pageQuery.artifacts.contains(ArtifactProjection("query-handler", "")))

        val ordinaryApiPayload = blocks.single { it.tag == "api_payload" && it.name == "OrderSearchPayload" }
        assertEquals(listOf(ArtifactProjection("api-payload", "")), ordinaryApiPayload.artifacts)
        val pageApiPayload = blocks.single { it.tag == "api_payload" && it.name == "OrderPagePayload" }
        assertEquals(listOf(ArtifactProjection("api-payload", "page")), pageApiPayload.artifacts)

        assertTrue(
            blocks.any { block -> block.artifacts.any { it.family in SecondaryArtifactFamilies } },
            "Rich fixture must select optional secondary artifacts",
        )

        val persisted = blocks.single { it.tag == "domain_event" && it.name == "OrderConfirmed" }
        assertEquals(true, persisted.persist)
        assertEquals("order.confirmed", persisted.eventName)
        assertTrue(persisted.request.fields.isNotEmpty())
        assertTrue(persisted.artifacts.contains(ArtifactProjection("domain-subscriber", "")))

        val transientPayload = blocks.single { it.tag == "domain_event" && it.name == "OrderObserved" }
        assertEquals(false, transientPayload.persist)
        assertTrue(transientPayload.eventName.isEmpty())
        assertTrue(transientPayload.request.fields.isNotEmpty())
        assertEquals(listOf(ArtifactProjection("domain-event", "")), transientPayload.artifacts)

        val marker = blocks.single { it.tag == "domain_event" && it.name == "OrderHeartbeat" }
        assertEquals(false, marker.persist)
        assertTrue(marker.eventName.isEmpty())
        assertTrue(marker.request.fields.isEmpty())
        assertTrue(marker.artifacts.contains(ArtifactProjection("domain-subscriber", "")))

        val integrationEvents = blocks.filter { it.tag == "integration_event" }
        assertTrue(
            integrationEvents.any { it.artifacts.contains(ArtifactProjection("integration-event", "inbound")) },
            "Rich fixture must cover inbound Integration Events",
        )
        assertTrue(
            integrationEvents.any { it.artifacts.contains(ArtifactProjection("integration-event", "outbound")) },
            "Rich fixture must cover outbound Integration Events",
        )

        val defaults = blocks.flatMap { block ->
            block.request.defaultExpressions() + block.response?.defaultExpressions().orEmpty()
        }
        assertTrue(
            defaults.any { it.contains("\\u000c") },
            "Rich fixture must carry a U+000C string default as a Kotlin Unicode escape",
        )
    }

    private fun ValueProjection.defaultExpressions(): List<String> =
        fields.mapNotNull(FieldProjection::defaultValue) +
            nestedDefinitions.flatMap { it.defaultExpressions() } +
            pageItem?.defaultExpressions().orEmpty()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun generateAndCompile(projectDir: Path) {
        val generateResult = runner(
            projectDir,
            "cap4kPlan",
            "cap4kGenerate",
            "--stacktrace",
        ).build()
        assertBuildSucceeded(generateResult)
        assertTaskSucceeded(generateResult, ":cap4kPlan")
        assertTaskSucceeded(generateResult, ":cap4kGenerate")

        val compileResult = runner(
            projectDir,
            ":demo-domain:compileKotlin",
            ":demo-application:compileKotlin",
            ":demo-adapter:compileKotlin",
            "--stacktrace",
        ).build()
        assertBuildSucceeded(compileResult)
        listOf("demo-domain", "demo-application", "demo-adapter").forEach { module ->
            assertTaskSucceeded(compileResult, ":$module:compileKotlin")
        }
    }

    private fun assertBuildSucceeded(result: BuildResult) {
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
    }

    private fun assertTaskSucceeded(result: BuildResult, path: String) {
        val task = result.task(path)
        assertNotNull(task, "Missing task result for $path")
        val outcome = requireNotNull(task).outcome
        assertTrue(
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
            "Expected $path to succeed, but was $outcome\n${result.output}",
        )
    }

    private fun analyzeWithRealCompiler(projectDir: Path): List<AnalyzedModule> = synchronized(AnalyzerLock) {
        val pluginClasspaths = resolveAnalyzerPluginClasspaths()
        val analyzed = mutableListOf<AnalyzedModule>()
        val dependencyOutputs = mutableListOf<File>()

        listOf(
            "domain" to "demo-domain",
            "application" to "demo-application",
            "adapter" to "demo-adapter",
        ).forEach { (role, moduleName) ->
            val moduleDir = projectDir.resolve(moduleName)
            val sources = kotlinSources(moduleDir).mapIndexed { index, source ->
                SourceFile.kotlin(
                    "${role}_${index}_${source.fileName}",
                    source.readText(),
                )
            }
            assertTrue(sources.isNotEmpty(), "No Kotlin sources generated for $moduleName")

            val outputRoot = projectDir.resolve("real-analysis/$role")
            Files.createDirectories(outputRoot)
            val compilerWorkingDir = Files.createTempDirectory("cap4k-roundtrip-compiler-$role").toFile()
            val compilerMessages = ByteArrayOutputStream()
            val originalOutputDir = System.getProperty(OptionsKeys.OUTPUT_DIR)
            val result = try {
                System.setProperty(OptionsKeys.OUTPUT_DIR, outputRoot.toString())
                KotlinCompilation().apply {
                    this.sources = sources
                    workingDir = compilerWorkingDir
                    inheritClassPath = true
                    classpaths = dependencyOutputs.toList()
                    supportsK2 = true
                    compilerPluginRegistrars = listOf(Cap4kCodeAnalysisCompilerRegistrar())
                    this.pluginClasspaths = pluginClasspaths
                    messageOutputStream = compilerMessages
                }.compile()
            } finally {
                if (originalOutputDir == null) {
                    System.clearProperty(OptionsKeys.OUTPUT_DIR)
                } else {
                    System.setProperty(OptionsKeys.OUTPUT_DIR, originalOutputDir)
                }
            }

            assertEquals(
                KotlinCompilation.ExitCode.OK,
                result.exitCode,
                "Real Analyzer compilation failed for $role:\n${compilerMessages}",
            )
            dependencyOutputs += result.outputDirectory
            analyzed += AnalyzedModule(
                role = role,
                analysisDir = outputRoot.resolve("build/cap4k-code-analysis"),
                classesDir = result.outputDirectory,
            )
        }
        analyzed
    }

    private fun kotlinSources(moduleDir: Path): List<Path> = listOf(
        moduleDir.resolve("src/main/kotlin"),
        moduleDir.resolve("build/generated/cap4k/main/kotlin"),
    ).flatMap { sourceRoot ->
        if (!Files.isDirectory(sourceRoot)) {
            emptyList()
        } else {
            Files.walk(sourceRoot).use { paths ->
                paths
                    .filter { path -> path.isRegularFile() && path.extension == "kt" }
                    .sorted()
                    .toList()
            }
        }
    }

    private fun assertRealAnalysisOutput(module: AnalyzedModule) {
        listOf("nodes.json", "rels.json", "design-elements.json").forEach { fileName ->
            assertTrue(
                Files.isRegularFile(module.analysisDir.resolve(fileName)),
                "Real Analyzer did not write $fileName for ${module.role}: ${module.analysisDir}",
            )
        }
        val designElements = JsonParser.parseString(
            module.analysisDir.resolve("design-elements.json").readText(),
        ).asJsonArray
        assertTrue(
            designElements.size() > 0,
            "Real Analyzer produced no design elements for ${module.role}",
        )
        assertTrue(Files.isDirectory(module.classesDir.toPath()))
    }

    private fun resolveAnalyzerPluginClasspaths(): List<File> {
        val root = repositoryRoot()
        return listOf(
            resolveMainJar(root, "cap4k-plugin-code-analysis-compiler"),
            resolveMainJar(root, "cap4k-plugin-code-analysis-core"),
        )
    }

    private fun resolveMainJar(root: Path, moduleName: String): File {
        val libsDir = root.resolve("$moduleName/build/libs").toFile()
        require(libsDir.isDirectory) { "Unable to locate $moduleName build/libs: $libsDir" }
        return libsDir.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.name.startsWith(moduleName) &&
                    file.name.endsWith(".jar") &&
                    !file.name.endsWith("-sources.jar") &&
                    !file.name.endsWith("-javadoc.jar")
            }
            .maxByOrNull(File::lastModified)
            ?: error("No main jar found for $moduleName in $libsDir")
    }

    private fun configureDrawingBoard(projectDir: Path, analysisDirs: List<Path>) {
        val inputDirs = analysisDirs.joinToString(",\n") { path ->
            "                \"${path.toGradlePath()}\""
        }
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("\r\n", "\n") +
                """

                cap4k {
                    sources {
                        irAnalysis {
                            inputDirs.from(
$inputDirs
                            )
                        }
                    }
                    layout {
                        drawingBoard {
                            outputRoot.set("analysis-design")
                        }
                    }
                    generators {
                        drawingBoard {
                        }
                    }
                }
                """.trimIndent() + "\n",
        )
    }

    private fun registerDrawingBoardAsDesignJson(
        projectDir: Path,
        drawingBoardFiles: List<Path>,
    ): List<Path> {
        val recoveredDir = projectDir.resolve("design/recovered")
        Files.createDirectories(recoveredDir)
        val recoveredFiles = drawingBoardFiles.map { source ->
            recoveredDir.resolve(source.name).also { target ->
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        val buildFile = projectDir.resolve("build.gradle.kts")
        val originalRegistration = """files.from("design/design.json")"""
        val registeredPaths = recoveredFiles.joinToString(",\n") { path ->
            "                \"design/recovered/${path.name}\""
        }
        val originalBuild = buildFile.readText().replace("\r\n", "\n")
        require(originalBuild.contains(originalRegistration)) {
            "Round-trip fixture no longer contains the expected original Design JSON registration"
        }
        buildFile.writeText(
            originalBuild.replace(
                originalRegistration,
                """files.from(
$registeredPaths
            )""",
            ),
        )
        Files.delete(projectDir.resolve("design/design.json"))
        assertFalse(Files.exists(projectDir.resolve("design/design.json")))
        assertFalse(buildFile.readText().contains(originalRegistration))
        return recoveredFiles
    }

    private fun canonicalTacticalProjection(
        projectDir: Path,
        designFiles: List<Path>,
    ): TacticalProjection {
        val config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            sources = mapOf(
                "db" to SourceConfig(
                    mapOf(
                        "url" to databaseUrl(projectDir),
                        "username" to "sa",
                        "password" to "secret",
                        "schema" to "PUBLIC",
                        "includeTables" to listOf("order"),
                        "excludeTables" to emptyList<String>(),
                    ),
                ),
                "design-json" to SourceConfig(
                    mapOf("files" to designFiles.map { it.toAbsolutePath().toString() }),
                ),
                "enum-manifest" to SourceConfig(
                    mapOf("files" to listOf(projectDir.resolve("design/enums.json").toString())),
                ),
                "value-object-manifest" to SourceConfig(
                    mapOf("files" to listOf(projectDir.resolve("design/value-objects.json").toString())),
                ),
            ),
        )
        val snapshots = listOf(
            DbSchemaSourceProvider().collect(config),
            EnumManifestSourceProvider().collect(config),
            ValueObjectManifestSourceProvider().collect(config),
            DesignJsonSourceProvider().collect(config),
        )
        val model = DefaultCanonicalAssembler().assemble(config, snapshots).model
        return model.toTacticalProjection()
    }

    private fun databaseUrl(projectDir: Path): String {
        val databaseName = "cap4k_roundtrip_" + Integer.toUnsignedString(
            projectDir.toFile().absolutePath.hashCode(),
            16,
        )
        return "jdbc:h2:mem:$databaseName;MODE=MySQL;DATABASE_TO_UPPER=false;" +
            "INIT=RUNSCRIPT FROM '${projectDir.resolve("schema.sql").toGradlePath()}'"
    }

    private fun CanonicalModel.toTacticalProjection(): TacticalProjection = TacticalProjection(
        blocks = designBlocks
            .map { block -> block.toProjection() }
            .sortedWith(compareBy(BlockProjection::tag, BlockProjection::packageName, BlockProjection::name)),
    )

    private fun DesignBlockModel.toProjection(): BlockProjection = BlockProjection(
        tag = tag,
        packageName = packageName,
        name = name,
        description = description,
        aggregates = aggregates,
        eventName = eventName,
        persist = persist,
        artifacts = artifacts
            .map { artifact -> ArtifactProjection(artifact.family, artifact.variant) }
            .sortedWith(compareBy(ArtifactProjection::family, ArtifactProjection::variant)),
        request = request.toProjection(),
        response = response?.toProjection(),
    )

    private fun SemanticValueDefinition.toProjection(): ValueProjection = ValueProjection(
        identity = identity.toProjection(),
        role = role.name,
        fields = fields.map { field -> field.toProjection() },
        nestedDefinitions = nestedDefinitions.map { definition -> definition.toProjection() },
        pageItem = (envelope as? SemanticValueEnvelope.Page)?.itemDefinition?.toProjection(),
    )

    private fun SemanticValueField.toProjection(): FieldProjection = FieldProjection(
        name = name,
        type = type.toProjection(),
        defaultValue = defaultValue?.kotlinExpression,
    )

    private fun SemanticTypeRef.toProjection(): TypeProjection = when (this) {
        is SemanticBuiltinTypeRef -> TypeProjection(
            shape = "builtin:${kind.name}",
            nullable = nullable,
        )
        is SemanticNamedTypeRef -> TypeProjection(
            shape = "named",
            nullable = nullable,
            identity = symbol.toProjection(),
        )
        is SemanticListTypeRef -> TypeProjection(
            shape = "list",
            nullable = nullable,
            arguments = listOf(elementType.toProjection()),
        )
        is SemanticSetTypeRef -> TypeProjection(
            shape = "set",
            nullable = nullable,
            arguments = listOf(elementType.toProjection()),
        )
        is SemanticArrayTypeRef -> TypeProjection(
            shape = "array",
            nullable = nullable,
            arguments = listOf(elementType.toProjection()),
        )
        is SemanticMapTypeRef -> TypeProjection(
            shape = "map",
            nullable = nullable,
            arguments = listOf(keyType.toProjection(), valueType.toProjection()),
        )
    }

    private fun CanonicalTypeIdentity.toProjection(): IdentityProjection = IdentityProjection(
        fqn = fqn,
        kind = kind.name,
        ownerAggregateName = ownerAggregateName,
    )

    private fun frameworkOwnedSkeleton(projectDir: Path): Map<String, String> {
        val plan = JsonParser.parseString(projectDir.resolve("build/cap4k/plan.json").readText()).asJsonObject
        val skeleton = TreeMap<String, String>()
        plan.getAsJsonArray("items")
            .map { it.asJsonObject }
            .filter { item -> item.get("generatorId").asString in DesignGeneratorIds }
            .forEach { item ->
                val outputPath = item.get("outputPath").asString.replace('\\', '/')
                val output = Path.of(outputPath).let { path ->
                    if (path.isAbsolute) path else projectDir.resolve(outputPath)
                }.normalize()
                assertTrue(Files.isRegularFile(output), "Missing planned framework skeleton: $output")
                val key = if (output.startsWith(projectDir)) {
                    projectDir.relativize(output).toString().replace('\\', '/')
                } else {
                    outputPath
                }
                val previous = skeleton.put(key, output.readText().replace("\r\n", "\n").trimEnd() + "\n")
                require(previous == null) { "Duplicate framework-owned skeleton output: $key" }
            }
        assertTrue(skeleton.isNotEmpty(), "No framework-owned design skeleton found in plan")
        assertEquals(DesignGeneratorIds, plan.getAsJsonArray("items")
            .map { it.asJsonObject.get("generatorId").asString }
            .filterTo(linkedSetOf()) { it in DesignGeneratorIds })
        return skeleton
    }

    private fun runtimeAnnotationProjection(skeleton: Map<String, String>): RuntimeAnnotationProjection =
        RuntimeAnnotationProjection(
            domainEvents = skeleton
                .filterValues { source -> source.contains("@DomainEvent(") }
                .mapValues { (_, source) -> extractAnnotationArguments(source, "DomainEvent") },
            integrationEvents = skeleton
                .filterValues { source -> source.contains("@IntegrationEvent(") }
                .mapValues { (_, source) -> extractAnnotationArguments(source, "IntegrationEvent") },
        )

    private fun extractAnnotationArguments(source: String, annotationName: String): String {
        val marker = "@$annotationName("
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "Missing annotation $annotationName" }
        val openIndex = markerIndex + marker.length - 1
        var depth = 0
        var inString = false
        var escaped = false
        for (index in openIndex until source.length) {
            val character = source[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        return source.substring(openIndex + 1, index)
                            .replace(Whitespace, "")
                            .replace("\\\$", "\$")
                    }
                }
            }
        }
        error("Unclosed annotation $annotationName")
    }

    private fun assertExpectedRuntimeAnnotations(annotations: RuntimeAnnotationProjection) {
        assertEquals(3, annotations.domainEvents.size)
        val persisted = annotations.domainEvents.entries.single { (path, _) ->
            path.contains("OrderConfirmed")
        }.value
        assertTrue(persisted.contains("value=\"order.confirmed\""), persisted)
        assertTrue(persisted.contains("persist=true"), persisted)

        val transientPayload = annotations.domainEvents.entries.single { (path, _) ->
            path.contains("OrderObserved")
        }.value
        assertEquals("persist=false", transientPayload)
        val marker = annotations.domainEvents.entries.single { (path, _) ->
            path.contains("OrderHeartbeat")
        }.value
        assertEquals("persist=false", marker)

        assertEquals(2, annotations.integrationEvents.size)
        val inbound = annotations.integrationEvents.values.single { value ->
            value.contains("value=\"payment.received\"")
        }
        assertTrue(inbound.contains("subscriber=\"\${spring.application.name:}\""), inbound)
        val outbound = annotations.integrationEvents.values.single { value ->
            value.contains("value=\"order.exported\"")
        }
        assertTrue(outbound.contains("subscriber=IntegrationEvent.NONE_SUBSCRIBER"), outbound)
    }

    private fun Path.toGradlePath(): String = toAbsolutePath().normalize().toString().replace('\\', '/')

    private data class AnalyzedModule(
        val role: String,
        val analysisDir: Path,
        val classesDir: File,
    )

    private data class TacticalProjection(
        val blocks: List<BlockProjection>,
    )

    private data class BlockProjection(
        val tag: String,
        val packageName: String,
        val name: String,
        val description: String,
        val aggregates: List<String>,
        val eventName: String,
        val persist: Boolean?,
        val artifacts: List<ArtifactProjection>,
        val request: ValueProjection,
        val response: ValueProjection?,
    )

    private data class ArtifactProjection(
        val family: String,
        val variant: String,
    )

    private data class ValueProjection(
        val identity: IdentityProjection,
        val role: String,
        val fields: List<FieldProjection>,
        val nestedDefinitions: List<ValueProjection>,
        val pageItem: ValueProjection?,
    )

    private data class FieldProjection(
        val name: String,
        val type: TypeProjection,
        val defaultValue: String?,
    )

    private data class TypeProjection(
        val shape: String,
        val nullable: Boolean,
        val identity: IdentityProjection? = null,
        val arguments: List<TypeProjection> = emptyList(),
    )

    private data class IdentityProjection(
        val fqn: String,
        val kind: String,
        val ownerAggregateName: String?,
    )

    private data class RuntimeAnnotationProjection(
        val domainEvents: Map<String, String>,
        val integrationEvents: Map<String, String>,
    )

    private companion object {
        const val FixtureName = "design-roundtrip-compile-sample"
        val AnalyzerLock = Any()
        val Whitespace = Regex("""\s+""")
        val DrawingBoardTags = listOf(
            "command",
            "query",
            "capability",
            "api_payload",
            "domain_event",
            "integration_event",
            "domain_service",
        )
        val SecondaryArtifactFamilies = setOf(
            "query-handler",
            "capability-handler",
            "domain-subscriber",
            "integration-subscriber",
        )
        val DesignGeneratorIds = linkedSetOf(
            "command",
            "query",
            "query-handler",
            "capability",
            "capability-handler",
            "api-payload",
            "domain-event",
            "domain-subscriber",
            "integration-event",
            "integration-subscriber",
            "domain-service",
        )
    }
}

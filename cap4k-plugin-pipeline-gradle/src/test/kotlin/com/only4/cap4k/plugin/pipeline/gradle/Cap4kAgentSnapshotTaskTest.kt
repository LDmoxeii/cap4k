package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.agent.AgentHashing
import com.only4.cap4k.plugin.pipeline.agent.AgentSnapshotCodec
import com.only4.cap4k.plugin.pipeline.api.AggregateDiagnostics
import com.only4.cap4k.plugin.pipeline.api.AgentManifest
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilitiesSection
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityStatus
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticsSection
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipSection
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnostics
import com.only4.cap4k.plugin.pipeline.api.PlanOutcome
import com.only4.cap4k.plugin.pipeline.api.PlanReport
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.UnsupportedAggregateTable
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class Cap4kAgentSnapshotTaskTest {
    @Test
    fun `writes one manifest-first snapshot with all seven sections`() {
        val project = project("agent-snapshot-valid")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme.publishing")
        val output = project.layout.buildDirectory.dir("cap4k/agent").get().asFile
        output.resolve("manifest.json").apply { parentFile.mkdirs(); writeText("stale") }
        output.resolve("stale.json").apply { parentFile.mkdirs(); writeText("stale") }
        output.resolve("old/nested.json").apply { parentFile.mkdirs(); writeText("stale") }

        snapshotTask(project).writeSnapshot()

        assertEquals(EXPECTED_FILES, output.listFiles().orEmpty().map(File::getName).toSet())
        assertFalse(output.resolve("old").exists())
        val manifest = readManifest(output)
        assertEquals(AgentSnapshotStatus.PARTIAL, manifest.status)
        assertEquals(EXPECTED_SECTION_IDS, manifest.sections.map { it.id }.toSet())
        assertTrue(PipelinePublicTasks.AGENT_SNAPSHOT in manifest.project.publicTasks)
        assertFalse(manifest.project.publicTasks.any { it.startsWith("cap4kBootstrap") })
        manifest.sections.forEach { section ->
            val sectionJson = output.resolve(section.path).readText(Charsets.UTF_8)
            assertEquals(section.sha256, AgentHashing.sha256(sectionJson), section.path)
        }
        val capabilities = AgentSnapshotCodec().fromJson(
            output.resolve("capabilities.json").readText(Charsets.UTF_8),
            AgentCapabilitiesSection::class.java,
        )
        assertEquals(
            listOf(
                "API Payload",
                "Capability",
                "Command",
                "Domain Event",
                "Domain Service",
                "Integration Event",
                "Query",
                "Subscriber",
            ),
            capabilities.supported.single { it.providerId == "design-json" }.tacticalCarriers,
        )
        val runtime = AgentSnapshotCodec().fromJson(
            output.resolve("runtime.json").readText(Charsets.UTF_8),
            AgentRuntimeSection::class.java,
        )
        assertEquals("METHOD_LEVEL_EVENT_LISTENER", runtime.eventHandler.authoring.name)
        assertEquals("SYNCHRONOUS_SEQUENTIAL_FAIL_FAST", runtime.eventHandler.execution.name)
        assertEquals("UNSPECIFIED", runtime.eventHandler.ordering.equalValues.name)
        assertEquals(
            setOf("Mediator.queries.askAsync", "Mediator.capabilities.callAsync"),
            runtime.eventHandler.managedAsyncCompletion.trackedOperations.toSet(),
        )
    }

    @Test
    fun `invalid project configuration leaves a diagnostic snapshot before failing`() {
        val project = project("agent-snapshot-invalid")

        val failure = assertThrows(GradleException::class.java) {
            snapshotTask(project).writeSnapshot()
        }

        val output = project.layout.buildDirectory.dir("cap4k/agent").get().asFile
        assertEquals(EXPECTED_FILES, output.listFiles().orEmpty().map(File::getName).toSet())
        val manifest = readManifest(output)
        assertEquals(AgentSnapshotStatus.INVALID, manifest.status)
        assertTrue(failure.message.orEmpty().contains("diagnostics.json"))
        val diagnostics = output.resolve("diagnostics.json").readText(Charsets.UTF_8)
        assertTrue(diagnostics.contains("project-configuration-invalid"))
        assertTrue(diagnostics.contains("project.basePackage"))
    }

    @Test
    fun `database configuration is inspected without connecting or exposing values`() {
        val project = project("agent-snapshot-db-safe")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme.publishing")
        extension.sources.db {
            enabled.set(true)
            url.set("jdbc:postgresql://127.0.0.1:1/private_catalog")
            username.set("private-user")
            password.set("super-secret-password")
        }

        snapshotTask(project).writeSnapshot()

        val output = project.layout.buildDirectory.dir("cap4k/agent").get().asFile
        val snapshotText = EXPECTED_FILES.joinToString("\n") { name -> output.resolve(name).readText(Charsets.UTF_8) }
        assertFalse(snapshotText.contains("jdbc:postgresql://127.0.0.1:1/private_catalog"))
        assertFalse(snapshotText.contains("private-user"))
        assertFalse(snapshotText.contains("super-secret-password"))
        assertTrue(snapshotText.contains("password"))
        assertTrue(snapshotText.contains("externalIoSafe"))
        assertTrue(snapshotText.contains("true"))

        val capabilities = AgentSnapshotCodec().fromJson(
            output.resolve("capabilities.json").readText(Charsets.UTF_8),
            AgentCapabilitiesSection::class.java,
        )
        assertEquals(
            AgentCapabilityStatus.CONFIGURED,
            capabilities.effective.single { it.providerId == "db" }.status,
        )
        assertEquals(
            AgentCapabilityStatus.NOT_APPLICABLE,
            capabilities.effective.single { it.providerId == "aggregate" }.status,
        )
    }

    @Test
    fun `ownership includes previously recorded generated source roots`() {
        val project = project("agent-snapshot-managed-roots")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme.publishing")
        val stateFile = generatedSourceManagedRootsStateFile(project.rootProject)
        stateFile.parentFile.mkdirs()
        stateFile.writeText(
            """{"version":1,"roots":{"domain":"publishing-domain/build/generated/cap4k/main/kotlin"}}"""
        )

        snapshotTask(project).writeSnapshot()

        val ownership = AgentSnapshotCodec().fromJson(
            project.layout.buildDirectory.file("cap4k/agent/ownership.json").get().asFile.readText(Charsets.UTF_8),
            AgentOwnershipSection::class.java,
        )
        assertEquals(
            "publishing-domain/build/generated/cap4k/main/kotlin",
            ownership.managedRoots["domain"],
        )
    }

    @Test
    fun `plan evidence ignores password values and tracks local db runscript content`() {
        val project = project("agent-plan-evidence")
        val schemaFile = project.projectDir.resolve("schema.sql")
        schemaFile.writeText("create table publication(id bigint primary key);")
        val url = "jdbc:h2:file:./build/h2/demo;INIT=RUNSCRIPT FROM '${schemaFile.absolutePath.replace("\\", "/")}'"
        val config = ProjectConfig(
            basePackage = "com.acme.publishing",
            sources = mapOf(
                "db" to SourceConfig(
                    options = mapOf(
                        "url" to url,
                        "username" to "sa",
                        "password" to "first-secret",
                    )
                )
            ),
        )

        val first = planEvidence(project, config)
        val passwordChanged = planEvidence(
            project,
            config.copy(
                sources = mapOf(
                    "db" to config.sources.getValue("db").copy(
                        options = config.sources.getValue("db").options + ("password" to "second-secret")
                    )
                )
            ),
        )
        schemaFile.writeText("create table publication(id bigint primary key, title varchar(255));")
        val scriptChanged = planEvidence(project, config)

        assertEquals(first.configurationIdentity, passwordChanged.configurationIdentity)
        assertEquals(first.localInputIdentity, passwordChanged.localInputIdentity)
        assertFalse(first.localInputIdentity == scriptChanged.localInputIdentity)
    }

    @Test
    fun `malformed local design input writes diagnostics before snapshot failure`() {
        val project = project("agent-snapshot-malformed-design")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme.publishing")
        val designFile = project.projectDir.resolve("design.json")
        designFile.writeText("{not-json")
        extension.sources.designJson.files.from(designFile)

        assertThrows(GradleException::class.java) {
            snapshotTask(project).writeSnapshot()
        }

        val output = project.layout.buildDirectory.dir("cap4k/agent").get().asFile
        assertEquals(AgentSnapshotStatus.INVALID, readManifest(output).status)
        val diagnostics = output.resolve("diagnostics.json").readText(Charsets.UTF_8)
        assertTrue(diagnostics.contains("input-content-invalid"))
        assertTrue(diagnostics.contains("design.json"))
    }

    @Test
    fun `design manifest transitive file content participates in local input identity`() {
        val project = project("agent-plan-design-manifest")
        val designDir = project.projectDir.resolve("design").apply { mkdirs() }
        val manifest = designDir.resolve("manifest.json").apply { writeText("[\"entry.json\"]") }
        val entry = designDir.resolve("entry.json").apply { writeText("[]") }
        val config = ProjectConfig(
            basePackage = "com.acme.publishing",
            sources = mapOf(
                "design-json" to SourceConfig(
                    options = mapOf(
                        "manifestFile" to manifest.absolutePath,
                        "projectDir" to designDir.absolutePath,
                    )
                )
            ),
        )

        val first = planEvidence(project, config)
        entry.writeText("[{\"tag\":\"query\",\"name\":\"Publication\"}]")
        val changed = planEvidence(project, config)

        assertNotEquals(first.localInputIdentity, changed.localInputIdentity)
    }

    @Test
    fun `failed plan evidence makes snapshot invalid even when identities match`() {
        val project = project("agent-snapshot-failed-plan")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme.publishing")
        val task = snapshotTask(project)
        val config = sourceTaskConfig(task.configFactory.build(project, extension))
        val planFile = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        planFile.parentFile.mkdirs()
        planFile.writeText(
            GsonBuilder().setPrettyPrinting().create().toJson(
                PlanReport(
                    items = emptyList(),
                    outcome = PlanOutcome.FAILED,
                    evidence = planEvidence(project, config),
                )
            )
        )

        assertThrows(GradleException::class.java) {
            task.writeSnapshot()
        }

        val output = project.layout.buildDirectory.dir("cap4k/agent").get().asFile
        assertEquals(AgentSnapshotStatus.INVALID, readManifest(output).status)
        val diagnostics = output.resolve("diagnostics.json").readText(Charsets.UTF_8)
        assertTrue(diagnostics.contains("source-plan-failed"))
    }

    @Test
    fun `parseable plan report with missing required fields leaves a diagnostic snapshot`() {
        val project = project("agent-snapshot-corrupt-plan-structure")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme.publishing")
        val planFile = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        planFile.parentFile.mkdirs()
        planFile.writeText("""{"outcome":"SUCCEEDED"}""")

        snapshotTask(project).writeSnapshot()

        val output = project.layout.buildDirectory.dir("cap4k/agent").get().asFile
        assertEquals(AgentSnapshotStatus.PARTIAL, readManifest(output).status)
        val diagnostics = output.resolve("diagnostics.json").readText(Charsets.UTF_8)
        assertTrue(diagnostics.contains("plan-evidence-invalid"))
        assertTrue(diagnostics.contains("items must be a JSON array"))
    }

    @Test
    fun `plan report with unsupported evidence schema leaves a diagnostic snapshot`() {
        val project = project("agent-snapshot-corrupt-plan-schema")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme.publishing")
        val planFile = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        planFile.parentFile.mkdirs()
        planFile.writeText(
            """
            {
              "items": [],
              "outcome": "SUCCEEDED",
              "evidence": {
                "schema": "cap4k.plan-evidence.v0",
                "configurationIdentity": "identity",
                "containsLiveExternalInput": false
              }
            }
            """.trimIndent()
        )

        snapshotTask(project).writeSnapshot()

        val output = project.layout.buildDirectory.dir("cap4k/agent").get().asFile
        assertEquals(AgentSnapshotStatus.PARTIAL, readManifest(output).status)
        val diagnostics = output.resolve("diagnostics.json").readText(Charsets.UTF_8)
        assertTrue(diagnostics.contains("plan-evidence-invalid"))
        assertTrue(diagnostics.contains("evidence schema is unsupported"))
    }

    @Test
    fun `duplicate unsupported table entries produce one stable diagnostic per table`() {
        val project = project("agent-snapshot-duplicate-plan-diagnostics")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        extension.project.basePackage.set("com.acme.publishing")
        val task = snapshotTask(project)
        val config = sourceTaskConfig(task.configFactory.build(project, extension))
        val planFile = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        planFile.parentFile.mkdirs()
        planFile.writeText(
            GsonBuilder().setPrettyPrinting().create().toJson(
                PlanReport(
                    items = emptyList(),
                    diagnostics = PipelineDiagnostics(
                        aggregate = AggregateDiagnostics(
                            discoveredTables = listOf("publication"),
                            includedTables = listOf("publication"),
                            excludedTables = emptyList(),
                            supportedTables = emptyList(),
                            unsupportedTables = listOf(
                                UnsupportedAggregateTable("publication", "missing primary key"),
                                UnsupportedAggregateTable("publication", "missing primary key"),
                                UnsupportedAggregateTable("publication", "unsupported column"),
                            ),
                        )
                    ),
                    evidence = planEvidence(project, config),
                )
            )
        )

        task.writeSnapshot()

        val diagnostics = AgentSnapshotCodec().fromJson(
            project.layout.buildDirectory.file("cap4k/agent/diagnostics.json").get().asFile.readText(Charsets.UTF_8),
            AgentDiagnosticsSection::class.java,
        ).diagnostics.filter { diagnostic -> diagnostic.id.startsWith("aggregate-table-unsupported-") }
        assertEquals(1, diagnostics.size)
        assertTrue(diagnostics.single().message.contains("missing primary key"))
        assertTrue(diagnostics.single().message.contains("unsupported column"))
    }

    private fun project(prefix: String) = ProjectBuilder.builder()
        .withProjectDir(createTempDirectory(prefix).toFile())
        .build()
        .also { it.pluginManager.apply(PipelinePlugin::class.java) }

    private fun snapshotTask(project: org.gradle.api.Project): Cap4kAgentSnapshotTask =
        project.tasks.named(PipelinePublicTasks.AGENT_SNAPSHOT, Cap4kAgentSnapshotTask::class.java).get()

    private fun readManifest(output: File): AgentManifest = AgentSnapshotCodec().fromJson(
        output.resolve("manifest.json").readText(Charsets.UTF_8),
        AgentManifest::class.java,
    )

    private companion object {
        val EXPECTED_FILES = setOf(
            "manifest.json",
            "project.json",
            "capabilities.json",
            "inputs.json",
            "ownership.json",
            "runtime.json",
            "analysis.json",
            "diagnostics.json",
        )
        val EXPECTED_SECTION_IDS = EXPECTED_FILES
            .filterNot { it == "manifest.json" }
            .map { it.removeSuffix(".json") }
            .toSet()
    }
}

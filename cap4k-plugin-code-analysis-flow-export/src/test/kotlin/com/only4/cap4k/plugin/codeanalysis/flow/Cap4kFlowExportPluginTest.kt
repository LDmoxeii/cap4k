package com.only4.cap4k.plugin.codeanalysis.flow

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class Cap4kFlowExportPluginTest {

    @Test
    fun `published flow-export plugin id applies plugin and registers flow tasks`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()

        root.pluginManager.apply("io.github.ldmoxeii.cap4k.codeanalysis.flow-export")

        assertNotNull(root.extensions.findByName("cap4kFlow"))
        assertNotNull(root.tasks.findByName("cap4kFlowExport"))
        assertNotNull(root.tasks.findByName("cap4kFlowClean"))
        assertNotNull(root.tasks.findByName("cap4kFlowCompile"))
        assertNotNull(root.tasks.findByName("cap4kFlow"))
    }

    @Test
    fun `flow export rejects missing aggregate analysis metadata with actionable diagnostic`() {
        val error = assertThrows(GradleException::class.java) {
            requireFlowAnalysisMetadata(
                mapOf(
                    "demo.domain.aggregates.order.Order" to setOf(
                        "com.only4.cap4k.analysis.metadata.AggregateElementMetadata"
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("demo.domain.aggregates.order.Order"))
        assertTrue(error.message!!.contains("AggregateElementMetadata"))
        assertTrue(error.message!!.contains("affected capability: Flow Analysis"))
        assertTrue(error.message!!.contains("restore the default ddd-default generator template"))
        assertTrue(error.message!!.contains("compileOnly classpath"))
        assertTrue(error.message!!.contains("apparently complete partial result"))
    }

    @Test
    fun `legacy flow export task rejects missing metadata before touching output directory`() {
        val projectDir = Files.createTempDirectory("cap4k-legacy-flow-metadata")
        val root = ProjectBuilder.builder()
            .withName("sample")
            .withProjectDir(projectDir.toFile())
            .build()
        root.pluginManager.apply("io.github.ldmoxeii.cap4k.codeanalysis.flow-export")
        val analysisDir = projectDir.resolve("analysis")
        Files.createDirectories(analysisDir)
        analysisDir.resolve("nodes.json").toFile().writeText(
            """
            [
              {
                "id":"demo.Order",
                "name":"Order",
                "fullName":"demo.Order",
                "type":"aggregate",
                "missingMetadata":["com.only4.cap4k.analysis.metadata.AggregateElementMetadata"],
                "metadataOwner":"demo.domain.aggregates.order.Order"
              }
            ]
            """.trimIndent()
        )
        analysisDir.resolve("rels.json").toFile().writeText("[]")
        val outputDir = projectDir.resolve("flows")
        Files.createDirectories(outputDir)
        val sentinel = outputDir.resolve("sentinel.txt")
        sentinel.toFile().writeText("unchanged")
        val task = root.tasks.getByName("cap4kFlowExport") as Cap4kFlowExportTask
        task.inputDirs.set(listOf(root.layout.projectDirectory.dir("analysis")))
        task.outputDir.set(root.layout.projectDirectory.dir("flows"))
        task.labelPrefixes.set(emptyList())
        task.entryTypes.set(listOf("aggregate"))
        task.entryTypeExcludes.set(emptyList())

        val error = assertThrows(GradleException::class.java) {
            task.export()
        }

        assertTrue(error.message!!.contains("demo.domain.aggregates.order.Order"))
        assertTrue(error.message!!.contains("affected capability: Flow Analysis"))
        assertEquals("unchanged", sentinel.toFile().readText())
        assertTrue(!outputDir.resolve("index.json").toFile().exists())
    }

    @Test
    fun `resolve module projects uses explicit pipeline module paths`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()
        val adapter = ProjectBuilder.builder()
            .withName("sample-adapter")
            .withParent(root)
            .build()
        val application = ProjectBuilder.builder()
            .withName("sample-application")
            .withParent(root)
            .build()
        val domain = ProjectBuilder.builder()
            .withName("sample-domain")
            .withParent(root)
            .build()

        val shape = FlowProjectShape(
            basePackage = "com.acme.demo",
            adapterModulePath = adapter.path,
            applicationModulePath = application.path,
            domainModulePath = domain.path,
        )

        assertEquals(
            listOf(adapter.path, application.path, domain.path),
            resolveModuleProjects(root, shape).map { it.path }
        )
    }

    @Test
    fun `resolve module projects falls back to root when no pipeline shape is present`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()

        assertEquals(listOf(root.path), resolveModuleProjects(root, null).map { it.path })
    }

    @Test
    fun `resolve module projects fails fast when explicit pipeline module paths are unresolved`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()

        val shape = FlowProjectShape(
            basePackage = "com.acme.demo",
            adapterModulePath = ":missing-adapter",
            applicationModulePath = "missing/application",
            domainModulePath = null,
        )

        val error = assertThrows(GradleException::class.java) {
            resolveModuleProjects(root, shape)
        }

        assertEquals(
            "Unable to resolve configured Cap4k flow-export module paths: :missing-adapter, missing/application",
            error.message
        )
    }

    @Test
    fun `resolve input dirs uses pipeline irAnalysis input dirs when module paths are absent`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()
        val configuredInputDir = root.projectDir.resolve("analysis/app/build/cap4k-code-analysis")

        root.extensions.add(
            "cap4k",
            FakeCap4kExtension(
                project = FakeCap4kProjectExtension(
                    basePackage = "com.acme.demo",
                    adapterModulePath = null,
                    applicationModulePath = null,
                    domainModulePath = null,
                ),
                sources = FakeCap4kSourcesExtension(
                    irAnalysis = FakeIrAnalysisSourceExtension(
                        inputDirs = listOf(configuredInputDir)
                    )
                )
            )
        )

        assertEquals(
            listOf(configuredInputDir.absolutePath),
            resolveInputDirs(root, resolvePipelineProjectShape(root)).map { it.asFile.absolutePath }
        )
    }

    @Test
    fun `resolve input dirs falls back to root build dir when pipeline irAnalysis input dirs are absent`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()

        root.extensions.add(
            "cap4k",
            FakeCap4kExtension(
                project = FakeCap4kProjectExtension(
                    basePackage = "com.acme.demo",
                    adapterModulePath = null,
                    applicationModulePath = null,
                    domainModulePath = null,
                )
            )
        )

        assertEquals(
            listOf(root.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath),
            resolveInputDirs(root, resolvePipelineProjectShape(root)).map { it.asFile.absolutePath }
        )
    }

    @Test
    fun `resolve label prefixes includes pipeline base package and project group`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()
        root.group = "com.acme"

        val shape = FlowProjectShape(
            basePackage = "com.acme.demo",
            adapterModulePath = null,
            applicationModulePath = null,
            domainModulePath = null,
        )

        assertEquals(
            listOf("com.acme.demo.", "com.acme."),
            resolveLabelPrefixes(root, shape)
        )
    }

    @Test
    fun `resolve pipeline project shape reads cap4k project extension`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()

        root.extensions.add(
            "cap4k",
            FakeCap4kExtension(
                FakeCap4kProjectExtension(
                    basePackage = "com.acme.demo",
                    adapterModulePath = ":sample-adapter",
                    applicationModulePath = ":sample-application",
                    domainModulePath = ":sample-domain",
                )
            )
        )

        assertEquals(
            FlowProjectShape(
                basePackage = "com.acme.demo",
                adapterModulePath = ":sample-adapter",
                applicationModulePath = ":sample-application",
                domainModulePath = ":sample-domain",
            ),
            resolvePipelineProjectShape(root)
        )
    }
}

private class FakeCap4kExtension(
    private val project: FakeCap4kProjectExtension,
    private val sources: FakeCap4kSourcesExtension = FakeCap4kSourcesExtension()
) {
    fun getProject(): FakeCap4kProjectExtension = project
    fun getSources(): FakeCap4kSourcesExtension = sources
}

private class FakeCap4kProjectExtension(
    private val basePackage: String?,
    private val adapterModulePath: String?,
    private val applicationModulePath: String?,
    private val domainModulePath: String?,
) {
    fun getBasePackage(): FakeGradleProperty = FakeGradleProperty(basePackage)
    fun getAdapterModulePath(): FakeGradleProperty = FakeGradleProperty(adapterModulePath)
    fun getApplicationModulePath(): FakeGradleProperty = FakeGradleProperty(applicationModulePath)
    fun getDomainModulePath(): FakeGradleProperty = FakeGradleProperty(domainModulePath)
}

private class FakeCap4kSourcesExtension(
    private val irAnalysis: FakeIrAnalysisSourceExtension = FakeIrAnalysisSourceExtension()
) {
    fun getIrAnalysis(): FakeIrAnalysisSourceExtension = irAnalysis
}

private class FakeIrAnalysisSourceExtension(
    private val inputDirs: List<File> = emptyList()
) {
    fun getInputDirs(): FakeGradleFileCollection = FakeGradleFileCollection(inputDirs)
}

private class FakeGradleProperty(private val value: String?) {
    fun getOrNull(): String? = value
}

private class FakeGradleFileCollection(private val files: List<File>) {
    fun getFiles(): Set<File> = files.toSet()
}

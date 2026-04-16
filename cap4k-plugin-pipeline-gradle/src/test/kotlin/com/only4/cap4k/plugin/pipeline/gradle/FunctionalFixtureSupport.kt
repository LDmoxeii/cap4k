package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText

object FunctionalFixtureSupport {

    @OptIn(ExperimentalPathApi::class)
    fun copyFixture(targetDir: Path, fixtureName: String = "design-sample") {
        val sourceDir = Path.of(
            requireNotNull(FunctionalFixtureSupport::class.java.getResource("/functional/$fixtureName")) {
                "Missing functional fixture directory: $fixtureName"
            }.toURI()
        )
        sourceDir.copyToRecursively(targetDir, followLinks = false)
    }

    @OptIn(ExperimentalPathApi::class)
    fun copyCompileFixture(targetDir: Path, fixtureName: String) {
        copyFixture(targetDir, fixtureName)

        val repoRoot = discoverRepositoryRoot()
        val settingsFile = targetDir.resolve("settings.gradle.kts")
        val repoPath = repoRoot.toString()
            .replace("\\", "/")
            .replace("$", "\\$")
        settingsFile.writeText(settingsFile.readText().replace("__CAP4K_REPO_ROOT__", repoPath))
    }

    fun runner(projectDir: Path, vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(*arguments)

    fun generateThenCompile(projectDir: Path, vararg compileTasks: String): Pair<BuildResult, BuildResult> {
        require(compileTasks.isNotEmpty()) {
            "compileTasks must not be empty"
        }
        val generateResult = runner(projectDir, "cap4kGenerate").build()
        val compileResult = runner(projectDir, *compileTasks).build()
        return generateResult to compileResult
    }

    private fun discoverRepositoryRoot(): Path {
        val startPoints = linkedSetOf<Path>()
        val userDir = System.getProperty("user.dir")
        if (!userDir.isNullOrBlank()) {
            startPoints.add(Path.of(userDir).toAbsolutePath().normalize())
        }
        resolveClassLocationStartPoint()?.let { startPoints.add(it) }

        for (start in startPoints) {
            searchUpwardForRepositoryRoot(start)?.let { return it }
        }

        throw IllegalStateException(
            "Unable to locate cap4k repository root from start points: ${
                startPoints.joinToString { it.toString() }
            }"
        )
    }

    private fun resolveClassLocationStartPoint(): Path? = runCatching {
        val location = FunctionalFixtureSupport::class.java.protectionDomain.codeSource.location
        val path = Path.of(location.toURI()).toAbsolutePath().normalize()
        if (Files.isRegularFile(path)) path.parent else path
    }.getOrNull()

    private fun searchUpwardForRepositoryRoot(start: Path): Path? {
        var cursor = start
        while (true) {
            if (isRepositoryRoot(cursor)) {
                return cursor
            }
            cursor = cursor.parent ?: return null
        }
    }

    private fun isRepositoryRoot(dir: Path): Boolean = Files.exists(dir.resolve("settings.gradle.kts")) &&
        Files.isDirectory(dir.resolve("ddd-core")) &&
        Files.isDirectory(dir.resolve("cap4k-plugin-pipeline-gradle"))
}

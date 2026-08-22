package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CompositeBuildConsumerFunctionalTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `official consumer resolves plugin and runtime modules from included cap4k build`() {
        FunctionalFixtureSupport.copyCompileFixture(
            targetDir = projectDir,
            fixtureName = "official-composite-consumer-sample",
        )

        val build = runner(
            "cap4kPlan",
            ":domain:compileKotlin",
            ":application:compileKotlin",
            ":adapter:compileKotlin",
            ":start:test",
        ).build()

        assertEquals(TaskOutcome.SUCCESS, build.task(":cap4kPlan")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":domain:compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":application:compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":adapter:compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":start:test")?.outcome)

        assertCompositeDependency(":domain", "ddd-core")
        assertCompositeDependency(":adapter", "ddd-domain-repo-jpa")
        assertCompositeDependency(":start", "cap4k-ddd-jpa-starter")
    }

    private fun assertCompositeDependency(projectPath: String, dependency: String) {
        val insight = runner(
            "$projectPath:dependencyInsight",
            "--dependency",
            dependency,
            "--configuration",
            "compileClasspath",
        ).build()

        assertTrue(
            Regex("(?m)^project '?[^\\r\\n]*:${Regex.escape(dependency)}'? \\(by composite build\\)$")
                .containsMatchIn(insight.output),
            "Expected $dependency to resolve from the included cap4k build. Output:\n${insight.output}",
        )
        assertTrue(
            !insight.output.contains("Could not find io.github.ldmoxeii:$dependency:999.0.0-local"),
            "The nonexistent remote version must not be resolved for $dependency. Output:\n${insight.output}",
        )
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withTestKitDir(gradleUserHome().toFile())
        .withArguments(
            *arguments,
            "-Pcap4k.local.path=${FunctionalFixtureSupport.repositoryRoot()}",
            "--stacktrace",
            "--no-configuration-cache",
        )

    private fun gradleUserHome(): Path {
        val configured = System.getenv("GRADLE_USER_HOME")
        return if (configured.isNullOrBlank()) {
            Path.of(System.getProperty("user.home"), ".gradle")
        } else {
            Path.of(configured)
        }
    }
}

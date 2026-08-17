package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.readText

class EndpointRpcGenerationFunctionalTest {
    @Test
    fun `generated Provider and endpoint client compile package and complete cross process roundtrips`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-endpoint-rpc-generation")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "endpoint-rpc-generation-compile-sample")

        val generate = FunctionalFixtureSupport.runner(projectDir, "cap4kGenerate").build()
        val build = FunctionalFixtureSupport.runner(
            projectDir,
            "clean",
            ":provider-start:installDist",
            ":endpoint-client:jar",
            ":consumer-start:installDist",
        ).build()

        assertEquals(TaskOutcome.SUCCESS, generate.task(":cap4kGenerate")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":cap4kGenerateSources")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":provider-adapter:compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":endpoint-client:jar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":consumer:compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":provider-start:installDist")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, build.task(":consumer-start:installDist")?.outcome)

        val providerSource = projectDir.resolve(
            "provider-adapter/build/generated/cap4k/main/kotlin/com/acme/rpc/endpoint/rpc/generated/EndpointRpcProviderBindings.kt",
        )
        val clientSource = projectDir.resolve(
            "endpoint-client/build/generated/cap4k/main/kotlin/com/acme/rpc/endpoint/rpc/generated/EndpointRpcClientAutoConfiguration.kt",
        )
        assertTrue(Files.exists(providerSource), providerSource.toString())
        assertTrue(Files.exists(clientSource), clientSource.toString())
        assertTrue(providerSource.readText().contains("CreateBookingEndpoint.OPERATION_NAME"))
        assertTrue(clientSource.readText().contains("EndpointTransportInvoker"))
        assertFalse(clientSource.readText().contains("endpoint.rpc.http"))

        val clientJar = Files.list(projectDir.resolve("endpoint-client/build/libs")).use { files ->
            files.filter { it.fileName.toString().endsWith(".jar") }.findFirst().orElse(null)
        }
        assertNotNull(clientJar)
        ZipFile(clientJar!!.toFile()).use { jar ->
            val metadata = jar.getEntry("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            assertNotNull(metadata)
            val text = jar.getInputStream(metadata).bufferedReader().use { it.readText() }
            assertEquals("com.acme.rpc.endpoint.rpc.generated.EndpointRpcClientAutoConfiguration", text.trim())
        }

        val clientBuild = projectDir.resolve("endpoint-client/build.gradle.kts").readText()
        assertTrue(clientBuild.contains("ddd-endpoint-rpc"))
        assertFalse(clientBuild.contains("ddd-endpoint-rpc-http"))
        assertFalse(clientBuild.contains("endpoint-rpc-http-starter"))
        val consumerSource = projectDir.resolve("consumer/src/main/kotlin/demo/consumer/BookingConsumer.kt").readText()
        assertTrue(consumerSource.contains("Mediator.endpoints.send"))
        assertTrue(consumerSource.contains("Mediator.capabilities").not())
        assertFalse(consumerSource.contains("EndpointTransportInvoker"))
        assertFalse(consumerSource.contains(".handle("))

        assertGeneratedCrossProcessRoundtrip(projectDir)
    }

    private fun assertGeneratedCrossProcessRoundtrip(projectDir: Path) {
        val port = ServerSocket(0).use { it.localPort }
        val provider = ProcessBuilder(
            distributionCommand(projectDir, "provider-start", "demo.providerstart.ProviderApplicationKt", port.toString()),
        )
            .redirectErrorStream(true)
            .start()
        try {
            awaitMarker(provider, "CAP4K_GENERATED_RPC_PROVIDER_READY", Duration.ofSeconds(30))
            val consumer = ProcessBuilder(
                distributionCommand(projectDir, "consumer-start", "demo.consumerstart.ConsumerApplicationKt", "http://127.0.0.1:$port"),
            ).redirectErrorStream(true).start()
            val consumerOutput = consumer.inputStream.bufferedReader().use { it.readText() }
            assertTrue(consumer.waitFor(30, TimeUnit.SECONDS), "Consumer process timed out:\n$consumerOutput")
            assertEquals(0, consumer.exitValue(), consumerOutput)
            assertTrue(consumerOutput.contains("CAP4K_GENERATED_RPC_CONSUMER_SUCCESS"), consumerOutput)
        } finally {
            runCatching {
                provider.outputStream.bufferedWriter().use { it.newLine() }
            }
            if (!provider.waitFor(10, TimeUnit.SECONDS)) provider.destroyForcibly()
        }
    }

    private fun distributionCommand(
        projectDir: Path,
        module: String,
        mainClass: String,
        vararg args: String,
    ): List<String> {
        val java = Path.of(
            System.getProperty("java.home"),
            "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java",
        )
        val classpath = projectDir.resolve("$module/build/install/$module/lib").toString() + File.separator + "*"
        return listOf(java.toString(), "-cp", classpath, mainClass, *args)
    }

    private fun awaitMarker(process: Process, marker: String, timeout: Duration) {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val deadline = System.nanoTime() + timeout.toNanos()
        val output = StringBuilder()
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) {
                reader.lines().forEach { output.appendLine(it) }
                throw AssertionError("Process exited before marker $marker:\n$output")
            }
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                output.appendLine(line)
                if (line == marker) return
            } else {
                Thread.sleep(25)
            }
        }
        throw AssertionError("Process did not emit marker $marker:\n$output")
    }
}





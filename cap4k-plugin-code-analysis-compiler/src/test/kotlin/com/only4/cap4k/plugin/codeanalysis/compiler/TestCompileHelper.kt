@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.config.OptionsKeys
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import java.io.File
import java.nio.file.Path

private class ProbeIrExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        System.setProperty("cap4k.test.ir", "true")
    }
}

private class ProbeRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: org.jetbrains.kotlin.config.CompilerConfiguration) {
        IrGenerationExtension.registerExtension(ProbeIrExtension())
        System.setProperty("cap4k.test.plugin.registrar", "true")
    }
}

fun compileWithCap4kPlugin(sources: List<SourceFile>): Path {
    val workingDir = java.nio.file.Files.createTempDirectory("cap4k-compile").toFile()
    System.clearProperty("cap4k.test.plugin.registrar")
    System.clearProperty("cap4k.test.ir")
    val compilation = KotlinCompilation().apply {
        this.sources = sources
        this.workingDir = workingDir
        inheritClassPath = true
        supportsK2 = true
        compilerPluginRegistrars = listOf(
            Cap4kCodeAnalysisCompilerRegistrar(),
            ProbeRegistrar()
        )
        pluginClasspaths = resolvePluginClasspaths()
        messageOutputStream = System.out
    }
    val outputDir = workingDir.toPath().resolve("build/cap4k-code-analysis")
    val originalOutput = System.getProperty(OptionsKeys.OUTPUT_DIR)
    try {
        System.setProperty(OptionsKeys.OUTPUT_DIR, workingDir.absolutePath)
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        check(System.getProperty("cap4k.test.plugin.registrar") == "true") {
            "Compiler plugin registrars were not invoked"
        }
        check(System.getProperty("cap4k.test.ir") == "true") {
            "IR generation extensions were not invoked"
        }
    } finally {
        if (originalOutput == null) {
            System.clearProperty(OptionsKeys.OUTPUT_DIR)
        } else {
            System.setProperty(OptionsKeys.OUTPUT_DIR, originalOutput)
        }
    }
    return outputDir
}

private fun resolvePluginClasspaths(): List<File> {
    val compilerJar = resolveJarFrom(
        listOf(
            File("cap4k-plugin-code-analysis-compiler/build/libs"),
            File("build/libs")
        ),
        "cap4k-plugin-code-analysis-compiler"
    )
    val coreJar = resolveJarFrom(
        listOf(
            File("cap4k-plugin-code-analysis-core/build/libs"),
            File("../cap4k-plugin-code-analysis-core/build/libs")
        ),
        "cap4k-plugin-code-analysis-core"
    )
    return listOf(compilerJar, coreJar)
}

private fun resolveJarFrom(candidates: List<File>, hint: String): File {
    val libsDir = candidates.firstOrNull { it.exists() }
        ?: error("Unable to locate $hint build/libs")
    return libsDir.listFiles()
        ?.firstOrNull { it.name.endsWith(".jar") && !it.name.endsWith("-sources.jar") }
        ?: error("No jar found in ${libsDir.absolutePath}")
}

package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestCompileHelperTest {
    @Test
    fun `compile helper emits output dir`() {
        val outputDir = compileWithCap4kPlugin(
            listOf(
                SourceFile.kotlin(
                    "Ping.kt",
                    "package demo; class Ping"
                )
            )
        )
        assertTrue(outputDir.fileName.toString() == "cap4k-code-analysis")
    }
}

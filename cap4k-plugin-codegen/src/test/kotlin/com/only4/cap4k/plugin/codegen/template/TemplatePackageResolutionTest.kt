package com.only4.cap4k.plugin.codegen.template

import com.only4.cap4k.plugin.codegen.misc.resolveTemplatePackage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class TemplatePackageResolutionTest {

    @Test
    fun `package node resolves relative Kotlin package from source path`() {
        val root = Files.createTempDirectory("template-package").toFile()
        val packageDir = File(root, "module/src/main/kotlin/com/acme/domain/aggregates").apply { mkdirs() }
        val node = TemplateNode().apply { type = "package" }

        val resolved = resolveTemplatePackage(node.type, packageDir.absolutePath, "com.acme")

        assertEquals("domain.aggregates", resolved)
    }

    @Test
    fun `plain dir node skips Kotlin package resolution`() {
        val root = Files.createTempDirectory("template-dir").toFile()
        val designDir = File(root, "design").apply { mkdirs() }
        val node = TemplateNode().apply { type = "dir" }

        val resolved = resolveTemplatePackage(node.type, designDir.absolutePath, "com.acme")

        assertNull(resolved)
    }

    @Test
    fun `package node is treated as directory template`() {
        val node = TemplateNode().apply { type = "package" }

        assertTrue(node.isDirNode())
    }
}

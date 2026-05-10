package com.only4.cap4k.plugin.pipeline.renderer.pebble

import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PresetTemplateResolverTest {

    @Test
    fun `resolver keeps two argument jvm constructor for existing callers`() {
        val constructor = PresetTemplateResolver::class.java.getConstructor(String::class.java, List::class.java)
        val resolver = constructor.newInstance("ddd-default-bootstrap", emptyList<String>())

        val resolved = resolver.resolve("bootstrap/root/settings.gradle.kts.peb")

        assertTrue(resolved.contains("rootProject.name"))
    }

    @Test
    fun `resolve prefers absolute direct file before override and resource templates`() {
        val directFile = Files.createTempFile("bootstrap-direct-template", ".peb")
        directFile.writeText("direct={{ projectName }}")

        val overrideDir = Files.createTempDirectory("bootstrap-override")
        val overrideTemplateDir = Files.createDirectories(overrideDir.resolve("bootstrap/root"))
        overrideTemplateDir.resolve("settings.gradle.kts.peb").writeText("override={{ projectName }}")

        val resolver = PresetTemplateResolver(
            preset = "ddd-default-bootstrap",
            overrideDirs = listOf(overrideDir.toString())
        )

        val resolved = resolver.resolve(directFile.toString())

        assertEquals("direct={{ projectName }}", resolved)
    }

    @Test
    fun `resolve prefers project override for addon template before addon classloader resource`() {
        val templateId = "addons/sample-addon/sample.kt.peb"
        val overrideDir = Files.createTempDirectory("addon-override")
        overrideDir.resolve(templateId).parent.createDirectories()
        overrideDir.resolve(templateId).writeText("override addon template")

        val addonResourceDir = Files.createTempDirectory("addon-resource")
        addonResourceDir.resolve("cap4k/$templateId").parent.createDirectories()
        addonResourceDir.resolve("cap4k/$templateId").writeText("jar addon template")

        URLClassLoader(arrayOf(addonResourceDir.toUri().toURL()), null).use { addonClassLoader ->
            val resolver = PresetTemplateResolver(
                preset = "ddd-default-bootstrap",
                overrideDirs = listOf(overrideDir.toString()),
                addonTemplateClassLoaders = mapOf("sample-addon" to addonClassLoader)
            )

            val resolved = resolver.resolve(templateId)

            assertEquals("override addon template", resolved)
        }
    }

    @Test
    fun `resolve uses addon classloader resource when addon template has no override`() {
        val templateId = "addons/sample-addon/sample.kt.peb"
        val addonResourceDir = Files.createTempDirectory("addon-resource")
        addonResourceDir.resolve("cap4k/$templateId").parent.createDirectories()
        addonResourceDir.resolve("cap4k/$templateId").writeText("jar addon template")

        URLClassLoader(arrayOf(addonResourceDir.toUri().toURL()), null).use { addonClassLoader ->
            val resolver = PresetTemplateResolver(
                preset = "ddd-default-bootstrap",
                overrideDirs = emptyList(),
                addonTemplateClassLoaders = mapOf("sample-addon" to addonClassLoader)
            )

            val resolved = resolver.resolve(templateId)

            assertEquals("jar addon template", resolved)
        }
    }

    @Test
    fun `resolve rejects addon template when addon id is not registered`() {
        val templateId = "addons/sample-addon/missing.kt.peb"
        val resolver = PresetTemplateResolver(
            preset = "ddd-default-bootstrap",
            overrideDirs = emptyList()
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(templateId)
        }

        assertEquals("Template references addon 'sample-addon' but no addon provider is loaded.", exception.message)
    }

    @Test
    fun `resolve rejects addon template for different registered addon namespace`() {
        val templateId = "addons/missing-addon/sample.kt.peb"
        val addonResourceDir = Files.createTempDirectory("addon-resource")
        addonResourceDir.resolve("cap4k/$templateId").parent.createDirectories()
        addonResourceDir.resolve("cap4k/$templateId").writeText("jar addon template")

        URLClassLoader(arrayOf(addonResourceDir.toUri().toURL()), null).use { addonClassLoader ->
            val resolver = PresetTemplateResolver(
                preset = "ddd-default-bootstrap",
                overrideDirs = emptyList(),
                addonTemplateClassLoaders = mapOf("sample-addon" to addonClassLoader)
            )

            val exception = assertThrows(IllegalArgumentException::class.java) {
                resolver.resolve(templateId)
            }

            assertEquals(
                "Template references addon 'missing-addon' but no addon provider is loaded.",
                exception.message,
            )
        }
    }

    @Test
    fun `resolve fails fast for missing addon template without preset fallback`() {
        val templateId = "addons/sample-addon/missing.kt.peb"
        val resolver = PresetTemplateResolver(
            preset = "ddd-default-bootstrap",
            overrideDirs = emptyList(),
            addonTemplateClassLoaders = mapOf("sample-addon" to javaClass.classLoader)
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(templateId)
        }

        assertEquals("Addon template not found: cap4k/$templateId", exception.message)
    }

    @Test
    fun `resolve keeps built in preset resource resolution for non addon template`() {
        val resolver = PresetTemplateResolver(
            preset = "ddd-default-bootstrap",
            overrideDirs = emptyList()
        )

        val resolved = resolver.resolve("bootstrap/root/settings.gradle.kts.peb")

        assertTrue(resolved.contains("rootProject.name"))
    }
}

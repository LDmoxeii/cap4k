package com.only4.cap4k.plugin.pipeline.renderer.pebble

import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultKotlinTemplateImportContractTest {
    private val presetRoot: Path = Path.of("src/main/resources/presets/ddd-default")
    private val noImportTemplates = setOf(
        "aggregate/repository.kt.peb",
        "aggregate/schema.kt.peb"
    )

    @Test
    fun `default non bootstrap kotlin templates follow unified import contract`() {
        assertTrue(Files.isDirectory(presetRoot), "preset root does not exist: $presetRoot")

        val templatePaths = Files.walk(presetRoot).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt.peb") }
                .map { path -> presetRoot.relativize(path).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }

        assertTrue(templatePaths.isNotEmpty(), "no default Kotlin templates found under $presetRoot")
        assertTrue(templatePaths.none { path -> path.startsWith("bootstrap/") }, "bootstrap templates are out of scope")

        for (templatePath in templatePaths) {
            val templateContent = Files.readString(presetRoot.resolve(templatePath))
            val importsImportsCount = Regex("imports\\(imports\\)").findAll(templateContent).count()
            val importsJpaImportsCount = Regex("imports\\(jpaImports\\)").findAll(templateContent).count()
            val directImportCount = Regex("(?m)^import\\s+(?!\\{\\{\\s*\\w+\\s*\\}\\})")
                .findAll(templateContent)
                .count()

            assertEquals(0, importsJpaImportsCount, "$templatePath must not use imports(jpaImports)")
            assertEquals(0, directImportCount, "$templatePath must not contain direct import lines")

            if (templatePath in noImportTemplates) {
                assertEquals(0, importsImportsCount, "$templatePath should not emit imports(imports)")
                continue
            }

            assertEquals(1, importsImportsCount, "$templatePath must contain exactly one imports(imports) helper usage")
            assertTrue(
                Regex("""\{%\s*for\s+\w+\s+in\s+imports\(imports\)\s*-?%\}[\s\r\n]*import\s+\{\{\s*\w+\s*\}\}""")
                    .containsMatchIn(templateContent),
                "$templatePath must emit final import lines through imports(imports)"
            )
            assertFalse(
                templateContent.contains("imports(list)"),
                "$templatePath should not document legacy imports(list) helper usage"
            )
        }
    }
}

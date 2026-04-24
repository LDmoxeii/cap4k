package com.only4.cap4k.plugin.pipeline.generator.design

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DesignInteractionSiblingsTest {

    @Test
    fun `sibling lookup bytecode does not depend on coroutine spilling internals`() {
        val resourcePath =
            "com/only4/cap4k/plugin/pipeline/generator/design/DesignInteractionSiblingsKt\$designInteractionSiblingTypeNames\$1.class"
        val bytecode = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.use { stream -> stream.readBytes() }
            ?: return

        val constantPoolText = bytecode.toString(Charsets.ISO_8859_1)

        assertFalse(
            constantPoolText.contains("kotlin/coroutines/jvm/internal/SpillingKt"),
            "Gradle plugin runtime may not provide Kotlin coroutine spilling internals.",
        )
    }
}

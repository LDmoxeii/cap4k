package com.only4.cap4k.plugin.pipeline.gradle

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalInputStateTest {
    @Test
    fun `comparison detects content and source membership drift`() {
        val baseline = CollectedLocalInputs(
            bySource = mapOf("design-json" to mapOf("design.json" to "first".toByteArray())),
            ordinary = mapOf("design.json" to "first".toByteArray()),
            analysis = emptyMap(),
        )
        val sameContent = CollectedLocalInputs(
            bySource = mapOf("design-json" to mapOf("design.json" to "first".toByteArray())),
            ordinary = mapOf("design.json" to "first".toByteArray()),
            analysis = emptyMap(),
        )
        val changedContent = sameContent.copy(
            ordinary = mapOf("design.json" to "second".toByteArray())
        )
        val changedSource = sameContent.copy(
            bySource = mapOf("ir-analysis" to mapOf("design.json" to "first".toByteArray()))
        )

        assertTrue(sameLocalInputState(baseline, sameContent))
        assertFalse(sameLocalInputState(baseline, changedContent))
        assertFalse(sameLocalInputState(baseline, changedSource))
    }
}

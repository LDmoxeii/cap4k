package com.only4.cap4k.plugin.pipeline.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootstrapManagedSectionMergerTest {

    private val merger = BootstrapManagedSectionMerger()

    @Test
    fun `merge replaces matching managed section bodies and preserves surrounding content`() {
        val existing = """
            plugins {
                id("custom-root")
            }

            // [cap4k-bootstrap:managed-begin:root-repositories]
            repositories {
                mavenLocal()
            }
            // [cap4k-bootstrap:managed-end:root-repositories]

            rootProject.name = "user-owned"

            // [cap4k-bootstrap:managed-begin:root-includes]
            include(":legacy-module")
            // [cap4k-bootstrap:managed-end:root-includes]
        """.trimIndent()

        val generated = """
            // [cap4k-bootstrap:managed-begin:root-repositories]
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
            // [cap4k-bootstrap:managed-end:root-repositories]

            // [cap4k-bootstrap:managed-begin:root-includes]
            include(":demo-domain")
            include(":demo-application")
            // [cap4k-bootstrap:managed-end:root-includes]
        """.trimIndent()

        val merged = merger.merge(existing, generated)

        assertTrue(merged.contains("id(\"custom-root\")"))
        assertTrue(merged.contains("rootProject.name = \"user-owned\""))
        assertTrue(merged.contains("mavenCentral()"))
        assertTrue(merged.contains("gradlePluginPortal()"))
        assertTrue(merged.contains("include(\":demo-domain\")"))
        assertTrue(merged.contains("include(\":demo-application\")"))
        assertTrue(!merged.contains("mavenLocal()"))
        assertTrue(!merged.contains("include(\":legacy-module\")"))
        assertEquals(existing.lines().size + 2, merged.lines().size)
    }

    @Test
    fun `merge fails when existing managed section is missing closing marker`() {
        val existing = """
            plugins {
                id("custom-root")
            }

            // [cap4k-bootstrap:managed-begin:root-repositories]
            repositories {
                mavenLocal()
            }
        """.trimIndent()

        val generated = """
            // [cap4k-bootstrap:managed-begin:root-repositories]
            repositories {
                mavenCentral()
            }
            // [cap4k-bootstrap:managed-end:root-repositories]
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            merger.merge(existing, generated)
        }

        assertTrue(error.message!!.contains("root-repositories"))
    }

    @Test
    fun `merge fails when generated managed markers are malformed`() {
        val existing = """
            // [cap4k-bootstrap:managed-begin:root-repositories]
            repositories {
                mavenLocal()
            }
            // [cap4k-bootstrap:managed-end:root-repositories]
        """.trimIndent()

        val generated = """
            // [cap4k-bootstrap:managed-begin:root-repositories]
            repositories {
                mavenCentral()
            }
            // [cap4k-bootstrap:managed-end:other-section]
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            merger.merge(existing, generated)
        }

        assertTrue(error.message!!.contains("other-section"))
    }
}

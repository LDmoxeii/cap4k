package com.only4.cap4k.plugin.pipeline.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentHashingIdentityTest {
    @Test
    fun `sha256 and snapshot hashing are deterministic`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AgentHashing.sha256("abc"),
        )

        val first = AgentHashing.snapshotSha256(
            linkedMapOf(
                "project.json" to AgentHashing.sha256("project"),
                "inputs.json" to AgentHashing.sha256("inputs"),
            )
        )
        val second = AgentHashing.snapshotSha256(
            linkedMapOf(
                "inputs.json" to AgentHashing.sha256("inputs"),
                "project.json" to AgentHashing.sha256("project"),
            )
        )

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertThrows(IllegalArgumentException::class.java) {
            AgentHashing.snapshotSha256(mapOf("project.json" to "not-a-hash"))
        }
    }

    @Test
    fun `configuration identity excludes credentials and raw jdbc urls`() {
        val identity = AgentIdentity()
        val first = identity.configurationIdentity(
            linkedMapOf(
                "preset" to "default",
                "database" to linkedMapOf(
                    "jdbcUrl" to "jdbc:postgresql://db-a/demo?user=alice&password=first",
                    "password" to "first",
                    "poolSize" to 8,
                ),
            )
        )
        val changedCredentials = identity.configurationIdentity(
            linkedMapOf(
                "database" to linkedMapOf(
                    "poolSize" to 8,
                    "password" to "second",
                    "jdbcUrl" to "jdbc:postgresql://db-b/other?user=bob&password=second",
                ),
                "preset" to "default",
            )
        )
        val changedSafeOption = identity.configurationIdentity(
            linkedMapOf(
                "preset" to "default",
                "database" to linkedMapOf(
                    "jdbcUrl" to "jdbc:postgresql://db-a/demo?password=first",
                    "password" to "first",
                    "poolSize" to 16,
                ),
            )
        )

        assertEquals(first, changedCredentials)
        assertNotEquals(first, changedSafeOption)
    }

    @Test
    fun `option summary reports keys without option values`() {
        val redactor = AgentCredentialRedactor()
        val summary = redactor.optionSummary(
            mapOf(
                "mode" to "safe",
                "database" to mapOf(
                    "password" to "hunter2",
                    "jdbcUrl" to "jdbc:mysql://localhost/demo?token=secret",
                ),
            )
        )

        assertEquals(
            listOf("database.jdbcUrl", "database.password", "mode"),
            summary.configuredKeys,
        )
        assertEquals(
            listOf("database.jdbcUrl", "database.password"),
            summary.sensitiveKeys,
        )
        val rendered = summary.toString()
        assertFalse(rendered.contains("hunter2"))
        assertFalse(rendered.contains("jdbc:mysql"))

        assertEquals(
            listOf("database.username"),
            redactor.optionSummary(
                options = mapOf("database" to mapOf("username" to "alice")),
                additionalSensitiveKeys = setOf("database.username"),
            ).sensitiveKeys,
        )
    }

    @Test
    fun `local input identity is path ordered content sensitive and credential safe`() {
        val identity = AgentIdentity()
        val first = identity.localTextInputIdentity(
            linkedMapOf(
                "design\\model.json" to "{\"name\":\"Demo\"}",
                "config.yml" to "password: first\nmode: safe",
            )
        )
        val reorderedAndChangedPassword = identity.localTextInputIdentity(
            linkedMapOf(
                "config.yml" to "password: second\nmode: safe",
                "design/model.json" to "{\"name\":\"Demo\"}",
            )
        )
        val changedInput = identity.localTextInputIdentity(
            linkedMapOf(
                "config.yml" to "password: second\nmode: safe",
                "design/model.json" to "{\"name\":\"Changed\"}",
            )
        )

        assertEquals(first, reorderedAndChangedPassword)
        assertNotEquals(first, changedInput)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertThrows(IllegalArgumentException::class.java) {
            identity.localTextInputIdentity(mapOf("../outside.json" to "{}"))
        }
    }

    @Test
    fun `diagnostic redaction covers json headers and raw connection strings`() {
        val redacted = AgentCredentialRedactor().redact(
            """failure {"password":"hunter2","token":"abc","Cookie":"sid=secret","auth":"auth-secret","signingKey":"signing-secret","encryption_key":"encryption-secret"} """ +
                "Authorization: Bearer bearer-secret " +
                "mongodb://alice:secret@db.internal/catalog " +
                "Server=db.internal;Database=demo;User Id=alice;Password=secret;"
        )

        listOf(
            "hunter2",
            "abc",
            "sid=secret",
            "bearer-secret",
            "auth-secret",
            "signing-secret",
            "encryption-secret",
            "mongodb://",
            "db.internal",
        ).forEach { secret -> assertFalse(redacted.contains(secret), redacted) }
        assertTrue(redacted.contains("<configured>"))
    }

    @Test
    fun `extension credential aliases do not affect configuration or local input identities`() {
        val identity = AgentIdentity()
        val firstConfiguration = identity.configurationIdentity(
            mapOf(
                "extension" to mapOf(
                    "auth" to "first-auth",
                    "signingKey" to "first-signing-key",
                    "encryption_key" to "first-encryption-key",
                    "mode" to "safe",
                )
            )
        )
        val changedCredentials = identity.configurationIdentity(
            mapOf(
                "extension" to mapOf(
                    "auth" to "second-auth",
                    "signingKey" to "second-signing-key",
                    "encryption_key" to "second-encryption-key",
                    "mode" to "safe",
                )
            )
        )
        val firstLocalInput = identity.localTextInputIdentity(
            mapOf(
                "design/extension.json" to
                    """{"mode":"safe","auth":"first-auth","signingKey":"first-signing-key","encryptionKey":"first-encryption-key"}"""
            )
        )
        val changedLocalCredentials = identity.localTextInputIdentity(
            mapOf(
                "design/extension.json" to
                    """{"mode":"safe","auth":"second-auth","signingKey":"second-signing-key","encryptionKey":"second-encryption-key"}"""
            )
        )

        assertEquals(firstConfiguration, changedCredentials)
        assertEquals(firstLocalInput, changedLocalCredentials)
    }

    @Test
    fun `json and binary local identities never hash credential values`() {
        val identity = AgentIdentity()
        val firstJson = identity.localTextInputIdentity(
            mapOf("design/config.json" to """{"mode":"safe","password":"first"}""")
        )
        val changedJsonCredential = identity.localTextInputIdentity(
            mapOf("design/config.json" to """{"password":"second","mode":"safe"}""")
        )
        val changedJsonFact = identity.localTextInputIdentity(
            mapOf("design/config.json" to """{"mode":"changed","password":"second"}""")
        )
        val firstBinary = identity.localInputIdentity(
            mapOf("design/blob.bin" to byteArrayOf(0xC3.toByte(), 0x28))
        )
        val changedBinary = identity.localInputIdentity(
            mapOf("design/blob.bin" to byteArrayOf(0xC3.toByte(), 0x29))
        )
        val firstSensitiveFile = identity.localTextInputIdentity(
            mapOf("config/password.txt" to "first")
        )
        val changedSensitiveFile = identity.localTextInputIdentity(
            mapOf("config/password.txt" to "second")
        )

        assertEquals(firstJson, changedJsonCredential)
        assertNotEquals(firstJson, changedJsonFact)
        assertEquals(firstBinary, changedBinary)
        assertEquals(firstSensitiveFile, changedSensitiveFile)
    }
}

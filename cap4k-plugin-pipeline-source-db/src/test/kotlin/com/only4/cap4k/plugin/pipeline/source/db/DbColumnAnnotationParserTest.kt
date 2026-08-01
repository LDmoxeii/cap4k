package com.only4.cap4k.plugin.pipeline.source.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class DbColumnAnnotationParserTest {
    @Test
    fun `preserves exact managed policy key and strips the annotation`() {
        val metadata = DbColumnAnnotationParser.parse(
            "creation time @Managed=enrichment.audit-time.created-at;",
        )

        assertEquals("enrichment.audit-time.created-at", metadata.managedPolicyKey)
        assertEquals("creation time", metadata.cleanedComment)
    }

    @TestFactory
    fun `accepts standard and custom syntactically valid policy keys`() = listOf(
        "identifier.uuid7",
        "identifier.snowflake",
        "identifier.assigned",
        "identifier.database-identity",
        "version",
        "soft-delete",
        "database.generated-always",
        "scope.tenant",
        "custom.policy-with-kebab-segments",
    ).map { key ->
        DynamicTest.dynamicTest(key) {
            assertEquals(key, DbColumnAnnotationParser.parse("@Managed=$key;").managedPolicyKey)
        }
    }

    @TestFactory
    fun `rejects malformed policy keys without normalizing them`() = listOf(
        "Identifier.uuid7",
        "identifier_uuid7",
        "identifier..uuid7",
        "identifier.UUID7",
        ".identifier",
        "identifier.",
        "identifier uuid7",
    ).map { key ->
        DynamicTest.dynamicTest(key) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                DbColumnAnnotationParser.parse("@Managed=$key;")
            }
            assertTrue(error.message!!.contains("must match"))
        }
    }

    @Test
    fun `rejects blank valueless and duplicate managed annotations`() {
        assertEquals(
            "invalid @Managed annotation: value is required.",
            assertThrows(IllegalArgumentException::class.java) {
                DbColumnAnnotationParser.parse("@Managed;")
            }.message,
        )
        assertEquals(
            "invalid @Managed annotation: value is required.",
            assertThrows(IllegalArgumentException::class.java) {
                DbColumnAnnotationParser.parse("@Managed=;")
            }.message,
        )
        assertEquals(
            "multiple @Managed annotations are not allowed.",
            assertThrows(IllegalArgumentException::class.java) {
                DbColumnAnnotationParser.parse("@Managed=version;@Managed=soft-delete;")
            }.message,
        )
    }

    @TestFactory
    fun `rejects removed policy annotations through the unsupported path`() = listOf(
        "@IdStrategy=uuid7;",
        "@Inherited;",
    ).map { comment ->
        DynamicTest.dynamicTest(comment) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                DbColumnAnnotationParser.parse(comment)
            }
            assertTrue(error.message!!.startsWith("unsupported column annotation @"))
            assertTrue(error.message!!.contains("@Managed=<policy-key>"))
        }
    }

    @Test
    fun `retains relation and type annotations beside managed policy`() {
        val metadata = DbColumnAnnotationParser.parse(
            "status @Type=VideoPostVisibility;@RefAggregate=VideoPost;@Managed=scope.tenant;",
        )

        assertEquals("VideoPostVisibility", metadata.typeBinding)
        assertEquals("VideoPost", metadata.refAggregate)
        assertNull(metadata.refId)
        assertEquals("scope.tenant", metadata.managedPolicyKey)
        assertEquals("status", metadata.cleanedComment)
    }

    @Test
    fun `parent ref rejects competing relation references`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DbColumnAnnotationParser.parse("@ParentRef;@RefAggregate=VideoPost;")
        }
        assertEquals("@ParentRef cannot be combined with @RefAggregate or @RefId.", error.message)
    }

    @Test
    fun `silent source leaves policy empty`() {
        val metadata = DbColumnAnnotationParser.parse("plain comment")

        assertNull(metadata.managedPolicyKey)
        assertEquals("plain comment", metadata.cleanedComment)
    }
}

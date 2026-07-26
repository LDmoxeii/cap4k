package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateIdStorageKind
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteActiveSentinel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SoftDeleteDefaultNormalizerTest {
    @Test
    fun `normalizes the finite supported literal and wrapper grammar`() {
        val cases = listOf(
            SuccessCase("0", AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("  0\t", AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("((( 0 )))", AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("0", AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("'0'", AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("'0'", AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.ZERO),
            SuccessCase(" ( ( '0' ) ) ", AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("'00000000-0000-0000-0000-000000000000'", AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.NIL_UUID),
            SuccessCase("(('00000000-0000-0000-0000-000000000000'))", AggregateIdStorageKind.NATIVE_UUID, SoftDeleteActiveSentinel.NIL_UUID),
            SuccessCase("UUID '00000000-0000-0000-0000-000000000000'", AggregateIdStorageKind.NATIVE_UUID, SoftDeleteActiveSentinel.NIL_UUID),
            SuccessCase(" uuid\t'00000000-0000-0000-0000-000000000000' ", AggregateIdStorageKind.NATIVE_UUID, SoftDeleteActiveSentinel.NIL_UUID),
            SuccessCase("CAST(0 AS BIGINT)", AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO),
            SuccessCase(" cast ( '0' as character varying ) ", AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("CAST('00000000-0000-0000-0000-000000000000' AS UUID)", AggregateIdStorageKind.NATIVE_UUID, SoftDeleteActiveSentinel.NIL_UUID),
            SuccessCase("0::bigint", AggregateIdStorageKind.INTEGRAL, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("'0'::character varying", AggregateIdStorageKind.CHARACTER, SoftDeleteActiveSentinel.ZERO),
            SuccessCase("'00000000-0000-0000-0000-000000000000'::uuid", AggregateIdStorageKind.NATIVE_UUID, SoftDeleteActiveSentinel.NIL_UUID),
        )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                SoftDeleteDefaultNormalizer.normalize(case.rawDefaultValue, case.storageKind),
                "${case.storageKind}: ${case.rawDefaultValue}",
            )
        }
    }

    @Test
    fun `accepts every target in the finite storage-family allowlists`() {
        val integralTargets = listOf("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT")
        val characterTargets = listOf(
            "CHAR",
            "CHARACTER",
            "CHARACTER VARYING",
            "VARCHAR",
            "LONGVARCHAR",
            "NCHAR",
            "NVARCHAR",
            "LONGNVARCHAR",
        )

        integralTargets.forEach { target ->
            assertEquals(
                SoftDeleteActiveSentinel.ZERO,
                SoftDeleteDefaultNormalizer.normalize("CAST(0 AS $target)", AggregateIdStorageKind.INTEGRAL),
                "standard integral cast: $target",
            )
            assertEquals(
                SoftDeleteActiveSentinel.ZERO,
                SoftDeleteDefaultNormalizer.normalize("0::${target.lowercase()}", AggregateIdStorageKind.INTEGRAL),
                "postfix integral cast: $target",
            )
        }
        characterTargets.forEach { target ->
            assertEquals(
                SoftDeleteActiveSentinel.ZERO,
                SoftDeleteDefaultNormalizer.normalize("CAST('0' AS $target)", AggregateIdStorageKind.CHARACTER),
                "standard character cast: $target",
            )
            assertEquals(
                SoftDeleteActiveSentinel.ZERO,
                SoftDeleteDefaultNormalizer.normalize("'0'::${target.lowercase()}", AggregateIdStorageKind.CHARACTER),
                "postfix character cast: $target",
            )
        }
    }

    @Test
    fun `rejects unsupported values malformed delimiters and noncanonical literals`() {
        val cases = listOf(
            RejectionCase("NULL", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("''", AggregateIdStorageKind.CHARACTER),
            RejectionCase("1", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("gen_random_uuid()", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("uuid_nil()", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("current_timestamp", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("0", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("'00000000-0000-0000-0000-000000000000'", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("(0", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("0)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("((0)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("'0", AggregateIdStorageKind.CHARACTER),
            RejectionCase("0'", AggregateIdStorageKind.CHARACTER),
            RejectionCase("'0''suffix'", AggregateIdStorageKind.CHARACTER),
            RejectionCase("\"0\"", AggregateIdStorageKind.CHARACTER),
            RejectionCase("00000000-0000-0000-0000-000000000000", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("'00000000000000000000000000000000'", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("'{00000000-0000-0000-0000-000000000000}'", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("'00000000-0000-0000-0000-000000000000 '", AggregateIdStorageKind.NATIVE_UUID),
        )

        assertRejected(cases)
    }

    @Test
    fun `rejects casts outside the finite grammar or storage family`() {
        val nilUuid = "'00000000-0000-0000-0000-000000000000'"
        val cases = listOf(
            RejectionCase("CAST(CAST(0 AS BIGINT) AS BIGINT)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("CAST(0::bigint AS BIGINT)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("CAST(0 AS BIGINT)::BIGINT", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("CAST($nilUuid AS UUID)::UUID", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("UUID $nilUuid::UUID", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("0::bigint::bigint", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("UUID UUID $nilUuid", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("CAST(UUID $nilUuid AS UUID)", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("CAST(0 AS BIGINT)", AggregateIdStorageKind.CHARACTER),
            RejectionCase("CAST('0' AS VARCHAR)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("CAST($nilUuid AS UUID)", AggregateIdStorageKind.CHARACTER),
            RejectionCase("0::bigint", AggregateIdStorageKind.CHARACTER),
            RejectionCase("'0'::varchar", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("$nilUuid::uuid", AggregateIdStorageKind.CHARACTER),
            RejectionCase("UUID $nilUuid", AggregateIdStorageKind.CHARACTER),
            RejectionCase("CAST(0 AS DECIMAL)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("CAST(0 AS NUMERIC)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("CAST('0' AS TEXT)", AggregateIdStorageKind.CHARACTER),
            RejectionCase("CAST('0' AS CLOB)", AggregateIdStorageKind.CHARACTER),
            RejectionCase("CAST($nilUuid AS UUID_CHAR)", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("CAST($nilUuid AS BINARY(16))", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("0::serial", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("'0'::varchar(1)", AggregateIdStorageKind.CHARACTER),
        )

        assertRejected(cases)
    }

    @Test
    fun `rejects operators functions concatenation and trailing text`() {
        val nilUuid = "'00000000-0000-0000-0000-000000000000'"
        val cases = listOf(
            RejectionCase("0 + 0", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("0 - 0", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("+0", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("-0", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("00", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("0.0", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("'0' || ''", AggregateIdStorageKind.CHARACTER),
            RejectionCase("COALESCE(0, 0)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("(SELECT 0)", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("0 trailing", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("0;", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("0::bigint trailing", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("CAST(0 AS BIGINT) trailing", AggregateIdStorageKind.INTEGRAL),
            RejectionCase("UUID $nilUuid trailing", AggregateIdStorageKind.NATIVE_UUID),
            RejectionCase("(0) + 0", AggregateIdStorageKind.INTEGRAL),
        )

        assertRejected(cases)
    }

    private fun assertRejected(cases: List<RejectionCase>) {
        cases.forEach { case ->
            assertNull(
                SoftDeleteDefaultNormalizer.normalize(case.rawDefaultValue, case.storageKind),
                "${case.storageKind}: ${case.rawDefaultValue}",
            )
        }
    }

    private data class SuccessCase(
        val rawDefaultValue: String,
        val storageKind: AggregateIdStorageKind,
        val expected: SoftDeleteActiveSentinel,
    )

    private data class RejectionCase(
        val rawDefaultValue: String,
        val storageKind: AggregateIdStorageKind,
    )
}

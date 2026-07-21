package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OwnedParentBindingResolverTest {
    @Test
    fun `skips out of scope parent before requiring parent ref column`() {
        val bindings = OwnedParentBindingResolver.resolve(
            tables = listOf(
                table(
                    name = "video_post_item",
                    parentTable = "video_post",
                    columns = listOf(
                        DbColumnSnapshot("id", "BIGINT", "Long", false),
                    ),
                )
            ),
            outOfScopeTableNames = setOf("video_post"),
        )

        assertTrue(bindings.isEmpty())
    }

    @Test
    fun `skips ignored child before requiring parent ref column`() {
        val bindings = OwnedParentBindingResolver.resolve(
            tables = listOf(
                table(
                    name = "video_post_item",
                    parentTable = "video_post",
                    columns = listOf(
                        DbColumnSnapshot("id", "BIGINT", "Long", false),
                    ),
                )
            ),
            skippedTableNames = setOf("video_post_item"),
        )

        assertTrue(bindings.isEmpty())
    }

    @Test
    fun `skips out of scope child before requiring parent ref column`() {
        val bindings = OwnedParentBindingResolver.resolve(
            tables = listOf(
                table(
                    name = "video_post_item",
                    parentTable = "video_post",
                    columns = listOf(
                        DbColumnSnapshot("id", "BIGINT", "Long", false),
                    ),
                )
            ),
            outOfScopeTableNames = setOf("video_post_item"),
        )

        assertTrue(bindings.isEmpty())
    }

    @Test
    fun `skips ignored parent with multiple parent refs before ambiguous check`() {
        val bindings = OwnedParentBindingResolver.resolve(
            tables = listOf(
                table(
                    name = "video_post_item",
                    parentTable = "video_post",
                    columns = listOf(
                        DbColumnSnapshot("parent_a_id", "BIGINT", "Long", false, parentRef = true),
                        DbColumnSnapshot("parent_b_id", "BIGINT", "Long", false, parentRef = true),
                    ),
                )
            ),
            skippedTableNames = setOf("video_post"),
        )

        assertTrue(bindings.isEmpty())
    }

    private fun table(
        name: String,
        parentTable: String,
        columns: List<DbColumnSnapshot>,
    ): DbTableSnapshot = DbTableSnapshot(
        tableName = name,
        comment = "",
        columns = columns,
        primaryKey = listOf("id"),
        uniqueConstraints = emptyList(),
        parentTable = parentTable,
    )
}

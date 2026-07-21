package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OwnedRelationCardinalityInferenceTest {
    @Test
    fun `primary key parent ref infers one`() {
        val binding = binding(
            columns = listOf(parentRef("video_post_id")),
            primaryKey = listOf("VIDEO_POST_ID"),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref infers one independent of case`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(unique("uk_parent", "VIDEO_POST_ID")),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus non null deleted discriminator infers one`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("deleted", managedRole = DbManagedRole.DELETED),
            ),
            uniqueConstraints = listOf(unique("uk_parent_deleted", "deleted", "video_post_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus non null scope discriminator infers one`() {
        val binding = binding(
            columns = listOf(
                id(),
                column("tenant_id", managedRole = DbManagedRole.SCOPE),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent", "tenant_id", "video_post_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus scope and deleted infers one only when both roles are declared and non null`() {
        val binding = binding(
            columns = listOf(
                id(),
                column("tenant_id", managedRole = DbManagedRole.SCOPE),
                parentRef("video_post_id"),
                column("deleted", managedRole = DbManagedRole.DELETED),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent_deleted", "deleted", "video_post_id", "tenant_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus business column infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("code"),
            ),
            uniqueConstraints = listOf(unique("uk_parent_code", "video_post_id", "code")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus version infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("version", managedRole = DbManagedRole.VERSION),
            ),
            uniqueConstraints = listOf(unique("uk_parent_version", "video_post_id", "version")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus system field infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("created_by", managedRole = DbManagedRole.SYSTEM),
            ),
            uniqueConstraints = listOf(unique("uk_parent_created_by", "video_post_id", "created_by")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `nullable scope or deleted columns do not prove one`() {
        val nullableScope = binding(
            columns = listOf(
                id(),
                column("tenant_id", nullable = true, managedRole = DbManagedRole.SCOPE),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent", "tenant_id", "video_post_id")),
        )
        val nullableDeleted = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("deleted", nullable = true, managedRole = DbManagedRole.DELETED),
            ),
            uniqueConstraints = listOf(unique("uk_parent_deleted", "video_post_id", "deleted")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(nullableScope))
        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(nullableDeleted))
    }

    @Test
    fun `unique without parent ref infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("code"),
            ),
            uniqueConstraints = listOf(unique("uk_code", "code")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(binding))
    }

    private fun binding(
        columns: List<DbColumnSnapshot>,
        primaryKey: List<String> = listOf("id"),
        uniqueConstraints: List<UniqueConstraintModel> = emptyList(),
    ): OwnedParentBinding {
        val child = DbTableSnapshot(
            tableName = "video_post_file",
            comment = "",
            columns = columns,
            primaryKey = primaryKey,
            uniqueConstraints = uniqueConstraints,
            parentTable = "video_post",
            aggregateRoot = false,
        )
        return OwnedParentBinding(
            childTable = child,
            parentTable = "video_post",
            parentRefColumn = columns.single { it.parentRef },
        )
    }

    private fun id(): DbColumnSnapshot = column("id", primaryKey = true)

    private fun parentRef(name: String): DbColumnSnapshot = column(name, parentRef = true)

    private fun column(
        name: String,
        nullable: Boolean = false,
        primaryKey: Boolean = false,
        parentRef: Boolean = false,
        managedRole: DbManagedRole? = null,
    ): DbColumnSnapshot = DbColumnSnapshot(
        name = name,
        dbType = "BIGINT",
        kotlinType = "Long",
        nullable = nullable,
        isPrimaryKey = primaryKey,
        parentRef = parentRef,
        managedRole = managedRole,
    )

    private fun unique(name: String, vararg columns: String): UniqueConstraintModel =
        UniqueConstraintModel(physicalName = name, columns = columns.toList())
}

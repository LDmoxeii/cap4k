package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.ManagedCreationInputPolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedExplicitValuePolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldLifecycle
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicyDefinitionOwner
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicySelectionProvenance
import com.only4.cap4k.plugin.pipeline.api.ManagedValueAuthority
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.PersistenceParticipation
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedEntityPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedFieldPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedWriteSurfacePolicy
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OwnedRelationCardinalityInferenceTest {
    @Test
    fun `parent ref without unique constraint infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
            ),
        )

        assertEquals(OwnedRelationCardinality.MANY, infer(binding))
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

        assertEquals(OwnedRelationCardinality.ONE, infer(binding))
    }

    @Test
    fun `incomplete unique constraint does not prove one`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(
                unique("uk_parent", "video_post_id").copy(complete = false)
            ),
        )

        assertEquals(OwnedRelationCardinality.MANY, infer(binding))
    }

    @Test
    fun `filtered unique constraint does not prove one`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(
                unique("uk_parent_active", "video_post_id").copy(filterCondition = "deleted = 0")
            ),
        )

        assertEquals(OwnedRelationCardinality.MANY, infer(binding))
    }

    @Test
    fun `unique parent ref plus non null deleted discriminator infers one`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("deleted", managedRole = ManagedFieldRole.SOFT_DELETE),
            ),
            uniqueConstraints = listOf(unique("uk_parent_deleted", "deleted", "video_post_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, infer(binding))
    }

    @Test
    fun `unique parent ref plus non null scope discriminator infers one`() {
        val binding = binding(
            columns = listOf(
                id(),
                column("tenant_id", managedRole = ManagedFieldRole.SCOPE),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent", "tenant_id", "video_post_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, infer(binding))
    }

    @Test
    fun `unique parent ref plus scope and deleted infers one only when both roles are declared and non null`() {
        val binding = binding(
            columns = listOf(
                id(),
                column("tenant_id", managedRole = ManagedFieldRole.SCOPE),
                parentRef("video_post_id"),
                column("deleted", managedRole = ManagedFieldRole.SOFT_DELETE),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent_deleted", "deleted", "video_post_id", "tenant_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, infer(binding))
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

        assertEquals(OwnedRelationCardinality.MANY, infer(binding))
    }

    @Test
    fun `unique parent ref plus version infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("version", managedRole = ManagedFieldRole.VERSION),
            ),
            uniqueConstraints = listOf(unique("uk_parent_version", "video_post_id", "version")),
        )

        assertEquals(OwnedRelationCardinality.MANY, infer(binding))
    }

    @Test
    fun `unique parent ref plus system field infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("created_by", managedRole = ManagedFieldRole.ENRICHMENT),
            ),
            uniqueConstraints = listOf(unique("uk_parent_created_by", "video_post_id", "created_by")),
        )

        assertEquals(OwnedRelationCardinality.MANY, infer(binding))
    }

    @Test
    fun `nullable scope or deleted columns do not prove one`() {
        val nullableScope = binding(
            columns = listOf(
                id(),
                column("tenant_id", nullable = true, managedRole = ManagedFieldRole.SCOPE),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent", "tenant_id", "video_post_id")),
        )
        val nullableDeleted = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("deleted", nullable = true, managedRole = ManagedFieldRole.SOFT_DELETE),
            ),
            uniqueConstraints = listOf(unique("uk_parent_deleted", "video_post_id", "deleted")),
        )

        assertEquals(OwnedRelationCardinality.MANY, infer(nullableScope))
        assertEquals(OwnedRelationCardinality.MANY, infer(nullableDeleted))
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

        assertEquals(OwnedRelationCardinality.MANY, infer(binding))
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
        managedRole: ManagedFieldRole? = null,
    ): DbColumnSnapshot = DbColumnSnapshot(
        name = name,
        dbType = "BIGINT",
        kotlinType = "Long",
        nullable = nullable,
        isPrimaryKey = primaryKey,
        parentRef = parentRef,
        managedPolicyKey = managedRole?.name?.lowercase(),
    )

    private fun infer(binding: OwnedParentBinding): OwnedRelationCardinality {
        val managedFields = binding.childTable.columns.mapNotNull { column ->
            val role = when (column.managedPolicyKey) {
                "scope" -> ManagedFieldRole.SCOPE
                "soft_delete" -> ManagedFieldRole.SOFT_DELETE
                "version" -> ManagedFieldRole.VERSION
                "enrichment" -> ManagedFieldRole.ENRICHMENT
                else -> null
            } ?: return@mapNotNull null
            ResolvedManagedFieldPolicy(
                fieldName = column.name,
                columnName = column.name,
                fieldType = column.kotlinType,
                nullable = column.nullable,
                selection = ManagedPolicySelectionProvenance.ExplicitColumnAnnotation("test"),
                definitionOwner = ManagedPolicyDefinitionOwner.BuiltIn,
                policyKey = column.managedPolicyKey!!,
                role = role,
                creationInput = ManagedCreationInputPolicy.OMIT,
                explicitValue = ManagedExplicitValuePolicy.FORBID,
                lifecycles = setOf(ManagedFieldLifecycle.PERSISTENCE_PROVIDER),
                handlerQualifier = null,
                handlerSlot = null,
                semanticValueType = column.kotlinType,
                valueAdapterQualifier = null,
                persistence = PersistenceParticipation(
                    ManagedValueAuthority.PERSISTENCE_PROVIDER,
                    ManagedValueAuthority.PERSISTENCE_PROVIDER,
                ),
            )
        }
        val policy = ResolvedManagedEntityPolicy(
            entityName = "VideoPostFile",
            entityPackageName = "sample",
            tableName = binding.childTable.tableName,
            fields = managedFields,
            writeSurface = ResolvedWriteSurfacePolicy(),
        )
        return OwnedRelationCardinalityInference.infer(binding, policy)
    }

    private fun unique(name: String, vararg columns: String): UniqueConstraintModel =
        UniqueConstraintModel(physicalName = name, columns = columns.toList())
}

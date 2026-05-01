package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregateEnumPlanningTest {

    @Test
    fun `resolves local enum items for aggregate field`() {
        val enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"), EnumItemModel(1, "PUBLIC", "Public"))
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "",
            fields = listOf(FieldModel("visibility", "Int", typeBinding = "Visibility", enumItems = enumItems)),
            idField = FieldModel("id", "Long"),
        )
        val planning = AggregateEnumPlanning.from(
            CanonicalModel(entities = listOf(entity)),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals(enumItems, planning.resolveEnumItems(entity.packageName, entity.fields.single()))
    }

    @Test
    fun `resolves shared enum items for aggregate field`() {
        val enumItems = listOf(EnumItemModel(0, "DRAFT", "Draft"), EnumItemModel(1, "PUBLISHED", "Published"))
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "",
            fields = listOf(FieldModel("status", "Int", typeBinding = "Status")),
            idField = FieldModel("id", "Long"),
        )
        val planning = AggregateEnumPlanning.from(
            CanonicalModel(
                sharedEnums = listOf(
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "shared",
                        generateTranslation = true,
                        items = enumItems,
                    )
                ),
                entities = listOf(entity),
            ),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals(enumItems, planning.resolveEnumItems(entity.packageName, entity.fields.single()))
    }

    @Test
    fun `type binding prefers local enum definition over shared enum lookup only when E and T coexist`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "",
            fields = listOf(
                FieldModel(name = "status", type = "Int", typeBinding = "Status"),
                FieldModel(
                    name = "visibility",
                    type = "Int",
                    typeBinding = "VideoPostVisibility",
                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
                ),
            ),
            idField = FieldModel(name = "id", type = "Long"),
        )
        val planning = AggregateEnumPlanning.from(
            CanonicalModel(
                sharedEnums = listOf(
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "shared",
                        generateTranslation = true,
                        items = listOf(EnumItemModel(0, "DRAFT", "Draft"))
                    )
                ),
                entities = listOf(entity)
            ),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals("com.acme.demo.domain.shared.enums.Status", planning.resolveFieldType("Status", emptyList()))
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.enums.VideoPostVisibility",
            planning.resolveFieldType(
                entity.packageName,
                "VideoPostVisibility",
                listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
            )
        )
    }

    @Test
    fun `ambiguous simple type between shared enum and general registry fails`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEnumPlanning.from(
                CanonicalModel(
                    sharedEnums = listOf(
                        SharedEnumDefinition(
                            typeName = "Status",
                            packageName = "shared",
                            generateTranslation = true,
                            items = listOf(EnumItemModel(0, "DRAFT", "Draft"))
                        )
                    ),
                    entities = emptyList(),
                ),
                basePackage = "com.acme.demo",
                typeRegistry = mapOf("Status" to TypeRegistryEntry("com.acme.shared.Status")),
            )
        }

        assertEquals(
            "ambiguous type binding for Status: matches both shared enum and general type registry",
            error.message
        )
    }

    @Test
    fun `shared and local enum with the same type binding fails fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEnumPlanning.from(
                CanonicalModel(
                    sharedEnums = listOf(
                        SharedEnumDefinition(
                            typeName = "Status",
                            packageName = "shared",
                            generateTranslation = true,
                            items = listOf(EnumItemModel(0, "DRAFT", "Draft"))
                        )
                    ),
                    entities = listOf(
                        EntityModel(
                            name = "VideoPost",
                            packageName = "com.acme.demo.domain.aggregates.video_post",
                            tableName = "video_post",
                            comment = "",
                            fields = listOf(
                                FieldModel(
                                    name = "status",
                                    type = "Int",
                                    typeBinding = "Status",
                                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
                                )
                            ),
                            idField = FieldModel(name = "id", type = "Long"),
                        )
                    ),
                ),
                basePackage = "com.acme.demo",
                typeRegistry = emptyMap(),
            )
        }

        assertEquals(
            "ambiguous enum ownership for Status: matches both shared enum and local enum in com.acme.demo.domain.aggregates.video_post",
            error.message
        )
    }

    @Test
    fun `local enum and general registry with the same type binding fails fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEnumPlanning.from(
                CanonicalModel(
                    entities = listOf(
                        EntityModel(
                            name = "UserLoginLog",
                            packageName = "com.acme.demo.domain.aggregates.user_login_log",
                            tableName = "user_login_log",
                            comment = "",
                            fields = listOf(
                                FieldModel(
                                    name = "userType",
                                    type = "Int",
                                    typeBinding = "UserType",
                                    enumItems = listOf(EnumItemModel(0, "UNKNOWN", "Unknown"))
                                )
                            ),
                            idField = FieldModel(name = "id", type = "Long"),
                        )
                    )
                ),
                basePackage = "com.acme.demo",
                typeRegistry = mapOf(
                    "UserType" to TypeRegistryEntry("com.acme.demo.domain.aggregates.user.enums.UserType")
                ),
            )
        }

        assertEquals(
            "ambiguous enum ownership for UserType: matches both local enum in com.acme.demo.domain.aggregates.user_login_log and general type registry",
            error.message
        )
    }

    @Test
    fun `unresolved short type binding fails fast`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "",
            fields = listOf(
                FieldModel(name = "payload", type = "String", typeBinding = "SubmitPayload")
            ),
            idField = FieldModel(name = "id", type = "Long"),
        )
        val planning = AggregateEnumPlanning.from(
            CanonicalModel(entities = listOf(entity)),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            planning.resolveFieldType(entity.packageName, entity.fields.single())
        }

        assertEquals(
            "unresolved type binding for SubmitPayload: expected enum manifest, type registry, FQN, or built-in type",
            error.message
        )
    }

    @Test
    fun `same local enum type name is allowed across different aggregate owners`() {
        val videoPost = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "",
            fields = listOf(
                FieldModel(
                    name = "status",
                    type = "Int",
                    typeBinding = "Visibility",
                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
                )
            ),
            idField = FieldModel(name = "id", type = "Long"),
        )
        val articlePost = EntityModel(
            name = "ArticlePost",
            packageName = "com.acme.demo.domain.aggregates.article_post",
            tableName = "article_post",
            comment = "",
            fields = listOf(
                FieldModel(
                    name = "status",
                    type = "Int",
                    typeBinding = "Visibility",
                    enumItems = listOf(EnumItemModel(1, "PUBLIC", "Public"))
                )
            ),
            idField = FieldModel(name = "id", type = "Long"),
        )

        val planning = AggregateEnumPlanning.from(
            CanonicalModel(entities = listOf(videoPost, articlePost)),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.enums.Visibility",
            planning.resolveFieldType(
                videoPost.packageName,
                "Visibility",
                videoPost.fields.single().enumItems,
            )
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.article_post.enums.Visibility",
            planning.resolveFieldType(
                articlePost.packageName,
                "Visibility",
                articlePost.fields.single().enumItems,
            )
        )
    }

    @Test
    fun `conflicting local enum definitions under the same aggregate owner fail fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEnumPlanning.from(
                CanonicalModel(
                    entities = listOf(
                        EntityModel(
                            name = "VideoPost",
                            packageName = "com.acme.demo.domain.aggregates.video_post",
                            tableName = "video_post",
                            comment = "",
                            fields = listOf(
                                FieldModel(
                                    name = "visibility",
                                    type = "Int",
                                    typeBinding = "Visibility",
                                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
                                ),
                                FieldModel(
                                    name = "secondaryVisibility",
                                    type = "Int",
                                    typeBinding = "Visibility",
                                    enumItems = listOf(EnumItemModel(1, "PUBLIC", "Public"))
                                ),
                            ),
                            idField = FieldModel(name = "id", type = "Long"),
                        )
                    )
                ),
                basePackage = "com.acme.demo",
                typeRegistry = emptyMap(),
            )
        }

        assertEquals(
            "conflicting local enum definition for com.acme.demo.domain.aggregates.video_post.enums.Visibility",
            error.message
        )
    }
}

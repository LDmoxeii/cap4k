package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanonicalEnumCatalogTest {

    @Test
    fun `resolves shared and local enum descriptors`() {
        val sharedItems = listOf(EnumItemModel(0, "DRAFT", "Draft"), EnumItemModel(1, "PUBLISHED", "Published"))
        val localItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"), EnumItemModel(1, "PUBLIC", "Public"))
        val entity = videoPostEntity(
            fields = listOf(FieldModel("visibility", "Int", typeBinding = "Visibility", enumItems = localItems))
        )

        val catalog = CanonicalEnumCatalog.from(
            CanonicalModel(
                sharedEnums = listOf(sharedEnum("Status", "shared", sharedItems)),
                entities = listOf(entity),
            ),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals(
            CanonicalEnumDescriptor(
                ownerPackageName = null,
                ownerScope = null,
                typeName = "Status",
                fqn = "com.acme.demo.domain.shared.enums.Status",
                items = sharedItems,
                shared = true,
            ),
            catalog.sharedEnums.single(),
        )
        assertEquals(
            CanonicalEnumDescriptor(
                ownerPackageName = entity.packageName,
                ownerScope = "video_post",
                typeName = "Visibility",
                fqn = "com.acme.demo.domain.aggregates.video_post.enums.Visibility",
                items = localItems,
                shared = false,
            ),
            catalog.localEnums.single(),
        )
        assertEquals(catalog.sharedEnums + catalog.localEnums, catalog.allEnums)
        assertNull(catalog.sharedEnums.single().ownerPackageName)
        assertNull(catalog.sharedEnums.single().ownerScope)
        assertTrue(catalog.sharedEnums.single().shared)
        assertEquals("video_post", catalog.localEnums.single().ownerScope)
        assertFalse(catalog.localEnums.single().shared)
    }

    @Test
    fun `resolves shared enum field type from type binding`() {
        val enumItems = listOf(EnumItemModel(0, "DRAFT", "Draft"))
        val field = FieldModel("status", "Int", typeBinding = "Status")
        val catalog = CanonicalEnumCatalog.from(
            CanonicalModel(sharedEnums = listOf(sharedEnum("Status", "shared", enumItems))),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals("com.acme.demo.domain.shared.enums.Status", catalog.resolveFieldType(field))
        assertEquals(enumItems, catalog.resolveEnumItems(null, field))
    }

    @Test
    fun `resolves local enum field type using owner package name`() {
        val enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
        val field = FieldModel("visibility", "Int", typeBinding = "Visibility", enumItems = enumItems)
        val entity = videoPostEntity(fields = listOf(field))
        val catalog = CanonicalEnumCatalog.from(
            CanonicalModel(entities = listOf(entity)),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.enums.Visibility",
            catalog.resolveFieldType(entity.packageName, field),
        )
        assertEquals(enumItems, catalog.resolveEnumItems(entity.packageName, field))
    }

    @Test
    fun `resolves aggregate local value object for root and child sharing owner package`() {
        val field = FieldModel("publishWindow", "String", typeBinding = "PublishWindow")
        val root = EntityModel(
            name = "Content",
            packageName = "com.acme.demo.domain.aggregates.content",
            tableName = "content",
            comment = "",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
            aggregateRoot = true,
        )
        val child = EntityModel(
            name = "ContentSchedule",
            packageName = "com.acme.demo.domain.aggregates.content",
            tableName = "content_schedule",
            comment = "",
            fields = listOf(field),
            idField = FieldModel("id", "Long"),
            aggregateRoot = false,
            parentEntityName = "Content",
        )
        val catalog = CanonicalEnumCatalog.from(
            CanonicalModel(
                entities = listOf(root, child),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "PublishWindow",
                        packageName = "com.acme.demo.domain.aggregates.content.values",
                        scope = ValueObjectScope.AGGREGATE,
                        aggregate = "Content",
                    )
                ),
            ),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals(
            "com.acme.demo.domain.aggregates.content.values.PublishWindow",
            catalog.resolveFieldType(child.packageName, field),
        )
    }

    @Test
    fun `returns field enum items directly when present`() {
        val directItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"))
        val field = FieldModel("visibility", "Int", enumItems = directItems)
        val catalog = CanonicalEnumCatalog.from(
            CanonicalModel(
                sharedEnums = listOf(sharedEnum("Visibility", "shared", listOf(EnumItemModel(1, "PUBLIC", "Public"))))
            ),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        assertEquals(directItems, catalog.resolveEnumItems("com.acme.demo.domain.aggregates.video_post", field))
    }

    @Test
    fun `rejects ambiguous shared enum versus type registry`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            CanonicalEnumCatalog.from(
                CanonicalModel(
                    sharedEnums = listOf(sharedEnum("Status", "shared", listOf(EnumItemModel(0, "DRAFT", "Draft"))))
                ),
                basePackage = "com.acme.demo",
                typeRegistry = mapOf("Status" to TypeRegistryEntry("com.acme.shared.Status")),
            )
        }

        assertEquals(
            "ambiguous type binding for Status: matches both shared enum and general type registry",
            error.message,
        )
    }

    @Test
    fun `rejects local enum ownership conflicts`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            CanonicalEnumCatalog.from(
                CanonicalModel(
                    entities = listOf(
                        videoPostEntity(
                            fields = listOf(
                                FieldModel(
                                    name = "visibility",
                                    type = "Int",
                                    typeBinding = "Visibility",
                                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                                ),
                                FieldModel(
                                    name = "secondaryVisibility",
                                    type = "Int",
                                    typeBinding = "Visibility",
                                    enumItems = listOf(EnumItemModel(1, "PUBLIC", "Public")),
                                ),
                            )
                        )
                    )
                ),
                basePackage = "com.acme.demo",
                typeRegistry = emptyMap(),
            )
        }

        assertEquals(
            "conflicting local enum definition for com.acme.demo.domain.aggregates.video_post.enums.Visibility",
            error.message,
        )
    }

    @Test
    fun `rejects unresolved non built in type binding`() {
        val field = FieldModel("payload", "String", typeBinding = "SubmitPayload")
        val catalog = CanonicalEnumCatalog.from(
            CanonicalModel(entities = listOf(videoPostEntity(fields = listOf(field)))),
            basePackage = "com.acme.demo",
            typeRegistry = emptyMap(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            catalog.resolveFieldType("com.acme.demo.domain.aggregates.video_post", field)
        }

        assertEquals(
            "unresolved type binding for SubmitPayload: expected enum manifest, type registry, FQN, or built-in type",
            error.message,
        )
    }

    private fun sharedEnum(
        typeName: String,
        packageName: String,
        items: List<EnumItemModel>,
    ): SharedEnumDefinition =
        SharedEnumDefinition(
            typeName = typeName,
            packageName = packageName,
            items = items,
        )

    private fun videoPostEntity(fields: List<FieldModel>): EntityModel =
        EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "",
            fields = fields,
            idField = FieldModel("id", "Long"),
        )
}

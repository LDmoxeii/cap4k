package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregateEnumPlanningTest {

    @Test
    fun `type binding prefers local enum definition over shared enum lookup only when E and T coexist`() {
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
                entities = listOf(
                    EntityModel(
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
                )
            ),
            typeRegistry = emptyMap(),
        )

        assertEquals("com.acme.demo.domain.shared.enums.Status", planning.resolveFieldType("Status", emptyList()))
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.enums.VideoPostVisibility",
            planning.resolveFieldType(
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
                typeRegistry = mapOf("Status" to "com.acme.shared.Status"),
            )
        }

        assertEquals(
            "ambiguous type binding for Status: matches both shared enum and general type registry",
            error.message
        )
    }
}

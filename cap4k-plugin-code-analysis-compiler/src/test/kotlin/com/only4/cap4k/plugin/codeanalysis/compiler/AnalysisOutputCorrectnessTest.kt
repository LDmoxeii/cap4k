package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AnalysisOutputCorrectnessTest {
    @Test
    fun `command handler calling top level aggregate behavior extension emits exact entity method edges`() {
        val rels = compileRelationships(
            categorySources(
                useTopLevelBehavior = true,
                behaviorBody = """
                    fun Category.changeSort(sort: Int) {
                        CategorySortChanged(sort)
                    }
                """.trimIndent()
            )
        )

        assertMethodEdgeShape(
            rels = rels,
            handlerId = "demo.application.commands.category.UpdateCategorySortCmd.Handler",
            aggregateId = "demo.domain.aggregates.category.Category",
            methodId = "demo.domain.aggregates.category.Category::changeSort",
            eventId = "demo.domain.aggregates.category.events.CategorySortChanged",
            wrongMethodIds = setOf("changeSort", "demo.domain.aggregates.category.CategoryBehaviorKt::changeSort")
        )
    }

    @Test
    fun `command handler calling aggregate member method keeps exact entity method edges`() {
        val rels = compileRelationships(
            categorySources(
                categoryBody = """
                    @Aggregate(aggregate = "Category", type = "entity", root = true)
                    class Category {
                        fun changeSort(sort: Int) {
                            CategorySortChanged(sort)
                        }
                    }
                """.trimIndent(),
                useTopLevelBehavior = false,
                behaviorBody = ""
            )
        )

        assertMethodEdgeShape(
            rels = rels,
            handlerId = "demo.application.commands.category.UpdateCategorySortCmd.Handler",
            aggregateId = "demo.domain.aggregates.category.Category",
            methodId = "demo.domain.aggregates.category.Category::changeSort",
            eventId = "demo.domain.aggregates.category.events.CategorySortChanged",
            wrongMethodIds = setOf("changeSort", "demo.domain.aggregates.category.CategoryBehaviorKt::changeSort")
        )
    }

    private fun compileRelationships(sources: List<SourceFile>): List<RelationshipView> {
        val outputDir = compileWithCap4kPlugin(sources)
        return readRelationships(outputDir)
    }

    private fun readRelationships(outputDir: Path): List<RelationshipView> {
        val json = outputDir.resolve("rels.json").toFile().readText()
        if (json == "[]") return emptyList()

        val objectPattern = Regex("""\{[^}]+\}""")
        return objectPattern.findAll(json).map { match ->
            val obj = match.value
            RelationshipView(
                fromId = extractJsonField(obj, "fromId"),
                toId = extractJsonField(obj, "toId"),
                type = extractJsonField(obj, "type")
            )
        }.toList()
    }

    private fun extractJsonField(jsonObject: String, field: String): String {
        val pattern = Regex(""""$field":"((?:\\\\|\\\"|[^\"])*)"""")
        val raw = pattern.find(jsonObject)?.groupValues?.get(1)
            ?: error("Missing field '$field' in $jsonObject")
        return raw
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun assertMethodEdgeShape(
        rels: List<RelationshipView>,
        handlerId: String,
        aggregateId: String,
        methodId: String,
        eventId: String,
        wrongMethodIds: Set<String>,
    ) {
        val expected = setOf(
            RelationshipView(handlerId, methodId, "CommandHandlerToEntityMethod"),
            RelationshipView(aggregateId, methodId, "AggregateToEntityMethod"),
            RelationshipView(methodId, eventId, "EntityMethodToDomainEvent")
        )

        val relevant = rels.filter {
            it.toId == methodId ||
                it.fromId == methodId ||
                it.fromId in wrongMethodIds ||
                it.toId in wrongMethodIds
        }.toSet()

        assertEquals(expected, relevant, "Unexpected relevant relationships: $relevant")
        wrongMethodIds.forEach { wrongId ->
            assertEquals(
                0,
                rels.count { it.fromId == wrongId || it.toId == wrongId },
                "Wrong method id leaked into graph: $wrongId"
            )
        }
        assertEquals(
            1,
            rels.count { it.fromId == handlerId && it.toId == methodId && it.type == "CommandHandlerToEntityMethod" }
        )
        assertEquals(
            1,
            rels.count { it.fromId == aggregateId && it.toId == methodId && it.type == "AggregateToEntityMethod" }
        )
        assertEquals(
            1,
            rels.count { it.fromId == methodId && it.toId == eventId && it.type == "EntityMethodToDomainEvent" }
        )
    }

    private fun categorySources(
        categoryBody: String = DEFAULT_CATEGORY_BODY,
        useTopLevelBehavior: Boolean,
        behaviorBody: String,
    ): List<SourceFile> {
        val behaviorImport = if (useTopLevelBehavior) {
            "import demo.domain.aggregates.category.changeSort"
        } else {
            ""
        }
        val sources = mutableListOf(
            SourceFile.kotlin(
                "RequestParam.kt",
                """
                    package com.only4.cap4k.ddd.core.application

                    interface RequestParam<RESULT : Any>
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "RequestHandler.kt",
                """
                    package com.only4.cap4k.ddd.core.application

                    interface RequestHandler<REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> {
                        fun exec(request: REQUEST): RESPONSE
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Command.kt",
                """
                    package com.only4.cap4k.ddd.core.application.command

                    import com.only4.cap4k.ddd.core.application.RequestHandler
                    import com.only4.cap4k.ddd.core.application.RequestParam

                    interface Command<PARAM : RequestParam<RESULT>, RESULT : Any> : RequestHandler<PARAM, RESULT> {
                        override fun exec(request: PARAM): RESULT
                    }
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Aggregate.kt",
                """
                    package com.only4.cap4k.ddd.core.domain.aggregate.annotation

                    annotation class Aggregate(
                        val aggregate: String = "",
                        val type: String = "",
                        val root: Boolean = false
                    )
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "DomainEvent.kt",
                """
                    package com.only4.cap4k.ddd.core.domain.event.annotation

                    annotation class DomainEvent(val value: String = "", val persist: Boolean = false)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "Category.kt",
                """
                    package demo.domain.aggregates.category

                    import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
                    import demo.domain.aggregates.category.events.CategorySortChanged

                    $categoryBody
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "CategorySortChanged.kt",
                """
                    package demo.domain.aggregates.category.events

                    import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

                    @DomainEvent
                    data class CategorySortChanged(val sort: Int)
                """.trimIndent()
            ),
            SourceFile.kotlin(
                "UpdateCategorySortCmd.kt",
                """
                    package demo.application.commands.category

                    import com.only4.cap4k.ddd.core.application.RequestParam
                    import com.only4.cap4k.ddd.core.application.command.Command
                    import demo.domain.aggregates.category.Category
                    $behaviorImport

                    class UpdateCategorySortCmd(val sort: Int) : RequestParam<UpdateCategorySortCmd.Response> {
                        class Response

                        class Handler : Command<UpdateCategorySortCmd, Response> {
                            override fun exec(request: UpdateCategorySortCmd): Response {
                                val category = Category()
                                category.changeSort(request.sort)
                                return Response()
                            }
                        }
                    }
                """.trimIndent()
            )
        )

        if (behaviorBody.isNotBlank()) {
            sources += SourceFile.kotlin(
                "CategoryBehavior.kt",
                """
                    package demo.domain.aggregates.category

                    import demo.domain.aggregates.category.events.CategorySortChanged

                    $behaviorBody
                """.trimIndent()
            )
        }

        return sources
    }

    private data class RelationshipView(
        val fromId: String,
        val toId: String,
        val type: String,
    )

    companion object {
        private const val DEFAULT_CATEGORY_BODY = """
            @Aggregate(aggregate = "Category", type = "entity", root = true)
            class Category
        """
    }
}

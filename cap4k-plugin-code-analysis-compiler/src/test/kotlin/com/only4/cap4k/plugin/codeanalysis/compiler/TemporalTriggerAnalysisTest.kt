package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.codeanalysis.core.model.NodeType
import com.only4.cap4k.plugin.codeanalysis.core.model.RelationshipType
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemporalTriggerAnalysisTest {
    @Test
    fun `production graph contracts expose only the temporal trigger entry model`() {
        val nodeTypes = NodeType.entries.map { it.name }.toSet()
        val relationshipTypes = RelationshipType.entries.map { it.name }.toSet()

        assertTrue("temporaltriggermethod" in nodeTypes)
        assertFalse("commandsendermethod" in nodeTypes)
        assertTrue("TemporalTriggerMethodToCommand" in relationshipTypes)
        assertFalse("CommandSenderMethodToCommand" in relationshipTypes)
    }

    @Test
    fun `scheduled method sending command emits temporal trigger evidence`() {
        val outputDir = compileWithCap4kPlugin(
            applicationContractSources(
                """
                package demo

                import com.only4.cap4k.ddd.core.application.command.Command
                import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
                import org.springframework.scheduling.annotation.Scheduled

                class RefreshCatalogCmd : Command<Unit>

                class CatalogSchedule(private val commands: CommandSupervisor) {
                    @Scheduled
                    fun refresh() {
                        commands.send(RefreshCatalogCmd())
                    }
                }
                """.trimIndent()
            )
        )

        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertTrue(nodes.contains("\"id\":\"demo.CatalogSchedule::refresh\""), nodes)
        assertTrue(nodes.contains("\"type\":\"temporaltriggermethod\""), nodes)
        assertTrue(
            rels.contains(
                "{\"fromId\":\"demo.CatalogSchedule::refresh\",\"toId\":\"demo.RefreshCatalogCmd\",\"type\":\"TemporalTriggerMethodToCommand\"}"
            ),
            rels,
        )
    }

    @Test
    fun `scheduled query and capability calls do not emit temporal command evidence`() {
        val outputDir = compileWithCap4kPlugin(
            applicationContractSources(
                """
                package demo

                import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
                import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
                import com.only4.cap4k.ddd.core.application.query.Query
                import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
                import org.springframework.scheduling.annotation.Scheduled

                class ReadCatalogQuery : Query<Unit>
                class RefreshSearchCapability : CapabilityCall<Unit>

                class CatalogChecks(
                    private val queries: QuerySupervisor,
                    private val capabilities: CapabilitySupervisor,
                ) {
                    @Scheduled
                    fun inspect() {
                        queries.ask(ReadCatalogQuery())
                        capabilities.call(RefreshSearchCapability())
                    }
                }
                """.trimIndent()
            )
        )

        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertTrue(nodes.contains("\"id\":\"demo.CatalogChecks::inspect\""), nodes)
        assertTrue(nodes.contains("\"type\":\"temporaltriggermethod\""), nodes)
        assertFalse(rels.contains("TemporalTriggerMethodToCommand"), rels)
    }

    @Test
    fun `ordinary method sending command emits no generic sender evidence`() {
        val outputDir = compileWithCap4kPlugin(
            applicationContractSources(
                """
                package demo

                import com.only4.cap4k.ddd.core.application.command.Command
                import com.only4.cap4k.ddd.core.application.command.CommandSupervisor

                class RefreshCatalogCmd : Command<Unit>

                class InternalHelper(private val commands: CommandSupervisor) {
                    fun refresh() {
                        commands.send(RefreshCatalogCmd())
                    }
                }
                """.trimIndent()
            )
        )

        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertFalse(nodes.contains("demo.InternalHelper::refresh"), nodes)
        assertFalse(nodes.contains("commandsendermethod"), nodes)
        assertFalse(rels.contains("CommandSenderMethodToCommand"), rels)
        assertFalse(rels.contains("demo.InternalHelper::refresh"), rels)
    }

    private fun applicationContractSources(applicationSource: String): List<SourceFile> = listOf(
        SourceFile.kotlin(
            "Scheduled.kt",
            """
            package org.springframework.scheduling.annotation

            @Target(AnnotationTarget.FUNCTION)
            @Retention(AnnotationRetention.RUNTIME)
            annotation class Scheduled
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Command.kt",
            """
            package com.only4.cap4k.ddd.core.application.command

            interface Command<R : Any>
            interface CommandSupervisor {
                fun <R : Any> send(command: Command<R>): R
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Query.kt",
            """
            package com.only4.cap4k.ddd.core.application.query

            interface Query<R : Any>
            interface QuerySupervisor {
                fun <R : Any> ask(query: Query<R>): R
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Capability.kt",
            """
            package com.only4.cap4k.ddd.core.application.capability

            interface CapabilityCall<R : Any>
            interface CapabilitySupervisor {
                fun <R : Any> call(capability: CapabilityCall<R>): R
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin("Application.kt", applicationSource),
    )
}

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
        assertTrue("endpointhttpbinding" in nodeTypes)
        assertFalse("apipayload" in nodeTypes)
        assertTrue("EndpointHttpBindingToCommand" in relationshipTypes)
        assertTrue("EndpointHttpBindingToQuery" in relationshipTypes)
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

    @Test
    fun `typed endpoint mvc binding associates cross file handler command evidence`() {
        val outputDir = compileWithCap4kPlugin(endpointHttpSources())
        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertTrue(nodes.contains("\"id\":\"endpoint-http:booking.create\""), nodes)
        assertTrue(nodes.contains("\"type\":\"endpointhttpbinding\""), nodes)
        assertTrue(nodes.contains("booking.create [POST /api/bookings]"), nodes)
        assertTrue(rels.contains("\"type\":\"EndpointHttpBindingToCommand\""), rels)
        assertTrue(rels.contains("\"toId\":\"demo.CreateBookingCmd\""), rels)
        assertFalse(nodes.contains("apipayload"), nodes)
    }

    @Test
    fun `endpoint handler association follows handle helpers and ignores unreachable methods`() {
        val outputDir = compileWithCap4kPlugin(
            endpointHttpSources(
                handleExpression = "dispatch(request)",
                extraHandlerMembers = """
                    private fun dispatch(request: CreateBookingEndpoint.Request) =
                        commands.send(CreateBookingCmd(request.id))

                    private fun unusedMaintenance(request: CreateBookingEndpoint.Request) =
                        commands.send(UnusedBookingCmd(request.id))
                """.trimIndent(),
            )
        )
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertTrue(
            rels.contains(
                "{\"fromId\":\"endpoint-http:booking.create\",\"toId\":\"demo.CreateBookingCmd\",\"type\":\"EndpointHttpBindingToCommand\"}"
            ),
            rels,
        )
        assertFalse(
            rels.contains(
                "{\"fromId\":\"endpoint-http:booking.create\",\"toId\":\"demo.UnusedBookingCmd\",\"type\":\"EndpointHttpBindingToCommand\"}"
            ),
            rels,
        )
    }

    @Test
    fun `copied endpoint operation name literal does not establish binding provenance`() {
        val outputDir = compileWithCap4kPlugin(
            endpointHttpSources(operationNameExpression = "\"booking.create\"")
        )
        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertFalse(nodes.contains("\"id\":\"endpoint-http:booking.create\""), nodes)
        assertFalse(rels.contains("EndpointHttpBindingToCommand"), rels)
    }

    private fun endpointHttpSources(
        operationNameExpression: String = "CreateBookingEndpoint.OPERATION_NAME",
        handleExpression: String = "commands.send(CreateBookingCmd(request.id))",
        extraHandlerMembers: String = "",
    ): List<SourceFile> = listOf(
        SourceFile.kotlin("Metadata.kt", """
            package com.only4.cap4k.analysis.metadata
            @Target(AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.BINARY)
            annotation class DesignBlockMetadata(
                val tag: String, val name: String, val packageName: String,
                val operationName: String, val family: String,
            )
        """.trimIndent()),
        SourceFile.kotlin("EndpointContract.kt", """
            package com.only4.cap4k.contract
            interface EndpointRequest<R : Any>
        """.trimIndent()),
        SourceFile.kotlin("EndpointHandler.kt", """
            package com.only4.cap4k.ddd.core.application.endpoint
            import com.only4.cap4k.contract.EndpointRequest
            fun interface EndpointHandler<REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> {
                fun handle(request: REQUEST): RESPONSE
            }
        """.trimIndent()),
        SourceFile.kotlin("Command.kt", """
            package com.only4.cap4k.ddd.core.application.command
            interface Command<R : Any>
            interface CommandSupervisor { fun <R : Any> send(command: Command<R>): R }
        """.trimIndent()),
        SourceFile.kotlin("Http.kt", """
            package org.springframework.http
            enum class HttpMethod { GET, POST }
        """.trimIndent()),
        SourceFile.kotlin("Binding.kt", """
            package com.only4.cap4k.ddd.endpoint.http
            import com.only4.cap4k.contract.EndpointRequest
            import org.springframework.http.HttpMethod
            import kotlin.reflect.KClass
            class EndpointMvcBinding<RQ : EndpointRequest<RS>, RS : Any> private constructor() {
                companion object {
                    fun <RQ : EndpointRequest<RS>, RS : Any> json(
                        operationName: String, requestType: KClass<RQ>, responseType: KClass<RS>,
                        method: HttpMethod, path: String,
                    ): EndpointMvcBinding<RQ, RS> = EndpointMvcBinding()
                }
            }
        """.trimIndent()),
        SourceFile.kotlin("Contract.kt", """
            package demo
            import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
            import com.only4.cap4k.contract.EndpointRequest
            @DesignBlockMetadata(tag = "endpoint", name = "CreateBooking", packageName = "booking", operationName = "booking.create", family = "endpoint")
            object CreateBookingEndpoint {
                const val OPERATION_NAME = "booking.create"
                data class Request(val id: Long) : EndpointRequest<Response>
                data class Response(val id: Long)
            }
            class CreateBookingCmd(val id: Long) : com.only4.cap4k.ddd.core.application.command.Command<CreateBookingEndpoint.Response>
            class UnusedBookingCmd(val id: Long) : com.only4.cap4k.ddd.core.application.command.Command<CreateBookingEndpoint.Response>
        """.trimIndent()),
        SourceFile.kotlin("Handler.kt", """
            package demo
            import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
            import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
            class BookingHandler(private val commands: CommandSupervisor) : EndpointHandler<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> {
                override fun handle(request: CreateBookingEndpoint.Request) = $handleExpression

                $extraHandlerMembers
            }
        """.trimIndent()),
        SourceFile.kotlin("Registration.kt", """
            package demo
            import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
            import org.springframework.http.HttpMethod
            fun binding() = EndpointMvcBinding.json(
                operationName = $operationNameExpression,
                requestType = CreateBookingEndpoint.Request::class,
                responseType = CreateBookingEndpoint.Response::class,
                method = HttpMethod.POST,
                path = "/api/bookings",
            )
        """.trimIndent()),
    )

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

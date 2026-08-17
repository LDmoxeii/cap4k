package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EndpointRpcAnalysisTest {
    @Test
    fun `real Provider Bean registration creates RPC Actor evidence`() {
        val outputDir = compileWithCap4kPlugin(endpointRpcSources())
        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertTrue(nodes.contains("\"id\":\"endpoint-rpc:booking-service:booking.create\""), nodes)
        assertTrue(nodes.contains("\"type\":\"endpointrpcproviderbinding\""), nodes)
        assertTrue(
            rels.contains(
                "{\"fromId\":\"endpoint-rpc:booking-service:booking.create\",\"toId\":\"demo.CreateBookingCmd\",\"type\":\"EndpointRpcProviderBindingToCommand\"}",
            ),
            rels,
        )
    }

    @Test
    fun `descriptor without Spring production registration creates no RPC Actor evidence`() {
        val outputDir = compileWithCap4kPlugin(endpointRpcSources(beanAnnotation = ""))
        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertFalse(nodes.contains("endpoint-rpc:booking-service:booking.create"), nodes)
        assertFalse(rels.contains("EndpointRpcProviderBindingToCommand"), rels)
    }

    @Test
    fun `copied operation literal in Provider Bean creates no RPC Actor evidence`() {
        val outputDir = compileWithCap4kPlugin(
            endpointRpcSources(operationNameExpression = "\"booking.create\""),
        )
        val nodes = outputDir.resolve("nodes.json").toFile().readText()
        val rels = outputDir.resolve("rels.json").toFile().readText()

        assertFalse(nodes.contains("endpoint-rpc:booking-service:booking.create"), nodes)
        assertFalse(rels.contains("EndpointRpcProviderBindingToCommand"), rels)
    }

    private fun endpointRpcSources(
        beanAnnotation: String = "@Bean",
        operationNameExpression: String = "CreateBookingEndpoint.OPERATION_NAME",
    ): List<SourceFile> = listOf(
        SourceFile.kotlin(
            "Metadata.kt",
            """
            package com.only4.cap4k.analysis.metadata
            @Target(AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.BINARY)
            annotation class DesignBlockMetadata(
                val tag: String, val name: String, val packageName: String,
                val operationName: String, val family: String,
            )
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "SpringBean.kt",
            """
            package org.springframework.context.annotation
            @Target(AnnotationTarget.FUNCTION)
            @Retention(AnnotationRetention.RUNTIME)
            annotation class Bean
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "EndpointContract.kt",
            """
            package com.only4.cap4k.contract
            interface EndpointRequest<R : Any>
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "EndpointHandler.kt",
            """
            package com.only4.cap4k.ddd.core.application.endpoint
            import com.only4.cap4k.contract.EndpointRequest
            fun interface EndpointHandler<REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> {
                fun handle(request: REQUEST): RESPONSE
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Command.kt",
            """
            package com.only4.cap4k.ddd.core.application.command
            interface Command<R : Any>
            interface CommandSupervisor { fun <R : Any> send(command: Command<R>): R }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "RpcBinding.kt",
            """
            package com.only4.cap4k.ddd.endpoint.rpc
            import com.only4.cap4k.contract.EndpointRequest
            import kotlin.reflect.KClass
            class EndpointRpcProviderBinding<REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any>(
                val serviceId: String,
                val operationName: String,
                val requestType: KClass<REQUEST>,
                val responseType: KClass<RESPONSE>,
            )
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Contract.kt",
            """
            package demo
            import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
            import com.only4.cap4k.contract.EndpointRequest
            @DesignBlockMetadata(
                tag = "endpoint",
                name = "CreateBooking",
                packageName = "booking",
                operationName = "booking.create",
                family = "endpoint",
            )
            object CreateBookingEndpoint {
                const val OPERATION_NAME = "booking.create"
                data class Request(val id: Long) : EndpointRequest<Response>
                data class Response(val id: Long)
            }
            class CreateBookingCmd(val id: Long) :
                com.only4.cap4k.ddd.core.application.command.Command<CreateBookingEndpoint.Response>
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Handler.kt",
            """
            package demo
            import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
            import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
            class BookingHandler(private val commands: CommandSupervisor) :
                EndpointHandler<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> {
                override fun handle(request: CreateBookingEndpoint.Request) =
                    commands.send(CreateBookingCmd(request.id))
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Registration.kt",
            """
            package demo
            import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderBinding
            import org.springframework.context.annotation.Bean
            class RpcBindings {
                $beanAnnotation
                fun createBookingProviderBinding(): EndpointRpcProviderBinding<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> =
                    EndpointRpcProviderBinding(
                        serviceId = "booking-service",
                        operationName = $operationNameExpression,
                        requestType = CreateBookingEndpoint.Request::class,
                        responseType = CreateBookingEndpoint.Response::class,
                    )
            }
            """.trimIndent(),
        ),
    )
}

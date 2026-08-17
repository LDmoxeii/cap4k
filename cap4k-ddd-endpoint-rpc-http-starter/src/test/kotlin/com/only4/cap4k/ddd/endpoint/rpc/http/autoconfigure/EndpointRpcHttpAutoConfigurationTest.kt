package com.only4.cap4k.ddd.endpoint.rpc.http.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.endpoint.rpc.ENDPOINT_RPC_PROTOCOL_VERSION
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderBinding
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderDispatcher
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderRegistry
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcRequestEnvelope
import com.only4.cap4k.ddd.endpoint.rpc.EndpointTransportInvoker
import com.only4.cap4k.ddd.endpoint.rpc.http.JacksonEndpointRpcCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.function.Supplier

class EndpointRpcHttpAutoConfigurationTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `fixed provider handler is POST JSON only and keeps RPC failures in a 200 envelope`() {
        val handler = handler()

        val methodResponse = MockHttpServletResponse()
        handler.handleRequest(MockHttpServletRequest().apply { method = HttpMethod.GET.name() }, methodResponse)
        assertEquals(405, methodResponse.status)
        assertEquals(HttpMethod.POST.name(), methodResponse.getHeader("Allow"))

        val mediaResponse = MockHttpServletResponse()
        handler.handleRequest(
            request("{}", MediaType.TEXT_PLAIN_VALUE),
            mediaResponse,
        )
        assertEquals(415, mediaResponse.status)
        assertTrue(mediaResponse.contentAsString.contains("unsupported_media_type"))

        val malformedResponse = MockHttpServletResponse()
        handler.handleRequest(request("not-json"), malformedResponse)
        assertEquals(400, malformedResponse.status)
        assertTrue(malformedResponse.contentAsString.contains("malformed_envelope"))

        val unknownServiceResponse = MockHttpServletResponse()
        handler.handleRequest(
            request(
                mapper.writeValueAsString(
                    EndpointRpcRequestEnvelope(
                        ENDPOINT_RPC_PROTOCOL_VERSION,
                        "json",
                        "other",
                        TestOperation.OPERATION_NAME,
                        mapper.writeValueAsString(TestOperation.Request("Ada")),
                    ),
                ),
            ),
            unknownServiceResponse,
        )
        assertEquals(200, unknownServiceResponse.status)
        assertTrue(unknownServiceResponse.contentAsString.contains("UNKNOWN_SERVICE"))
        assertTrue(unknownServiceResponse.contentAsString.contains("unknown_service"))
    }

    @Test
    fun `consumer only assembly creates invoker without Provider ingress`() {
        contextRunner().run { context ->
            assertEquals(null, context.startupFailure)
            assertEquals(1, context.getBeansOfType(EndpointTransportInvoker::class.java).size)
            assertTrue(context.getBeansOfType(EndpointRpcProviderRegistry::class.java).isEmpty())
            assertTrue(context.getBeansOfType(EndpointRpcProviderDispatcher::class.java).isEmpty())
            assertFalse(context.containsBean(EndpointRpcHttpAutoConfiguration.RPC_PATH))
        }
    }

    @Test
    fun `Provider binding rejects blank configured service identity during startup`() {
        contextRunner()
            .withBean(
                EndpointRpcProviderBinding::class.java,
                Supplier {
                    EndpointRpcProviderBinding(
                        "catalog",
                        TestOperation.OPERATION_NAME,
                        TestOperation.Request::class,
                        TestOperation.Response::class,
                    )
                },
            )
            .run { context ->
                val failure = context.startupFailure
                assertNotNull(failure)
                val messages = generateSequence(failure) { it.cause }
                    .mapNotNull(Throwable::message)
                    .joinToString("\n")
                assertTrue(messages.contains("provider serviceId must not be blank"), messages)
            }
    }

    private fun contextRunner(): WebApplicationContextRunner {
        val contextManager = DefaultExecutionContextManager()
        return WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EndpointRpcHttpAutoConfiguration::class.java))
            .withBean(ObjectMapper::class.java, Supplier { mapper })
            .withBean(DefaultExecutionContextManager::class.java, Supplier { contextManager })
            .withBean(
                ExecutionContextCodecRegistry::class.java,
                Supplier { ExecutionContextCodecRegistry(emptyList()) },
            )
    }

    private fun handler(): org.springframework.web.HttpRequestHandler {
        val binding = EndpointRpcProviderBinding(
            "catalog",
            TestOperation.OPERATION_NAME,
            TestOperation.Request::class,
            TestOperation.Response::class,
        )
        val contextManager = DefaultExecutionContextManager()
        val codec = JacksonEndpointRpcCodec(mapper)
        val dispatcher = EndpointRpcProviderDispatcher(
            EndpointRpcProviderRegistry("catalog", listOf(binding)),
            codec,
            ExecutionContextCodecRegistry(emptyList()),
            contextManager,
        )
        return EndpointRpcHttpAutoConfiguration().endpointRpcHttpHandler(dispatcher, mapper, codec)
    }

    private fun request(
        body: String,
        contentType: String = MediaType.APPLICATION_JSON_VALUE,
    ) = MockHttpServletRequest().apply {
        method = HttpMethod.POST.name()
        this.contentType = contentType
        setContent(body.toByteArray())
    }

    object TestOperation {
        const val OPERATION_NAME = "catalog.get"
        data class Request(val name: String) : EndpointRequest<Response>
        data class Response(val value: String)
    }
}



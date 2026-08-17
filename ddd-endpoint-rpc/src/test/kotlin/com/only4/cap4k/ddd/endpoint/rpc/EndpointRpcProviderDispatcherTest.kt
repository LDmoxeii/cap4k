package com.only4.cap4k.ddd.endpoint.rpc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElementCodec
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.endpoint.EndpointSupervisor
import com.only4.cap4k.ddd.core.application.endpoint.EndpointSupervisorSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.reflect.KClass

class EndpointRpcProviderDispatcherTest {
    private val contextManager = DefaultExecutionContextManager()
    private val contextCodecs = ExecutionContextCodecRegistry(listOf(TenantCodec))
    private val supervisor = RecordingSupervisor(contextManager)

    @BeforeEach
    fun bindSupervisor() {
        EndpointSupervisorSupport.configure(supervisor)
    }

    @AfterEach
    fun releaseSupervisor() {
        EndpointSupervisorSupport.release(supervisor)
    }

    @Test
    fun `valid dispatch installs RPC context invokes Mediator and restores scope`() {
        supervisor.result = TestOperation.Response("Ada@tenant-a")
        val result = dispatcher().dispatch(
            requestEnvelope(
                context = listOf(EncodedExecutionContextElement("tenant", 1, "tenant-a")),
            ),
        )

        assertTrue(result.success)
        assertEquals(TestOperation.Request("Ada"), supervisor.request)
        assertEquals("tenant-a", supervisor.observedTenant)
        assertTrue(contextManager.current().isEmpty)
        assertEquals(TestOperation.Response("Ada@tenant-a"), TestCodec.decode(requireNotNull(result.payload), TestOperation.Response::class))
    }

    @Test
    fun `protocol service operation context and request failures do not invoke Mediator`() {
        val cases = listOf(
            requestEnvelope(protocolVersion = 99) to EndpointRpcFailureCategory.PROTOCOL,
            requestEnvelope(codec = "other") to EndpointRpcFailureCategory.PROTOCOL,
            requestEnvelope(serviceId = "other") to EndpointRpcFailureCategory.UNKNOWN_SERVICE,
            requestEnvelope(operationName = "missing") to EndpointRpcFailureCategory.UNKNOWN_OPERATION,
            requestEnvelope(
                context = listOf(
                    EncodedExecutionContextElement("tenant", 1, "a"),
                    EncodedExecutionContextElement("tenant", 1, "b"),
                ),
            ) to EndpointRpcFailureCategory.CONTEXT,
            requestEnvelope(payload = "not-json") to EndpointRpcFailureCategory.REQUEST_CODEC,
        )

        cases.forEach { (envelope, expected) ->
            supervisor.reset()
            val result = dispatcher().dispatch(envelope)
            assertEquals(false, result.success)
            assertEquals(expected, result.failure?.category)
            assertEquals(0, supervisor.sendCount)
            assertTrue(contextManager.current().isEmpty)
        }
    }

    @Test
    fun `provider exception and response codec failure remain sanitized`() {
        supervisor.failure = IllegalStateException("provider-secret")
        val providerFailure = dispatcher().dispatch(requestEnvelope())
        assertEquals(EndpointRpcFailureCategory.REMOTE, providerFailure.failure?.category)
        assertEquals("provider_invocation_failed", providerFailure.failure?.code)
        assertTrue(providerFailure.toString().contains("provider-secret").not())
        assertTrue(contextManager.current().isEmpty)

        supervisor.reset()
        supervisor.result = TestOperation.Response("secret-response")
        val encodeFailureCodec = object : EndpointRpcCodec by TestCodec {
            override fun <T : Any> encode(value: T, type: KClass<T>): String {
                if (type == TestOperation.Response::class) throw IllegalStateException("response-secret")
                return TestCodec.encode(value, type)
            }
        }
        val responseFailure = dispatcher(encodeFailureCodec).dispatch(requestEnvelope())
        assertEquals(EndpointRpcFailureCategory.RESPONSE_CODEC, responseFailure.failure?.category)
        assertEquals("response_encode_failed", responseFailure.failure?.code)
        assertTrue(responseFailure.toString().contains("response-secret").not())
        assertTrue(contextManager.current().isEmpty)
    }

    private fun dispatcher(codec: EndpointRpcCodec = TestCodec) = EndpointRpcProviderDispatcher(
        EndpointRpcProviderRegistry(
            "catalog",
            listOf(
                EndpointRpcProviderBinding(
                    "catalog",
                    TestOperation.OPERATION_NAME,
                    TestOperation.Request::class,
                    TestOperation.Response::class,
                ),
            ),
        ),
        codec,
        contextCodecs,
        contextManager,
    )

    private fun requestEnvelope(
        protocolVersion: Int = ENDPOINT_RPC_PROTOCOL_VERSION,
        codec: String = TestCodec.identity,
        serviceId: String = "catalog",
        operationName: String = TestOperation.OPERATION_NAME,
        payload: String = TestCodec.encode(TestOperation.Request("Ada"), TestOperation.Request::class),
        context: List<EncodedExecutionContextElement> = emptyList(),
    ) = EndpointRpcRequestEnvelope(protocolVersion, codec, serviceId, operationName, payload, context)

    object TestOperation {
        const val OPERATION_NAME = "catalog.get"
        data class Request(val name: String) : EndpointRequest<Response>
        data class Response(val value: String)
    }

    data class Tenant(val value: String) : ExecutionContextElement

    private object TenantCodec : ExecutionContextElementCodec<Tenant> {
        override val key = ExecutionContextKey("tenant", Tenant::class.java)
        override val version = 1
        override val boundaries = setOf(ExecutionContextBoundary.RPC)
        override fun encode(element: Tenant): String = element.value
        override fun decode(value: String): Tenant = Tenant(value)
    }

    private object TestCodec : EndpointRpcCodec {
        private val mapper = jacksonObjectMapper()
        override val identity: String = "json"
        override fun <T : Any> encode(value: T, type: KClass<T>): String = mapper.writeValueAsString(value)
        override fun <T : Any> decode(payload: String, type: KClass<T>): T = mapper.readValue(payload, type.java)
    }

    private class RecordingSupervisor(
        private val contextManager: DefaultExecutionContextManager,
    ) : EndpointSupervisor {
        var request: Any? = null
        var result: Any? = null
        var failure: RuntimeException? = null
        var sendCount: Int = 0
        var observedTenant: String? = null

        override fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE {
            sendCount += 1
            this.request = request
            observedTenant = contextManager.current()[TenantCodec.key]?.value
            failure?.let { throw it }
            @Suppress("UNCHECKED_CAST")
            return result as RESPONSE
        }

        override fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> sendAsync(
            request: REQUEST,
        ): CompletionStage<RESPONSE> = CompletableFuture.completedFuture(send(request))

        fun reset() {
            request = null
            result = null
            failure = null
            sendCount = 0
            observedTenant = null
        }
    }
}

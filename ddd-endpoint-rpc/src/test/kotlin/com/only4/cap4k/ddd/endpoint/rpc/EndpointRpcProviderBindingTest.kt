package com.only4.cap4k.ddd.endpoint.rpc

import com.only4.cap4k.contract.EndpointRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EndpointRpcProviderBindingTest {
    @Test
    fun `validates typed generated operation evidence uniqueness service and operation diagnostics`() {
        val binding = EndpointRpcProviderBinding(
            "catalog",
            TestOperation.OPERATION_NAME,
            TestOperation.Request::class,
            TestOperation.Response::class,
        )
        val registry = EndpointRpcProviderRegistry("catalog", listOf(binding))
        assertSame(binding, registry.resolve("catalog", TestOperation.OPERATION_NAME))
        assertThrows<IllegalArgumentException> { EndpointRpcProviderRegistry("other", listOf(binding)) }
        assertThrows<IllegalArgumentException> { EndpointRpcProviderRegistry("catalog", listOf(binding, binding)) }
        assertThrows<IllegalArgumentException> { EndpointRpcProviderRegistry("catalog", emptyList()) }

        val unknownService = assertThrows<EndpointRpcProtocolException> {
            registry.resolve("other", TestOperation.OPERATION_NAME)
        }
        assertEquals(EndpointRpcFailureCategory.UNKNOWN_SERVICE, unknownService.category)
        assertEquals("unknown_service", unknownService.code)

        val unknownOperation = assertThrows<EndpointRpcProtocolException> {
            registry.resolve("catalog", "catalog.missing")
        }
        assertEquals(EndpointRpcFailureCategory.UNKNOWN_OPERATION, unknownOperation.category)
        assertEquals("unknown_operation", unknownOperation.code)
    }

    @Test
    fun `response envelope rejects contradictory or Consumer local Provider failures`() {
        listOf(
            EndpointRpcResponseEnvelope(codec = "json", success = true),
            EndpointRpcResponseEnvelope(
                codec = "json",
                success = true,
                payload = "{}",
                failure = EndpointRpcFailure(EndpointRpcFailureCategory.REMOTE, "remote"),
            ),
            EndpointRpcResponseEnvelope(codec = "json", success = false),
            EndpointRpcResponseEnvelope(
                codec = "json",
                success = false,
                failure = EndpointRpcFailure(EndpointRpcFailureCategory.TIMEOUT, "forged"),
            ),
        ).forEach { envelope ->
            assertThrows<IllegalArgumentException> { EndpointRpcResponseEnvelopeValidator.validate(envelope) }
        }

        EndpointRpcResponseEnvelopeValidator.validate(
            EndpointRpcResponseEnvelope(codec = "json", success = true, payload = "{}"),
        )
        EndpointRpcResponseEnvelopeValidator.validate(
            EndpointRpcResponseEnvelope(
                codec = "json",
                success = false,
                failure = EndpointRpcFailure(EndpointRpcFailureCategory.REMOTE, "provider_invocation_failed"),
            ),
        )
    }

    @Test
    fun `remote exception is sanitized`() {
        val failure = EndpointRemoteInvocationException(
            EndpointRpcFailureCategory.REMOTE,
            "catalog",
            "catalog.get",
            502,
            "safe-id",
        )
        assertTrue(failure.message!!.contains("catalog.get"))
        assertFalse(failure.message!!.contains("payload"))
        assertEquals(null, failure.cause)
    }

    class TestOperation {
        companion object {
            @JvmField
            val OPERATION_NAME = "catalog.get"
        }

        data class Request(val id: String) : EndpointRequest<Response>
        data class Response(val name: String)
    }
}

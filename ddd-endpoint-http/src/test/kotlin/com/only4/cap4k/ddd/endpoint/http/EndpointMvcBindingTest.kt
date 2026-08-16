package com.only4.cap4k.ddd.endpoint.http

import com.only4.cap4k.contract.EndpointRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class EndpointMvcBindingTest {
    object SampleEndpoint {
        const val OPERATION_NAME = "sample.create"
        data class Request(val value: String) : EndpointRequest<Response>
        data class Response(val url: String)
        data class BrokenRequest(val value: String) : EndpointRequest<OtherEndpoint.Response>
    }
    object OtherEndpoint {
        const val OPERATION_NAME = "sample.other"
        data class Request(val value: String) : EndpointRequest<Response>
        data class Response(val value: String)
    }

    @Test
    fun `normalizes route and preserves typed registration`() {
        val binding = EndpointMvcBinding.json(
            SampleEndpoint.OPERATION_NAME, SampleEndpoint.Request::class, SampleEndpoint.Response::class,
            HttpMethod.POST, "//api//samples/",
        )
        assertEquals(HttpMethod.POST, binding.method)
        assertEquals("/api/samples", binding.path)
    }

    @Test
    fun `rejects operation name and response coherence defects`() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointMvcBinding.json("wrong", SampleEndpoint.Request::class, SampleEndpoint.Response::class, HttpMethod.POST, "/x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EndpointMvcBinding.json(SampleEndpoint.OPERATION_NAME, SampleEndpoint.BrokenRequest::class, OtherEndpoint.Response::class, HttpMethod.POST, "/x")
        }
    }

    @Test
    fun `rejects unsupported method malformed path duplicates deterministically`() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointMvcBinding.json(SampleEndpoint.OPERATION_NAME, SampleEndpoint.Request::class, SampleEndpoint.Response::class, HttpMethod.TRACE, "/x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EndpointMvcBinding.json(SampleEndpoint.OPERATION_NAME, SampleEndpoint.Request::class, SampleEndpoint.Response::class, HttpMethod.POST, "relative")
        }
        val first = EndpointMvcBinding.json(SampleEndpoint.OPERATION_NAME, SampleEndpoint.Request::class, SampleEndpoint.Response::class, HttpMethod.POST, "/x")
        val sameOperation = EndpointMvcBinding.json(SampleEndpoint.OPERATION_NAME, SampleEndpoint.Request::class, SampleEndpoint.Response::class, HttpMethod.GET, "/y")
        assertEquals(
            "Duplicate Endpoint HTTP binding for operation 'sample.create'",
            assertThrows(IllegalArgumentException::class.java) { EndpointMvcBindingValidator.validateAll(listOf(first, sameOperation)) }.message,
        )
        val sameRoute = EndpointMvcBinding.json(OtherEndpoint.OPERATION_NAME, OtherEndpoint.Request::class, OtherEndpoint.Response::class, HttpMethod.POST, "/x/")
        assertEquals(
            "Duplicate Endpoint HTTP route 'POST /x'",
            assertThrows(IllegalArgumentException::class.java) { EndpointMvcBindingValidator.validateAll(listOf(first, sameRoute)) }.message,
        )
    }

    @Test
    fun `response policy supports typed redirect header`() {
        val policy = EndpointMvcResponsePolicy.none<SampleEndpoint.Response>(
            status = 302,
            headers = listOf(EndpointMvcResponseHeader.property("Location", SampleEndpoint.Response::url)),
        )
        assertEquals(302, policy.status)
        assertEquals("/next", policy.headers.single().resolve(SampleEndpoint.Response("/next")))
        assertEquals(EndpointMvcResponseBody.NONE, policy.body)
    }
}

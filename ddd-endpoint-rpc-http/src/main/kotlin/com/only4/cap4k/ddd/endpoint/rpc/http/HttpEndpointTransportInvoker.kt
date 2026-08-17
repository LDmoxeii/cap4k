package com.only4.cap4k.ddd.endpoint.rpc.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.endpoint.rpc.ENDPOINT_RPC_PROTOCOL_VERSION
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRemoteInvocationException
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcCodec
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcFailureCategory
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcRequestEnvelope
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcResponseEnvelope
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcResponseEnvelopeValidator
import com.only4.cap4k.ddd.endpoint.rpc.EndpointTransportInvoker
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import kotlin.reflect.KClass

class HttpEndpointTransportInvoker(
    private val routeResolver: EndpointRpcRouteResolver,
    private val codec: EndpointRpcCodec,
    private val objectMapper: ObjectMapper,
    private val contextAccessor: ExecutionContextAccessor,
    private val contextCodecs: ExecutionContextCodecRegistry,
    connectTimeout: Duration = Duration.ofSeconds(3),
    private val responseTimeout: Duration = Duration.ofSeconds(10),
    private val requestCustomizer: EndpointRpcHttpRequestCustomizer = EndpointRpcHttpRequestCustomizer.NONE,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build(),
) : EndpointTransportInvoker {
    init {
        require(connectTimeout > Duration.ZERO) { "Endpoint RPC connect timeout must be positive" }
        require(responseTimeout > Duration.ZERO) { "Endpoint RPC response timeout must be positive" }
    }

    override fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> invoke(
        serviceId: String,
        operationName: String,
        request: REQUEST,
        requestType: KClass<REQUEST>,
        responseType: KClass<RESPONSE>,
    ): RESPONSE {
        val route = try {
            StaticEndpointRpcRouteResolver.endpoint(routeResolver.resolve(serviceId))
        } catch (failure: EndpointRemoteInvocationException) {
            throw failure
        } catch (_: Exception) {
            throw remote(EndpointRpcFailureCategory.ROUTE, serviceId, operationName)
        }
        val envelope = try {
            EndpointRpcRequestEnvelope(
                protocolVersion = ENDPOINT_RPC_PROTOCOL_VERSION,
                codec = codec.identity,
                serviceId = serviceId,
                operationName = operationName,
                payload = codec.encode(request, requestType),
                context = contextCodecs.encode(contextAccessor.current(), ExecutionContextBoundary.RPC),
            )
        } catch (_: Exception) {
            throw remote(EndpointRpcFailureCategory.TRANSPORT, serviceId, operationName)
        }
        val httpRequest = try {
            val additionalHeaders = linkedMapOf<String, String>()
            requestCustomizer.customize(serviceId, operationName, additionalHeaders)
            val reservedHeader = additionalHeaders.keys.firstOrNull { it.lowercase() in RESERVED_HEADERS }
            require(reservedHeader == null) {
                "Endpoint RPC request customizer must not replace transport-owned headers"
            }
            val body = objectMapper.writeValueAsString(envelope)
            val builder = HttpRequest.newBuilder(route)
                .timeout(responseTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
            additionalHeaders.forEach(builder::header)
            builder.build()
        } catch (_: Exception) {
            throw remote(EndpointRpcFailureCategory.TRANSPORT, serviceId, operationName)
        }
        val response = try {
            client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        } catch (_: HttpTimeoutException) {
            throw remote(EndpointRpcFailureCategory.TIMEOUT, serviceId, operationName)
        } catch (_: ConnectException) {
            throw remote(EndpointRpcFailureCategory.CONNECTION, serviceId, operationName)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw remote(EndpointRpcFailureCategory.TRANSPORT, serviceId, operationName)
        } catch (_: Exception) {
            throw remote(EndpointRpcFailureCategory.TRANSPORT, serviceId, operationName)
        }
        if (response.statusCode() !in 200..299) {
            throw EndpointRemoteInvocationException(
                EndpointRpcFailureCategory.TRANSPORT,
                serviceId,
                operationName,
                response.statusCode(),
            )
        }
        val rpcResponse = try {
            objectMapper.readValue(response.body(), EndpointRpcResponseEnvelope::class.java).also {
                require(it.protocolVersion == ENDPOINT_RPC_PROTOCOL_VERSION)
                require(it.codec == codec.identity)
                EndpointRpcResponseEnvelopeValidator.validate(it)
            }
        } catch (_: Exception) {
            throw remote(EndpointRpcFailureCategory.PROTOCOL, serviceId, operationName)
        }
        if (!rpcResponse.success) {
            val failure = requireNotNull(rpcResponse.failure)
            throw EndpointRemoteInvocationException(
                failure.category,
                serviceId,
                operationName,
                response.statusCode(),
                failure.correlationId,
            )
        }
        return try {
            codec.decode(requireNotNull(rpcResponse.payload), responseType)
        } catch (_: Exception) {
            throw remote(EndpointRpcFailureCategory.RESPONSE_CODEC, serviceId, operationName)
        }
    }

    private fun remote(
        category: EndpointRpcFailureCategory,
        serviceId: String,
        operationName: String,
    ) = EndpointRemoteInvocationException(category, serviceId, operationName)

    private companion object {
        val RESERVED_HEADERS = setOf("content-type", "accept", "host", "content-length")
    }
}
